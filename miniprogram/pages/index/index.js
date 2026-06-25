const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    role: 'supplier',
    roleIndex: 0,
    roleOptions: [
      { value: 'supplier', text: '我是供应商' },
      { value: 'buyer', text: '我是采购商' }
    ],
    period: 'total',
    companyName: '',
    companyDisplayName: '企业信息加载中',
    roleText: '',
    roleDisplayText: '请选择身份',
    rankingTitle: '客户销售业绩排名',
    periods: [
      { key: 'total', text: '总', activeClass: 'active' },
      { key: 'year', text: '年', activeClass: '' },
      { key: 'month', text: '月', activeClass: '' }
    ],
    ranking: [],
    showEmpty: false,
    showRanking: false,
    loading: false,
    counterparties: [],
    showJoinForm: false,
    joinCompanyId: '',
    companies: [],
    isLegalPerson: false,
    counterpartyInviteCode: ''
  },

  onLoad(options) {
    if (options.inviteCode) {
      app.globalData.pendingInvite = { code: options.inviteCode, type: options.type || 'member' };
      this.setData({ joinCompanyId: options.inviteCode, showJoinForm: true });
    }
  },

  onShow() {
    this.setData({ companies: app.globalData.companies || [] });
    this.checkMemberStatus();
    this.initRoleFromMember();

    // 处理待处理的邀请
    if (!this.data.showJoinForm && app.globalData.pendingInvite) {
      const invite = app.globalData.pendingInvite;
      const member = app.globalData.memberInfo;
      // 供方邀请：必须是法人
      if (invite.type === 'counterparty') {
        if (!member || member.roleCode !== 'LEGAL') {
          wx.showModal({
            title: '需要法人身份',
            content: '接受供方邀请需要您是公司的法人。请先在"我的"页面完成企业认证。',
            showCancel: false
          });
          app.globalData.pendingInvite = null;
          return;
        }
      }
      // 自动处理邀请
      this.processInvite(invite.code);
      return;
    }

    if (!this.data.showJoinForm) {
      this.loadHome();
      this.loadCounterparties();
    }
  },

  async processInvite(code) {
    const user = app.globalData.userInfo;
    if (!user || !user.id) return;
    try {
      const result = await request({
        url: '/companies/join',
        method: 'POST',
        data: { code, userId: parseInt(user.id) }
      });
      wx.showToast({ title: result.message || '加入成功', icon: 'success' });
      app.globalData.pendingInvite = null;
      app.loadMe();
      setTimeout(() => this.onShow(), 500);
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
      app.globalData.pendingInvite = null;
    }
  },

  /** 多公司切换 */
  switchCompany() {
    const companies = this.data.companies;
    if (companies.length <= 1) return;
    const items = companies.map(c => c.companyName);
    wx.showActionSheet({
      itemList: items,
      success: async (res) => {
        const company = companies[res.tapIndex];
        try {
          const meData = await app.switchCompany(company.companyId);
          // 立即更新公司名和角色
          this.setData({
            companyDisplayName: (meData.company && meData.company.name) || company.companyName,
            companies: meData.companies || [],
            role: meData.member && meData.member.roleCode === 'PURCHASER' ? 'buyer' : 'supplier',
            roleIndex: meData.member && meData.member.roleCode === 'PURCHASER' ? 1 : 0
          });
          this.loadHome();
          this.loadCounterparties();
        } catch (e) {
          wx.showToast({ title: '切换失败', icon: 'none' });
        }
      }
    });
  },

  checkMemberStatus() {
    const member = app.globalData.memberInfo;
    const isLegal = member && member.roleCode === 'LEGAL';
    this.setData({ isLegalPerson: !!isLegal });
    if (!member || member.memberStatus === 'NONE' || !member.roleCode || member.roleCode === 'GUEST') {
      this.setData({ showJoinForm: true });
    } else if (member.memberStatus === 'PENDING') {
      this.setData({ showJoinForm: true });
    } else {
      this.setData({ showJoinForm: false });
    }
  },

  onJoinIdInput(event) {
    this.setData({ joinCompanyId: event.detail.value });
  },

  async joinCompany() {
    const companyId = this.data.joinCompanyId.trim();
    if (!companyId) {
      wx.showToast({ title: '请输入邀请码', icon: 'none' });
      return;
    }
    const user = app.globalData.userInfo;
    if (!user || !user.id) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    try {
      const result = await request({
        url: '/companies/join',
        method: 'POST',
        data: { code: companyId, userId: parseInt(user.id) }
      });
      wx.showToast({ title: result.message, icon: 'success' });
      // 重新加载 me 信息
      app.loadMe();
      setTimeout(() => this.onShow(), 500);
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    }
  },

  /** 根据当前登录用户角色，设置默认身份 */
  initRoleFromMember() {
    const member = app.globalData.memberInfo;
    if (!member) return;

    const rc = member.roleCode;
    if (rc === 'SALES') {
      this.setData({ role: 'supplier', roleIndex: 0 });
    } else if (rc === 'PURCHASER') {
      this.setData({ role: 'buyer', roleIndex: 1 });
    }
  },

  onRoleChange(event) {
    const index = parseInt(event.detail.value);
    const roleOption = this.data.roleOptions[index];
    this.setData({
      role: roleOption.value,
      roleIndex: index,
      period: 'total'
    });
    this.refreshViewState();
    this.loadHome();
    this.loadCounterparties();
  },

  switchPeriod(event) {
    this.setData({ period: event.currentTarget.dataset.period });
    this.refreshViewState();
    this.loadHome();
  },

  async loadHome() {
    this.setData({ loading: true });
    try {
      const currentCompanyId = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
      const payload = await request({
        url: `/home/${this.data.role}?period=${this.data.period}&companyId=${currentCompanyId}`
      });
      this.setData({
        companyName: payload.companyName,
        companyDisplayName: payload.companyName || '企业信息加载中',
        roleText: payload.roleText,
        roleDisplayText: payload.roleText || '请选择身份',
        ranking: payload.ranking || []
      });
      this.refreshViewState();
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    } finally {
      this.setData({ loading: false });
      this.refreshViewState();
    }
  },

  async loadCounterparties() {
    if (this.data.role !== 'supplier') {
      this.setData({ counterparties: [] });
      return;
    }
    try {
      const companyId = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
      const list = await request({ url: `/counterparties?companyId=${companyId}` });
      this.setData({ counterparties: list || [] });
    } catch (error) {
      // 静默
    }
  },

  openCounterparty(event) {
    const name = event.currentTarget.dataset.name;
    wx.navigateTo({
      url: `/pages/order-detail/order-detail?counterpartyName=${encodeURIComponent(name)}`
    });
  },

  addCounterparty() {
    const member = app.globalData.memberInfo;
    if (!member || member.roleCode !== 'LEGAL') {
      wx.showToast({ title: '仅法人可添加供方公司', icon: 'none' });
      return;
    }
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
    const uid = (app.globalData.userInfo && app.globalData.userInfo.id) || '1';
    request({
      url: '/companies/counterparty-invite',
      method: 'POST',
      data: { companyId: cid, userId: parseInt(uid) }
    }).then(result => {
      this.setData({ counterpartyInviteCode: result.code });
      // 触发微信分享
      wx.showShareMenu({ withShareTicket: true });
    }).catch(e => {
      wx.showToast({ title: e.message, icon: 'none' });
    });
  },

  onShareAppMessage() {
    const code = this.data.counterpartyInviteCode;
    if (!code) return { title: '贸易通', path: '/pages/index/index' };
    return {
      title: '邀请你成为供方合作伙伴',
      path: `/pages/index/index?inviteCode=${code}&type=counterparty`
    };
  },

  refreshViewState() {
    const role = this.data.role;
    const period = this.data.period;
    const ranking = this.data.ranking || [];

    this.setData({
      rankingTitle: role === 'supplier' ? '客户销售业绩排名' : '采购业绩排名',
      periods: this.data.periods.map((item) => ({
        key: item.key,
        text: item.text,
        activeClass: item.key === period ? 'active' : ''
      })),
      showEmpty: !this.data.loading && ranking.length === 0,
      showRanking: !this.data.loading && ranking.length > 0
    });
  }
});
