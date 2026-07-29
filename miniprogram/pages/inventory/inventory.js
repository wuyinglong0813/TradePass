const { request } = require('../../utils/request');

Page({
  data: {
    loading: false,
    overview: { warehouseCount: 0, productCount: 0, balances: [] },
    warehouses: [],
    showCreate: false,
    warehouseName: '',
    warehouseAddress: '',
    creating: false
  },

  onShow() {
    this.loadData();
  },

  onPullDownRefresh() {
    this.loadData().finally(() => wx.stopPullDownRefresh());
  },

  async loadData() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const [overview, warehouses] = await Promise.all([
        request({ url: '/inventory/overview' }),
        request({ url: '/warehouses' })
      ]);
      this.setData({ overview: overview || this.data.overview, warehouses: warehouses || [] });
    } catch (error) {
      wx.showToast({ title: error.message || '库存加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  openCreate() {
    this.setData({ showCreate: true, warehouseName: '', warehouseAddress: '' });
  },

  closeCreate() {
    if (!this.data.creating) this.setData({ showCreate: false });
  },

  onWarehouseInput(e) {
    const field = e.currentTarget.dataset.field;
    if (field === 'warehouseName' || field === 'warehouseAddress') {
      this.setData({ [field]: e.detail.value });
    }
  },

  async createWarehouse() {
    if (!this.data.warehouseName.trim() || this.data.creating) {
      if (!this.data.warehouseName.trim()) wx.showToast({ title: '请输入仓库名称', icon: 'none' });
      return;
    }
    try {
      this.setData({ creating: true });
      await request({
        url: '/warehouses',
        method: 'POST',
        data: { name: this.data.warehouseName.trim(), address: this.data.warehouseAddress.trim() }
      });
      this.setData({ showCreate: false });
      wx.showToast({ title: '仓库已创建', icon: 'success' });
      await this.loadData();
    } catch (error) {
      wx.showToast({ title: error.message || '创建失败', icon: 'none' });
    } finally {
      this.setData({ creating: false });
    }
  },

  noop() {}
});
