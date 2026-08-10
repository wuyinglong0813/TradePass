const { request } = require('../../utils/request');

Page({
  data: {
    counterpartyName: '',
    counterpartyCompanyId: '',
    isGlobal: false,
    role: 'supplier',
    pageTitle: '客户对账',
    loading: false,
    accounts: []
  },

  onLoad(options) {
    const counterpartyName = decodeURIComponent(options.counterpartyName || '');
    const counterpartyCompanyId = decodeURIComponent(options.counterpartyCompanyId || '');
    const role = options.role === 'buyer' ? 'buyer' : 'supplier';
    this.setData({
      counterpartyName,
      counterpartyCompanyId,
      isGlobal: !counterpartyCompanyId,
      role,
      pageTitle: role === 'buyer' ? '供应商对账' : '客户对账'
    });
    wx.setNavigationBarTitle({ title: role === 'buyer' ? '供应商对账' : '客户对账' });
    this.loadAccounts();
  },

  onPullDownRefresh() {
    this.loadAccounts().finally(() => wx.stopPullDownRefresh());
  },

  mapAccount(item) {
    return {
      ...item,
      entryCount: Number(item.entryCount || 0),
      updatedText: item.updatedAt
        ? String(item.updatedAt).replace('T', ' ').slice(0, 16)
        : '暂无已确认单据'
    };
  },

  async loadAccounts() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const raw = this.data.isGlobal
        ? await request({ url: '/reconciliation-accounts' })
        : [await request({ url: `/reconciliation-accounts/${encodeURIComponent(this.data.counterpartyCompanyId)}` })];
      this.setData({ accounts: (raw || []).map(item => this.mapAccount(item)) });
    } catch (error) {
      wx.showToast({ title: error.message || '对账单加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  viewWorkbook(e) {
    this.downloadWorkbookFile(e.currentTarget.dataset.account, false);
  },

  downloadWorkbook(e) {
    this.downloadWorkbookFile(e.currentTarget.dataset.account, true);
  },

  downloadWorkbookFile(account, download) {
    if (!account || !account.counterpartyCompanyId) return;
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    wx.showLoading({ title: download ? '下载对账单中...' : '打开对账单中...' });
    wx.downloadFile({
      url: `${app.globalData.baseUrl}/reconciliation-accounts/${account.counterpartyCompanyId}/workbook?download=${download}`,
      header,
      timeout: 30000,
      success: response => {
        if (response.statusCode !== 200) {
          wx.showToast({ title: `对账单获取失败（${response.statusCode}）`, icon: 'none' });
          return;
        }
        wx.openDocument({
          filePath: response.filePath || response.tempFilePath,
          fileType: 'xlsx',
          showMenu: true,
          fail: () => wx.showToast({ title: 'Excel打开失败', icon: 'none' })
        });
      },
      fail: error => wx.showToast({ title: error.errMsg || '对账单获取失败', icon: 'none' }),
      complete: () => wx.hideLoading()
    });
  }
});
