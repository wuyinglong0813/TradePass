const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    authorizations: [],
    filtered: [],
    keyword: '',
    showApproveModal: false,
    approveTargetId: '',
    approveRoles: [],
    approveCode: ''
  },

  noop() {},

  onSearch(e) {
    const keyword = (e.detail.value || '').trim().toLowerCase();
    const filtered = !keyword ? this.data.authorizations : this.data.authorizations.filter(m =>
      (m.memberName || '').toLowerCase().indexOf(keyword) >= 0 ||
      (m.phone || '').indexOf(keyword) >= 0
    );
    this.setData({ keyword, filtered });
  },

  onShow() {
    this.loadMembers();
    this.loadRoles();
  },

  async loadRoles() {
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    try {
      const list = await request({ url: `/roles?companyId=${cid}` });
      this.setData({ approveRoles: (list || []).map(r => ({ label: r.name, value: r.name, code: r.name })) });
    } catch (e) {}
  },

  async loadMembers() {
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    try {
      const list = await request({ url: `/authorizations?companyId=${cid}` });
      this.setData({ authorizations: list || [] });
      this.onSearch({ detail: { value: this.data.keyword } });
    } catch (e) {}
  },

  inviteCode: '',

  async shareInvite() {
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    try {
      const result = await request({ url: '/companies/invite', method: 'POST', data: { companyId: cid } });
      this.setData({ inviteCode: result.code });
      // 触发分享
      wx.showShareMenu({ withShareTicket: true });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  onShareAppMessage() {
    const code = this.data.inviteCode;
    if (!code) return { title: '商签通', path: '/pages/index/index' };
    return {
      title: '邀请你加入我的企业',
      path: `/pages/index/index?inviteCode=${code}&type=member`
    };
  },

  showApproveModal(e) { this.setData({ showApproveModal: true, approveTargetId: e.currentTarget.dataset.id }); },
  hideApproveModal() { this.setData({ showApproveModal: false, approveTargetId: '' }); },

  async confirmApprove(e) {
    const roleCode = e.currentTarget.dataset.role;
    const memberId = this.data.approveTargetId;
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    try {
      await request({ url: `/authorizations/${memberId}/approve?companyId=${cid}`, method: 'POST', data: { roleCode, customPermissions: [] } });
      wx.showToast({ title: '已通过', icon: 'success' });
      this.hideApproveModal();
      this.loadMembers();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async rejectMember(e) {
    const id = e.currentTarget.dataset.id;
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    const res = await new Promise(r => wx.showModal({ title: '确认拒绝', success: r }));
    if (!res.confirm) return;
    try {
      await request({ url: `/authorizations/${id}/reject?companyId=${cid}`, method: 'POST' });
      wx.showToast({ title: '已拒绝', icon: 'success' });
      this.loadMembers();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async revokeAuth(e) {
    const id = e.currentTarget.dataset.id;
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    const res = await new Promise(r => wx.showModal({ title: '确认移除', success: r }));
    if (!res.confirm) return;
    try {
      await request({ url: `/authorizations/${id}?companyId=${cid}`, method: 'DELETE' });
      wx.showToast({ title: '已移除', icon: 'success' });
      this.loadMembers();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  }
});
