const { request } = require('../../utils/request');

function currentPeriod() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

Page({
  data: {
    counterpartyName: '',
    counterpartyCompanyId: '',
    isGlobal: false,
    role: 'supplier',
    pageTitle: '客户对账',
    loading: false,
    uploading: false,
    statementPeriod: currentPeriod(),
    statements: []
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    const counterpartyCompanyId = decodeURIComponent(options.counterpartyCompanyId || '');
    const role = options.role === 'buyer' ? 'buyer' : 'supplier';
    this.setData({
      counterpartyName: name,
      counterpartyCompanyId,
      isGlobal: !counterpartyCompanyId,
      role,
      pageTitle: role === 'buyer' ? '供应商对账' : '客户对账'
    });
    wx.setNavigationBarTitle({ title: counterpartyCompanyId ? '对账情况' : '对账中心' });
    this.loadStatements();
  },

  onPullDownRefresh() {
    this.loadStatements().finally(() => wx.stopPullDownRefresh());
  },

  formatFileSize(size) {
    const bytes = Number(size || 0);
    if (bytes < 1024) return `${bytes}B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
  },

  async loadStatements() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const query = this.data.counterpartyCompanyId
        ? `?counterpartyCompanyId=${encodeURIComponent(this.data.counterpartyCompanyId)}`
        : '';
      const list = await request({ url: `/reconciliation-statements${query}` });
      this.setData({
        statements: (list || []).map(item => ({
          ...item,
          fileSizeText: this.formatFileSize(item.fileSize),
          dateText: String(item.createdAt || '').replace('T', ' ').slice(0, 16)
        }))
      });
    } catch (error) {
      wx.showToast({ title: error.message || '对账单加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  onPeriodChange(e) {
    this.setData({ statementPeriod: e.detail.value });
  },

  chooseStatement() {
    if (!this.data.counterpartyCompanyId) {
      wx.showToast({ title: '请从具体合作企业进入后上传', icon: 'none' });
      return;
    }
    if (this.data.uploading) return;
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['xlsx'],
      success: result => {
        const file = result.tempFiles && result.tempFiles[0];
        if (!file || !file.path) return;
        if (file.size && file.size > 10 * 1024 * 1024) {
          wx.showToast({ title: '对账单不能超过10MB', icon: 'none' });
          return;
        }
        this.uploadStatement(file);
      }
    });
  },

  uploadStatement(file) {
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    this.setData({ uploading: true });
    wx.showLoading({ title: '上传Excel中...' });
    wx.uploadFile({
      url: `${app.globalData.baseUrl}/reconciliation-statements`,
      filePath: file.path,
      name: 'file',
      formData: {
        counterpartyCompanyId: this.data.counterpartyCompanyId,
        period: this.data.statementPeriod,
        originalName: file.name || `客户对账单-${this.data.statementPeriod}.xlsx`
      },
      header,
      timeout: 30000,
      success: response => {
        let body = null;
        try { body = JSON.parse(response.data || '{}'); } catch (error) { /* 统一提示 */ }
        if (response.statusCode >= 200 && response.statusCode < 300 && body && body.code === 0) {
          wx.showToast({ title: '对账单已上传', icon: 'success' });
          this.loadStatements();
          return;
        }
        wx.showToast({ title: (body && body.message) || '上传失败', icon: 'none' });
      },
      fail: error => wx.showToast({ title: error.errMsg || '上传失败', icon: 'none' }),
      complete: () => {
        wx.hideLoading();
        this.setData({ uploading: false });
      }
    });
  },

  viewStatement(e) {
    this.downloadStatementFile(e.currentTarget.dataset.statement, false);
  },

  downloadStatement(e) {
    this.downloadStatementFile(e.currentTarget.dataset.statement, true);
  },

  downloadStatementFile(statement, download) {
    if (!statement || !statement.id) return;
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    wx.showLoading({ title: download ? '下载中...' : '打开Excel中...' });
    wx.downloadFile({
      url: `${app.globalData.baseUrl}/reconciliation-statements/${statement.id}/content?download=${download}`,
      header,
      timeout: 30000,
      success: response => {
        if (response.statusCode !== 200) {
          wx.showToast({ title: `文件获取失败（${response.statusCode}）`, icon: 'none' });
          return;
        }
        wx.openDocument({
          filePath: response.filePath || response.tempFilePath,
          fileType: 'xlsx',
          showMenu: true,
          fail: () => wx.showToast({ title: 'Excel打开失败', icon: 'none' })
        });
      },
      fail: error => wx.showToast({ title: error.errMsg || '文件获取失败', icon: 'none' }),
      complete: () => wx.hideLoading()
    });
  }
});
