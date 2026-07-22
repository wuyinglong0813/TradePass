const { calcTableTotal, reorderClauses } = require('../../utils/chineseCurrency');

function newClientRequestId() {
  return `contract-${Date.now()}-${Math.random().toString(16).slice(2, 14)}`;
}

Page({
  data: {
    counterpartyName: '',
    counterpartyCompanyId: '',
    myCompanyName: '',
    role: 'supplier',
    contractActionText: '发起销售合同',
    // 合同名称
    contractName: '',
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
    hasTemplate: false,
    clientRequestId: ''
  },

  onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    const counterpartyCompanyId = decodeURIComponent(options.counterpartyCompanyId || '');
    const role = options.role === 'buyer' ? 'buyer' : 'supplier';
    const app = getApp();
    const companies = app.globalData.companies || [];
    const cid = (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId) || '';
    const cur = companies.find(c => c.companyId === cid);
    this.setData({
      counterpartyName: name,
      counterpartyCompanyId,
      myCompanyName: (cur && cur.companyName) || '我的企业',
      role,
      contractActionText: role === 'supplier' ? '发起销售合同' : '发起采购合同',
      clientRequestId: newClientRequestId()
    });
    this.loadTemplates();
  },

  async loadTemplates() {
    const { request } = require('../../utils/request');
    try {
      const payload = await request({ url: '/contract-templates?page=1&size=100' });
      const list = Array.isArray(payload) ? payload : (payload.items || []);
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
    const role = this.data.role;

    // 自动填充字段
    const fields = (content.fields || []).map(f => {
      let value = f.value || '';
      if (f.key === 'supplier') value = role === 'supplier' ? myName : cpName;
      else if (f.key === 'buyer') value = role === 'supplier' ? cpName : myName;
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
      contractName: this.data.contractName || (content.title || '购销合同'),
      fields,
      tableSection: tableSection ? { title: tableSection.title, columns: [...tableSection.columns] } : null,
      tableRows: result.rows,
      totalAmount: String(result.totalAmount),
      totalAmountCn: result.totalAmountCn,
      clauses: clauses.map(c => ({ title: c.title || '', content: c.content || '' })),
      hasTemplate: true
    });
  },

  onContractNameInput(e) { this.setData({ contractName: e.detail.value }); },

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
    const newRow = new Array(cols).fill('');
    newRow[3] = '0'; newRow[4] = '0'; newRow[5] = '0';
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
    const { templates, templateIndex, contractName, fields, tableSection, tableRows, totalAmount, clauses,
      counterpartyName, counterpartyCompanyId, role, clientRequestId } = this.data;
    if (templateIndex < 0) { wx.showToast({ title: '请选择合同模板', icon: 'none' }); return; }
    if (!contractName.trim()) { wx.showToast({ title: '请输入合同名称', icon: 'none' }); return; }

    const total = parseFloat(totalAmount) || 0;
    // 构建完整合同 JSON 存入 terms
    const contractContent = JSON.stringify({
      title: contractName.trim(),
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
      const result = await request({
        url: '/contracts',
        method: 'POST',
        data: {
          counterpartyName,
          counterpartyCompanyId: Number(counterpartyCompanyId),
          direction: role === 'supplier' ? 'SALE' : 'PURCHASE',
          clientRequestId,
          name: contractName.trim(),
          templateName: templates[templateIndex].name,
          amount: total,
          startDate: (fields.find(f => f.key === 'signDate') || {}).value || null,
          endDate: null,
          terms: contractContent
        }
      });
      wx.hideLoading();
      const contractId = String(result.id);
      wx.showToast({ title: '合同已发起，等待对方审批', icon: 'success', duration: 1500 });
      // 签订成功后跳转到合同预览页查看完整合同
      setTimeout(() => {
        wx.redirectTo({
          url: `/pages/contract-preview/contract-preview?contractId=${contractId}&contractName=${encodeURIComponent(contractName.trim())}&counterpartyName=${encodeURIComponent(counterpartyName)}`
        });
      }, 1500);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: e.message, icon: 'none' });
    }
  }
});
