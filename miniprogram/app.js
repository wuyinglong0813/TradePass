App({
  globalData: {
    baseUrl: 'https://tradepass-274155-4-1446724178.sh.run.tcloudbase.com/api',
    token: '',
    userInfo: null,
    memberInfo: null,
    pendingInvite: null  // 待处理的邀请 { code, type }
  },

  onLaunch() {
    const token = wx.getStorageSync('tradepass_token');
    if (token) {
      this.globalData.token = token;
      this.loadMe();
      return;
    }
    this.doLogin();
  },

  doLogin() {
    wx.login({
      success: ({ code }) => {
        wx.request({
          url: `${this.globalData.baseUrl}/auth/wechat-login`,
          method: 'POST',
          data: { code: code },
          success: ({ data }) => {
            if (data && data.code === 0 && data.data && data.data.token) {
              this.globalData.token = data.data.token;
              wx.setStorageSync('tradepass_token', data.data.token);
              this.loadMe();
            }
          }
        });
      }
    });
  },

  loadMe() {
    return new Promise((resolve) => {
      wx.request({
        url: `${this.globalData.baseUrl}/me`,
        method: 'GET',
        header: { Authorization: this.globalData.token },
        success: ({ data }) => {
          if (data && data.code === 0 && data.data) {
            this.globalData.userInfo = data.data.user;
            this.globalData.memberInfo = data.data.member;
            this.globalData.companies = data.data.companies || [];
          }
          resolve();
        },
        fail: () => resolve()
      });
    });
  },

  /** 切换当前操作的企业 */
  async switchCompany(companyId) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}/me/switch-company`,
        method: 'POST',
        data: { companyId },
        header: { Authorization: this.globalData.token },
        success: ({ data }) => {
          if (data && data.code === 0 && data.data) {
            this.globalData.userInfo = data.data.user;
            this.globalData.memberInfo = data.data.member;
            this.globalData.companies = data.data.companies || [];
            resolve(data.data);
          } else {
            reject(new Error('切换失败'));
          }
        },
        fail: reject
      });
    });
  },

  /** 切换 dev 用户后刷新全局状态 */
  async switchUser(userId) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}/dev/switch-user`,
        method: 'POST',
        data: { userId },
        success: ({ data }) => {
          if (data && data.code === 0) {
            this.loadMe().then(() => resolve(data.data));
          } else {
            reject(new Error('切换失败'));
          }
        },
        fail: reject
      });
    });
  }
});
