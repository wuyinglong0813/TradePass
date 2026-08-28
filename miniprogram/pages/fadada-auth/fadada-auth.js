const { request } = require('../../utils/request');

Page({
  data: {
    loading: true,
    authUrl: '',
    errorMessage: ''
  },

  onLoad(options) {
    if (options.scene !== 'personal') {
      this.setData({ loading: false, errorMessage: '暂不支持该认证类型' });
      return;
    }
    this.loadAuthUrl();
  },

  async loadAuthUrl() {
    this.setData({ loading: true, authUrl: '', errorMessage: '' });
    try {
      const result = await request({
        url: '/fadada/users/me/auth-url',
        method: 'POST',
        withCompany: false,
        timeout: 20000
      });
      if (!result || !result.authUrl) throw new Error('未获取到法大大认证地址');
      this.setData({ authUrl: result.authUrl });
    } catch (error) {
      this.setData({ errorMessage: error.message || '法大大认证页面加载失败' });
    } finally {
      this.setData({ loading: false });
    }
  },

  onWebViewError() {
    this.setData({ errorMessage: '法大大认证页面打开失败，请检查网络或业务域名配置。' });
  },

  retry() {
    this.loadAuthUrl();
  },

  goBack() {
    wx.navigateBack();
  }
});
