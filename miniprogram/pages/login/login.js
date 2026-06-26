const app = getApp();

Page({
  data: {
    avatarUrl: '',
    nickName: '',
    token: ''
  },

  onShow() {
    const token = wx.getStorageSync('tradepass_token');
    if (token) {
      this.setData({ token });
      // 已有 token，等一下直接跳
      setTimeout(() => {
        wx.switchTab({ url: '/pages/index/index' });
      }, 500);
    }
  },

  handleLogin() {
    wx.showLoading({ title: '登录中...' });
    // 先获取用户信息
    wx.getUserProfile({
      desc: '用于完善会员资料',
      success: (res) => {
        const nickName = res.userInfo.nickName;
        const avatarUrl = res.userInfo.avatarUrl;
        this.setData({ nickName, avatarUrl });

        // 调微信登录
        wx.login({
          success: ({ code }) => {
            wx.request({
              url: `${app.globalData.baseUrl}/auth/wechat-login`,
              method: 'POST',
              data: { code, nickName, avatarUrl },
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
          },
          fail: () => {
            wx.hideLoading();
            wx.showToast({ title: '微信登录失败', icon: 'none' });
          }
        });
      },
      fail: () => {
        wx.hideLoading();
        // 用户拒绝授权，仍可登录
        wx.login({
          success: ({ code }) => {
            wx.request({
              url: `${app.globalData.baseUrl}/auth/wechat-login`,
              method: 'POST',
              data: { code, nickName: '', avatarUrl: '' },
              success: ({ data }) => {
                wx.hideLoading();
                if (data && data.code === 0 && data.data && data.data.token) {
                  app.globalData.token = data.data.token;
                  wx.setStorageSync('tradepass_token', data.data.token);
                  app.loadMe().then(() => {
                    wx.switchTab({ url: '/pages/index/index' });
                  });
                }
              },
              fail: () => { wx.hideLoading(); }
            });
          }
        });
      }
    });
  },

  enterApp() {
    wx.switchTab({ url: '/pages/index/index' });
  }
});
