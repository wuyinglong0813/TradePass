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

function companyAbbr(name) {
  const clean = (name || '企业').replace(/有限公司|有限责任公司|股份有限公司/g, '');
  return clean.slice(0, 2) || '企业';
}

function maskCreditCode(code) {
  const value = code || '';
  if (!value) return '暂未录入';
  if (value.length <= 8) return value;
  return `${value.slice(0, 4)}••••${value.slice(-4)}`;
}

Page({
  data: {
    isLoggedIn: false,
    hasCompany: false,
    company: {},
    companyAbbr: '企业',
    maskedCreditCode: '暂未录入',
    certBadge: { text: '', color: '' },
    certCompleted: false,
    member: {},
    canManage: false,
    canContractTemplate: false,
    memberCount: 0,
    roleCount: 0,
    templateCount: 0,
    companies: [],
    certificationApplications: [],
    currentCompanyId: '',
    todos: [],
    showJoinModal: false,
    joinCode: '',
    showInventoryModal: false,
    showCompanySwitcher: false,
    switchingCompanyId: ''
  },

  onShow() {
    const loggedIn = !!(app.globalData.token || wx.getStorageSync('tradepass_token'));
    this.setData({ isLoggedIn: loggedIn });
    if (!loggedIn) return;
    this.loadData();
  },

  onPullDownRefresh() {
    if (!this.data.isLoggedIn) {
      wx.stopPullDownRefresh();
      return;
    }
    this.loadData().finally(() => wx.stopPullDownRefresh());
  },

  async loadData() {
    try {
      const payload = await request({ url: '/me' });
      const certificationApplications = await request({ url: '/me/company-certification-applications' }).catch(() => []);
      const company = payload.company || {};
      const member = payload.member || {};
      const companies = payload.companies || [];
      const hasCompany = !!(member && member.roleCode && member.roleCode !== 'GUEST') && companies.length > 0;
      const canManage = member.roleCode === 'LEGAL' || member.roleCode === 'ADMIN';
      const certStatus = company.certificationStatus || '';
      const permissions = member.permissions || [];
      const canContractTemplate = canManage && (permissions.includes('all') || permissions.includes('contract_template') || hasPerm('contract_template'));
      const currentCompanyId = (payload.user && payload.user.currentCompanyId) || '';
      const companyItems = companies.map(item => ({ ...item, initial: companyAbbr(item.companyName) }));

      this.setData({
        hasCompany,
        company,
        companyAbbr: companyAbbr(company.name),
        maskedCreditCode: maskCreditCode(company.creditCode),
        canContractTemplate,
        certBadge: dict.certification(certStatus),
        certCompleted: certStatus === 'VERIFIED',
        member,
        canManage,
        companies: companyItems,
        certificationApplications: (certificationApplications || []).map(item => ({
          ...item,
          statusText: item.status === 'APPROVED' ? '已通过' : item.status === 'REJECTED' ? '已驳回' : '审核中'
        })),
        currentCompanyId
      });
      await Promise.all([
        this.loadTodos(),
        this.loadEnterpriseMetrics(currentCompanyId || company.id, canManage, canContractTemplate)
      ]);
    } catch (e) {}
  },

  async loadEnterpriseMetrics(companyId, canManage, canContractTemplate) {
    if (!companyId || !canManage) {
      this.setData({ memberCount: 0, roleCount: 0, templateCount: 0 });
      return;
    }
    const safe = (promise, fallback) => promise.catch(() => fallback);
    const [members, roles, templates] = await Promise.all([
      safe(request({ url: `/authorizations?companyId=${companyId}&status=ACTIVE&page=1&size=1` }), { total: 0 }),
      safe(request({ url: `/roles?companyId=${companyId}` }), []),
      canContractTemplate ? safe(request({ url: '/contract-templates?page=1&size=1' }), { total: 0 }) : Promise.resolve({ total: 0 })
    ]);
    this.setData({
      memberCount: Number(members.total || 0),
      roleCount: (roles || []).length,
      templateCount: Number(templates.total || 0)
    });
  },

  async loadTodos() {
    try {
      const todos = await request({ url: '/me/todos' });
      const iconMap = {
        APPROVAL: '/images/icons/team.svg',
        CERT: '/images/icons/company.svg',
        CONTRACT: '/images/icons/approval.svg'
      };
      const enhanced = (todos || []).map(t => ({ ...t, iconPath: iconMap[t.type] || '/images/icons/contracts.svg' }));
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
    if (this.data.companies.length === 0) return;
    this.setData({ showCompanySwitcher: true, switchingCompanyId: '' });
  },

  closeCompanySwitcher() {
    if (this.data.switchingCompanyId) return;
    this.setData({ showCompanySwitcher: false });
  },

  async selectCompanyFromSwitcher(e) {
    const companyId = String(e.currentTarget.dataset.companyId || '');
    const company = this.data.companies.find(item => String(item.companyId) === companyId);
    if (!company || this.data.switchingCompanyId) return;
    if (companyId === String(this.data.currentCompanyId)) {
      this.setData({ showCompanySwitcher: false });
      return;
    }
    try {
      this.setData({ switchingCompanyId: companyId });
      await app.switchCompany(companyId);
      this.setData({ showCompanySwitcher: false, switchingCompanyId: '' });
      wx.showToast({ title: '企业已切换', icon: 'success' });
      await this.loadData();
    } catch (error) {
      this.setData({ switchingCompanyId: '' });
      wx.showToast({ title: '切换失败', icon: 'none' });
    }
  },

  goCreateCompany() {
    this.setData({ showCompanySwitcher: false });
    wx.navigateTo({ url: '/pages/company-bind/company-bind' });
  },
  goPhoneLogin() { wx.navigateTo({ url: '/pages/login/login' }); },
  goCert() { wx.navigateTo({ url: '/pages/company-cert/company-cert' }); },
  goAuthManage() { wx.navigateTo({ url: '/pages/auth-manage/auth-manage' }); },
  goRoleManage() { wx.navigateTo({ url: '/pages/role-manage/role-manage' }); },
  goContractTemplate() { wx.navigateTo({ url: '/pages/contract-template/contract-template' }); },
  goDocumentTemplate() { wx.navigateTo({ url: '/pages/document-template/document-template' }); },
  goInventory() {
    this.setData({ showInventoryModal: true });
  },
  closeInventoryModal() { this.setData({ showInventoryModal: false }); },

  openJoin() { this.setData({ showCompanySwitcher: false, showJoinModal: true, joinCode: '' }); },
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
