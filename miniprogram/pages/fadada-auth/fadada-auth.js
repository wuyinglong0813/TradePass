const { request } = require('../../utils/request');

const SCENES = {
  personal: { title: '个人认证', loading: '正在打开个人认证', endpoint: '/fadada/users/me/auth-url', withCompany: false },
  company: { title: '企业认证', loading: '正在打开企业认证' },
  seal: { title: '电子印章', loading: '正在打开印章管理' },
  contract: { title: '合同签署', loading: '正在打开合同签署' },
  abolish: { title: '合同作废签署', loading: '正在打开作废签署' }
};

Page({
  data: {
    loading: true,
    serviceUrl: '',
    errorMessage: '',
    scene: 'personal',
    pageTitle: '认证服务',
    loadingText: '正在连接安全服务',
    options: {},
    serviceHost: ''
  },

  onLoad(options) {
    const scene = SCENES[options.scene] ? options.scene : 'personal';
    const config = SCENES[scene];
    this.setData({ scene, options, pageTitle: config.title, loadingText: config.loading });
    wx.setNavigationBarTitle({ title: config.title });
    this.loadServiceUrl();
  },

  onShow() {
    if (this.data.serviceUrl && !this.pollTimer) this.startStatusPolling();
  },

  onHide() { this.stopStatusPolling(); },
  onUnload() { this.stopStatusPolling(); },

  async loadServiceUrl() {
    if (this._loadingServiceUrl) return;
    this._loadingServiceUrl = true;
    const { scene, options } = this.data;
    const config = SCENES[scene];
    this.setData({ loading: true, serviceUrl: '', errorMessage: '' });
    try {
      let endpoint = config.endpoint;
      if (scene === 'company') endpoint = `/fadada/companies/${options.companyId}/auth-url`;
      if (scene === 'seal') endpoint = `/fadada/companies/${options.companyId}/seal-manage-url`;
      if (scene === 'contract') endpoint = `/contracts/${options.contractId}/sign-url`;
      if (scene === 'abolish') endpoint = `/contracts/${options.contractId}/abolish-url`;
      if (!endpoint) throw new Error('服务参数不完整');
      const result = await request({
        url: endpoint,
        method: 'POST',
        data: scene === 'abolish' ? { reason: decodeURIComponent(options.reason || '') } : {},
        withCompany: config.withCompany !== false,
        timeout: 30000
      });
      const serviceUrl = result && (result.url || result.authUrl);
      if (!serviceUrl) throw new Error('未获取到服务地址');
      this.setData({ serviceUrl, serviceHost: this.extractHost(serviceUrl) });
      this.startStatusPolling();
    } catch (error) {
      this.setData({ errorMessage: error.message || '服务页面加载失败' });
    } finally {
      this._loadingServiceUrl = false;
      this.setData({ loading: false });
    }
  },

  onWebViewError() {
    this.stopStatusPolling();
    const host = this.data.serviceHost;
    this.setData({
      errorMessage: host
        ? `服务页面打开失败，请在小程序业务域名中检查：${host}`
        : '服务页面打开失败，请检查网络或小程序业务域名配置。'
    });
  },

  startStatusPolling() {
    const { scene } = this.data;
    if (!['personal', 'company', 'seal'].includes(scene) || this.pollTimer) return;
    this.pollAttempts = 0;
    this.scheduleStatusPoll();
  },

  scheduleStatusPoll() {
    this.stopStatusPolling();
    if ((this.pollAttempts || 0) >= 120) return;
    this.pollTimer = setTimeout(() => this.pollStatus(), 2500);
  },

  async pollStatus() {
    this.pollTimer = null;
    const { scene, options } = this.data;
    try {
      let result;
      if (scene === 'personal') {
        result = await request({
          url: '/fadada/users/me/identity/sync', method: 'POST', withCompany: false,
          timeout: 15000
        });
      } else {
        result = await request({
          url: `/fadada/companies/${options.companyId}/identity/sync`, method: 'POST',
          timeout: 15000
        });
      }
      const completed = scene === 'seal'
        ? Number((result && result.enabledSealCount) || 0) > 0
        : !!(result && ['VERIFIED', 'FAILED'].includes(result.status));
      if (completed) {
        this.openReturnPage();
        return;
      }
    } catch (error) {
      // The account may not exist at the provider while the user is still filling the form.
      // Keep polling and let the provider page remain interactive.
    }
    this.pollAttempts = (this.pollAttempts || 0) + 1;
    this.scheduleStatusPoll();
  },

  openReturnPage() {
    this.stopStatusPolling();
    const { scene, options } = this.data;
    const query = [`scene=${scene}`];
    if (options.companyId) query.push(`companyId=${encodeURIComponent(options.companyId)}`);
    if (options.contractId) query.push(`contractId=${encodeURIComponent(options.contractId)}`);
    wx.redirectTo({ url: `/pages/service-return/service-return?${query.join('&')}` });
  },

  stopStatusPolling() {
    if (this.pollTimer) clearTimeout(this.pollTimer);
    this.pollTimer = null;
  },

  extractHost(value) {
    const match = String(value || '').match(/^https:\/\/([^/?#]+)/i);
    return match ? match[1] : '';
  },

  retry() { this.loadServiceUrl(); },
  goBack() { wx.navigateBack(); }
});
