const { request } = require('../../utils/request');

Page({
  data: {
    templateId: '',
    name: '',
    category: '',
    content: '',
    categories: ['采购', '供货', '交易', '物流', '服务', '其他'],
    showDelete: false
  },

  onLoad(options) {
    const id = options.id || '';
    const name = decodeURIComponent(options.name || '');
    this.setData({ templateId: id, name });
    if (id) this.loadTemplate();
  },

  async loadTemplate() {
    try {
      const tpl = await request({ url: `/contract-templates/${this.data.templateId}` });
      this.setData({
        name: tpl.name || '',
        category: tpl.category || '',
        content: tpl.content || ''
      });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  onNameInput(e) { this.setData({ name: e.detail.value }); },
  onContentInput(e) { this.setData({ content: e.detail.value }); },

  onCategoryTap(e) {
    this.setData({ category: e.currentTarget.dataset.cat });
  },

  /* 保存模板 */
  async save() {
    const { templateId, name, category, content } = this.data;
    if (!name.trim()) { wx.showToast({ title: '请输入模板名称', icon: 'none' }); return; }
    try {
      wx.showLoading({ title: '保存中...' });
      await request({
        url: `/contract-templates/${templateId}`,
        method: 'POST',
        data: { name: name.trim(), category, content }
      });
      wx.hideLoading();
      wx.showToast({ title: '已保存', icon: 'success' });
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message, icon: 'none' });
    }
  },

  /* 删除 */
  async deleteTemplate() {
    const res = await new Promise(r => {
      wx.showModal({ title: '确认删除', content: `确定删除模板"${this.data.name}"？删除后不可恢复`, success: r });
    });
    if (!res.confirm) return;
    try {
      wx.showLoading({ title: '删除中...' });
      await request({ url: `/contract-templates/${this.data.templateId}/delete`, method: 'POST' });
      wx.hideLoading();
      wx.showToast({ title: '已删除', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1000);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message, icon: 'none' });
    }
  }
});
