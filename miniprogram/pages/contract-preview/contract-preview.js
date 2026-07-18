Page({
  data: {
    contractId: '',
    contractName: '',
    counterpartyName: '',
    // 合同详情
    contract: null,
    // Tab
    tabs: [
      { key: 'detail', label: '合同详情' },
      { key: 'sales', label: '销售单' },
      { key: 'logistics', label: '物流单' },
      { key: 'delivery', label: '送货单' },
      { key: 'invoice', label: '发票浏览' }
    ],
    activeTab: 'detail',
    // 列表
    salesList: [],
    logisticsList: [],
    salesDocuments: [],
    deliveryDocuments: [],
    invoiceList: [],
    loading: false,
    pdfLoading: false,
    pdfReady: false,
    pdfFilePath: '',
    pdfTitle: '购销合同',
    pdfSupplier: '',
    pdfBuyer: '',
    pdfDate: '',
    // 结构化合同
    hasStructured: false,
    sData: null,
    // 详情面板
    showDetail: false,
    detailTitle: '',
    detailFields: []
  },

  onLoad(options) {
    const contractId = options.contractId || '';
    const contractName = decodeURIComponent(options.contractName || '');
    const counterpartyName = decodeURIComponent(options.counterpartyName || '');
    this.setData({ contractId, contractName, counterpartyName });
    wx.setNavigationBarTitle({ title: '合同详情' });
    setTimeout(() => this.loadContractDetail(), 100);
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.activeTab) return;
    this.setData({ activeTab: tab });
    if (tab === 'sales') {
      this.loadSalesOrders();
      this.loadBusinessDocuments('SALES_ORDER');
    } else if (tab === 'delivery') {
      this.loadBusinessDocuments('DELIVERY_NOTE');
    } else if ((tab === 'logistics' || tab === 'invoice') && this.data.logisticsList.length === 0) {
      this.loadSalesOrders();
    }
  },

  /* 加载合同详情 */
  async loadContractDetail() {
    const { request } = require('../../utils/request');
    try {
      const contract = await request({
        url: `/contracts/${this.data.contractId}`
      });
      const statusMap = {
        PENDING: '待签署',
        ACTIVE: '履行中',
        REJECTED: '已拒绝',
        COMPLETED: '已完成'
      };
      // 尝试解析结构化合同数据
      let sData = null;
      try {
        sData = JSON.parse(contract.terms || '');
        if (!sData || (!sData.fields && !sData.sections)) sData = null;
      } catch (e) { /* 非 JSON，使用旧版显示 */ }

      const structuredFields = (sData && sData.fields) || [];
      const fieldValue = key => {
        const field = structuredFields.find(item => item.key === key);
        return (field && field.value) || '';
      };
      const isPurchase = contract.direction === 'PURCHASE';
      const currentCompany = this.currentCompanyName();
      const pdfTitle = (sData && sData.title) || contract.name || this.data.contractName || '购销合同';
      const pdfSupplier = fieldValue('supplier')
        || (isPurchase ? contract.counterpartyName : currentCompany);
      const pdfBuyer = fieldValue('buyer')
        || (isPurchase ? currentCompany : contract.counterpartyName);
      const pdfDate = fieldValue('signDate')
        || contract.startDate
        || String(contract.createdAt || '').slice(0, 10);

      this.setData({
        contract: {
          ...contract,
          statusText: statusMap[contract.status] || contract.status,
          amount: contract.amount || 0
        },
        hasStructured: !!sData,
        sData,
        pdfTitle,
        pdfSupplier: pdfSupplier || '—',
        pdfBuyer: pdfBuyer || '—',
        pdfDate: pdfDate || '—'
      });
      wx.setNavigationBarTitle({ title: '合同详情' });
    } catch (e) {
      wx.showToast({ title: '加载合同失败', icon: 'none' });
    }
  },

  currentCompanyName() {
    const app = getApp();
    const companies = app.globalData.companies || [];
    const currentCompanyId = String(
      app.globalData.currentCompanyId
      || (app.globalData.userInfo && app.globalData.userInfo.currentCompanyId)
      || ''
    );
    const company = companies.find(item => String(item.companyId) === currentCompanyId);
    return (company && company.companyName) || '本方企业';
  },

  openContractPdf() {
    if (!this.data.contract || this.data.pdfLoading) return;
    if (this.data.pdfFilePath) {
      this.openPdfDocument(this.data.pdfFilePath);
      return;
    }
    this.downloadContractPdfFile(false);
  },

  downloadContractPdf() {
    if (!this.data.contract || this.data.pdfLoading) return;
    this.downloadContractPdfFile(true);
  },

  downloadContractPdfFile(showDownloadedToast) {
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const safeName = String(this.data.pdfTitle || '购销合同')
      .replace(/[\\/:*?"<>|\r\n]/g, '_')
      .trim() || '购销合同';
    const filePath = `${wx.env.USER_DATA_PATH}/${safeName}.pdf`;
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);

    try {
      wx.getFileSystemManager().unlinkSync(filePath);
    } catch (e) {
      // 首次下载时文件不存在。
    }

    this.setData({ pdfLoading: true });
    wx.showLoading({ title: '生成PDF中...' });
    wx.downloadFile({
      url: `${app.globalData.baseUrl}/contracts/${this.data.contractId}/pdf`,
      header,
      filePath,
      timeout: 30000,
      success: response => {
        if (response.statusCode !== 200) {
          wx.showToast({ title: `PDF下载失败（${response.statusCode}）`, icon: 'none' });
          return;
        }
        const downloadedPath = response.filePath || response.tempFilePath || filePath;
        this.setData({
          pdfReady: true,
          pdfFilePath: downloadedPath
        });
        if (showDownloadedToast) {
          wx.showToast({ title: 'PDF已下载', icon: 'success' });
        }
        this.openPdfDocument(downloadedPath);
      },
      fail: error => {
        const message = error && error.errMsg ? error.errMsg : '网络异常';
        wx.showToast({ title: `PDF下载失败：${message}`, icon: 'none' });
      },
      complete: () => {
        wx.hideLoading();
        this.setData({ pdfLoading: false });
      }
    });
  },

  openPdfDocument(filePath) {
    wx.openDocument({
      filePath,
      fileType: 'pdf',
      showMenu: true,
      fail: () => {
        this.setData({ pdfReady: false, pdfFilePath: '' });
        wx.showToast({ title: 'PDF打开失败，请重新下载', icon: 'none' });
      }
    });
  },

  /* 加载销售单 */
  async loadSalesOrders() {
    const { request } = require('../../utils/request');
    try {
      const orders = await request({
        url: `/orders?counterpartyName=${encodeURIComponent(this.data.counterpartyName)}`
      });
      const filteredSales = (orders || []).slice(0, 5).map(o => ({
        id: parseInt(o.id),
        orderNo: `SO-${String(o.id).padStart(4, '0')}`,
        product: '贸易订单',
        qty: 1,
        amount: o.amount || 0,
        date: o.orderDate || '',
        status: o.status === 'CONFIRMED' ? '已确认' : o.status,
        direction: o.direction || '采购'
      }));
      this.setData({ salesList: filteredSales });
    } catch (e) { /* 静默 */ }

    if (this.data.logisticsList.length === 0) {
      this.setData({
        logisticsList: [
          { id: 1, trackingNo: 'SF1234567890', carrier: '顺丰速运', status: '运输中', weight: '25kg', date: '2026-07-02', from: '深圳', to: '上海', contact: '张师傅 138****5678', desc: '电子产品' },
          { id: 2, trackingNo: 'YT0987654321', carrier: '圆通快递', status: '已签收', weight: '12kg', date: '2026-07-01', from: '广州', to: '北京', contact: '李师傅 139****1234', desc: '办公用品' }
        ],
        invoiceList: [
          { id: 1, invoiceNo: 'INV-20260701', type: '增值税专用发票', amount: 50000, taxRate: '13%', status: '已开票', date: '2026-07-02' },
          { id: 2, invoiceNo: 'INV-20260702', type: '增值税专用发票', amount: 25000, taxRate: '13%', status: '待开票', date: '2026-07-03' }
        ]
      });
    }
  },

  async loadBusinessDocuments(documentType) {
    const { request } = require('../../utils/request');
    try {
      const list = await request({
        url: `/contracts/${this.data.contractId}/documents?type=${documentType}`
      });
      const documents = (list || []).map(item => ({
        ...item,
        dateText: String(item.createdAt || '').slice(0, 10)
      }));
      if (documentType === 'SALES_ORDER') {
        this.setData({ salesDocuments: documents });
      } else {
        this.setData({ deliveryDocuments: documents });
      }
    } catch (error) {
      wx.showToast({ title: error.message || '单据加载失败', icon: 'none' });
    }
  },

  async createBusinessDocument(e) {
    const documentType = e.currentTarget.dataset.type;
    const label = documentType === 'SALES_ORDER' ? '销售单' : '送货单';
    const { request } = require('../../utils/request');
    try {
      const templates = await request({
        url: `/document-templates?type=${documentType}`
      });
      if (!templates || templates.length === 0) {
        wx.showModal({
          title: `暂无${label}模板`,
          content: `请先到“企业 - 销售与送货单模板”上传${label}模板。`,
          showCancel: false
        });
        return;
      }
      wx.showActionSheet({
        alertText: `选择${label}模板`,
        itemList: templates.map(item => item.name),
        success: async result => {
          const template = templates[result.tapIndex];
          await this.generateBusinessDocument(documentType, template);
        }
      });
    } catch (error) {
      wx.showToast({ title: error.message || '模板加载失败', icon: 'none' });
    }
  },

  async generateBusinessDocument(documentType, template) {
    const { request } = require('../../utils/request');
    try {
      wx.showLoading({ title: '生成PDF中...' });
      const document = await request({
        url: `/contracts/${this.data.contractId}/documents`,
        method: 'POST',
        data: {
          documentType,
          templateId: template.id
        }
      });
      await this.loadBusinessDocuments(documentType);
      wx.showToast({ title: 'PDF已生成', icon: 'success' });
      this.downloadBusinessDocumentFile(document, false);
    } catch (error) {
      wx.showToast({ title: error.message || '单据生成失败', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  viewBusinessDocument(e) {
    this.downloadBusinessDocumentFile(e.currentTarget.dataset.document, false);
  },

  downloadBusinessDocument(e) {
    this.downloadBusinessDocumentFile(e.currentTarget.dataset.document, true);
  },

  downloadBusinessDocumentFile(document, showDownloadedToast) {
    if (!document || !document.id) return;
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const label = document.documentType === 'SALES_ORDER' ? '销售单' : '送货单';
    const safeNo = String(document.documentNo || document.id).replace(/[\\/:*?"<>|\r\n]/g, '_');
    const filePath = `${wx.env.USER_DATA_PATH}/${label}-${safeNo}.pdf`;
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    try {
      wx.getFileSystemManager().unlinkSync(filePath);
    } catch (error) {
      // 首次生成时目标文件不存在。
    }
    wx.showLoading({ title: '下载PDF中...' });
    wx.downloadFile({
      url: `${app.globalData.baseUrl}/trade-documents/${document.id}/pdf`,
      header,
      filePath,
      timeout: 30000,
      success: response => {
        if (response.statusCode !== 200) {
          wx.showToast({ title: `PDF下载失败（${response.statusCode}）`, icon: 'none' });
          return;
        }
        const downloadedPath = response.filePath || response.tempFilePath || filePath;
        if (showDownloadedToast) {
          wx.showToast({ title: 'PDF已下载', icon: 'success' });
        }
        this.openPdfDocument(downloadedPath);
      },
      fail: error => {
        wx.showToast({ title: error.errMsg || 'PDF下载失败', icon: 'none' });
      },
      complete: () => wx.hideLoading()
    });
  },

  /* 列表项点击 → 显示详情面板 */
  onItemTap(e) {
    const { type, item } = e.currentTarget.dataset;
    if (type === '销售单') {
      this.showSalesDetail(item);
    } else if (type === '物流单') {
      this.showLogisticsDetail(item);
    } else if (type === '发票') {
      this.showInvoiceDetail(item);
    }
  },

  showSalesDetail(o) {
    this.setData({
      showDetail: true,
      detailTitle: '销售单详情',
      detailFields: [
        { label: '订单编号', value: o.orderNo },
        { label: '订单状态', value: o.status, highlight: true },
        { label: '交易方向', value: o.direction === '采购' ? '采购订单' : '销售订单' },
        { label: '商品名称', value: o.product },
        { label: '数量', value: `${o.qty} 件` },
        { label: '订单金额', value: `¥${o.amount}`, amount: true },
        { label: '对方公司', value: this.data.counterpartyName },
        { label: '订单日期', value: o.date }
      ]
    });
  },

  showLogisticsDetail(l) {
    const steps = [];
    if (l.status === '已签收') {
      steps.push(
        { title: '已揽收', time: l.date, done: true },
        { title: '运输中', time: l.date, done: true },
        { title: '已签收', time: l.date, done: true }
      );
    } else {
      steps.push(
        { title: '已揽收', time: l.date, done: true },
        { title: '运输中', time: l.date, done: true, active: true },
        { title: '派送中', time: '', done: false },
        { title: '已签收', time: '', done: false }
      );
    }
    this.setData({
      showDetail: true,
      detailTitle: '物流单详情',
      detailSteps: steps,
      detailFields: [
        { label: '运单号', value: l.trackingNo },
        { label: '承运商', value: l.carrier },
        { label: '物流状态', value: l.status, highlight: true },
        { label: '货物描述', value: l.desc || '--' },
        { label: '重量', value: l.weight },
        { label: '发货地', value: l.from || '--' },
        { label: '目的地', value: l.to || '--' },
        { label: '联系方式', value: l.contact || '--' }
      ]
    });
  },

  showInvoiceDetail(inv) {
    this.setData({
      showDetail: true,
      detailTitle: '发票详情',
      detailFields: [
        { label: '发票编号', value: inv.invoiceNo },
        { label: '发票类型', value: inv.type },
        { label: '开票状态', value: inv.status, highlight: true },
        { label: '发票金额', value: `¥${inv.amount}`, amount: true },
        { label: '税率', value: inv.taxRate },
        { label: '开票日期', value: inv.date || '--' }
      ]
    });
  },

  closeDetail() {
    this.setData({ showDetail: false, detailSteps: [] });
  },

  noop() {}
});
