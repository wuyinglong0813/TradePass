const { request } = require('../../utils/request');
const dict = require('../../utils/dict');
const app = getApp();

function hasPerm(perm) {
  const member = app.globalData.memberInfo;
  if (!member || !member.permissions) return false;
  const perms = member.permissions;
  if (perms.includes('all')) return true;
  return perms.includes(perm);
}

Page({
  data: {
    isLoggedIn: false,
    hasCompany: false,
    company: {},
    companyNameFirst: '',
    certBadge: { text: '', color: '' },
    certProgress: 0,
    member: {},
    canManage: false,
    canContractTemplate: false,
    companies: [],
    currentCompanyId: '',
    todos: [],
    showJoinModal: false,
    joinCode: ''
  },

  onShow() {
    const loggedIn = !!(app.globalData.token || wx.getStorageSync('tradepass_token'));
    this.setData({ isLoggedIn: loggedIn });
    if (!loggedIn) return;
    this.loadData();
  },

  onPullDownRefresh() {
    this.loadData().finally(() => wx.stopPullDownRefresh());
  },

  async loadData() {
    try {
      const payload = await request({ url: '/me' });
      const company = payload.company || {};
      const member = payload.member || {};
      const companies = payload.companies || [];
      const hasCompany = !!(member && member.roleCode && member.roleCode !== 'GUEST') && companies.length > 0;
      const canManage = member.roleCode === 'LEGAL' || member.roleCode === 'ADMIN';

      // 认证进度
      const certStatus = company.certificationStatus || '';
      let certProgress = 0;
      if (certStatus === 'CERTIFIED') certProgress = 100;
      else if (certStatus === 'IN_PROGRESS') certProgress = 60;
      else if (certStatus === 'PENDING') certProgress = 20;
      else certProgress = 0;

      this.setData({
        hasCompany,
        company,
        companyNameFirst: (company.name || '企')[0],
        canContractTemplate: canManage && hasPerm('contract_template'),
        certBadge: dict.certification(certStatus),
        certProgress,
        member,
        canManage,
        companies,
        currentCompanyId: (payload.user && payload.user.currentCompanyId) || ''
      });
      this.loadTodos();
    } catch (e) {}
  },

  async loadTodos() {
    try {
      const todos = await request({ url: '/me/todos' });
      // 给每种待办加上图标
      const iconMap = { APPROVAL: '👤', CERT: '🏅', CONTRACT: '📋' };
      const enhanced = (todos || []).map(t => ({ ...t, icon: iconMap[t.type] || '📋' }));
      this.setData({ todos: enhanced });
    } catch (e) { this.setData({ todos: [] }); }
  },

  goTodo(e) {
    const target = e.currentTarget.dataset.target;
    const map = {
      'auth-manage': '/pages/auth-manage/auth-manage',
      'company-cert': '/pages/company-cert/company-cert',
      'contract-approval': '/pages/contract-approval/contract-approval'
    };
    if (map[target]) wx.navigateTo({ url: map[target] });
  },

  switchCompany() {
    const companies = this.data.companies;
    if (companies.length <= 1) return;
    wx.showActionSheet({
      itemList: companies.map(c => c.companyName),
      success: async (res) => {
        try {
          await app.switchCompany(companies[res.tapIndex].companyId);
          this.loadData();
        } catch (e) { wx.showToast({ title: '切换失败', icon: 'none' }); }
      }
    });
  },

  goCreateCompany() { wx.navigateTo({ url: '/pages/company-bind/company-bind' }); },
  goCert() { wx.navigateTo({ url: '/pages/company-cert/company-cert' }); },
  goAuthManage() { wx.navigateTo({ url: '/pages/auth-manage/auth-manage' }); },
  goRoleManage() { wx.navigateTo({ url: '/pages/role-manage/role-manage' }); },
  goContractTemplate() { wx.navigateTo({ url: '/pages/contract-template/contract-template' }); },

  openJoin() { this.setData({ showJoinModal: true, joinCode: '' }); },
  closeJoin() { this.setData({ showJoinModal: false }); },
  onJoinInput(e) { this.setData({ joinCode: e.detail.value }); },
  noop() {},

  async submitJoin() {
    const code = this.data.joinCode.trim();
    if (!code) { wx.showToast({ title: '请输入邀请码', icon: 'none' }); return; }
    try {
      const result = await request({ url: '/companies/join', method: 'POST', data: { code } });
      wx.showToast({ title: result.message || '已提交', icon: 'none' });
      this.setData({ showJoinModal: false });
      app.loadMe().then(() => this.loadData());
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    }
  }
});
