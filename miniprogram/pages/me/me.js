const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    user: {},
    userDisplayName: '用户',
    memberRoleText: '加载中',
    company: {},
    canManageAuth: false,
    companies: [],
    currentCompanyId: '',
    devUsers: [{ label: '加载中...' }],
    devUserIndex: 0
  },

  onShow() {
    const loggedIn = !!(app.globalData.token || wx.getStorageSync('tradepass_token'));
    this.setData({ isLoggedIn: loggedIn });
    if (!loggedIn) return;
    this.loadMe();
    this.loadDevUsers();
  },

  goLogin() {
    wx.reLaunch({ url: '/pages/login/login' });
  },

  async loadMe() {
    try {
      const payload = await request({ url: '/me' });
      const company = payload.company || {};
      const member = payload.member || {};
      const canManage = member.roleCode === 'LEGAL' || member.roleCode === 'ADMIN';
      const companies = payload.companies || [];
      this.setData({
        user: payload.user || {},
        userDisplayName: (payload.user && payload.user.nickname) || '用户',
        memberRoleText: member.roleText || '未分配角色',
        company,
        companies,
        currentCompanyId: (payload.user && payload.user.currentCompanyId) || '',
        canManageAuth: canManage
      });
    } catch (e) {}
  },

  goAuthManage() {
    wx.navigateTo({ url: '/pages/auth-manage/auth-manage' });
  },

  goRoleManage() {
    wx.navigateTo({ url: '/pages/role-manage/role-manage' });
  },

  goCompanyCert() {
    wx.navigateTo({ url: '/pages/company-cert/company-cert' });
  },

  // ---- Dev ----
  async loadDevUsers() {
    try {
      const list = await request({ url: '/dev/users' });
      const users = (list || []).map(u => ({ label: `${u.name}（${u.roleText}）`, value: u.id }));
      const cur = app.globalData.memberInfo;
      const idx = users.findIndex(u => u.value === (cur && cur.userId));
      this.setData({ devUsers: users, devUserIndex: idx >= 0 ? idx : 0 });
    } catch (e) {}
  },

  async onSwitchUser(e) {
    const user = this.data.devUsers[parseInt(e.detail.value)];
    try {
      await app.switchUser(user.value);
      wx.showToast({ title: '已切换', icon: 'success' });
      this.loadMe();
    } catch (e) { wx.showToast({ title: '切换失败', icon: 'none' }); }
  }
});
