Page({
  data: {
    agreed: false,
    showAgreementModal: false
  },

  onLoad() {
    if (wx.getStorageSync('privacy_agreed')) {
      wx.reLaunch({ url: '/pages/index/index' });
    }
  },

  toggleAgree() {
    this.setData({ agreed: !this.data.agreed });
  },

  showAgreement() {
    this.setData({ showAgreementModal: true });
  },

  closeAgreement() {
    this.setData({ showAgreementModal: false });
  },

  noop() {},

  doAgree() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意隐私保护指引', icon: 'none' });
      return;
    }
    wx.setStorageSync('privacy_agreed', true);
    wx.reLaunch({ url: '/pages/index/index' });
  },

  doDeny() {
    wx.showModal({
      title: '提示',
      content: '需要同意隐私保护指引才能使用小程序。',
      showCancel: false,
      confirmText: '知道了'
    });
  }
});
