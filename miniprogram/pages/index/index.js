const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    role: 'supplier',
    roleIndex: 0,
    roleOptions: [
      { value: 'supplier', text: '供应商' },
      { value: 'buyer', text: '采购商' }
    ],
    period: 'total',
    periodText: '累计',
    periods: [
      { key: 'total', text: '累计' },
      { key: 'year', text: '今年' },
      { key: 'month', text: '本月' }
    ],
    companyName: '',
    companyDisplayName: '企业信息加载中',
    ranking: [],
    rankingTitle: '客户销售业绩排名',
    loading: false,
    counterparties: [],
    showJoinForm: false,
    joinCompanyId: '',
    companies: [],
    isLegalPerson: false,
    counterpartyInviteCode: '',
    counterpartyEmptyBtn: '',
    isLoggedIn: false,
    userName: '',

    // Banner 轮播（模拟数据）
    banners: [
      { id: 1, bg: 'linear-gradient(135deg, #0f766e, #14b8a6)', title: '商签通', desc: '安全合规的企业贸易管理平台' },
      { id: 2, bg: 'linear-gradient(135deg, #6366f1, #8b5cf6)', title: '企业认证', desc: '完成企业认证解锁全部功能' },
      { id: 3, bg: 'linear-gradient(135deg, #f59e0b, #f97316)', title: '交易排行', desc: '查看你的客户销售业绩排名' }
    ],

    // 数据统计
    stats: { totalAmount: 0, totalOrders: 0, counterpartyCount: 0 },

    // 隐私协议弹窗
    showPrivacy: false,
    privacyAgreed: false,
    showPrivacyDetail: false,
    shaking: false
  },

  onLoad(options) {
    if (!wx.getStorageSync('privacy_agreed')) {
      this.setData({ showPrivacy: true });
      wx.hideTabBar();
    }
    if (options.inviteCode) {
      app.globalData.pendingInvite = { code: options.inviteCode, type: options.type || 'member' };
      this.setData({ joinCompanyId: options.inviteCode, showJoinForm: true });
    }
  },

  onShow() {
    const loggedIn = !!(app.globalData.token || wx.getStorageSync('tradepass_token'));
    const user = app.globalData.userInfo;
    this.setData({
      isLoggedIn: loggedIn,
      companies: app.globalData.companies || [],
      userName: (user && user.nickname) || ''
    });
    if (!loggedIn) return;
    this.checkMemberStatus();
    this.initRoleFromMember();

    if (!this.data.showJoinForm && app.globalData.pendingInvite) {
      const invite = app.globalData.pendingInvite;
      const member = app.globalData.memberInfo;
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
      this.processInvite(invite.code);
      return;
    }

    if (!this.data.showJoinForm) {
      this.loadHome();
      this.loadCounterparties();
    }
  },

  /* 下拉刷新 */
  onPullDownRefresh() {
    if (!this.data.showJoinForm) {
      Promise.all([this.loadHome(), this.loadCounterparties()]).finally(() => {
        wx.stopPullDownRefresh();
      });
    } else {
      wx.stopPullDownRefresh();
    }
  },

  async processInvite(code) {
    const user = app.globalData.userInfo;
    if (!user || !user.id) return;
    try {
      const result = await request({
        url: '/companies/join',
        method: 'POST',
        data: { code }
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

  /* 公司切换 */
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
          this.setData({
            companyDisplayName: (meData.company && meData.company.name) || company.companyName,
            companies: meData.companies || [],
            role: meData.member && meData.member.roleCode === 'PURCHASER' ? 'buyer' : 'supplier'
          });
          this.loadHome();
          this.loadCounterparties();
        } catch (e) {
          wx.showToast({ title: '切换失败', icon: 'none' });
        }
      }
    });
  },

  /* 角色切换（Tab 点击）*/
  switchRole(e) {
    const role = e.currentTarget.dataset.role;
    if (role === this.data.role) return;
    this.setData({ role, period: 'total' });
    this.loadHome();
    this.loadCounterparties();
  },

  /* 时期切换 */
  switchPeriod(e) {
    const period = e.currentTarget.dataset.period;
    if (period === this.data.period) return;
    const periodTextMap = { total: '累计', year: '今年', month: '本月' };
    this.setData({ period, periodText: periodTextMap[period] || '' });
    this.loadHome();
  },

  checkMemberStatus() {
    const member = app.globalData.memberInfo;
    const isLegal = member && member.roleCode === 'LEGAL';
    this.setData({ isLegalPerson: !!isLegal, counterpartyEmptyBtn: isLegal ? '邀请供方' : '' });
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
    if (!companyId) { wx.showToast({ title: '请输入邀请码', icon: 'none' }); return; }
    const user = app.globalData.userInfo;
    if (!user || !user.id) { wx.showToast({ title: '请先登录', icon: 'none' }); return; }
    try {
      const result = await request({
        url: '/companies/join',
        method: 'POST',
        data: { code: companyId }
      });
      wx.showToast({ title: result.message, icon: 'success' });
      app.loadMe();
      setTimeout(() => this.onShow(), 500);
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    }
  },

  initRoleFromMember() {
    const member = app.globalData.memberInfo;
    if (!member) return;
    if (member.roleCode === 'SALES') this.setData({ role: 'supplier' });
    else if (member.roleCode === 'PURCHASER') this.setData({ role: 'buyer' });
  },

  async loadHome() {
    this.setData({ loading: true });
    try {
      const currentCompanyId = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
      const payload = await request({
        url: `/home/${this.data.role}?period=${this.data.period}&companyId=${currentCompanyId}`
      });
      const ranking = payload.ranking || [];
      // 计算统计数据
      const totalAmount = ranking.reduce((sum, item) => sum + (item.amount || 0), 0);
      const totalOrders = ranking.reduce((sum, item) => sum + (item.orderCount || 0), 0);
      this.setData({
        companyName: payload.companyName,
        companyDisplayName: payload.companyName || '企业信息加载中',
        ranking,
        rankingTitle: this.data.role === 'supplier' ? '客户销售业绩排名' : '采购业绩排名',
        stats: {
          totalAmount: totalAmount.toFixed(0),
          totalOrders,
          counterpartyCount: ranking.length
        }
      });
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  async loadCounterparties() {
    if (this.data.role !== 'supplier') { this.setData({ counterparties: [] }); return; }
    try {
      const companyId = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '1';
      const list = await request({ url: `/counterparties?companyId=${companyId}` });
      this.setData({ counterparties: list || [] });
    } catch (error) { /* 静默 */ }
  },

  goLogin() {
    wx.reLaunch({ url: '/pages/login/login' });
  },

  openCounterparty(e) {
    const name = e.currentTarget.dataset.name;
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
    request({
      url: '/companies/counterparty-invite',
      method: 'POST',
      data: { companyId: cid }
    }).then(result => {
      this.setData({ counterpartyInviteCode: result.code });
      wx.showShareMenu({ withShareTicket: true });
    }).catch(e => {
      wx.showToast({ title: e.message, icon: 'none' });
    });
  },

  // ===== 隐私协议弹窗 =====
  togglePrivacyAgree() {
    this.setData({ privacyAgreed: !this.data.privacyAgreed });
  },
  doPrivacyAgree() {
    if (!this.data.privacyAgreed) {
      this.setData({ shaking: true });
      setTimeout(() => this.setData({ shaking: false }), 500);
      return;
    }
    wx.setStorageSync('privacy_agreed', true);
    this.setData({ showPrivacy: false });
    wx.showTabBar();
  },
  doPrivacyDeny() {
    wx.showModal({
      title: '提示',
      content: '需要同意隐私保护指引才能使用小程序。',
      showCancel: false,
      confirmText: '知道了'
    });
  },
  showPrivacyDetail() {
    this.setData({ showPrivacyDetail: true });
  },
  closePrivacyDetail() {
    this.setData({ showPrivacyDetail: false });
  },

  onShareAppMessage() {
    const code = this.data.counterpartyInviteCode;
    if (!code) return { title: '商签通', path: '/pages/index/index' };
    return {
      title: '邀请你成为供方合作伙伴',
      path: `/pages/index/index?inviteCode=${code}&type=counterparty`
    };
  }
});
