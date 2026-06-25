const { request } = require('../../utils/request');
const app = getApp();

const ALL_PERM_DEFS = [
  { value: 'supplier_view', label: '供方首页' },
  { value: 'buyer_view', label: '需方首页' },
  { value: 'counterparty_manage', label: '供方公司管理' },
  { value: 'order_view', label: '订单查看' },
  { value: 'order_create', label: '下单' },
  { value: 'invoice_view', label: '发票查看' },
  { value: 'reconciliation', label: '对账' },
  { value: 'member_manage', label: '成员管理' },
  { value: 'auth_manage', label: '授权管理' },
  { value: 'company_manage', label: '企业认证' },
  { value: 'seal_manage', label: '电子章管理' }
];

Page({
  data: {
    roles: [],
    showModal: false,
    editRoleId: '',
    roleName: '',
    allPerms: []
  },

  onShow() { this.loadRoles(); },

  async loadRoles() {
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    try {
      const list = await request({ url: `/roles?companyId=${cid}` });
      this.setData({ roles: (list || []).map(r => ({
        ...r,
        permsDisplay: this.formatPerms(r.permissions)
      })) });
    } catch (e) {}
  },

  formatPerms(permissions) {
    if (!permissions) return '无';
    let arr = [];
    try { arr = JSON.parse(permissions); } catch (e) { arr = permissions.split(','); }
    if (arr.length === 0) return '无';
    if (arr.includes('all')) return '全部权限';
    return arr.map(v => (ALL_PERM_DEFS.find(p => p.value === v.trim()) || { label: v }).label).join('、');
  },

  showAddRole() {
    this.setData({
      showModal: true, editRoleId: '', roleName: '',
      allPerms: ALL_PERM_DEFS.map(p => ({ ...p, checked: false }))
    });
  },

  editRole(e) {
    const { id, name, perms } = e.currentTarget.dataset;
    let selected = [];
    try { selected = JSON.parse(perms); } catch (e) { selected = (perms || '').split(',').map(s => s.trim()); }
    this.setData({
      showModal: true, editRoleId: id, roleName: name,
      allPerms: ALL_PERM_DEFS.map(p => ({ ...p, checked: selected.includes(p.value) }))
    });
  },

  hideModal() { this.setData({ showModal: false }); },

  onNameInput(e) { this.setData({ roleName: e.detail.value }); },

  togglePerm(e) {
    const val = e.currentTarget.dataset.value;
    const allPerms = this.data.allPerms.map(p =>
      p.value === val ? { ...p, checked: !p.checked } : p
    );
    this.setData({ allPerms });
  },

  async saveRole() {
    const name = this.data.roleName.trim();
    if (!name) { wx.showToast({ title: '请输入角色名', icon: 'none' }); return; }
    const perms = this.data.allPerms.filter(p => p.checked).map(p => p.value);
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    try {
      if (this.data.editRoleId) {
        await request({ url: `/roles/${this.data.editRoleId}`, method: 'PUT', data: { companyId: cid, name, permissions: perms } });
      } else {
        await request({ url: '/roles', method: 'POST', data: { companyId: cid, name, permissions: perms } });
      }
      wx.showToast({ title: '已保存', icon: 'success' });
      this.hideModal();
      this.loadRoles();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async deleteRole() {
    const res = await new Promise(r => wx.showModal({ title: '确认删除', content: '删除后不可恢复', success: r }));
    if (!res.confirm) return;
    try {
      await request({ url: `/roles/${this.data.editRoleId}`, method: 'DELETE' });
      wx.showToast({ title: '已删除', icon: 'success' });
      this.hideModal();
      this.loadRoles();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  }
});
