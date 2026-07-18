const app = getApp();
const { request } = require('../../utils/request');

Page({
  data: {
    agreed: false,
    showAgreement: false,
    agreementTitle: '',
    agreementType: '',
    // 小程序完成企业认证、开通「手机号验证组件」后改为 true
    quickPhoneEnabled: false
  },

  onLoad() {
    this.setData({ quickPhoneEnabled: !app.globalData.isLocalDevelopment });
  },

  async onShow() {
    await app.ensureSessionReady();
    if (app.globalData.token) wx.switchTab({ url: '/pages/index/index' });
  },

  toggleAgree() { this.setData({ agreed: !this.data.agreed }); },

  noop() {},

  skipLogin() { wx.switchTab({ url: '/pages/index/index' }); },

  /* 快捷登录未开通时，模拟 dev 登录 */
  onWechatPhoneTap() {
    if (this.data.quickPhoneEnabled) return; // 已开通由 open-type 处理
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
    }
    this.loginWithPayload({ code: 'dev-openid-001', nickName: '满帅', phone: '18800000001' });
  },

  /* 跳转输入手机号登录页 */
  goPhoneLogin() {
    if (!app.globalData.isLocalDevelopment) {
      wx.showToast({ title: '短信登录服务暂未开放', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: '/pages/phone-login/phone-login' });
  },

  /* 微信手机号快捷登录 */
  quickPhoneLogin(e) {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
    }
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showToast({ title: '获取手机号失败', icon: 'none' });
      return;
    }
    wx.login({
      success: ({ code }) => {
        this.loginWithPayload({ code, phoneCode: e.detail.code });
      },
      fail: () => wx.showToast({ title: '微信登录失败', icon: 'none' })
    });
  },

  async loginWithPayload(payload) {
    wx.showLoading({ title: '登录中...' });
    try {
      const session = await request({
        url: '/auth/wechat-login',
        method: 'POST',
        data: payload,
        auth: false,
        withCompany: false
      });
      await app.establishSession(session);
      wx.showToast({ title: '登录成功', icon: 'success' });
      wx.switchTab({ url: '/pages/index/index' });
    } catch (error) {
      wx.showToast({ title: error.message || '登录失败', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  /* 协议弹窗 */
  openUserAgreement() {
    this.setData({ showAgreement: true, agreementTitle: '用户许可使用协议', agreementType: 'user' });
  },
  openPrivacyAgreement() {
    this.setData({ showAgreement: true, agreementTitle: '隐私协议', agreementType: 'privacy' });
  },
  closeAgreement() { this.setData({ showAgreement: false }); }
});
