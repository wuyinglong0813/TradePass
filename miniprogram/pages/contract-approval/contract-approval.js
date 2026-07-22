const { request } = require('../../utils/request');

Page({
  data: {
    contracts: [],
    loading: false
  },

  onShow() {
    this.loadPending();
  },

  async loadPending() {
    this.setData({ loading: true });
    try {
      const list = await request({ url: '/contracts/pending' });
      const statusMap = { ACTIVE: '履行中', PENDING: '待签署', REJECTED: '已拒绝', COMPLETED: '已完成' };
      const contracts = (list || []).map(c => ({
        id: parseInt(c.id),
        name: c.name,
        counterpartyName: c.viewerCounterpartyName || c.counterpartyName,
        templateName: c.templateName || '',
        amount: c.amount || 0,
        startDate: c.startDate || '',
        endDate: c.endDate || '',
        terms: c.terms || '',
        status: c.status,
        statusText: statusMap[c.status] || c.status,
        createdAt: (c.createdAt || '').substring(0, 10)
      }));
      this.setData({ contracts });
    } catch (e) { /* 静默 */ }
    finally { this.setData({ loading: false }); }
  },

  /* 审批通过 */
  async approve(e) {
    const contract = e.currentTarget.dataset.contract;
    wx.showModal({
      title: '确认签署',
      content: `确认签署合同"${contract.name}"？\n金额：¥${contract.amount}\n签署后合同将生效`,
      success: async (res) => {
        if (!res.confirm) return;
        try {
          wx.showLoading({ title: '处理中...' });
          await request({ url: `/contracts/${contract.id}/approve`, method: 'POST' });
          wx.hideLoading();
          wx.showToast({ title: '合同已签署生效', icon: 'success' });
          this.loadPending();
        } catch (e) {
          wx.hideLoading();
          wx.showToast({ title: e.message, icon: 'none' });
        }
      }
    });
  },

  /* 拒绝 */
  async reject(e) {
    const contract = e.currentTarget.dataset.contract;
    wx.showModal({
      title: '拒绝合同',
      content: `确定拒绝合同"${contract.name}"？`,
      success: async (res) => {
        if (!res.confirm) return;
        try {
          wx.showLoading({ title: '处理中...' });
          await request({ url: `/contracts/${contract.id}/reject`, method: 'POST' });
          wx.hideLoading();
          wx.showToast({ title: '已拒绝', icon: 'none' });
          this.loadPending();
        } catch (e) {
          wx.hideLoading();
          wx.showToast({ title: e.message, icon: 'none' });
        }
      }
    });
  }
});
