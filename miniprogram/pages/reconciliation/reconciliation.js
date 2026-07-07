Page({
  data: {
    counterpartyName: '',
    // 概览
    overview: {
      totalSales: 0,
      totalInvoiced: 0,
      matched: 0,
      unmatched: 0
    },
    // 对账明细
    items: []
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    this.setData({ counterpartyName: name });
    setTimeout(() => this.loadData(), 100);
  },

  loadData() {
    // TODO: 后端接口 GET /reconciliation?counterpartyName=xxx
    this.setData({
      overview: {
        totalSales: 85000,
        totalInvoiced: 75000,
        matched: 75000,
        unmatched: 10000
      },
      items: [
        { id: 1, orderNo: 'SO-20260701', salesAmount: 50000, invoiceNo: 'INV-20260701', invoiceAmount: 50000,
          status: 'MATCHED', statusText: '已对平', diff: 0 },
        { id: 2, orderNo: 'SO-20260702', salesAmount: 25000, invoiceNo: 'INV-20260702', invoiceAmount: 25000,
          status: 'MATCHED', statusText: '已对平', diff: 0 },
        { id: 3, orderNo: 'SO-20260703', salesAmount: 10000, invoiceNo: '—', invoiceAmount: 0,
          status: 'UNMATCHED', statusText: '未开票', diff: 10000 }
      ]
    });
  },

  onItemTap(e) {
    const item = e.currentTarget.dataset.item;
    wx.showToast({ title: `对账详情 #${item.id}（开发中）`, icon: 'none' });
  }
});
