const { request } = require('../../utils/request');

Page({
  data: {
    keyword: '',
    results: [],
    searched: false,
    selectedCompany: null
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value });
  },

  clearKeyword() {
    this.setData({ keyword: '', results: [], searched: false });
  },

  // 调用第三方企业查询接口
  async doSearch() {
    const keyword = this.data.keyword.trim();
    if (!keyword) {
      wx.showToast({ title: '请输入企业名称（与营业执照上一致）', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '搜索中...' });
    try {
      const results = await request({
        url: `/companies/search?keyword=${encodeURIComponent(keyword)}`
      });
      this.setData({ results: results || [], searched: true, selectedCompany: null });
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  // 从搜索结果中选择一家企业
  selectCompany(e) {
    const company = this.data.results[e.currentTarget.dataset.index];
    this.setData({ selectedCompany: company });
  },

  // 确认企业，跳转实名认证
  confirmCompany() {
    const c = this.data.selectedCompany;
    if (!c) return;
    wx.redirectTo({
      url: `/pages/company-cert/company-cert?name=${encodeURIComponent(c.name)}&creditCode=${encodeURIComponent(c.creditCode)}&legalPersonName=${encodeURIComponent(c.legalPersonName)}`
    });
  }
});
