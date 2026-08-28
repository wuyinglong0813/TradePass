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
    options: {}
  },

  onLoad(options) {
    const scene = SCENES[options.scene] ? options.scene : 'personal';
    const config = SCENES[scene];
    this.setData({ scene, options, pageTitle: config.title, loadingText: config.loading });
    wx.setNavigationBarTitle({ title: config.title });
    this.loadServiceUrl();
  },

  async loadServiceUrl() {
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
      this.setData({ serviceUrl });
    } catch (error) {
      this.setData({ errorMessage: error.message || '服务页面加载失败' });
    } finally {
      this.setData({ loading: false });
    }
  },

  onWebViewError() {
    this.setData({ errorMessage: '服务页面打开失败，请检查网络或小程序业务域名配置。' });
  },

  retry() { this.loadServiceUrl(); },
  goBack() { wx.navigateBack(); }
});
