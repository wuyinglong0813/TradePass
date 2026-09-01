function resolveVersion() {
  try {
    const account = wx.getAccountInfoSync();
    const miniProgram = account && account.miniProgram ? account.miniProgram : {};
    const envLabels = { develop: '开发版', trial: '体验版', release: '正式版' };
    const label = envLabels[miniProgram.envVersion] || '当前版本';
    return miniProgram.version ? `v${miniProgram.version} · ${label}` : label;
  } catch (e) {
    return '当前版本';
  }
}

Page({
  data: {
    versionText: '当前版本'
  },

  onLoad() {
    this.setData({ versionText: resolveVersion() });
  },

  openLegal(e) {
    const type = e.currentTarget.dataset.type;
    wx.navigateTo({ url: `/pages/legal-document/legal-document?type=${type}` });
  },

  openHelp() {
    wx.navigateTo({ url: '/pages/help-center/help-center' });
  }
});

module.exports = { resolveVersion };
