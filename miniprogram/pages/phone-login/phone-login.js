const app = getApp();

Page({
  data: {
    agreed: false,
    phoneNumber: '',
    smsCode: '',
    codeSending: false,
    countdown: 0,
    showAgreement: false,
    agreementTitle: '',
    agreementType: ''
  },

  toggleAgree() { this.setData({ agreed: !this.data.agreed }); },

  noop() {},

  onPhoneInput(e) { this.setData({ phoneNumber: e.detail.value }); },

  onCodeInput(e) { this.setData({ smsCode: e.detail.value }); },

  /* 发送验证码 */
  sendSmsCode() {
    if (this.data.codeSending) return;
    if (this.data.phoneNumber.length < 11) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }
    // TODO: 接入真实短信服务后，调用后端发送验证码接口
    this.setData({ codeSending: true, countdown: 60 });
    this._countdownTimer = setInterval(() => {
      const t = this.data.countdown - 1;
      if (t <= 0) {
        clearInterval(this._countdownTimer);
        this.setData({ codeSending: false, countdown: 0 });
      } else {
        this.setData({ countdown: t });
      }
    }, 1000);
    wx.showToast({ title: '验证码已发送（模拟）', icon: 'none' });
  },

  /* 登录 */
  onLogin() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意协议', icon: 'none' });
      return;
    }
    if (this.data.phoneNumber.length < 11) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' });
      return;
    }
    if (this.data.smsCode.length < 4) {
      wx.showToast({ title: '请输入验证码', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '登录中...' });

    const loginWithCode = (code) => {
      wx.request({
        url: `${app.globalData.baseUrl}/auth/wechat-login`,
        method: 'POST',
        data: { code, phone: this.data.phoneNumber },
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
          console.error('手机号登录请求失败:', JSON.stringify(err));
          wx.showToast({ title: (err && err.errMsg) || '网络错误', icon: 'none' });
        }
      });
    };

    if (app.globalData.isLocalDevelopment) {
      loginWithCode(`dev-phone-${this.data.phoneNumber.slice(-4)}`);
      return;
    }

    wx.login({
      success: ({ code }) => {
        loginWithCode(code);
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
