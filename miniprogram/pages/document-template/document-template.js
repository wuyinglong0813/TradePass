const { request } = require('../../utils/request');

const TYPE_META = {
  SALES_ORDER: {
    label: '销售单',
    empty: '暂无销售单模板',
    columns: ['序号', '品名', '规格', '单位', '数量', '单价', '金额', '备注'],
    blankRows: 8
  },
  DELIVERY_NOTE: {
    label: '送货单',
    empty: '暂无送货单模板',
    columns: ['序号', '品名', '规格', '数量', '单位', '备注'],
    blankRows: 10
  }
};

Page({
  data: {
    tabs: [
      { key: 'SALES_ORDER', label: '销售单模板' },
      { key: 'DELIVERY_NOTE', label: '送货单模板' }
    ],
    activeType: 'SALES_ORDER',
    templates: [],
    salesCount: 0,
    deliveryCount: 0,
    loading: false,
    emptyText: TYPE_META.SALES_ORDER.empty
  },

  onShow() {
    this.loadTemplates();
  },

  onPullDownRefresh() {
    this.loadTemplates().finally(() => wx.stopPullDownRefresh());
  },

  switchType(e) {
    const activeType = e.currentTarget.dataset.type;
    if (activeType === this.data.activeType) return;
    this.setData({
      activeType,
      templates: [],
      emptyText: TYPE_META[activeType].empty
    });
    this.loadTemplates();
  },

  async loadTemplates() {
    if (this.data.loading) return;
    this.setData({ loading: true });
    try {
      const [sales, delivery] = await Promise.all([
        request({ url: '/document-templates?type=SALES_ORDER' }),
        request({ url: '/document-templates?type=DELIVERY_NOTE' })
      ]);
      const source = this.data.activeType === 'SALES_ORDER' ? sales : delivery;
      const templates = (source || []).map(item => ({
        ...item,
        dateText: String(item.updatedAt || item.createdAt || '').slice(0, 10),
        sourceText: item.sourceFileName || '标准版式'
      }));
      this.setData({
        templates,
        salesCount: (sales || []).length,
        deliveryCount: (delivery || []).length
      });
    } catch (error) {
      wx.showToast({ title: error.message || '模板加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  uploadSalesTemplate() {
    this.chooseTemplateFile('SALES_ORDER');
  },

  uploadDeliveryTemplate() {
    this.chooseTemplateFile('DELIVERY_NOTE');
  },

  chooseTemplateFile(documentType) {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['json', 'pdf', 'doc', 'docx', 'xls', 'xlsx'],
      success: async result => {
        const file = result.tempFiles && result.tempFiles[0];
        if (!file) return;
        const content = await this.readTemplateContent(file.path, file.name, documentType);
        this.confirmUpload(documentType, file.name, content);
      },
      fail: error => {
        if (error && error.errMsg && !error.errMsg.includes('cancel')) {
          wx.showToast({ title: '文件选择失败', icon: 'none' });
        }
      }
    });
  },

  readTemplateContent(filePath, fileName, documentType) {
    const defaults = TYPE_META[documentType];
    const defaultContent = JSON.stringify({
      columns: defaults.columns,
      blankRows: defaults.blankRows
    });
    if (!String(fileName || '').toLowerCase().endsWith('.json')) {
      return Promise.resolve(defaultContent);
    }
    return new Promise(resolve => {
      wx.getFileSystemManager().readFile({
        filePath,
        encoding: 'utf8',
        success: result => {
          try {
            const parsed = JSON.parse(result.data);
            if (!Array.isArray(parsed.columns) || parsed.columns.length === 0) {
              throw new Error('invalid');
            }
            resolve(JSON.stringify(parsed));
          } catch (error) {
            wx.showToast({ title: 'JSON格式无效，已采用标准版式', icon: 'none' });
            resolve(defaultContent);
          }
        },
        fail: () => resolve(defaultContent)
      });
    });
  },

  confirmUpload(documentType, fileName, content) {
    const meta = TYPE_META[documentType];
    const baseName = String(fileName || `${meta.label}模板`)
      .replace(/\.[^.]+$/, '')
      .slice(0, 60);
    wx.showModal({
      title: `上传${meta.label}模板`,
      editable: true,
      placeholderText: '请输入模板名称',
      content: baseName,
      success: async result => {
        if (!result.confirm) return;
        const name = String(result.content || baseName).trim();
        if (!name) {
          wx.showToast({ title: '模板名称不能为空', icon: 'none' });
          return;
        }
        await this.saveTemplate({ documentType, name, sourceFileName: fileName, content });
      }
    });
  },

  async saveTemplate(payload) {
    try {
      wx.showLoading({ title: '上传中...' });
      await request({ url: '/document-templates', method: 'POST', data: payload });
      this.setData({ activeType: payload.documentType });
      await this.loadTemplates();
      wx.showToast({ title: '模板已上传', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '上传失败', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  deleteTemplate(e) {
    const template = e.currentTarget.dataset.template;
    wx.showModal({
      title: '删除单据模板',
      content: `确定删除“${template.name}”吗？`,
      success: async result => {
        if (!result.confirm) return;
        try {
          await request({ url: `/document-templates/${template.id}/delete`, method: 'POST' });
          wx.showToast({ title: '已删除', icon: 'success' });
          this.loadTemplates();
        } catch (error) {
          wx.showToast({ title: error.message || '删除失败', icon: 'none' });
        }
      }
    });
  },

  noop() {}
});
