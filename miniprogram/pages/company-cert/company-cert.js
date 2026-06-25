const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: { actions: [] },

  onShow() { this.loadCompany(); },

  async loadCompany() {
    try {
      const me = await request({ url: '/me' });
      const company = me.company || {};
      this.setData({ actions: [
        { key: 'company', title: '上传公司信息', status: company.certificationStatus || 'NOT_SUBMITTED' },
        { key: 'realName', title: '实名认证', status: company.realNameStatus || 'NOT_STARTED' },
        { key: 'face', title: '人脸录入', status: company.faceStatus || 'NOT_STARTED' },
        { key: 'seal', title: '上传电子章', status: company.sealStatus || 'NOT_UPLOADED' }
      ]});
    } catch (e) {}
  },

  async handleAction(e) {
    const key = e.currentTarget.dataset.key;
    const companyId = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    try {
      if (key === 'realName') {
        await request({ url: '/verifications/real-name', method: 'POST', data: { companyId } });
      } else if (key === 'face') {
        await request({ url: '/verifications/face', method: 'POST', data: { companyId } });
      } else if (key === 'seal') {
        await request({ url: '/seals', method: 'POST', data: { companyId, fileUrl: 'https://storage.example.com/demo-seal.png', usage: '合同签署' } });
      } else {
        await request({ url: `/companies/${companyId}/certifications`, method: 'POST', data: {} });
      }
      wx.showToast({ title: '已提交', icon: 'success' });
      this.loadCompany();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  }
});
