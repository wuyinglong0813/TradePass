const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    user: {},
    userDisplayName: '用户',
    userNameFirst: '用',
    maskedPhone: '',
    memberRoleText: '加载中',
    company: {},
    canManageAuth: false,
    companies: [],
    currentCompanyId: '',
    devUsers: [{ label: '加载中...' }],
    devUserIndex: 0,
    devOpen: false
  },

  onShow() {
    const loggedIn = !!(app.globalData.token || wx.getStorageSync('tradepass_token'));
    let isDev = true; // dev 阶段始终显示
    this.setData({ isLoggedIn: loggedIn, isDev });
    if (!loggedIn) return;
    this.loadMe();
    if (isDev) this.loadDevUsers();
  },

  onPullDownRefresh() {
    this.loadMe().finally(() => wx.stopPullDownRefresh());
  },

  goLogin() {
    wx.reLaunch({ url: '/pages/login/login' });
  },

  logout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: (res) => { if (res.confirm) app.logout(); }
    });
  },

  toggleDev() {
    this.setData({ devOpen: !this.data.devOpen });
  },

  async loadMe() {
    try {
      const payload = await request({ url: '/me' });
      const company = payload.company || {};
      const member = payload.member || {};
      const canManage = member.roleCode === 'LEGAL' || member.roleCode === 'ADMIN';
      const companies = payload.companies || [];
      const user = payload.user || {};
      const nickname = user.nickname || '用户';
      const phone = user.phone || '';

      // 手机号掩码：188****0001
      let maskedPhone = '';
      if (phone && phone.length >= 11) {
        maskedPhone = phone.substring(0, 3) + '****' + phone.substring(7);
      } else if (phone) {
        maskedPhone = phone;
      }

      this.setData({
        user,
        userDisplayName: nickname,
        userNameFirst: nickname[0] || '用',
        maskedPhone: maskedPhone || '未绑定手机号',
        memberRoleText: member.roleText || '未分配角色',
        company,
        companies,
        currentCompanyId: user.currentCompanyId || '',
        canManageAuth: canManage
      });
    } catch (e) {}
  },

  /* 设置菜单 */
  openPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
  },
  openAgreement() {
    wx.showModal({
      title: '用户许可使用协议',
      content: '欢迎使用商签通！本协议是您与商签通之间关于使用商签通微信小程序服务的法律协议。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },
  openAbout() {
    wx.showModal({
      title: '关于商签通',
      content: '商签通 v1.0\n安全合规的企业贸易管理平台\n\n提供企业认证、授权管理、交易排行、电子签章等服务。',
      showCancel: false,
      confirmText: '我知道了'
    });
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
