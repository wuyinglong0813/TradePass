const { request } = require('../../utils/request');
const dict = require('../../utils/dict');
const app = getApp();

function companyAbbr(name) {
  const clean = (name || '企业').replace(/有限公司|有限责任公司|股份有限公司/g, '');
  return clean.slice(0, 2) || '企业';
}

Page({
  data: {
    isLoggedIn: false,
    user: {},
    userDisplayName: '用户',
    userNameFirst: '用',
    maskedPhone: '',
    memberRoleText: '加载中',
    company: {},
    companyAbbr: '企业',
    companyVerified: false,
    companyStatusText: '未认证',
    realNameVerified: false,
    canManageAuth: false,
    companies: [],
    currentCompanyId: '',
    devUsers: [{ label: '加载中...' }],
    devUserIndex: 0,
    devOpen: false
  },

  onShow() {
    const loggedIn = !!(app.globalData.token || wx.getStorageSync('tradepass_token'));
    const isDev = !!app.globalData.isLocalDevelopment;
    this.setData({ isLoggedIn: loggedIn, isDev });
    if (!loggedIn) return;
    this.loadMe();
    if (isDev) this.loadDevUsers();
  },

  onPullDownRefresh() {
    if (!this.data.isLoggedIn) {
      wx.stopPullDownRefresh();
      return;
    }
    this.loadMe().finally(() => wx.stopPullDownRefresh());
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' });
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
        companyAbbr: companyAbbr(company.name),
        companyVerified: company.certificationStatus === 'VERIFIED',
        companyStatusText: dict.certification(company.certificationStatus).text,
        realNameVerified: company.realNameStatus === 'VERIFIED',
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
  openAccountSecurity() {
    if (!this.data.isLoggedIn) {
      this.goLogin();
      return;
    }
    wx.showModal({
      title: '账号与安全',
      content: `登录手机号：${this.data.maskedPhone}\n实名状态：${this.data.realNameVerified ? '已实名' : '待实名'}\n当前账号状态正常`,
      showCancel: false,
      confirmText: '我知道了'
    });
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
      content: '商签通 v1.0\n连接交易双方，让合同凭证可信流转。\n\n提供企业认证、组织授权、合同协同与履约对账等服务。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },
  openHelp() {
    wx.showModal({
      title: '帮助与反馈',
      content: '如需帮助，请在企业中心查看成员、合同和对账状态。反馈入口将在正式客服渠道接入后开放。',
      showCancel: false,
      confirmText: '我知道了'
    });
  },
  showProfileCode() {
    wx.showModal({
      title: '我的商签通身份',
      content: `用户：${this.data.userDisplayName}\n当前身份：${this.data.memberRoleText}\n企业：${this.data.company.name || '暂未加入企业'}`,
      showCancel: false,
      confirmText: '我知道了'
    });
  },
  goCompanyCenter() {
    wx.switchTab({ url: '/pages/company/company' });
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

  openCompanySwitcher() {
    const companies = this.data.companies || [];
    if (companies.length === 0) {
      this.goCompanyCenter();
      return;
    }
    if (companies.length === 1) {
      this.goCompanyCenter();
      return;
    }
    wx.showActionSheet({
      itemList: companies.map(item => `${item.companyName} · ${item.roleText}`),
      success: async ({ tapIndex }) => {
        const target = companies[tapIndex];
        if (!target || target.companyId === this.data.currentCompanyId) return;
        try {
          await app.switchCompany(target.companyId);
          wx.showToast({ title: '企业已切换', icon: 'success' });
          await this.loadMe();
        } catch (error) {
          wx.showToast({ title: '切换失败', icon: 'none' });
        }
      }
    });
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
