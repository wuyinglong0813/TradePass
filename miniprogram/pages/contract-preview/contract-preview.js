Page({
  data: {
    contractId: '',
    contractName: '',
    counterpartyName: '',
    // 合同详情
    contract: null,
    // Tab
    tabs: [
      { key: 'detail', label: '合同详情' },
      { key: 'sales', label: '销售单' },
      { key: 'logistics', label: '物流单' },
      { key: 'invoice', label: '发票浏览' }
    ],
    activeTab: 'detail',
    // 列表
    salesList: [],
    logisticsList: [],
    invoiceList: [],
    loading: false,
    // 结构化合同
    hasStructured: false,
    sData: null,
    // 详情面板
    showDetail: false,
    detailTitle: '',
    detailFields: []
  },

  onLoad(options) {
    const contractId = options.contractId || '';
    const contractName = decodeURIComponent(options.contractName || '');
    const counterpartyName = decodeURIComponent(options.counterpartyName || '');
    this.setData({ contractId, contractName, counterpartyName });
    setTimeout(() => this.loadContractDetail(), 100);
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.activeTab) return;
    this.setData({ activeTab: tab });
    if (tab === 'sales' && this.data.salesList.length === 0) {
      this.loadSalesOrders();
    }
  },

  /* 加载合同详情 */
  async loadContractDetail() {
    const { request } = require('../../utils/request');
    try {
      const contract = await request({
        url: `/contracts/${this.data.contractId}`
      });
      const statusMap = {
        PENDING: '待签署',
        ACTIVE: '履行中',
        REJECTED: '已拒绝',
        COMPLETED: '已完成'
      };
      // 尝试解析结构化合同数据
      let sData = null;
      try {
        sData = JSON.parse(contract.terms || '');
        if (!sData || (!sData.fields && !sData.sections)) sData = null;
      } catch (e) { /* 非 JSON，使用旧版显示 */ }

      this.setData({
        contract: {
          ...contract,
          statusText: statusMap[contract.status] || contract.status,
          amount: contract.amount || 0
        },
        hasStructured: !!sData,
        sData
      });
    } catch (e) {
      wx.showToast({ title: '加载合同失败', icon: 'none' });
    }
  },

  /* 加载销售单 */
  async loadSalesOrders() {
    const { request } = require('../../utils/request');
    try {
      const orders = await request({
        url: `/orders?counterpartyName=${encodeURIComponent(this.data.counterpartyName)}`
      });
      const filteredSales = (orders || []).slice(0, 5).map(o => ({
        id: parseInt(o.id),
        orderNo: `SO-${String(o.id).padStart(4, '0')}`,
        product: '贸易订单',
        qty: 1,
        amount: o.amount || 0,
        date: o.orderDate || '',
        status: o.status === 'CONFIRMED' ? '已确认' : o.status,
        direction: o.direction || '采购'
      }));
      this.setData({ salesList: filteredSales });
    } catch (e) { /* 静默 */ }

    if (this.data.logisticsList.length === 0) {
      this.setData({
        logisticsList: [
          { id: 1, trackingNo: 'SF1234567890', carrier: '顺丰速运', status: '运输中', weight: '25kg', date: '2026-07-02', from: '深圳', to: '上海', contact: '张师傅 138****5678', desc: '电子产品' },
          { id: 2, trackingNo: 'YT0987654321', carrier: '圆通快递', status: '已签收', weight: '12kg', date: '2026-07-01', from: '广州', to: '北京', contact: '李师傅 139****1234', desc: '办公用品' }
        ],
        invoiceList: [
          { id: 1, invoiceNo: 'INV-20260701', type: '增值税专用发票', amount: 50000, taxRate: '13%', status: '已开票', date: '2026-07-02' },
          { id: 2, invoiceNo: 'INV-20260702', type: '增值税专用发票', amount: 25000, taxRate: '13%', status: '待开票', date: '2026-07-03' }
        ]
      });
    }
  },

  /* 列表项点击 → 显示详情面板 */
  onItemTap(e) {
    const { type, item } = e.currentTarget.dataset;
    if (type === '销售单') {
      this.showSalesDetail(item);
    } else if (type === '物流单') {
      this.showLogisticsDetail(item);
    } else if (type === '发票') {
      this.showInvoiceDetail(item);
    }
  },

  showSalesDetail(o) {
    this.setData({
      showDetail: true,
      detailTitle: '销售单详情',
      detailFields: [
        { label: '订单编号', value: o.orderNo },
        { label: '订单状态', value: o.status, highlight: true },
        { label: '交易方向', value: o.direction === '采购' ? '采购订单' : '销售订单' },
        { label: '商品名称', value: o.product },
        { label: '数量', value: `${o.qty} 件` },
        { label: '订单金额', value: `¥${o.amount}`, amount: true },
        { label: '对方公司', value: this.data.counterpartyName },
        { label: '订单日期', value: o.date }
      ]
    });
  },

  showLogisticsDetail(l) {
    const steps = [];
    if (l.status === '已签收') {
      steps.push(
        { title: '已揽收', time: l.date, done: true },
        { title: '运输中', time: l.date, done: true },
        { title: '已签收', time: l.date, done: true }
      );
    } else {
      steps.push(
        { title: '已揽收', time: l.date, done: true },
        { title: '运输中', time: l.date, done: true, active: true },
        { title: '派送中', time: '', done: false },
        { title: '已签收', time: '', done: false }
      );
    }
    this.setData({
      showDetail: true,
      detailTitle: '物流单详情',
      detailSteps: steps,
      detailFields: [
        { label: '运单号', value: l.trackingNo },
        { label: '承运商', value: l.carrier },
        { label: '物流状态', value: l.status, highlight: true },
        { label: '货物描述', value: l.desc || '--' },
        { label: '重量', value: l.weight },
        { label: '发货地', value: l.from || '--' },
        { label: '目的地', value: l.to || '--' },
        { label: '联系方式', value: l.contact || '--' }
      ]
    });
  },

  showInvoiceDetail(inv) {
    this.setData({
      showDetail: true,
      detailTitle: '发票详情',
      detailFields: [
        { label: '发票编号', value: inv.invoiceNo },
        { label: '发票类型', value: inv.type },
        { label: '开票状态', value: inv.status, highlight: true },
        { label: '发票金额', value: `¥${inv.amount}`, amount: true },
        { label: '税率', value: inv.taxRate },
        { label: '开票日期', value: inv.date || '--' }
      ]
    });
  },

  closeDetail() {
    this.setData({ showDetail: false, detailSteps: [] });
  },

  noop() {}
});
