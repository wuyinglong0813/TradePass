Page({
  data: {
    contractId: '',
    contractName: '',
    counterpartyName: '',
    // Tab
    tabs: [
      { key: 'sales', label: '销售单' },
      { key: 'logistics', label: '物流单' },
      { key: 'invoice', label: '发票浏览' }
    ],
    activeTab: 'sales',
    // 列表
    salesList: [],
    logisticsList: [],
    invoiceList: [],
    loading: false
  },

  onLoad(options) {
    const contractId = options.contractId || '';
    const contractName = decodeURIComponent(options.contractName || '');
    const counterpartyName = decodeURIComponent(options.counterpartyName || '');
    this.setData({ contractId, contractName, counterpartyName });
    // 异步加载
    setTimeout(() => this.loadContractAndOrders(), 100);
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.activeTab) return;
    this.setData({ activeTab: tab });
  },

  /* 加载合同详情 + 销售单（TODO：物流单和发票后续接独立接口） */
  async loadContractAndOrders() {
    const { request } = require('../../utils/request');
    const cid = this.data.contractId;

    // 加载该合同的销售单（通过 orders 接口过滤）
    try {
      const orders = await request({
        url: `/orders?counterpartyName=${encodeURIComponent(this.data.counterpartyName)}`
      });
      const filteredSales = (orders || []).slice(0, 5).map(o => ({
        id: parseInt(o.id),
        orderNo: `SO-${o.id}`,
        product: '贸易订单',
        qty: 1,
        amount: o.amount || 0,
        date: o.orderDate || '',
        status: o.status === 'CONFIRMED' ? '已确认' : o.status
      }));
      this.setData({ salesList: filteredSales });
    } catch (e) { /* 静默 */ }

    // 物流单和发票暂用模拟数据
    this.setData({
      logisticsList: [
        { id: 1, trackingNo: 'SF1234567890', carrier: '顺丰速运', status: '运输中', weight: '25kg', date: '2026-07-02' },
        { id: 2, trackingNo: 'YT0987654321', carrier: '圆通快递', status: '已签收', weight: '12kg', date: '2026-07-01' }
      ],
      invoiceList: [
        { id: 1, invoiceNo: 'INV-20260701', type: '增值税专用发票', amount: 50000, taxRate: '13%', status: '已开票', date: '2026-07-02' },
        { id: 2, invoiceNo: 'INV-20260702', type: '增值税专用发票', amount: 25000, taxRate: '13%', status: '待开票', date: '2026-07-03' }
      ]
    });
  },

  /* 列表项点击 */
  onItemTap(e) {
    const { type, id } = e.currentTarget.dataset;
    wx.showToast({ title: `${type} 详情 #${id}（开发中）`, icon: 'none' });
  }
});
