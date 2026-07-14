const app = getApp();

Page({
  data: {
    agreed: false,
    showAgreement: false,
    agreementTitle: '',
    agreementType: '',
    // 小程序完成企业认证、开通「手机号验证组件」后改为 true
    quickPhoneEnabled: false
  },

  onShow() {
    if (wx.getStorageSync('tradepass_token')) {
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 300);
    }
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
    wx.showLoading({ title: '登录中...' });
    wx.request({
      url: `${app.globalData.baseUrl}/auth/wechat-login`,
      method: 'POST',
      data: { code: 'dev-openid-001', nickName: '满帅', phone: '18800000001' },
      success: ({ data }) => {
        wx.hideLoading();
        if (data && data.code === 0 && data.data && data.data.token) {
          app.globalData.token = data.data.token;
          wx.setStorageSync('tradepass_token', data.data.token);
          wx.showToast({ title: '登录成功', icon: 'none' });
          app.loadMe().then(() => setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 800));
        } else {
          wx.showToast({ title: '登录失败：' + (data && data.message || '未知'), icon: 'none' });
        }
      },
      fail: (err) => {
        wx.hideLoading();
        console.error('手机号快捷登录请求失败:', JSON.stringify(err));
        wx.showToast({ title: (err && err.errMsg) || '网络错误', icon: 'none' });
      }
    });
  },

  /* 跳转输入手机号登录页 */
  goPhoneLogin() {
    wx.navigateTo({ url: '/pages/phone-login/phone-login' });
  },

  /* 微信手机号快捷登录 */
  quickPhoneLogin(e) {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
    }
    console.log('getPhoneNumber 回调:', JSON.stringify(e.detail));
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showToast({ title: '获取手机号失败', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '登录中...' });
    wx.login({
      success: ({ code }) => {
        wx.request({
          url: `${app.globalData.baseUrl}/auth/wechat-login`,
          method: 'POST',
          data: { code, phoneCode: e.detail.code },
          success: ({ data }) => {
            wx.hideLoading();
            console.log('登录接口返回:', JSON.stringify(data));
            if (data && data.code === 0 && data.data && data.data.token) {
              app.globalData.token = data.data.token;
              wx.setStorageSync('tradepass_token', data.data.token);
              wx.showToast({ title: '登录成功', icon: 'none' });
              app.loadMe().then(() => setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 800));
            } else {
              wx.showToast({ title: '登录失败：' + (data && data.message || '未知'), icon: 'none' });
            }
          },
          fail: (err) => {
            wx.hideLoading();
            console.error('微信手机号登录请求失败:', JSON.stringify(err));
            wx.showToast({ title: (err && err.errMsg) || '网络错误', icon: 'none' });
          }
        });
      },
      fail: () => { wx.hideLoading(); wx.showToast({ title: '微信登录失败', icon: 'none' }); }
    });
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
