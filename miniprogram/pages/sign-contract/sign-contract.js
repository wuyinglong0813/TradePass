const { calcTableTotal, reorderClauses } = require('../../utils/chineseCurrency');

function newClientRequestId() {
  return `contract-${Date.now()}-${Math.random().toString(16).slice(2, 14)}`;
}

function normalizeNumericText(value) {
  const text = String(value == null ? '' : value).trim();
  return /^0+\d/.test(text) ? text.replace(/^0+(?=\d)/, '') : text;
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
    clientRequestId: '',
    editMode: false,
    contractId: '',
    originalContractNo: '',
    inventoryProducts: [],
    productSuggestions: [],
    specificationSuggestions: [],
    suggestionRow: -1,
    submitting: false
  },

  async onLoad(options) {
    const name = decodeURIComponent(options.counterpartyName || '');
    const counterpartyCompanyId = decodeURIComponent(options.counterpartyCompanyId || '');
    const role = options.role === 'buyer' ? 'buyer' : 'supplier';
    const editMode = options.mode === 'edit' && !!options.contractId;
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
      clientRequestId: newClientRequestId(),
      editMode,
      contractId: options.contractId || ''
    });
    await Promise.all([this.loadTemplates(), this.loadInventoryProducts()]);
    if (editMode) await this.loadExistingContract();
  },

  async loadTemplates() {
    const { request } = require('../../utils/request');
    try {
      const payload = await request({ url: '/contract-templates?page=1&size=100' });
      const list = Array.isArray(payload) ? payload : (payload.items || []);
      const templates = (list || []).map(t => ({ id: t.id, name: t.name, content: t.content }));
      this.setData({ templates });
      // 默认选中第一个模板
      if (!this.data.editMode && templates.length > 0) {
        this.selectTemplate(0);
      }
      return templates;
    } catch (e) {
      // 静默
      return [];
    }
  },

  async loadExistingContract() {
    const { request } = require('../../utils/request');
    try {
      const contract = await request({ url: `/contracts/${this.data.contractId}` });
      if (contract.perspective !== 'OUTGOING'
        || !['PENDING', 'REJECTED', 'CANCELLED'].includes(contract.status)) {
        throw new Error('当前合同不能修改');
      }
      let content;
      try {
        content = JSON.parse(contract.terms || '{}');
      } catch (error) {
        throw new Error('合同正文格式异常，无法修改');
      }
      const tableSection = (content.sections || []).find(section => section.type === 'table');
      const clauses = reorderClauses((content.sections || []).filter(section => section.type === 'clause'));
      const rows = tableSection && Array.isArray(tableSection.rows)
        ? tableSection.rows.map(row => row.slice()) : [['', '', '', '', '', '0']];
      const total = calcTableTotal(rows);
      let templateIndex = this.data.templates.findIndex(item => item.name === contract.templateName);
      if (templateIndex < 0) {
        const templates = this.data.templates.concat([{
          id: `contract-${contract.id}`,
          name: contract.templateName || '原合同模板',
          content: contract.terms
        }]);
        templateIndex = templates.length - 1;
        this.setData({ templates });
      }
      this.setData({
        counterpartyName: contract.viewerCounterpartyName || contract.counterpartyName || '',
        counterpartyCompanyId: String(contract.viewerCounterpartyCompanyId || contract.counterpartyCompanyId || ''),
        role: (contract.viewerDirection || contract.direction) === 'PURCHASE' ? 'buyer' : 'supplier',
        contractActionText: '保存修改并重新发起',
        contractName: contract.name || content.title || '',
        originalContractNo: contract.contractNo || '',
        templateIndex,
        fields: (content.fields || []).map(field => ({ ...field })),
        tableSection: tableSection ? {
          title: tableSection.title,
          columns: (tableSection.columns || []).slice()
        } : null,
        tableRows: total.rows,
        totalAmount: String(total.totalAmount),
        totalAmountCn: total.totalAmountCn,
        clauses: clauses.map(clause => ({ title: clause.title || '', content: clause.content || '' })),
        hasTemplate: true
      });
      wx.setNavigationBarTitle({ title: '修改合同' });
    } catch (error) {
      wx.showToast({ title: error.message || '合同加载失败', icon: 'none' });
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
    const rows = (tableSection && tableSection.rows)
      ? tableSection.rows.map(r => [...r]) : [['', '', '', '', '', '0']];
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

  async loadInventoryProducts() {
    const { request } = require('../../utils/request');
    try {
      const products = await request({ url: '/inventory/products?limit=200' });
      this.setData({ inventoryProducts: products || [] });
    } catch (error) {
      this.setData({ inventoryProducts: [] });
    }
  },

  tableColumnIndex(kind) {
    const columns = (this.data.tableSection && this.data.tableSection.columns) || [];
    const aliases = {
      product: ['品名', '名称', '产品'],
      specification: ['规格', '型号'],
      unit: ['单位'],
      quantity: ['数量'],
      price: ['单价'],
      amount: ['金额']
    }[kind] || [];
    return columns.findIndex(column => aliases.some(alias => String(column || '').includes(alias)));
  },

  // ======= 产品表格编辑 =======
  onTableCellChange(e) {
    const { row, col } = e.currentTarget.dataset;
    const rowIndex = Number(row);
    const columnIndex = Number(col);
    const quantityIndex = this.tableColumnIndex('quantity');
    const priceIndex = this.tableColumnIndex('price');
    const amountIndex = this.tableColumnIndex('amount');
    const productIndex = this.tableColumnIndex('product');
    const specificationIndex = this.tableColumnIndex('specification');
    const value = columnIndex === quantityIndex || columnIndex === priceIndex
      ? normalizeNumericText(e.detail.value)
      : e.detail.value;
    const targetRow = (this.data.tableRows[rowIndex] || []).slice();
    targetRow[columnIndex] = value;
    this.data.tableRows[rowIndex] = targetRow;
    const changes = { [`tableRows[${rowIndex}][${columnIndex}]`]: value };
    if ((columnIndex === quantityIndex || columnIndex === priceIndex)
      && quantityIndex >= 0 && priceIndex >= 0 && amountIndex >= 0) {
      const amount = Math.round(((parseFloat(targetRow[quantityIndex]) || 0)
        * (parseFloat(targetRow[priceIndex]) || 0)) * 100) / 100;
      targetRow[amountIndex] = String(amount);
      changes[`tableRows[${rowIndex}][${amountIndex}]`] = String(amount);
      const total = this.data.tableRows.reduce((sum, item) =>
        sum + (parseFloat((item || [])[amountIndex]) || 0), 0);
      const totals = calcTableTotal(this.data.tableRows);
      changes.totalAmount = String(Math.round(total * 100) / 100);
      changes.totalAmountCn = totals.totalAmountCn;
    }
    if (columnIndex === productIndex) {
      const keyword = String(value || '').trim().toLowerCase();
      const products = this.data.inventoryProducts || [];
      const seen = new Set();
      changes.suggestionRow = rowIndex;
      changes.productSuggestions = keyword ? products.filter(item => {
        const name = String(item.productName || '');
        if (!name.toLowerCase().includes(keyword) || seen.has(name)) return false;
        seen.add(name);
        return true;
      }).slice(0, 8) : [];
      changes.specificationSuggestions = [];
    } else if (columnIndex === specificationIndex) {
      const productName = String(targetRow[productIndex] || '').trim();
      const keyword = String(value || '').trim().toLowerCase();
      changes.suggestionRow = rowIndex;
      changes.productSuggestions = [];
      changes.specificationSuggestions = (this.data.inventoryProducts || [])
        .filter(item => item.productName === productName
          && (!keyword || String(item.specification || '').toLowerCase().includes(keyword)))
        .slice(0, 8);
    }
    this.setData(changes);
    return value;
  },

  selectInventoryProduct(e) {
    const rowIndex = Number(e.currentTarget.dataset.row);
    const productName = String(e.currentTarget.dataset.name || '');
    const products = (this.data.inventoryProducts || []).filter(item => item.productName === productName);
    if (!this.data.tableRows[rowIndex] || products.length === 0) return;
    const productIndex = this.tableColumnIndex('product');
    const specificationIndex = this.tableColumnIndex('specification');
    const unitIndex = this.tableColumnIndex('unit');
    const changes = {
      [`tableRows[${rowIndex}][${productIndex}]`]: productName,
      productSuggestions: [],
      specificationSuggestions: products.slice(0, 8),
      suggestionRow: rowIndex
    };
    this.data.tableRows[rowIndex][productIndex] = productName;
    if (products.length === 1) {
      const selected = products[0];
      if (specificationIndex >= 0) {
        this.data.tableRows[rowIndex][specificationIndex] = selected.specification || '';
        changes[`tableRows[${rowIndex}][${specificationIndex}]`] = selected.specification || '';
      }
      if (unitIndex >= 0) {
        this.data.tableRows[rowIndex][unitIndex] = selected.baseUnit || '';
        changes[`tableRows[${rowIndex}][${unitIndex}]`] = selected.baseUnit || '';
      }
      changes.specificationSuggestions = [];
    } else if (specificationIndex >= 0) {
      this.data.tableRows[rowIndex][specificationIndex] = '';
      changes[`tableRows[${rowIndex}][${specificationIndex}]`] = '';
    }
    this.setData(changes);
  },

  selectInventorySpecification(e) {
    const rowIndex = Number(e.currentTarget.dataset.row);
    const productId = String(e.currentTarget.dataset.id || '');
    const selected = (this.data.inventoryProducts || [])
      .find(item => String(item.id) === productId);
    if (!selected || !this.data.tableRows[rowIndex]) return;
    const productIndex = this.tableColumnIndex('product');
    const specificationIndex = this.tableColumnIndex('specification');
    const unitIndex = this.tableColumnIndex('unit');
    const changes = { productSuggestions: [], specificationSuggestions: [], suggestionRow: -1 };
    [
      [productIndex, selected.productName || ''],
      [specificationIndex, selected.specification || ''],
      [unitIndex, selected.baseUnit || '']
    ].forEach(([index, value]) => {
      if (index < 0) return;
      this.data.tableRows[rowIndex][index] = value;
      changes[`tableRows[${rowIndex}][${index}]`] = value;
    });
    this.setData(changes);
  },

  onNumberCellFocus(e) {
    const { row, col } = e.currentTarget.dataset;
    const value = String((this.data.tableRows[row] || [])[col] || '').trim();
    if (/^0(?:\.0*)?$/.test(value)) {
      this.setData({ [`tableRows[${row}][${col}]`]: '' });
    }
  },

  onNumberCellBlur(e) {
    const { row, col } = e.currentTarget.dataset;
    if (String(e.detail.value || '').trim()) return;
    const rows = [...this.data.tableRows];
    if (!rows[row]) rows[row] = [];
    rows[row][col] = '0';
    this.recalcTable(rows);
  },

  addTableRow() {
    const cols = this.data.tableSection ? this.data.tableSection.columns.length : 6;
    const newRow = new Array(cols).fill('');
    const amountIndex = this.tableColumnIndex('amount');
    if (amountIndex >= 0) newRow[amountIndex] = '0';
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

  onClauseTitleInput(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ [`clauses[${index}].title`]: e.detail.value });
  },

  onClauseContentInput(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ [`clauses[${index}].content`]: e.detail.value });
  },

  addClause() {
    this.setData({ clauses: this.data.clauses.concat([{ title: '', content: '' }]) });
  },

  deleteClause(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ clauses: this.data.clauses.filter((_, itemIndex) => itemIndex !== index) });
  },

  // ======= 提交 =======
  async onSubmit() {
    const { templates, templateIndex, contractName, fields, tableSection, tableRows, totalAmount, clauses,
      counterpartyName, counterpartyCompanyId, role, clientRequestId, editMode, contractId,
      submitting } = this.data;
    if (submitting) return;
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
          rows: tableRows.map(row => tableSection.columns.map((column, index) => {
            const value = row[index];
            return value == null ? '' : String(value);
          })),
          summary: { totalAmount: totalAmount, totalAmountCn: this.data.totalAmountCn }
        }] : []),
        ...clauses.map(c => ({ title: c.title, type: 'clause', content: c.content }))
      ]
    });

    const res = await new Promise(r => {
      wx.showModal({
        title: editMode ? '确认重新发起' : '确认签订',
        content: `即将与 ${counterpartyName} ${editMode ? '重新发起' : '签订'}合同\n金额：¥${totalAmount}\n模板：${templates[templateIndex].name}\n\n提交后需对方公司审批通过方可生效`,
        success: r
      });
    });
    if (!res.confirm) return;

    const { request } = require('../../utils/request');
    try {
      this.setData({ submitting: true });
      wx.showLoading({ title: editMode ? '提交中...' : '发起中...' });
      const result = await request({
        url: editMode ? `/contracts/${contractId}/resubmit` : '/contracts',
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
      const resultContractId = String(result.id);
      wx.showToast({
        title: editMode ? '合同已更新并重新发起' : '合同已发起，等待对方审批',
        icon: 'success', duration: 1500
      });
      // 签订成功后跳转到合同预览页查看完整合同
      setTimeout(() => {
        wx.redirectTo({
          url: `/pages/contract-preview/contract-preview?contractId=${resultContractId}&contractName=${encodeURIComponent(contractName.trim())}&counterpartyName=${encodeURIComponent(counterpartyName)}`
        });
      }, 1500);
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    } finally {
      wx.hideLoading();
      this.setData({ submitting: false });
    }
  }
});
