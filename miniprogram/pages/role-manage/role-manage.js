const { request } = require('../../utils/request');
const app = getApp();

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
      const [permDefs, list] = await Promise.all([
        request({ url: '/permissions' }),
        request({ url: `/roles?companyId=${cid}` })
      ]);
      // 缓存权限定义供后续使用
      this._permDefs = permDefs || [];
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
    const defs = this._permDefs || [];
    return arr.map(v => {
      const def = defs.find(p => p.code === v.trim());
      return def ? def.label : v;
    }).join('、');
  },

  showAddRole() {
    const defs = this._permDefs || [];
    this.setData({
      showModal: true, editRoleId: '', roleName: '',
      allPerms: defs.map(p => ({ code: p.code, label: p.label, checked: false }))
    });
  },

  editRole(e) {
    const { id, name, perms } = e.currentTarget.dataset;
    let selected = [];
    try { selected = JSON.parse(perms); } catch (e) { selected = (perms || '').split(',').map(s => s.trim()); }
    const defs = this._permDefs || [];
    this.setData({
      showModal: true, editRoleId: id, roleName: name,
      allPerms: defs.map(p => ({ code: p.code, label: p.label, checked: selected.includes(p.code) }))
    });
  },

  hideModal() { this.setData({ showModal: false }); },

  onNameInput(e) { this.setData({ roleName: e.detail.value }); },

  togglePerm(e) {
    const code = e.currentTarget.dataset.code;
    const allPerms = this.data.allPerms.map(p =>
      p.code === code ? { ...p, checked: !p.checked } : p
    );
    this.setData({ allPerms });
  },

  async saveRole() {
    const name = this.data.roleName.trim();
    if (!name) { wx.showToast({ title: '请输入角色名', icon: 'none' }); return; }
    const perms = this.data.allPerms.filter(p => p.checked).map(p => p.code);
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
