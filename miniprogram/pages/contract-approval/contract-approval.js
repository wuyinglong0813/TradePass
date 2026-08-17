const { request } = require('../../utils/request');
const { downloadApiFile } = require('../../utils/fileTransfer');

const app = getApp();

function money(value) {
  const number = Number(value || 0);
  return Number.isFinite(number) ? number.toFixed(2) : '0.00';
}

function currentCompanyName() {
  const companyId = String(app.getCurrentCompanyId() || '');
  const company = (app.globalData.companies || [])
    .find(item => String(item.companyId) === companyId);
  return (company && company.companyName) || '当前企业';
}

Page({
  data: {
    currentCompanyName: '',
    activeTab: 'CONTRACT',
    tabs: [
      { key: 'CONTRACT', label: '合同待审批', count: 0 },
      { key: 'FULFILLMENT', label: '履约资料待审批', count: 0 }
    ],
    contracts: [],
    fulfillmentItems: [],
    visibleGroups: [],
    companyOptions: [{ id: '', name: '全部往来公司' }],
    companyIndex: 0,
    selectedCompanyId: '',
    loading: false
  },

  onShow() {
    this.setData({ currentCompanyName: currentCompanyName() });
    this.loadPending();
  },

  onPullDownRefresh() {
    this.loadPending().finally(() => wx.stopPullDownRefresh());
  },

  async loadPending() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const [contractList, fulfillmentList] = await Promise.all([
        request({ url: '/contracts/pending' }),
        request({ url: '/approvals/fulfillment' })
      ]);
      const contracts = (contractList || []).map(item => ({
        ...item,
        id: Number(item.id),
        companyId: String(item.viewerCounterpartyCompanyId || item.companyId || ''),
        companyName: item.viewerCounterpartyName || item.counterpartyName || '往来公司',
        amountText: money(item.amount),
        createdDate: String(item.createdAt || '').substring(0, 10)
      }));
      const fulfillmentItems = (fulfillmentList || []).map(item => ({
        ...item,
        id: Number(item.id),
        contractId: Number(item.contractId),
        companyId: String(item.sourceCompanyId || ''),
        companyName: item.sourceCompanyName || '往来公司',
        amountText: item.amount == null ? '' : money(item.amount),
        dateText: item.businessDate || String(item.createdAt || '').substring(0, 10),
        iconText: item.approvalType === 'SALES_ORDER' ? '销'
          : (item.approvalType === 'INVOICE' ? '票' : '款'),
        typeClass: String(item.approvalType || '').toLowerCase()
      }));
      this.setData({
        contracts,
        fulfillmentItems,
        tabs: [
          { key: 'CONTRACT', label: '合同待审批', count: contracts.length },
          { key: 'FULFILLMENT', label: '履约资料待审批', count: fulfillmentItems.length }
        ]
      });
      this.rebuildGroups(false);
    } catch (error) {
      wx.showToast({ title: error.message || '待审批事项加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  switchTab(e) {
    const activeTab = e.currentTarget.dataset.key;
    if (!activeTab || activeTab === this.data.activeTab) return;
    this.setData({ activeTab, selectedCompanyId: '', companyIndex: 0 });
    this.rebuildGroups(true);
  },

  selectCompany(e) {
    const companyIndex = Number(e.detail.value || 0);
    const selected = this.data.companyOptions[companyIndex] || this.data.companyOptions[0];
    this.setData({ companyIndex, selectedCompanyId: selected.id || '' });
    this.rebuildGroups(false);
  },

  rebuildGroups(resetCompany) {
    const source = this.data.activeTab === 'CONTRACT'
      ? this.data.contracts : this.data.fulfillmentItems;
    const companies = [];
    const seen = new Set();
    source.forEach(item => {
      const id = String(item.companyId || '');
      if (!id || seen.has(id)) return;
      seen.add(id);
      companies.push({ id, name: item.companyName || '往来公司' });
    });
    const companyOptions = [
      { id: '', name: `全部往来公司（${companies.length}）` },
      ...companies
    ];
    let selectedCompanyId = resetCompany ? '' : this.data.selectedCompanyId;
    if (selectedCompanyId && !seen.has(String(selectedCompanyId))) selectedCompanyId = '';
    const companyIndex = Math.max(0,
      companyOptions.findIndex(item => String(item.id) === String(selectedCompanyId)));
    const grouped = new Map();
    source
      .filter(item => !selectedCompanyId || String(item.companyId) === String(selectedCompanyId))
      .forEach(item => {
        const id = String(item.companyId || 'unknown');
        if (!grouped.has(id)) {
          grouped.set(id, {
            companyId: id,
            companyName: item.companyName || '往来公司',
            initial: String(item.companyName || '企').substring(0, 1),
            items: []
          });
        }
        grouped.get(id).items.push(item);
      });
    this.setData({
      companyOptions,
      companyIndex,
      selectedCompanyId,
      visibleGroups: Array.from(grouped.values())
    });
  },

  viewContract(e) {
    const contract = e.currentTarget.dataset.contract;
    if (!contract || !contract.id) return;
    wx.navigateTo({ url: `/pages/contract-preview/contract-preview?contractId=${contract.id}` });
  },

  approve(e) {
    const contract = e.currentTarget.dataset.contract;
    if (!contract || !contract.id) return;
    wx.showModal({
      title: '确认签署',
      content: `确认签署合同“${contract.name}”？\n金额：¥${contract.amountText}\n签署后合同将生效`,
      success: async res => {
        if (!res.confirm) return;
        try {
          wx.showLoading({ title: '处理中...' });
          await request({ url: `/contracts/${contract.id}/approve`, method: 'POST' });
          wx.showToast({ title: '合同已签署生效', icon: 'success' });
          await this.loadPending();
        } catch (error) {
          wx.showToast({ title: error.message || '签署失败', icon: 'none' });
        } finally {
          wx.hideLoading();
        }
      }
    });
  },

  reject(e) {
    const contract = e.currentTarget.dataset.contract;
    if (!contract || !contract.id) return;
    wx.showModal({
      title: '拒绝合同',
      content: `确定拒绝合同“${contract.name}”？`,
      success: async res => {
        if (!res.confirm) return;
        try {
          wx.showLoading({ title: '处理中...' });
          await request({ url: `/contracts/${contract.id}/reject`, method: 'POST' });
          wx.showToast({ title: '已拒绝', icon: 'none' });
          await this.loadPending();
        } catch (error) {
          wx.showToast({ title: error.message || '操作失败', icon: 'none' });
        } finally {
          wx.hideLoading();
        }
      }
    });
  },

  openFulfillment(e) {
    const item = e.currentTarget.dataset.item;
    if (!item) return;
    if (item.approvalType === 'SALES_ORDER') {
      wx.navigateTo({ url: `/pages/sales-order-detail/sales-order-detail?id=${item.id}` });
    }
  },

  viewAttachment(e) {
    const item = e.currentTarget.dataset.item;
    if (!item || !item.id) return;
    const originalName = String(item.documentNo || '').trim();
    const matched = originalName.match(/\.([a-zA-Z0-9]{1,8})$/);
    const extensionByType = {
      'image/jpeg': 'jpg',
      'image/png': 'png',
      'image/gif': 'gif',
      'image/webp': 'webp',
      'application/pdf': 'pdf',
      'application/msword': 'doc',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx'
    };
    const extension = matched
      ? matched[1].toLowerCase()
      : (extensionByType[item.contentType] || 'bin');
    const safeName = (matched ? originalName.slice(0, -matched[0].length) : originalName)
      .replace(/[\\/:*?"<>|\r\n]/g, '_').replace(/^\.+/, '').trim().slice(0, 80) || '履约资料';
    const filePath = `${wx.env.USER_DATA_PATH}/${safeName}-${item.id}.${extension}`;
    wx.showLoading({ title: '打开中...' });
    downloadApiFile(`/contract-attachments/${item.id}/content-data`, filePath)
      .then(result => {
        if (item.isImage) {
          wx.previewImage({ current: result.filePath, urls: [result.filePath] });
          return;
        }
        wx.openDocument({
          filePath: result.filePath,
          fileType: ['pdf', 'doc', 'docx'].includes(extension) ? extension : undefined,
          showMenu: true,
          fail: () => wx.showToast({ title: '文件打开失败', icon: 'none' })
        });
      })
      .catch(error => wx.showToast({ title: error.message || '文件获取失败', icon: 'none' }))
      .finally(() => wx.hideLoading());
  },

  approveAttachment(e) {
    const item = e.currentTarget.dataset.item;
    if (!item || !item.id) return;
    wx.showModal({
      title: `确认${item.typeText}`,
      content: '通过后将计入双方对账。',
      confirmText: '确认通过',
      success: result => {
        if (result.confirm) this.submitAttachmentDecision(item, 'APPROVE', '');
      }
    });
  },

  rejectAttachment(e) {
    const item = e.currentTarget.dataset.item;
    if (!item || !item.id) return;
    wx.showModal({
      title: '驳回资料',
      editable: true,
      placeholderText: '请输入驳回原因',
      confirmText: '确认驳回',
      success: result => {
        if (!result.confirm) return;
        const reason = String(result.content || '').trim();
        if (!reason) {
          wx.showToast({ title: '请输入驳回原因', icon: 'none' });
          return;
        }
        this.submitAttachmentDecision(item, 'REJECT', reason);
      }
    });
  },

  async submitAttachmentDecision(item, decision, reason) {
    try {
      wx.showLoading({ title: '处理中...' });
      await request({
        url: `/contract-attachments/${item.id}/decision`,
        method: 'POST',
        data: { decision, reason }
      });
      wx.showToast({ title: decision === 'APPROVE' ? '已通过并更新对账' : '已驳回', icon: 'success' });
      await this.loadPending();
    } catch (error) {
      wx.showToast({ title: error.message || '确认失败', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  }
});
