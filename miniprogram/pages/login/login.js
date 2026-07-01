const app = getApp();

Page({
  data: {
    agreed: false,
    phoneNumber: '',
    showPhoneModal: false,
    showAgreement: false,
    showBubble: false,
    agreementTitle: '',
    agreementType: '',
    // 小程序完成企业认证、开通「手机号验证组件」后改为 true，即走真实快捷登录
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

  /* 点击主登录按钮：未同意协议先提示；已开通快捷登录则交给 getPhoneNumber；否则走临时 dev 登录 */
  onPrimaryTap() {
    if (!this.data.agreed) { this.showAgreeBubble(); return; }
    if (this.data.quickPhoneEnabled) return; // 交给 bindgetphonenumber 处理
    this.devLogin();
  },

  /* 临时假数据登录：用后端已有的 dev 账号（满帅/法人），认证完成后删除即可 */
  devLogin() {
    wx.showLoading({ title: '登录中...' });
    this.postLogin({ code: 'dev-openid-001', nickName: '满帅' });
  },

  showAgreeBubble() {
    if (this._bubbleTimer) clearTimeout(this._bubbleTimer);
    this.setData({ showBubble: true });
    this._bubbleTimer = setTimeout(() => this.setData({ showBubble: false }), 2000);
  },

  openUserAgreement() {
    this.setData({ showAgreement: true, agreementTitle: '用户许可使用协议', agreementType: 'user' });
  },
  openPrivacyAgreement() {
    this.setData({ showAgreement: true, agreementTitle: '隐私协议', agreementType: 'privacy' });
  },
  closeAgreement() { this.setData({ showAgreement: false }); },

  showPhoneInput() {
    if (!this.data.agreed) { this.showAgreeBubble(); return; }
    this.setData({ showPhoneModal: true });
  },
  hidePhoneModal() { this.setData({ showPhoneModal: false }); },
  onPhoneInput(e) { this.setData({ phoneNumber: e.detail.value }); },

  /* 微信一键登录 */
  handleLogin() {
    if (!this.data.agreed) { wx.showToast({ title: '请先阅读并同意协议', icon: 'none' }); return; }
    wx.showLoading({ title: '登录中...' });
    wx.getUserProfile({
      desc: '用于完善会员资料',
      success: res => this.doLogin(res.userInfo.nickName, res.userInfo.avatarUrl, ''),
      fail: () => this.doLogin('', '', '')
    });
  },

  /* 手机号快捷登录 */
  quickPhoneLogin(e) {
    if (!this.data.agreed) return;
    console.log('getPhoneNumber 回调:', JSON.stringify(e.detail));
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showToast({ title: '获取手机号失败：' + e.detail.errMsg, icon: 'none' });
      return;
    }
    // e.detail.code 是动态令牌，传后端换取真实手机号
    wx.showLoading({ title: '登录中...' });
    this.doLogin('', '', '', e.detail.code);
  },

  /* 输入手机号登录 */
  phoneLogin() {
    if (this.data.phoneNumber.length < 11) return;
    this.hidePhoneModal();
    wx.showLoading({ title: '登录中...' });
    wx.getUserProfile({
      desc: '用于完善会员资料',
      success: res => this.doLogin(res.userInfo.nickName, res.userInfo.avatarUrl, this.data.phoneNumber),
      fail: () => this.doLogin('', '', this.data.phoneNumber)
    });
  },

  doLogin(nickName, avatarUrl, phone, phoneCode) {
    wx.login({
      success: ({ code }) => this.postLogin({ code, nickName, avatarUrl, phone, phoneCode })
    });
  },

  postLogin(payload) {
    wx.request({
      url: `${app.globalData.baseUrl}/auth/wechat-login`,
      method: 'POST',
      data: payload,
      success: ({ data }) => {
        wx.hideLoading();
        console.log('登录接口返回:', JSON.stringify(data));
        if (data && data.code === 0 && data.data && data.data.token) {
          app.globalData.token = data.data.token;
          wx.setStorageSync('tradepass_token', data.data.token);
          const realPhone = data.data.user && data.data.user.phone;
          wx.showToast({ title: realPhone ? ('手机号：' + realPhone) : '登录成功', icon: 'none' });
          app.loadMe().then(() => setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 800));
        } else {
          wx.showToast({ title: '登录失败：' + (data && data.message || '未知'), icon: 'none' });
        }
      },
      fail: () => { wx.hideLoading(); wx.showToast({ title: '网络错误', icon: 'none' }); }
    });
  }
});
