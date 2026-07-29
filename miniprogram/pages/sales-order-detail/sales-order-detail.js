const { request } = require('../../utils/request');

Page({
  data: {
    id: '',
    documentNo: '',
    detail: null,
    items: [],
    warehouses: [],
    memo: '',
    memoDraft: '',
    memoPreview: '尚未记录，点击添加',
    memoUpdatedAt: '',
    memoSaving: false,
    showMemoEditor: false,
    receiving: false,
    publishing: false,
    showDraftEditor: false,
    draftSaving: false,
    draftTitle: '',
    draftCompanyName: '',
    draftCounterpartyName: '',
    draftContractNo: '',
    draftDate: '',
    draftColumns: [],
    draftRows: [],
    draftBlankRows: 8,
    draftTotalAmount: '0',
    loading: false
  },

  onLoad(options) {
    this.setData({
      id: options.id || '',
      documentNo: decodeURIComponent(options.documentNo || '')
    });
    this.loadAll();
  },

  onPullDownRefresh() {
    this.loadAll().finally(() => wx.stopPullDownRefresh());
  },

  async loadAll() {
    if (!this.data.id || this.data.loading) return;
    this.setData({ loading: true });
    try {
      const [detail, memo, warehouses] = await Promise.all([
        request({ url: `/trade-documents/${this.data.id}` }),
        request({ url: `/trade-documents/${this.data.id}/memo` }),
        request({ url: '/warehouses' }).catch(() => [])
      ]);
      const content = (detail && detail.content) || {};
      this.setData({
        detail: {
          ...detail,
          title: content.title || '销售单',
          companyName: content.companyName || '—',
          counterpartyName: content.counterpartyName || '—',
          contractNo: content.contractNo || '—',
          date: content.date || String(detail.createdAt || '').slice(0, 10),
          totalAmount: content.totalAmount || '0'
        },
        documentNo: detail.documentNo || this.data.documentNo,
        items: detail.items || [],
        warehouses: warehouses || [],
        memo: (memo && memo.content) || '',
        memoDraft: (memo && memo.content) || '',
        memoPreview: this.memoPreviewText((memo && memo.content) || ''),
        memoUpdatedAt: memo && memo.updatedAt ? String(memo.updatedAt).replace('T', ' ').slice(0, 16) : ''
      });
      wx.setNavigationBarTitle({ title: detail.documentNo || '销售单详情' });
    } catch (error) {
      wx.showToast({ title: error.message || '销售单加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  onMemoInput(e) {
    this.setData({ memoDraft: e.detail.value });
  },

  memoPreviewText(content) {
    const text = String(content || '').replace(/\s+/g, ' ').trim();
    if (!text) return '尚未记录，点击添加';
    return text.length > 54 ? `${text.slice(0, 54)}…` : text;
  },

  openMemoEditor() {
    this.setData({ showMemoEditor: true, memoDraft: this.data.memo });
  },

  closeMemoEditor() {
    if (this.data.memoSaving) return;
    this.setData({ showMemoEditor: false, memoDraft: this.data.memo });
  },

  async saveMemo() {
    if (this.data.memoSaving) return;
    try {
      this.setData({ memoSaving: true });
      const memo = await request({
        url: `/trade-documents/${this.data.id}/memo`,
        method: 'POST',
        data: { content: this.data.memoDraft }
      });
      this.setData({
        memo: (memo && memo.content) || '',
        memoDraft: (memo && memo.content) || '',
        memoPreview: this.memoPreviewText((memo && memo.content) || ''),
        showMemoEditor: false,
        memoUpdatedAt: memo && memo.updatedAt ? String(memo.updatedAt).replace('T', ' ').slice(0, 16) : ''
      });
      wx.showToast({ title: '个人备忘录已保存', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ memoSaving: false });
    }
  },

  openDraftEditor() {
    const detail = this.data.detail || {};
    if (!detail.canEditDraft) return;
    const content = detail.content || {};
    const columns = Array.isArray(content.columns) ? content.columns.map(item => String(item || '')) : [];
    const rows = Array.isArray(content.rows)
      ? content.rows.map(row => Array.isArray(row) ? row.map(item => String(item == null ? '' : item)) : [])
      : [];
    this.setData({
      showDraftEditor: true,
      draftTitle: content.title || '销售单',
      draftCompanyName: content.companyName || '',
      draftCounterpartyName: content.counterpartyName || '',
      draftContractNo: content.contractNo || '',
      draftDate: content.date || '',
      draftColumns: columns,
      draftRows: rows.length > 0 ? rows : [columns.map(column => String(column).includes('序号') ? '1' : '')],
      draftBlankRows: Math.max(Number(content.blankRows) || 8, rows.length),
      draftTotalAmount: String(content.totalAmount || '0')
    });
  },

  closeDraftEditor() {
    if (this.data.draftSaving) return;
    this.setData({ showDraftEditor: false });
  },

  onDraftFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    if (!['draftTitle', 'draftCompanyName', 'draftCounterpartyName', 'draftContractNo'].includes(field)) return;
    this.setData({ [field]: e.detail.value });
  },

  onDraftDateChange(e) {
    this.setData({ draftDate: e.detail.value });
  },

  onDraftCellInput(e) {
    const rowIndex = Number(e.currentTarget.dataset.row);
    const columnIndex = Number(e.currentTarget.dataset.col);
    const rows = this.data.draftRows.map(row => row.slice());
    if (!rows[rowIndex] || columnIndex < 0) return;
    rows[rowIndex][columnIndex] = e.detail.value;
    const columns = this.data.draftColumns;
    const quantityIndex = columns.findIndex(column => String(column).includes('数量'));
    const priceIndex = columns.findIndex(column => String(column).includes('单价'));
    const amountIndex = columns.findIndex(column => String(column).includes('金额'));
    if ((columnIndex === quantityIndex || columnIndex === priceIndex)
      && quantityIndex >= 0 && priceIndex >= 0 && amountIndex >= 0) {
      rows[rowIndex][amountIndex] = this.formatAmount(
        (parseFloat(rows[rowIndex][quantityIndex]) || 0) * (parseFloat(rows[rowIndex][priceIndex]) || 0)
      );
    }
    this.setData({
      draftRows: rows,
      draftTotalAmount: this.calculateDraftTotal(columns, rows)
    });
  },

  addDraftRow() {
    const columns = this.data.draftColumns;
    if (columns.length === 0) return;
    const rows = this.data.draftRows.map(row => row.slice());
    rows.push(columns.map(column => String(column).includes('序号') ? String(rows.length + 1) : ''));
    this.setData({ draftRows: rows, draftBlankRows: Math.max(this.data.draftBlankRows, rows.length) });
  },

  deleteDraftRow(e) {
    if (this.data.draftRows.length <= 1) return;
    const index = Number(e.currentTarget.dataset.index);
    const sequenceIndex = this.data.draftColumns.findIndex(column => String(column).includes('序号'));
    const rows = this.data.draftRows.filter((_, rowIndex) => rowIndex !== index).map((row, rowIndex) => {
      const next = row.slice();
      if (sequenceIndex >= 0) next[sequenceIndex] = String(rowIndex + 1);
      return next;
    });
    this.setData({ draftRows: rows, draftTotalAmount: this.calculateDraftTotal(this.data.draftColumns, rows) });
  },

  calculateDraftTotal(columns, rows) {
    const amountIndex = columns.findIndex(column => String(column).includes('金额'));
    if (amountIndex < 0) return this.data.draftTotalAmount || '0';
    return this.formatAmount(rows.reduce((sum, row) => sum + (parseFloat(row[amountIndex]) || 0), 0));
  },

  formatAmount(value) {
    return String(Math.round((parseFloat(value) || 0) * 100) / 100);
  },

  async saveDraft() {
    if (this.data.draftSaving) return;
    if (!this.data.draftTitle.trim()) {
      wx.showToast({ title: '请输入销售单名称', icon: 'none' });
      return;
    }
    try {
      this.setData({ draftSaving: true });
      await request({
        url: `/trade-documents/${this.data.id}/draft`,
        method: 'POST',
        data: { content: {
          title: this.data.draftTitle.trim(),
          companyName: this.data.draftCompanyName.trim(),
          counterpartyName: this.data.draftCounterpartyName.trim(),
          contractNo: this.data.draftContractNo.trim(),
          date: this.data.draftDate,
          columns: this.data.draftColumns,
          rows: this.data.draftRows,
          blankRows: this.data.draftBlankRows,
          totalAmount: this.data.draftTotalAmount
        } }
      });
      this.setData({ showDraftEditor: false });
      wx.showToast({ title: '销售单草稿已保存', icon: 'success' });
      await this.loadAll();
    } catch (error) {
      wx.showToast({ title: error.message || '草稿保存失败', icon: 'none' });
    } finally {
      this.setData({ draftSaving: false });
    }
  },

  publishDraft() {
    if (!this.data.detail || !this.data.detail.canPublish || this.data.publishing) return;
    wx.showModal({
      title: '确认发布销售单',
      content: '发布后需方将可以查看、接收并选择是否直接入库，销售单内容不能再编辑。',
      confirmText: '确认发布',
      success: result => {
        if (result.confirm) this.submitPublishDraft();
      }
    });
  },

  async submitPublishDraft() {
    try {
      this.setData({ publishing: true });
      await request({ url: `/trade-documents/${this.data.id}/publish`, method: 'POST', data: {} });
      wx.showToast({ title: '销售单已发布', icon: 'success' });
      await this.loadAll();
    } catch (error) {
      wx.showToast({ title: error.message || '发布失败', icon: 'none' });
    } finally {
      this.setData({ publishing: false });
    }
  },

  receiveOnly() {
    wx.showModal({
      title: '确认接收销售单',
      content: '接收后暂不增加库存，之后仍可在本页选择仓库入库。',
      success: result => {
        if (result.confirm) this.submitReceive('RECEIVE_ONLY');
      }
    });
  },

  chooseWarehouse() {
    if (this.data.warehouses.length === 0) {
      wx.showModal({
        title: '请先创建仓库',
        content: '直接入库前，需要在库存管理中至少创建一个仓库。',
        confirmText: '去创建',
        success: result => {
          if (result.confirm) wx.navigateTo({ url: '/pages/inventory/inventory' });
        }
      });
      return;
    }
    wx.showActionSheet({
      itemList: this.data.warehouses.map(item => item.name),
      success: result => {
        const warehouse = this.data.warehouses[result.tapIndex];
        if (warehouse) this.submitReceive('INBOUND', warehouse.id);
      }
    });
  },

  async submitReceive(decision, warehouseId) {
    if (this.data.receiving) return;
    try {
      this.setData({ receiving: true });
      const detail = await request({
        url: `/sales-orders/${this.data.id}/receive`,
        method: 'POST',
        data: { decision, warehouseId }
      });
      this.setData({ detail: { ...this.data.detail, ...detail }, items: detail.items || this.data.items });
      wx.showToast({ title: decision === 'INBOUND' ? '销售单已入库' : '销售单已接收', icon: 'success' });
      await this.loadAll();
    } catch (error) {
      wx.showToast({ title: error.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ receiving: false });
    }
  },

  viewPdf() {
    this.downloadPdf(false);
  },

  downloadPdf(showToast) {
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    wx.showLoading({ title: '下载PDF中...' });
    wx.downloadFile({
      url: `${app.globalData.baseUrl}/trade-documents/${this.data.id}/pdf`,
      header,
      timeout: 30000,
      success: response => {
        if (response.statusCode !== 200) {
          wx.showToast({ title: `PDF下载失败（${response.statusCode}）`, icon: 'none' });
          return;
        }
        if (showToast) wx.showToast({ title: 'PDF已下载', icon: 'success' });
        wx.openDocument({
          filePath: response.filePath || response.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          fail: () => wx.showToast({ title: 'PDF打开失败', icon: 'none' })
        });
      },
      fail: error => wx.showToast({ title: error.errMsg || 'PDF下载失败', icon: 'none' }),
      complete: () => wx.hideLoading()
    });
  },

  noop() {}
});
