const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    companyId: '',
    companyName: '',
    creditCode: '',
    legalPersonName: '',
    hasCompany: false,
    agreed: false,
    loading: false,
    submitting: false,
    identity: null,
    actions: []
  },

  onLoad(options) {
    if (options.name) {
      this.setData({
        hasCompany: false,
        companyName: decodeURIComponent(options.name),
        creditCode: decodeURIComponent(options.creditCode || ''),
        legalPersonName: decodeURIComponent(options.legalPersonName || '')
      });
      return;
    }
    this.setData({ hasCompany: true });
    this.loadCompany(false);
  },

  onShow() {
    if (this.data.hasCompany && this.data.companyId) this.loadCompany(true);
  },

  onPullDownRefresh() {
    this.loadCompany(true).finally(() => wx.stopPullDownRefresh());
  },

  async loadCompany(sync) {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const me = await request({ url: '/me' });
      const company = me.company || {};
      const companyId = company.id || app.getCurrentCompanyId();
      if (!companyId) throw new Error('请先选择企业');
      let identity = await request({ url: `/fadada/companies/${companyId}/identity` });
      if (sync && identity && identity.status !== 'NOT_STARTED' && identity.enabled) {
        identity = await request({
          url: `/fadada/companies/${companyId}/identity/sync`, method: 'POST'
        });
      }
      const companyDone = identity && identity.status === 'VERIFIED';
      const sealDone = identity && Number(identity.enabledSealCount || 0) > 0;
      this.setData({
        companyId: String(companyId),
        companyName: company.name || '',
        creditCode: company.creditCode || '',
        legalPersonName: company.legalPersonName || '',
        identity,
        actions: [
          {
            key: 'company', title: '企业认证', desc: '核验企业主体和经办人身份',
            done: companyDone, statusText: identity.statusText || '待认证',
            statusColor: companyDone ? '#20a66a' : '#f59e0b'
          },
          {
            key: 'seal', title: '电子印章', desc: '管理合同签署使用的企业印章',
            done: sealDone, statusText: sealDone ? '已启用' : (companyDone ? '待设置' : '完成企业认证后设置'),
            statusColor: sealDone ? '#20a66a' : '#f59e0b'
          }
        ]
      });
    } catch (error) {
      wx.showToast({ title: error.message || '认证状态加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  toggleAgree() { this.setData({ agreed: !this.data.agreed }); },

  openAgreement(e) {
    if (e.currentTarget.dataset.type === 'privacy') {
      wx.navigateTo({ url: '/pages/privacy/privacy' });
      return;
    }
    wx.showToast({ title: '协议内容待法务审核后开放', icon: 'none' });
  },

  async createAndAuthenticate() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意相关协议', icon: 'none' });
      return;
    }
    if (this.data.submitting) return;
    this.setData({ submitting: true });
    try {
      const personalIdentity = await request({
        url: '/fadada/users/me/identity', withCompany: false
      });
      if (!personalIdentity || personalIdentity.status !== 'VERIFIED') {
        wx.showModal({
          title: '请先完成个人认证',
          content: '创建企业前，需要先确认当前申请人的实名身份。',
          confirmText: '去认证',
          success: result => {
            if (result.confirm) wx.navigateTo({ url: '/pages/personal-cert/personal-cert' });
          }
        });
        return;
      }
      const created = await request({
        url: '/companies', method: 'POST',
        data: {
          name: this.data.companyName,
          creditCode: this.data.creditCode,
          legalPersonName: this.data.legalPersonName
        }
      });
      await request({
        url: '/me/company', method: 'POST',
        data: {
          id: created.id,
          name: created.name,
          creditCode: created.creditCode,
          legalPersonName: created.legalPersonName
        }
      });
      await app.loadMe();
      this.setData({ companyId: String(created.id), hasCompany: true });
      this.openService('company', created.id);
    } catch (error) {
      wx.showToast({ title: error.message || '企业创建失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  async handleAction(e) {
    const key = e.currentTarget.dataset.key;
    const identity = this.data.identity || {};
    if (!identity.enabled) {
      wx.showModal({ title: '认证服务未启用', content: '请联系管理员完成服务配置。', showCancel: false });
      return;
    }
    if (key === 'seal' && identity.status !== 'VERIFIED') {
      wx.showToast({ title: '请先完成企业认证', icon: 'none' });
      return;
    }
    if (key === 'company') {
      try {
        const personalIdentity = await request({
          url: '/fadada/users/me/identity', withCompany: false
        });
        if (!personalIdentity || personalIdentity.status !== 'VERIFIED') {
          wx.showModal({
            title: '请先完成个人认证',
            content: '企业认证前，需要先确认当前申请人的实名身份。',
            confirmText: '去认证',
            success: result => {
              if (result.confirm) wx.navigateTo({ url: '/pages/personal-cert/personal-cert' });
            }
          });
          return;
        }
      } catch (error) {
        wx.showToast({ title: error.message || '个人认证状态获取失败', icon: 'none' });
        return;
      }
    }
    this.openService(key, this.data.companyId);
  },

  openService(scene, companyId) {
    wx.navigateTo({
      url: `/pages/fadada-auth/fadada-auth?scene=${scene}&companyId=${companyId}`
    });
  },

  refreshStatus() { this.loadCompany(true); }
});
