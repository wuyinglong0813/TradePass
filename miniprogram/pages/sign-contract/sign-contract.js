const { calcTableTotal, reorderClauses } = require('../../utils/chineseCurrency');

Page({
  data: {
    counterpartyName: '',
    myCompanyName: '',
    // 模板
    templates: [],
    templateIndex: -1,
    // 合同编号（签订时自动生成）
    contractNo: '',
    // 头部字段（供方/需方/日期/地点）
    fields: [],
    // 产品表格
    tableSection: null,
    tableRows: [],
    totalAmount: '0',
    totalAmountCn: '零元整',
    // 条款
    clauses: [],
    // 是否已选择模板
    hasTemplate: false
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    const app = getApp();
    const companies = app.globalData.companies || [];
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '';
    const cur = companies.find(c => c.companyId === cid);
    this.setData({
      counterpartyName: name,
      myCompanyName: (cur && cur.companyName) || '我的企业'
    });
    this.loadTemplates();
  },

  async loadTemplates() {
    const { request } = require('../../utils/request');
    try {
      const list = await request({ url: '/contract-templates' });
      const templates = (list || []).map(t => ({ id: t.id, name: t.name, content: t.content }));
      this.setData({ templates });
      // 默认选中第一个模板
      if (templates.length > 0) {
        this.selectTemplate(0);
      }
    } catch (e) {
      // 静默
    }
  },

  selectTemplate(idx) {
    const tpl = this.data.templates[idx];
    if (!tpl) return;
    let content;
    try {
      content = JSON.parse(tpl.content || '{}');
    } catch (e) {
      wx.showToast({ title: '模板格式异常', icon: 'none' });
      return;
    }
    if (!content.fields && !content.sections) {
      wx.showToast({ title: '模板内容为空', icon: 'none' });
      return;
    }

    const myName = this.data.myCompanyName;
    const cpName = this.data.counterpartyName;

    // 自动填充字段
    const fields = (content.fields || []).map(f => {
      let value = f.value || '';
      if (f.key === 'supplier') value = cpName;   // 供方=对方公司
      else if (f.key === 'buyer') value = myName;  // 需方=我司
      else if (f.key === 'signDate') value = new Date().toISOString().slice(0, 10);
      return { ...f, value };
    });

    const tableSection = (content.sections || []).find(s => s.type === 'table');
    const rawClauses = (content.sections || []).filter(s => s.type === 'clause');
    const clauses = reorderClauses(rawClauses);
    const rows = (tableSection && tableSection.rows) ? tableSection.rows.map(r => [...r]) : [['', '', '', '0', '0', '0']];
    const result = calcTableTotal(rows);

    this.setData({
      templateIndex: idx,
      fields,
      tableSection: tableSection ? { title: tableSection.title, columns: [...tableSection.columns] } : null,
      tableRows: result.rows,
      totalAmount: String(result.totalAmount),
      totalAmountCn: result.totalAmountCn,
      clauses: clauses.map(c => ({ title: c.title || '', content: c.content || '' })),
      hasTemplate: true
    });
  },

  onTemplateChange(e) {
    const idx = parseInt(e.detail.value);
    if (idx >= 0) {
      this.selectTemplate(idx);
    }
  },

  // ======= 字段编辑 =======
  onFieldChange(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ [`fields[${index}].value`]: e.detail.value });
  },

  onDateFieldChange(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ [`fields[${index}].value`]: e.detail.value });
  },

  // ======= 产品表格编辑 =======
  onTableCellChange(e) {
    const { row, col } = e.currentTarget.dataset;
    const rows = [...this.data.tableRows];
    rows[row][col] = e.detail.value;
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

  // ======= 提交 =======
  async onSubmit() {
    const { templates, templateIndex, fields, tableSection, tableRows, totalAmount, clauses, counterpartyName, myCompanyName } = this.data;
    if (templateIndex < 0) { wx.showToast({ title: '请选择合同模板', icon: 'none' }); return; }

    const total = parseFloat(totalAmount) || 0;

    // 构建完整合同 JSON 存入 terms
    const contractContent = JSON.stringify({
      title: '购销合同',
      fields: fields.map(f => ({ ...f })),
      sections: [
        ...(tableSection ? [{
          title: tableSection.title,
          type: 'table',
          columns: [...tableSection.columns],
          rows: tableRows.map(r => [r[0] || '', r[1] || '', r[2] || '', r[3] || '0', r[4] || '0', r[5] || '0']),
          summary: { totalAmount: totalAmount, totalAmountCn: this.data.totalAmountCn }
        }] : []),
        ...clauses.map(c => ({ title: c.title, type: 'clause', content: c.content }))
      ]
    });

    const res = await new Promise(r => {
      wx.showModal({
        title: '确认签订',
        content: `即将与 ${counterpartyName} 签订合同\n金额：¥${totalAmount}\n模板：${templates[templateIndex].name}\n\n发起后需对方公司审批通过方可生效`,
        success: r
      });
    });
    if (!res.confirm) return;

    const { request } = require('../../utils/request');
    try {
      wx.showLoading({ title: '发起中...' });
      await request({
        url: '/contracts',
        method: 'POST',
        data: {
          counterpartyName,
          name: '购销合同',
          templateName: templates[templateIndex].name,
          amount: total,
          startDate: (fields.find(f => f.key === 'signDate') || {}).value || '',
          endDate: '',
          terms: contractContent
        }
      });
      wx.hideLoading();
      wx.showToast({ title: '合同已发起，等待对方审批', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1500);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message, icon: 'none' });
    }
  }
});
