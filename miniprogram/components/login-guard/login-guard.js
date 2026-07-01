Component({
  properties: {
    text: {
      type: String,
      value: '没有查到您的信息，请登录后查看'
    }
  },
  methods: {
    goLogin() {
      wx.reLaunch({ url: '/pages/login/login' });
    }
  }
});
