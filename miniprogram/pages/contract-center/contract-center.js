const { request } = require('../../utils/request');

Page({
  data: {
    loading: false,
    activeTab: 'ALL',
    tabs: [
      { key: 'ALL', label: '全部' },
      { key: 'PENDING', label: '待签署' },
      { key: 'ACTIVE', label: '履约中' },
      { key: 'COMPLETED', label: '已完成' }
    ],
    contracts: [],
    visibleContracts: [],
    signatureMode: false,
    page: 1,
    size: 20,
    hasMore: false,
    summary: { total: 0, pending: 0, active: 0, amount: '0' }
  },

  onLoad(options) {
    if (options.mode === 'signature') this.setData({ activeTab: 'PENDING', signatureMode: true });
  },

  onShow() {
    this.loadContracts(true);
  },

  onPullDownRefresh() {
    this.loadContracts(true).finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.loadContracts(false);
  },

  async loadContracts(reset = true) {
    this.setData({ loading: true });
    try {
      const page = reset ? 1 : this.data.page + 1;
      const signatureMode = this.data.signatureMode;
      const status = this.data.activeTab === 'ALL' ? '' : `&status=${this.data.activeTab}`;
      const endpoint = signatureMode ? '/contracts/pending' : `/contracts?page=${page}&size=${this.data.size}${status}`;
      const [payload, summaryPayload] = await Promise.all([
        request({ url: endpoint }),
        reset && !signatureMode ? request({ url: '/contracts/summary' }) : Promise.resolve(null)
      ]);
      const list = Array.isArray(payload) ? payload : (payload.items || []);
      const statusText = { PENDING: '待签署', ACTIVE: '履约中', COMPLETED: '已完成', REJECTED: '已拒绝' };
      const nextContracts = (list || []).map(item => ({
        ...item,
        counterpartyName: item.viewerCounterpartyName || item.counterpartyName,
        direction: item.viewerDirection || item.direction,
        id: parseInt(item.id),
        amount: Number(item.amount || 0),
        statusText: statusText[item.status] || item.status,
        createdDate: (item.createdAt || '').substring(0, 10)
      }));
      const contracts = reset ? nextContracts : this.data.contracts.concat(nextContracts);
      const localAmount = contracts.reduce((sum, item) => sum + item.amount, 0);
      const summary = summaryPayload ? {
        total: Number(summaryPayload.total || 0),
        pending: Number(summaryPayload.pending || 0),
        active: Number(summaryPayload.active || 0),
        amount: Number(summaryPayload.amount || 0).toFixed(0)
      } : {
        total: contracts.length,
        pending: contracts.filter(item => item.status === 'PENDING').length,
        active: contracts.filter(item => item.status === 'ACTIVE').length,
        amount: localAmount.toFixed(0)
      };
      this.setData({
        contracts,
        page,
        hasMore: signatureMode ? false : !!payload.hasMore,
        summary: reset ? summary : this.data.summary
      });
      this.applyFilter();
    } catch (error) {
      wx.showToast({ title: error.message || '合同加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  switchTab(event) {
    this.setData({ activeTab: event.currentTarget.dataset.key });
    if (this.data.signatureMode) this.applyFilter();
    else this.loadContracts(true);
  },

  applyFilter() {
    const activeTab = this.data.activeTab;
    const visibleContracts = activeTab === 'ALL'
      ? this.data.contracts
      : this.data.contracts.filter(item => item.status === activeTab);
    this.setData({ visibleContracts });
  },

  openContract(event) {
    const item = event.currentTarget.dataset.item;
    wx.navigateTo({
      url: `/pages/contract-preview/contract-preview?contractId=${item.id}&contractName=${encodeURIComponent(item.name || '')}&counterpartyName=${encodeURIComponent(item.counterpartyName || '')}`
    });
  }
});
