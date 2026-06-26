const app = getApp();

Page({
  data: {
    avatarUrl: '',
    nickName: '',
    phone: '',
    hasLogin: false
  },

  onLoad() {
    // 如果已经登录，直接进入
    if (app.globalData.token && app.globalData.userInfo) {
      wx.switchTab({ url: '/pages/index/index' });
    }
  },

  /** 获取用户头像和昵称 */
  getUserProfile() {
    wx.getUserProfile({
      desc: '用于完善会员资料',
      success: (res) => {
        this.setData({
          avatarUrl: res.userInfo.avatarUrl,
          nickName: res.userInfo.nickName,
          hasLogin: true
        });
      },
      fail: () => {
        // 用户拒绝授权，仍然允许以默认信息登录
        this.setData({
          avatarUrl: '',
          nickName: '微信用户',
          hasLogin: true
        });
      }
    });
  },

  /** 获取手机号（需要企业认证的小程序） */
  getPhoneNumber(e) {
    if (e.detail.errMsg !== 'getPhoneNumber:ok') return;
    // 手机号加密数据需要后端解密，demo 阶段暂存
    this.setData({ phone: '已授权' });
  },

  /** 进入应用 */
  enterApp() {
    wx.showLoading({ title: '登录中...' });

    const doRequest = () => {
      wx.login({
        success: ({ code }) => {
          wx.request({
            url: `${app.globalData.baseUrl}/auth/wechat-login`,
            method: 'POST',
            data: {
              code: code,
              nickName: this.data.nickName || '微信用户',
              avatarUrl: this.data.avatarUrl || ''
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
                wx.showToast({ title: '登录失败，请重试', icon: 'none' });
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
    };

    doRequest();
  }
});
