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
    counterpartyName: '',
    myCompanyName: '',
    // 快捷操作（带权限控制）
    canSignContract: false,
    canReconciliation: false,
    // 合同列表（我方与该公司签订的所有合同）
    contracts: [],
    // 月销售趋势
    monthlySales: [],
    // 弹窗
    showModal: false,
    modalTitle: '',
    modalContent: ''
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    // 获取我方当前企业名
    const companies = app.globalData.companies || [];
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '';
    const cur = companies.find(c => c.companyId === cid);
    this.setData({
      counterpartyName: name,
      myCompanyName: (cur && cur.companyName) || '我的企业',
      canSignContract: hasPerm('contract_sign'),
      canReconciliation: hasPerm('reconciliation')
    });
    // 异步加载
    setTimeout(() => {
      this.loadContracts(name);
      this.generateMonthlySales();
    }, 100);
  },

  onShow() {
    // 每次回到页面时刷新合同列表（例如从签订合同页返回）
    if (this.data.counterpartyName) {
      this.loadContracts(this.data.counterpartyName);
    }
  },

  /* 加载合同列表 */
  async loadContracts(counterpartyName) {
    const { request } = require('../../utils/request');
    try {
      const list = await request({
        url: `/contracts?counterpartyName=${encodeURIComponent(counterpartyName)}`
      });
      const statusMap = { ACTIVE: '履行中', PENDING: '待签署', REJECTED: '已拒绝', COMPLETED: '已完成' };
      const contracts = (list || []).map(c => ({
        id: parseInt(c.id),
        name: c.name,
        startDate: c.startDate || '',
        endDate: c.endDate || '',
        amount: c.amount || 0,
        templateName: c.templateName || '',
        status: c.status,
        statusText: statusMap[c.status] || c.status
      }));
      this.setData({ contracts });
    } catch (e) { /* 静默 */ }
  },

  generateMonthlySales() {
    const months = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'];
    const baseValues = [30, 25, 40, 35, 50, 45, 60, 55, 70, 65, 80, 75];
    const sales = months.map((m, i) => ({
      month: m,
      percent: baseValues[i] + Math.floor(Math.random() * 15)
    }));
    this.setData({ monthlySales: sales });
  },

  /* 点击合同 → 进入合同预览页 */
  onContractTap(e) {
    const contract = e.currentTarget.dataset.contract;
    wx.navigateTo({
      url: `/pages/contract-preview/contract-preview?contractId=${contract.id}&contractName=${encodeURIComponent(contract.name)}&counterpartyName=${encodeURIComponent(this.data.counterpartyName)}`
    });
  },

  /* 功能区点击 */
  onAction(e) {
    const key = e.currentTarget.dataset.key;
    const name = this.data.counterpartyName;
    switch (key) {
      case 'sign':
        wx.navigateTo({
          url: `/pages/sign-contract/sign-contract?counterpartyName=${encodeURIComponent(name)}`
        });
        break;
      case 'reconciliation':
        wx.navigateTo({
          url: `/pages/reconciliation/reconciliation?counterpartyName=${encodeURIComponent(name)}`
        });
        break;
    }
  },

  closeModal() { this.setData({ showModal: false }); },
  noop() {}
});
