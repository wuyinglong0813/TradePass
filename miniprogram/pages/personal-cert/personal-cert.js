const { request } = require('../../utils/request');

const STATUS_VIEW = {
  NOT_STARTED: { title: '待完成个人认证', desc: '完成法大大实名认证后，可建立可信的个人身份。', tone: 'pending', button: '去法大大认证' },
  IN_PROGRESS: { title: '个人认证进行中', desc: '如已完成法大大认证，请返回后刷新认证结果。', tone: 'processing', button: '继续认证' },
  VERIFIED: { title: '个人认证已完成', desc: '你的实名身份已经由法大大认证。', tone: 'success', button: '' },
  FAILED: { title: '个人认证未通过', desc: '请查看失败原因并重新发起认证。', tone: 'failed', button: '重新认证' }
};

const EMPTY_IDENTITY = {
  providerEnabled: false,
  status: 'NOT_STARTED',
  statusText: '待认证',
  verifiedName: '',
  identMethod: '',
  failureReason: '',
  verifiedAt: ''
};

Page({
  data: {
    loading: true,
    syncing: false,
    identity: EMPTY_IDENTITY,
    statusView: STATUS_VIEW.NOT_STARTED,
    providerEnabled: false,
    refreshAfterAuth: false
  },

  onLoad() {
    this.loadIdentity(false);
  },

  onShow() {
    if (!this.data.refreshAfterAuth) return;
    this.setData({ refreshAfterAuth: false });
    this.loadIdentity(true);
  },

  onPullDownRefresh() {
    this.loadIdentity(true).finally(() => wx.stopPullDownRefresh());
  },

  async loadIdentity(sync) {
    if (!this.data.identity || !this.data.identity.status) this.setData({ loading: true });
    try {
      let identity = await request({ url: '/fadada/users/me/identity', withCompany: false });
      if (sync && identity && identity.status !== 'NOT_STARTED' && identity.providerEnabled) {
        this.setData({ syncing: true });
        identity = await request({
          url: '/fadada/users/me/identity/sync',
          method: 'POST',
          withCompany: false
        });
      }
      const status = identity && STATUS_VIEW[identity.status] ? identity.status : 'NOT_STARTED';
      this.setData({
        identity: identity || EMPTY_IDENTITY,
        providerEnabled: !!(identity && identity.providerEnabled),
        statusView: STATUS_VIEW[status]
      });
    } catch (error) {
      wx.showToast({ title: error.message || '认证状态加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false, syncing: false });
    }
  },

  startAuth() {
    if (!this.data.providerEnabled) {
      wx.showModal({
        title: '认证服务未启用',
        content: '法大大个人认证参数尚未配置完成，请联系管理员。',
        showCancel: false
      });
      return;
    }
    this.setData({ refreshAfterAuth: true });
    wx.navigateTo({ url: '/pages/fadada-auth/fadada-auth?scene=personal' });
  },

  refreshStatus() {
    if (this.data.syncing) return;
    this.loadIdentity(true);
  }
});
