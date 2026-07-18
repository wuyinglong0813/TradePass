const { request } = require('../../utils/request');

Page({
  data: {
    counterpartyName: '',
    isGlobal: false,
    role: 'supplier',
    pageTitle: '应收对账',
    loading: false,
    page: 1,
    size: 20,
    hasMore: false,
    overview: {
      totalSales: 0,
      totalInvoiced: 0,
      matched: 0,
      unmatched: 0
    },
    items: []
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    const role = options.role === 'buyer' ? 'buyer' : 'supplier';
    this.setData({
      counterpartyName: name,
      isGlobal: !name,
      role,
      pageTitle: role === 'buyer' ? '应付对账' : '应收对账'
    });
    wx.setNavigationBarTitle({ title: name ? '对账情况' : '对账中心' });
    this.loadData(true);
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.loadData(false);
  },

  async loadData(reset = true) {
    this.setData({ loading: true });
    try {
      const page = reset ? 1 : this.data.page + 1;
      const direction = this.data.role === 'buyer' ? 'PURCHASE' : 'SALE';
      const counterpartyQuery = this.data.counterpartyName
        ? `&counterpartyName=${encodeURIComponent(this.data.counterpartyName)}`
        : '';
      const [payload, summaryPayload] = await Promise.all([
        request({ url: `/orders?page=${page}&size=${this.data.size}&direction=${direction}${counterpartyQuery}` }),
        reset
          ? request({ url: `/orders/summary?direction=${direction}${counterpartyQuery}` })
          : Promise.resolve(null)
      ]);
      const orders = Array.isArray(payload) ? payload : (payload.items || []);
      const nextItems = (orders || []).map(order => ({
        id: order.id,
        counterpartyName: order.counterpartyName,
        orderNo: `ORD-${String(order.id).padStart(6, '0')}`,
        salesAmount: Number(order.amount || 0).toFixed(2),
        invoiceNo: '发票数据未接入',
        invoiceAmount: '0.00',
        status: 'UNMATCHED',
        statusText: '待获取发票',
        diff: Number(order.amount || 0).toFixed(2)
      }));
      const items = reset ? nextItems : this.data.items.concat(nextItems);
      const loadedTotal = items.reduce((sum, order) => sum + Number(order.salesAmount || 0), 0);
      const total = summaryPayload ? Number(summaryPayload.amount || 0) : loadedTotal;
      this.setData({
        overview: reset ? {
          totalSales: total.toFixed(2),
          totalInvoiced: '0.00',
          matched: '0.00',
          unmatched: total.toFixed(2)
        } : this.data.overview,
        items,
        page,
        hasMore: !!payload.hasMore
      });
    } catch (error) {
      this.setData({ items: [] });
      wx.showToast({ title: error.message || '订单数据加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  onItemTap() {
    wx.showToast({ title: '发票数据源接入后可进行逐笔核对', icon: 'none' });
  }
});
