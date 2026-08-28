const { request } = require('../../utils/request');

Page({
  data: {
    title: '正在确认处理结果',
    message: '请稍候，正在同步最新状态',
    loading: true,
    failed: false,
    options: {}
  },

  onLoad(options) {
    this.setData({ options });
    this.syncResult();
  },

  async syncResult() {
    const options = this.data.options || {};
    try {
      let result;
      if (options.scene === 'personal') {
        result = await request({ url: '/fadada/users/me/identity/sync', method: 'POST', withCompany: false });
      } else if (options.scene === 'company' || options.scene === 'seal') {
        result = await request({
          url: `/fadada/companies/${options.companyId}/identity/sync`, method: 'POST'
        });
      } else if (options.scene === 'contract' || options.scene === 'abolish') {
        result = await request({ url: `/contracts/${options.contractId}/signing/sync`, method: 'POST' });
      }
      this.setData({
        loading: false,
        failed: false,
        title: '处理结果已同步',
        message: (result && (result.statusText || result.status)) || '你可以返回业务页面继续操作'
      });
      setTimeout(() => this.goBusinessPage(), 700);
    } catch (error) {
      this.setData({
        loading: false,
        failed: true,
        title: '结果同步暂未完成',
        message: error.message || '稍后返回业务页面刷新即可'
      });
    }
  },

  goBusinessPage() {
    const options = this.data.options || {};
    if (options.scene === 'personal') {
      wx.redirectTo({ url: '/pages/personal-cert/personal-cert' });
      return;
    }
    if (options.scene === 'company' || options.scene === 'seal') {
      wx.redirectTo({ url: '/pages/company-cert/company-cert' });
      return;
    }
    if (options.contractId) {
      wx.redirectTo({ url: `/pages/contract-preview/contract-preview?contractId=${options.contractId}` });
      return;
    }
    wx.switchTab({ url: '/pages/index/index' });
  }
});
