const app = getApp();

Page({
  data: {
    agreed: false,
    phoneNumber: '',
    showAgreement: false,
    agreementTitle: '',
    agreementType: ''
  },

  onShow() {
    const token = wx.getStorageSync('tradepass_token');
    if (token) {
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 300);
    }
  },

  toggleAgree() {
    this.setData({ agreed: !this.data.agreed });
  },

  openUserAgreement() {
    this.setData({ showAgreement: true, agreementTitle: '用户许可使用协议', agreementType: 'user' });
  },

  openPrivacyAgreement() {
    this.setData({ showAgreement: true, agreementTitle: '隐私协议', agreementType: 'privacy' });
  },

  closeAgreement() {
    this.setData({ showAgreement: false });
  },

  onPhoneInput(e) {
    this.setData({ phoneNumber: e.detail.value });
  },

  /** 微信一键登录 */
  handleLogin() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '登录中...' });
    wx.getUserProfile({
      desc: '用于完善会员资料',
      success: (res) => {
        this.doWxLogin(res.userInfo.nickName, res.userInfo.avatarUrl);
      },
      fail: () => {
        this.doWxLogin('', '');
      }
    });
  },

  /** 手机号快捷登录 */
  quickPhoneLogin(e) {
    if (!this.data.agreed) return;
    if (e.detail.errMsg !== 'getPhoneNumber:ok') return;
    wx.showLoading({ title: '登录中...' });
    // 真实环境需要解密手机号，这里先走微信登录
    wx.getUserProfile({
      desc: '用于完善会员资料',
      success: (res) => {
        this.doWxLogin(res.userInfo.nickName, res.userInfo.avatarUrl);
      },
      fail: () => {
        this.doWxLogin('', '');
      }
    });
  },

  /** 输入手机号登录 */
  phoneLogin() {
    if (this.data.phoneNumber.length < 11) return;
    wx.showLoading({ title: '登录中...' });
    wx.getUserProfile({
      desc: '用于完善会员资料',
      success: (res) => {
        this.doWxLogin(res.userInfo.nickName, res.userInfo.avatarUrl);
      },
      fail: () => {
        this.doWxLogin('', '');
      }
    });
  },

  doWxLogin(nickName, avatarUrl) {
    wx.login({
      success: ({ code }) => {
        wx.request({
          url: `${app.globalData.baseUrl}/auth/wechat-login`,
          method: 'POST',
          data: {
            code,
            nickName: nickName || '微信用户',
            avatarUrl: avatarUrl || '',
            phone: this.data.phoneNumber || ''
          },
          success: ({ data }) => {
            wx.hideLoading();
            if (data && data.code === 0 && data.data && data.data.token) {
              app.globalData.token = data.data.token;
              wx.setStorageSync('tradepass_token', data.data.token);
              app.loadMe().then(() => {
                wx.switchTab({ url: '/pages/index/index' });
              });
            } else {
              wx.showToast({ title: '登录失败', icon: 'none' });
            }
          },
          fail: () => {
            wx.hideLoading();
            wx.showToast({ title: '网络错误', icon: 'none' });
          }
        });
      }
    });
  },

  skipLogin() {
    wx.switchTab({ url: '/pages/index/index' });
  }
});
