const { request } = require('../../utils/request');
const { DEFAULT_TEMPLATE, calcTableTotal } = require('../../utils/chineseCurrency');

Page({
  data: {
    templateId: '',
    name: '',
    category: '',
    categories: ['采购', '供货', '交易', '物流', '服务', '其他'],
    // 解析后的模板内容
    title: '',
    fields: [],
    tableSection: null,
    tableRows: [],
    totalAmount: '0',
    totalAmountCn: '零元整',
    clauses: [],
    showDelete: false
  },

  onLoad(options) {
    const id = options.id || '';
    const name = decodeURIComponent(options.name || '');
    this.setData({ templateId: id, name });
    if (id) {
      this.loadTemplate();
    } else {
      // 新建：使用默认购销合同模板
      this.initFromTemplate(DEFAULT_TEMPLATE);
    }
  },

  async loadTemplate() {
    try {
      const tpl = await request({ url: `/contract-templates/${this.data.templateId}` });
      this.setData({ name: tpl.name || '', category: tpl.category || '' });
      let content;
      try {
        content = JSON.parse(tpl.content || '{}');
      } catch (e) {
        content = DEFAULT_TEMPLATE;
      }
      // 确保 content 有基本结构
      if (!content.fields && !content.sections) {
        content = DEFAULT_TEMPLATE;
      }
      this.initFromTemplate(content);
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    }
  },

  initFromTemplate(tpl) {
    const tableSection = (tpl.sections || []).find(s => s.type === 'table');
    const clauses = (tpl.sections || []).filter(s => s.type === 'clause');
    const rows = (tableSection && tableSection.rows) ? tableSection.rows.map(r => [...r]) : [['', '', '', '0', '0', '0']];
    const result = calcTableTotal(rows);

    this.setData({
      title: tpl.title || '购销合同',
      fields: (tpl.fields || []).map(f => ({ ...f })),
      tableSection: tableSection ? { title: tableSection.title, columns: [...tableSection.columns] } : null,
      tableRows: result.rows,
      totalAmount: String(result.totalAmount),
      totalAmountCn: result.totalAmountCn,
      clauses: clauses.map(c => ({ title: c.title || '', content: c.content || '' }))
    });
  },

  // ======= 基本信息 =======
  onNameInput(e) { this.setData({ name: e.detail.value }); },
  onCategoryTap(e) { this.setData({ category: e.currentTarget.dataset.cat }); },

  // ======= 合同信息字段 =======
  onFieldChange(e) {
    const index = e.currentTarget.dataset.index;
    const value = e.detail.value;
    this.setData({ [`fields[${index}].value`]: value });
  },

  onDateFieldChange(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ [`fields[${index}].value`]: e.detail.value });
  },

  // ======= 产品表格 =======
  onTableCellChange(e) {
    const { row, col } = e.currentTarget.dataset;
    const value = e.detail.value;
    const rows = [...this.data.tableRows];
    rows[row][col] = value;
    this.recalcTable(rows);
  },

  addTableRow() {
    const cols = this.data.tableSection ? this.data.tableSection.columns.length : 6;
    const newRow = new Array(cols).fill('0');
    newRow[0] = ''; newRow[1] = ''; newRow[2] = '';
    const rows = [...this.data.tableRows, newRow];
    this.recalcTable(rows);
  },

  deleteTableRow(e) {
    const index = e.currentTarget.dataset.index;
    if (this.data.tableRows.length <= 1) return;
    const rows = this.data.tableRows.filter((_, i) => i !== index);
    this.recalcTable(rows);
  },

  recalcTable(rows) {
    const result = calcTableTotal(rows);
    this.setData({
      tableRows: result.rows,
      totalAmount: String(result.totalAmount),
      totalAmountCn: result.totalAmountCn
    });
  },

  // ======= 条款 =======
  onClauseTitleChange(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ [`clauses[${index}].title`]: e.detail.value });
  },

  onClauseContentChange(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ [`clauses[${index}].content`]: e.detail.value });
  },

  addClause() {
    const clauses = [...this.data.clauses, { title: '', content: '' }];
    this.setData({ clauses });
  },

  deleteClause(e) {
    const index = e.currentTarget.dataset.index;
    const clauses = this.data.clauses.filter((_, i) => i !== index);
    this.setData({ clauses });
  },

  // ======= 保存 =======
  async save() {
    const { templateId, name, category, title, fields, tableSection, tableRows, totalAmount, totalAmountCn, clauses } = this.data;
    if (!name.trim()) { wx.showToast({ title: '请输入模板名称', icon: 'none' }); return; }

    // 构建产品表格的 rows（不含金额列，金额列是自动计算的）
    const saveRows = tableRows.map(r => [r[0] || '', r[1] || '', r[2] || '', r[3] || '0', r[4] || '0', r[5] || '0']);

    const content = JSON.stringify({
      title: title || name,
      fields: fields.map(f => ({ ...f })),
      sections: [
        ...(tableSection ? [{
          title: tableSection.title,
          type: 'table',
          columns: [...tableSection.columns],
          rows: saveRows
        }] : []),
        ...clauses.map(c => ({ title: c.title, type: 'clause', content: c.content }))
      ]
    });

    try {
      wx.showLoading({ title: '保存中...' });
      if (templateId) {
        await request({ url: `/contract-templates/${templateId}`, method: 'POST', data: { name: name.trim(), category, content } });
      } else {
        await request({ url: '/contract-templates', method: 'POST', data: { name: name.trim(), category, content } });
      }
      wx.hideLoading();
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1000);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message, icon: 'none' });
    }
  },

  // ======= 删除 =======
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
