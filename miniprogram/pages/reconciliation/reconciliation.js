const { request } = require('../../utils/request');
const { downloadApiFile } = require('../../utils/fileTransfer');

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

  async downloadWorkbookFile(account, download) {
    if (!account || !account.counterpartyCompanyId) return;
    const safeName = String(account.counterpartyName || account.counterpartyCompanyId)
      .replace(/[\\/:*?"<>|\r\n]/g, '_');
    const filePath = `${wx.env.USER_DATA_PATH}/对账单-${safeName}.xlsx`;
    wx.showLoading({ title: download ? '下载对账单中...' : '打开对账单中...' });
    try {
      const result = await downloadApiFile(
        `/reconciliation-accounts/${account.counterpartyCompanyId}/workbook-data`, filePath
      );
      wx.openDocument({
        filePath: result.filePath,
        fileType: 'xlsx',
        showMenu: true,
        fail: () => wx.showToast({ title: 'Excel打开失败', icon: 'none' })
      });
    } catch (error) {
      wx.showToast({ title: error.message || '对账单获取失败', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  }
});
