const { request } = require('../../utils/request');

Page({
  data: {
    counterpartyName: '',
    orders: [],
    monthlySales: [],
    actions: [
      { icon: '📝', label: '签订合同' },
      { icon: '📋', label: '合同浏览' },
      { icon: '📦', label: '销售单浏览' },
      { icon: '🔄', label: '退换货单浏览' },
      { icon: '🚚', label: '物流单浏览' },
      { icon: '🧾', label: '发票浏览' },
      { icon: '📄', label: '合同模版' },
      { icon: '💰', label: '对账情况' }
    ]
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    this.setData({ counterpartyName: name });
    this.loadOrders(name);
    this.generateMonthlySales();
  },

  async loadOrders(counterpartyName) {
    try {
      const list = await request({
        url: `/orders?counterpartyName=${encodeURIComponent(counterpartyName)}`
      });
      this.setData({ orders: (list || []).slice(0, 5) });
    } catch (error) {
      // 静默
    }
  },

  generateMonthlySales() {
    const months = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'];
    // 生成演示数据：随机百分比，模拟销售趋势
    const baseValues = [30, 25, 40, 35, 50, 45, 60, 55, 70, 65, 80, 75];
    const sales = months.map((m, i) => ({
      month: m,
      percent: baseValues[i] + Math.floor(Math.random() * 15)
    }));
    this.setData({ monthlySales: sales });
  },

  onAction(event) {
    const action = event.currentTarget.dataset.action;
    wx.showToast({ title: action + '（开发中）', icon: 'none' });
  }
});
