Page({
  data: {
    counterpartyName: '',
    myCompanyName: '',
    // 表单
    templateIndex: 0,
    templates: [],
    contractName: '',
    startDate: '',
    endDate: '',
    amount: '',
    terms: ''
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    const app = getApp();
    const companies = app.globalData.companies || [];
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '';
    const cur = companies.find(c => c.companyId === cid);
    this.setData({
      counterpartyName: name,
      myCompanyName: (cur && cur.companyName) || '我的企业'
    });
    this.loadTemplates();
  },

  loadTemplates() {
    // 复用合同模板列表（后续可接独立接口）
    this.setData({
      templates: ['标准采购合同模板', '框架供货协议模板', '单笔交易合同模板', '物流服务合同模板']
    });
  },

  onTemplateChange(e) {
    this.setData({ templateIndex: parseInt(e.detail.value) });
  },

  /* 日期选择 */
  onStartDateChange(e) { this.setData({ startDate: e.detail.value }); },
  onEndDateChange(e) { this.setData({ endDate: e.detail.value }); },

  /* 文本输入 */
  onContractNameInput(e) { this.setData({ contractName: e.detail.value }); },
  onAmountInput(e) { this.setData({ amount: e.detail.value }); },
  onTermsInput(e) { this.setData({ terms: e.detail.value }); },

  /* 提交签订 */
  async onSubmit() {
    const { templateIndex, templates, contractName, startDate, endDate, amount, terms, counterpartyName } = this.data;
    if (!contractName.trim()) { wx.showToast({ title: '请输入合同名称', icon: 'none' }); return; }
    if (!startDate) { wx.showToast({ title: '请选择开始日期', icon: 'none' }); return; }
    if (!endDate) { wx.showToast({ title: '请选择结束日期', icon: 'none' }); return; }
    if (!amount.trim()) { wx.showToast({ title: '请输入合同金额', icon: 'none' }); return; }

    const res = await new Promise(r => {
      wx.showModal({
        title: '确认签订',
        content: `即将与 ${counterpartyName} 签订合同"${contractName}"\n金额：¥${amount}\n模板：${templates[templateIndex]}\n\n发起后需对方公司审批通过方可生效`,
        success: r
      });
    });
    if (!res.confirm) return;

    const { request } = require('../../utils/request');
    try {
      wx.showLoading({ title: '发起中...' });
      await request({
        url: '/contracts',
        method: 'POST',
        data: {
          counterpartyName,
          name: contractName.trim(),
          templateName: templates[templateIndex],
          amount: parseFloat(amount),
          startDate,
          endDate,
          terms: terms || ''
        }
      });
      wx.hideLoading();
      wx.showToast({ title: '合同已发起，等待对方审批', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1500);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message, icon: 'none' });
    }
  }
});
