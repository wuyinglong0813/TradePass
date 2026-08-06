Page({
  data: {
    contractId: '',
    contractName: '',
    counterpartyName: '',
    // 合同详情
    contract: null,
    // Tab
    tabs: [
      { key: 'detail', label: '合同' },
      { key: 'fulfillment', label: '履约资料' }
    ],
    activeTab: 'detail',
    // 列表
    logisticsList: [],
    logisticsLoading: false,
    logisticsUploading: false,
    salesDocuments: [],
    paymentAttachments: [],
    otherAttachments: [],
    attachmentLoading: false,
    attachmentUploading: false,
    showPaymentAmountEditor: false,
    paymentAmount: '',
    paymentAmountError: '',
    pendingPaymentAttachment: null,
    fulfillmentLoading: false,
    fulfillmentCount: 0,
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
    canCreateSalesOrder: false,
    createAsDraft: false,
    salesOrderCreateText: '创建销售单',
    salesOrderEmptyHint: '等待合同供方创建销售单',
    personalMemo: '',
    memoDraft: '',
    memoPreview: '尚未记录，点击添加',
    memoUpdatedAt: '',
    memoSaving: false,
    showMemoEditor: false,
    // 详情面板
    showDetail: false,
    detailTitle: '',
    detailFields: [],
    // 销售单创建编辑器
    showDocumentEditor: false,
    documentEditorType: '',
    documentEditorLabel: '',
    documentTemplates: [],
    documentTemplateIndex: -1,
    documentTitle: '',
    documentCompanyName: '',
    documentCounterpartyName: '',
    documentContractNo: '',
    documentDate: '',
    documentColumns: [],
    documentRows: [],
    documentBlankRows: 1,
    documentTotalAmount: '0',
    documentEditorReady: false,
    documentEditorSubmitting: false
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
    if (tab === 'fulfillment') {
      this.loadBusinessDocuments('SALES_ORDER');
      this.loadFulfillmentData();
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
      const isPurchase = (contract.viewerDirection || contract.direction) === 'PURCHASE';
      const currentCompany = this.currentCompanyName();
      const viewerCounterpartyName = contract.viewerCounterpartyName || contract.counterpartyName;
      const pdfTitle = contract.name || (sData && sData.title) || this.data.contractName || '购销合同';
      const pdfSupplier = fieldValue('supplier')
        || (isPurchase ? viewerCounterpartyName : currentCompany);
      const pdfBuyer = fieldValue('buyer')
        || (isPurchase ? currentCompany : viewerCounterpartyName);
      const pdfDate = fieldValue('signDate')
        || contract.startDate
        || String(contract.createdAt || '').slice(0, 10);
      const viewerDirection = contract.viewerDirection || contract.direction;
      const isSupplier = viewerDirection === 'SALE';
      const canCreateSalesOrder = isSupplier
        && (contract.status === 'PENDING' || contract.status === 'ACTIVE');
      const createAsDraft = contract.status === 'PENDING';

      this.setData({
        contract: {
          ...contract,
          statusText: statusMap[contract.status] || contract.status,
          amount: contract.amount || 0
        },
        contractName: contract.name || this.data.contractName,
        counterpartyName: viewerCounterpartyName || this.data.counterpartyName,
        hasStructured: !!sData,
        sData,
        canCreateSalesOrder,
        createAsDraft,
        salesOrderCreateText: createAsDraft ? '创建草稿' : '创建销售单',
        salesOrderEmptyHint: canCreateSalesOrder
          ? (createAsDraft ? '合同待签署，可先按合同产品准备销售单草稿' : '按合同产品生成并发布销售单')
          : '等待合同供方创建销售单',
        pdfTitle,
        pdfSupplier: pdfSupplier || '—',
        pdfBuyer: pdfBuyer || '—',
        pdfDate: pdfDate || '—'
      }, () => {
        this.loadContractMemo();
        this.loadBusinessDocuments('SALES_ORDER');
        this.loadFulfillmentData();
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

  async loadLogisticsDocuments() {
    if (this.data.logisticsLoading) return;
    const { request } = require('../../utils/request');
    this.setData({ logisticsLoading: true });
    try {
      const list = await request({
        url: `/contracts/${this.data.contractId}/logistics-documents`
      });
      const logisticsList = (list || []).map(item => ({
        ...item,
        dateText: String(item.createdAt || '').slice(0, 10),
        fileSizeText: this.formatFileSize(item.fileSize)
      }));
      this.setData({ logisticsList }, () => this.updateFulfillmentCount());
    } catch (error) {
      wx.showToast({ title: error.message || '物流单加载失败', icon: 'none' });
    } finally {
      this.setData({ logisticsLoading: false });
    }
  },

  chooseLogisticsImage() {
    if (this.data.logisticsUploading) return;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: result => {
        const file = result.tempFiles && result.tempFiles[0];
        if (!file || !file.tempFilePath) return;
        if (file.size && file.size > 10 * 1024 * 1024) {
          wx.showToast({ title: '物流单图片不能超过10MB', icon: 'none' });
          return;
        }
        this.uploadLogisticsImage(file.tempFilePath);
      }
    });
  },

  uploadLogisticsImage(filePath) {
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    const originalName = this.buildLogisticsFileName(filePath);

    this.setData({ logisticsUploading: true });
    wx.showLoading({ title: '上传图片中...' });
    wx.uploadFile({
      url: `${app.globalData.baseUrl}/contracts/${this.data.contractId}/logistics-documents`,
      filePath,
      name: 'file',
      formData: { originalName },
      header,
      timeout: 30000,
      success: response => {
        let body = null;
        try {
          body = JSON.parse(response.data || '{}');
        } catch (error) {
          // 交由统一错误提示处理。
        }
        if (response.statusCode >= 200 && response.statusCode < 300 && body && body.code === 0) {
          wx.showToast({ title: '物流单已上传', icon: 'success' });
          this.loadLogisticsDocuments();
          return;
        }
        wx.showToast({
          title: (body && body.message) || `上传失败（${response.statusCode || '未知状态'}）`,
          icon: 'none'
        });
      },
      fail: error => {
        wx.showToast({ title: error.errMsg || '物流单上传失败', icon: 'none' });
      },
      complete: () => {
        wx.hideLoading();
        this.setData({ logisticsUploading: false });
      }
    });
  },

  buildLogisticsFileName(filePath) {
    const matched = String(filePath || '').match(/\.([a-zA-Z0-9]+)$/);
    const candidate = matched ? matched[1].toLowerCase() : 'jpg';
    const extension = ['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(candidate)
      ? candidate
      : 'jpg';
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    const timestamp = [
      now.getFullYear(),
      pad(now.getMonth() + 1),
      pad(now.getDate()),
      '-',
      pad(now.getHours()),
      pad(now.getMinutes()),
      pad(now.getSeconds())
    ].join('');
    return `物流单-${timestamp}.${extension}`;
  },

  previewLogisticsImage(e) {
    const document = e.currentTarget.dataset.document;
    if (!document || !document.id) return;
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    const extensionMap = {
      'image/png': 'png',
      'image/gif': 'gif',
      'image/webp': 'webp'
    };
    const extension = extensionMap[document.contentType] || 'jpg';
    const filePath = `${wx.env.USER_DATA_PATH}/logistics-${document.id}.${extension}`;
    try {
      wx.getFileSystemManager().unlinkSync(filePath);
    } catch (error) {
      // 首次查看时文件不存在。
    }

    wx.showLoading({ title: '加载图片中...' });
    wx.downloadFile({
      url: `${app.globalData.baseUrl}/logistics-documents/${document.id}/image`,
      header,
      filePath,
      timeout: 30000,
      success: response => {
        if (response.statusCode !== 200) {
          wx.showToast({ title: `图片加载失败（${response.statusCode}）`, icon: 'none' });
          return;
        }
        const imagePath = response.filePath || response.tempFilePath || filePath;
        wx.previewImage({
          current: imagePath,
          urls: [imagePath],
          fail: () => wx.showToast({ title: '图片打开失败', icon: 'none' })
        });
      },
      fail: error => {
        wx.showToast({ title: error.errMsg || '图片加载失败', icon: 'none' });
      },
      complete: () => wx.hideLoading()
    });
  },

  formatFileSize(size) {
    const bytes = Number(size || 0);
    if (bytes < 1024) return `${bytes}B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
  },

  async loadFulfillmentData() {
    if (this.data.fulfillmentLoading || !this.data.contractId) return;
    const { request } = require('../../utils/request');
    this.setData({ fulfillmentLoading: true });
    try {
      const [logistics, payments, invoices, others] = await Promise.all([
        request({ url: `/contracts/${this.data.contractId}/logistics-documents` }),
        request({ url: `/contracts/${this.data.contractId}/attachments?category=PAYMENT_VOUCHER` }),
        request({ url: `/contracts/${this.data.contractId}/attachments?category=INVOICE` }),
        request({ url: `/contracts/${this.data.contractId}/attachments?category=OTHER` })
      ]);
      const logisticsList = (logistics || []).map(item => ({
        ...item,
        dateText: String(item.createdAt || '').slice(0, 10),
        fileSizeText: this.formatFileSize(item.fileSize)
      }));
      const mapAttachment = item => ({
        ...item,
        fileSizeText: this.formatFileSize(item.fileSize),
        dateText: String(item.createdAt || '').replace('T', ' ').slice(0, 16),
        isImage: String(item.contentType || '').indexOf('image/') === 0,
        voucherAmountText: item.voucherAmount === null || item.voucherAmount === undefined
          ? '金额未填写'
          : `转款金额 ¥${Number(item.voucherAmount).toFixed(2)}`
      });
      const paymentAttachments = (payments || []).map(mapAttachment);
      const invoiceList = (invoices || []).map(mapAttachment);
      const otherAttachments = (others || []).map(mapAttachment);
      this.setData({
        logisticsList,
        paymentAttachments,
        invoiceList,
        otherAttachments
      }, () => this.updateFulfillmentCount());
    } catch (error) {
      wx.showToast({ title: error.message || '履约资料加载失败', icon: 'none' });
    } finally {
      this.setData({ fulfillmentLoading: false });
    }
  },

  updateFulfillmentCount() {
    this.setData({
      fulfillmentCount: this.data.logisticsList.length
        + this.data.salesDocuments.length
        + this.data.paymentAttachments.length
        + this.data.otherAttachments.length
        + this.data.invoiceList.length
    });
  },

  async loadContractMemo() {
    if (!this.data.contractId) return;
    const { request } = require('../../utils/request');
    try {
      const memo = await request({ url: `/contracts/${this.data.contractId}/memo` });
      this.setData({
        personalMemo: (memo && memo.content) || '',
        memoDraft: (memo && memo.content) || '',
        memoPreview: this.memoPreviewText((memo && memo.content) || ''),
        memoUpdatedAt: memo && memo.updatedAt ? String(memo.updatedAt).replace('T', ' ').slice(0, 16) : ''
      });
    } catch (error) {
      wx.showToast({ title: error.message || '备忘录加载失败', icon: 'none' });
    }
  },

  onMemoInput(e) {
    this.setData({ memoDraft: e.detail.value });
  },

  memoPreviewText(content) {
    const text = String(content || '').replace(/\s+/g, ' ').trim();
    if (!text) return '尚未记录，点击添加';
    return text.length > 54 ? `${text.slice(0, 54)}…` : text;
  },

  openMemoEditor() {
    this.setData({ showMemoEditor: true, memoDraft: this.data.personalMemo });
  },

  closeMemoEditor() {
    if (this.data.memoSaving) return;
    this.setData({ showMemoEditor: false, memoDraft: this.data.personalMemo });
  },

  async saveContractMemo() {
    if (this.data.memoSaving) return;
    const { request } = require('../../utils/request');
    try {
      this.setData({ memoSaving: true });
      const memo = await request({
        url: `/contracts/${this.data.contractId}/memo`,
        method: 'POST',
        data: { content: this.data.memoDraft }
      });
      this.setData({
        personalMemo: (memo && memo.content) || '',
        memoDraft: (memo && memo.content) || '',
        memoPreview: this.memoPreviewText((memo && memo.content) || ''),
        showMemoEditor: false,
        memoUpdatedAt: memo && memo.updatedAt ? String(memo.updatedAt).replace('T', ' ').slice(0, 16) : ''
      });
      wx.showToast({ title: '个人备忘录已保存', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '备忘录保存失败', icon: 'none' });
    } finally {
      this.setData({ memoSaving: false });
    }
  },

  async loadAttachments(category) {
    if (this.data.attachmentLoading) return;
    const { request } = require('../../utils/request');
    this.setData({ attachmentLoading: true });
    try {
      const list = await request({
        url: `/contracts/${this.data.contractId}/attachments?category=${category}`
      });
      const attachments = (list || []).map(item => ({
        ...item,
        fileSizeText: this.formatFileSize(item.fileSize),
        dateText: String(item.createdAt || '').replace('T', ' ').slice(0, 16),
        isImage: String(item.contentType || '').indexOf('image/') === 0,
        voucherAmountText: item.voucherAmount === null || item.voucherAmount === undefined
          ? '金额未填写'
          : `转款金额 ¥${Number(item.voucherAmount).toFixed(2)}`
      }));
      const dataKey = {
        PAYMENT_VOUCHER: 'paymentAttachments',
        INVOICE: 'invoiceList',
        OTHER: 'otherAttachments'
      }[category];
      this.setData({
        [dataKey]: attachments
      }, () => this.updateFulfillmentCount());
    } catch (error) {
      wx.showToast({ title: error.message || '资料加载失败', icon: 'none' });
    } finally {
      this.setData({ attachmentLoading: false });
    }
  },

  chooseAttachment(e) {
    if (this.data.attachmentUploading) return;
    const category = e.currentTarget.dataset.category;
    wx.showActionSheet({
      itemList: ['拍照或从相册选择图片', '从聊天文件选择'],
      success: result => {
        if (result.tapIndex === 0) {
          this.chooseAttachmentImage(category);
        } else {
          this.chooseAttachmentFile(category);
        }
      }
    });
  },

  chooseAttachmentImage(category) {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success: result => {
        const file = result.tempFiles && result.tempFiles[0];
        if (!file || !file.tempFilePath) return;
        if (file.size && file.size > 10 * 1024 * 1024) {
          wx.showToast({ title: '文件不能超过10MB', icon: 'none' });
          return;
        }
        this.prepareAttachmentUpload(
          category,
          file.tempFilePath,
          this.attachmentFileName(file.tempFilePath, 'jpg', category)
        );
      }
    });
  },

  chooseAttachmentFile(category) {
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: category === 'OTHER' ? ['pdf', 'doc', 'docx'] : ['pdf'],
      success: result => {
        const file = result.tempFiles && result.tempFiles[0];
        if (!file || !file.path) return;
        if (file.size && file.size > 10 * 1024 * 1024) {
          wx.showToast({ title: '文件不能超过10MB', icon: 'none' });
          return;
        }
        this.prepareAttachmentUpload(
          category,
          file.path,
          file.name || this.attachmentFileName(file.path, 'pdf', category)
        );
      }
    });
  },

  attachmentFileName(filePath, fallbackExtension, category) {
    const matched = String(filePath || '').match(/\.([a-zA-Z0-9]+)$/);
    const extension = matched ? matched[1].toLowerCase() : fallbackExtension;
    const prefix = {
      PAYMENT_VOUCHER: '转款凭证',
      INVOICE: '发票',
      OTHER: '其它资料'
    }[category] || '合同资料';
    return `${prefix}-${Date.now()}.${extension}`;
  },

  prepareAttachmentUpload(category, filePath, originalName) {
    if (category !== 'PAYMENT_VOUCHER') {
      this.uploadAttachment(category, filePath, originalName);
      return;
    }
    this.setData({
      showPaymentAmountEditor: true,
      paymentAmount: '',
      paymentAmountError: '',
      pendingPaymentAttachment: { filePath, originalName }
    });
  },

  onPaymentAmountInput(e) {
    this.setData({
      paymentAmount: e.detail.value,
      paymentAmountError: ''
    });
  },

  closePaymentAmountEditor() {
    if (this.data.attachmentUploading) return;
    this.setData({
      showPaymentAmountEditor: false,
      paymentAmount: '',
      paymentAmountError: '',
      pendingPaymentAttachment: null
    });
  },

  confirmPaymentAttachmentUpload() {
    if (this.data.attachmentUploading) return;
    const amount = String(this.data.paymentAmount || '').trim();
    if (!/^\d{1,16}(\.\d{1,2})?$/.test(amount)) {
      this.setData({ paymentAmountError: '请输入正确金额，最多保留两位小数' });
      return;
    }
    const attachment = this.data.pendingPaymentAttachment;
    if (!attachment || !attachment.filePath) return;
    const [integerPart, decimalPart = ''] = amount.split('.');
    const normalizedAmount = `${integerPart}.${decimalPart.padEnd(2, '0')}`;
    this.setData({
      showPaymentAmountEditor: false,
      paymentAmount: '',
      paymentAmountError: '',
      pendingPaymentAttachment: null
    }, () => {
      this.uploadAttachment(
        'PAYMENT_VOUCHER',
        attachment.filePath,
        attachment.originalName,
        { voucherAmount: normalizedAmount }
      );
    });
  },

  uploadAttachment(category, filePath, originalName, metadata = {}) {
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    this.setData({ attachmentUploading: true });
    wx.showLoading({ title: '上传资料中...' });
    wx.uploadFile({
      url: `${app.globalData.baseUrl}/contracts/${this.data.contractId}/attachments`,
      filePath,
      name: 'file',
      formData: { category, originalName, ...metadata },
      header,
      timeout: 30000,
      success: response => {
        let body = null;
        try { body = JSON.parse(response.data || '{}'); } catch (error) { /* 统一提示 */ }
        if (response.statusCode >= 200 && response.statusCode < 300 && body && body.code === 0) {
          const label = category === 'PAYMENT_VOUCHER'
            ? '转款凭证'
            : category === 'INVOICE' ? '发票' : '资料';
          wx.showToast({ title: `${label}已上传`, icon: 'success' });
          this.loadAttachments(category);
          return;
        }
        wx.showToast({
          title: (body && body.message) || `上传失败（${response.statusCode || '未知状态'}）`,
          icon: 'none'
        });
      },
      fail: error => wx.showToast({ title: error.errMsg || '资料上传失败', icon: 'none' }),
      complete: () => {
        wx.hideLoading();
        this.setData({ attachmentUploading: false });
      }
    });
  },

  viewAttachment(e) {
    this.downloadAttachmentFile(e.currentTarget.dataset.attachment, false);
  },

  downloadAttachment(e) {
    this.downloadAttachmentFile(e.currentTarget.dataset.attachment, true);
  },

  attachmentDownloadPath(attachment) {
    const originalName = String(attachment.originalName || '').trim();
    const matched = originalName.match(/\.([a-zA-Z0-9]{1,8})$/);
    const extensionByType = {
      'image/jpeg': 'jpg',
      'image/png': 'png',
      'image/gif': 'gif',
      'image/webp': 'webp',
      'application/pdf': 'pdf',
      'application/msword': 'doc',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx'
    };
    const extension = matched
      ? matched[1].toLowerCase()
      : (extensionByType[attachment.contentType] || 'bin');
    const rawBaseName = matched ? originalName.slice(0, -matched[0].length) : originalName;
    const safeBaseName = rawBaseName
      .replace(/[\\/:*?"<>|\r\n]/g, '_')
      .replace(/^\.+/, '')
      .trim()
      .slice(0, 80) || '合同资料';
    return `${wx.env.USER_DATA_PATH}/${safeBaseName}-${attachment.id}.${extension}`;
  },

  downloadAttachmentFile(attachment, download) {
    if (!attachment || !attachment.id) return;
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || wx.getStorageSync('tradepass_company_id') || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    const localFilePath = download ? this.attachmentDownloadPath(attachment) : '';
    if (localFilePath) {
      try {
        wx.getFileSystemManager().unlinkSync(localFilePath);
      } catch (error) {
        // 首次下载时目标文件不存在。
      }
    }
    wx.showLoading({ title: download ? '下载中...' : '打开中...' });
    const options = {
      url: `${app.globalData.baseUrl}/contract-attachments/${attachment.id}/content?download=${download}`,
      header,
      timeout: 30000,
      success: response => {
        if (response.statusCode !== 200) {
          wx.showToast({ title: `文件获取失败（${response.statusCode}）`, icon: 'none' });
          return;
        }
        const path = response.filePath || response.tempFilePath || localFilePath;
        if (attachment.isImage) {
          if (download) {
            wx.saveImageToPhotosAlbum({
              filePath: path,
              success: () => wx.showToast({ title: '图片已保存到相册', icon: 'success' }),
              fail: () => wx.showToast({ title: '图片保存失败，请检查相册权限', icon: 'none' })
            });
            return;
          }
          wx.previewImage({ current: path, urls: [path] });
          return;
        }
        const extension = String(attachment.originalName || '').split('.').pop().toLowerCase();
        wx.openDocument({
          filePath: path,
          fileType: ['pdf', 'doc', 'docx'].includes(extension) ? extension : undefined,
          showMenu: true,
          success: () => {
            if (download) wx.showToast({ title: '文件已下载', icon: 'success' });
          },
          fail: () => wx.showToast({ title: '文件打开失败', icon: 'none' })
        });
      },
      fail: error => wx.showToast({ title: error.errMsg || '文件获取失败', icon: 'none' }),
      complete: () => wx.hideLoading()
    };
    if (localFilePath) options.filePath = localFilePath;
    wx.downloadFile(options);
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
      this.setData({ salesDocuments: documents }, () => this.updateFulfillmentCount());
    } catch (error) {
      wx.showToast({ title: error.message || '单据加载失败', icon: 'none' });
    }
  },

  async createBusinessDocument(e) {
    const documentType = e.currentTarget.dataset.type;
    const label = '销售单';
    const { request } = require('../../utils/request');
    try {
      wx.showLoading({ title: '加载模板中...' });
      const templates = await request({
        url: `/document-templates?type=${documentType}`
      });
      if (!templates || templates.length === 0) {
        wx.showModal({
          title: `暂无${label}模板`,
          content: `请先到“企业 - 销售单模板”上传${label}模板。`,
          showCancel: false
        });
        return;
      }
      const contract = this.data.contract || {};
      const defaultTemplate = {
        ...templates[0],
        name: `合同默认销售单（${contract.contractNo || contract.id || this.data.contractId}）`,
        sourceType: 'CONTRACT_DEFAULT'
      };
      const selectableTemplates = [defaultTemplate].concat(templates.map(item => ({
        ...item,
        sourceType: 'TEMPLATE'
      })));
      this.setData({
        showDocumentEditor: true,
        documentEditorType: documentType,
        documentEditorLabel: label,
        documentTemplates: selectableTemplates,
        documentTemplateIndex: -1,
        documentTitle: label,
        documentCompanyName: this.currentCompanyName(),
        documentCounterpartyName: '',
        documentContractNo: '',
        documentDate: this.todayText(),
        documentColumns: [],
        documentRows: [],
        documentBlankRows: 8,
        documentTotalAmount: '0',
        documentEditorReady: false,
        documentEditorSubmitting: false
      }, () => this.applyDocumentTemplate(0));
    } catch (error) {
      wx.showToast({ title: error.message || '模板加载失败', icon: 'none' });
    } finally {
      wx.hideLoading();
    }
  },

  onDocumentTemplateChange(e) {
    const index = Number(e.detail.value);
    if (!Number.isInteger(index) || index < 0) return;
    this.applyDocumentTemplate(index);
  },

  applyDocumentTemplate(index) {
    const template = this.data.documentTemplates[index];
    if (!template) return;
    try {
      const templateContent = JSON.parse(template.content || '{}');
      const columns = Array.isArray(templateContent.columns)
        ? templateContent.columns.map(item => String(item || '').trim()).filter(Boolean)
        : [];
      if (columns.length === 0) {
        wx.showToast({ title: '模板内容为空', icon: 'none' });
        return;
      }
      const contract = this.data.contract || {};
      const rows = this.buildDocumentRows(columns);
      const totalAmount = this.calculateDocumentTotal(
        columns,
        rows,
        contract.amount || 0
      );
      this.setData({
        documentTemplateIndex: index,
        documentTitle: this.data.documentEditorLabel,
        documentCompanyName: this.currentCompanyName(),
        documentCounterpartyName: contract.viewerCounterpartyName
          || this.data.counterpartyName
          || contract.counterpartyName
          || '',
        documentContractNo: contract.contractNo || String(contract.id || this.data.contractId || ''),
        documentDate: this.todayText(),
        documentColumns: columns,
        documentRows: rows,
        documentBlankRows: Math.max(
          rows.length,
          Number(templateContent.blankRows) || 8
        ),
        documentTotalAmount: totalAmount,
        documentEditorReady: true
      });
    } catch (error) {
      wx.showToast({ title: '模板格式异常', icon: 'none' });
    }
  },

  todayText() {
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  },

  buildDocumentRows(columns) {
    const sections = (this.data.sData && this.data.sData.sections) || [];
    const table = sections.find(item => item && item.type === 'table') || {};
    const sourceColumns = Array.isArray(table.columns) ? table.columns : [];
    const sourceRows = (Array.isArray(table.rows) ? table.rows : []).filter(row =>
      Array.isArray(row) && row.some(value => {
        const text = String(value == null ? '' : value).trim();
        return text && text !== '0' && text !== '0.00';
      })
    );
    const rows = sourceRows.map((row, rowIndex) => columns.map(column =>
      this.valueForDocumentColumn(column, rowIndex, sourceColumns, row)
    ));
    return rows.length > 0 ? rows : [columns.map(column =>
      String(column).includes('序号') ? '1' : ''
    )];
  },

  valueForDocumentColumn(targetColumn, rowIndex, sourceColumns, sourceRow) {
    const target = String(targetColumn || '').trim();
    if (target.includes('序号')) return String(rowIndex + 1);
    let aliases = [target];
    if (target.includes('品名') || target === '名称' || target.includes('产品')) {
      aliases = ['品名', '名称', '产品'];
    } else if (target.includes('规格')) {
      aliases = ['规格', '型号'];
    } else if (target.includes('单位')) {
      aliases = ['单位'];
    } else if (target.includes('数量')) {
      aliases = ['数量'];
    } else if (target.includes('单价')) {
      aliases = ['单价'];
    } else if (target.includes('金额')) {
      aliases = ['金额'];
    } else if (target.includes('备注')) {
      aliases = ['备注'];
    }
    const sourceIndex = sourceColumns.findIndex(column =>
      aliases.some(alias => String(column || '').includes(alias))
    );
    return sourceIndex >= 0 && sourceIndex < sourceRow.length
      ? String(sourceRow[sourceIndex] == null ? '' : sourceRow[sourceIndex])
      : '';
  },

  onDocumentFieldInput(e) {
    const field = e.currentTarget.dataset.field;
    const editableFields = [
      'documentTitle', 'documentCompanyName', 'documentCounterpartyName',
      'documentContractNo', 'documentTotalAmount'
    ];
    if (!editableFields.includes(field)) return;
    this.setData({ [field]: e.detail.value });
  },

  onDocumentDateChange(e) {
    this.setData({ documentDate: e.detail.value });
  },

  onDocumentCellInput(e) {
    const rowIndex = Number(e.currentTarget.dataset.row);
    const columnIndex = Number(e.currentTarget.dataset.col);
    const rows = this.data.documentRows.map(row => row.slice());
    if (!rows[rowIndex] || columnIndex < 0) return;
    rows[rowIndex][columnIndex] = e.detail.value;

    const columns = this.data.documentColumns;
    const quantityIndex = columns.findIndex(column => String(column).includes('数量'));
    const priceIndex = columns.findIndex(column => String(column).includes('单价'));
    const amountIndex = columns.findIndex(column => String(column).includes('金额'));
    const changesCalculatedAmount = (columnIndex === quantityIndex || columnIndex === priceIndex)
      && quantityIndex >= 0 && priceIndex >= 0 && amountIndex >= 0;
    if (changesCalculatedAmount) {
      const amount = (parseFloat(rows[rowIndex][quantityIndex]) || 0)
        * (parseFloat(rows[rowIndex][priceIndex]) || 0);
      rows[rowIndex][amountIndex] = this.formatDocumentAmount(amount);
    }
    const shouldRecalculateTotal = changesCalculatedAmount || columnIndex === amountIndex;
    this.setData({
      documentRows: rows,
      documentTotalAmount: shouldRecalculateTotal
        ? this.calculateDocumentTotal(columns, rows, 0)
        : this.data.documentTotalAmount
    });
  },

  addDocumentRow() {
    const columns = this.data.documentColumns;
    if (columns.length === 0) return;
    const rows = this.data.documentRows.map(row => row.slice());
    rows.push(columns.map(column => String(column).includes('序号') ? String(rows.length + 1) : ''));
    this.setData({
      documentRows: rows,
      documentBlankRows: Math.max(this.data.documentBlankRows, rows.length)
    });
  },

  deleteDocumentRow(e) {
    if (this.data.documentRows.length <= 1) return;
    const index = Number(e.currentTarget.dataset.index);
    const sequenceIndex = this.data.documentColumns.findIndex(column => String(column).includes('序号'));
    const rows = this.data.documentRows
      .filter((_, rowIndex) => rowIndex !== index)
      .map((row, rowIndex) => {
        const next = row.slice();
        if (sequenceIndex >= 0) next[sequenceIndex] = String(rowIndex + 1);
        return next;
      });
    this.setData({
      documentRows: rows,
      documentTotalAmount: this.calculateDocumentTotal(
        this.data.documentColumns,
        rows,
        0
      )
    });
  },

  calculateDocumentTotal(columns, rows, fallback) {
    const amountIndex = (columns || []).findIndex(column => String(column).includes('金额'));
    if (amountIndex < 0) return this.formatDocumentAmount(fallback);
    const values = (rows || []).map(row => String(row[amountIndex] == null ? '' : row[amountIndex]).trim());
    if (!values.some(Boolean)) return this.formatDocumentAmount(fallback);
    return this.formatDocumentAmount(values.reduce((sum, value) => sum + (parseFloat(value) || 0), 0));
  },

  formatDocumentAmount(value) {
    const amount = Math.round((parseFloat(value) || 0) * 100) / 100;
    return String(amount);
  },

  closeDocumentEditor() {
    if (this.data.documentEditorSubmitting) return;
    this.setData({ showDocumentEditor: false });
  },

  async submitBusinessDocument() {
    const {
      documentEditorType, documentEditorLabel, documentTemplates, documentTemplateIndex,
      documentTitle, documentCompanyName, documentCounterpartyName, documentContractNo,
      documentDate, documentColumns, documentRows, documentBlankRows,
      documentTotalAmount, createAsDraft
    } = this.data;
    if (documentTemplateIndex < 0 || !documentTemplates[documentTemplateIndex]) {
      wx.showToast({ title: `请选择${documentEditorLabel}模板`, icon: 'none' });
      return;
    }
    if (!documentTitle.trim()) {
      wx.showToast({ title: '请输入单据标题', icon: 'none' });
      return;
    }
    const confirmed = await new Promise(resolve => {
      wx.showModal({
        title: createAsDraft ? '确认保存销售单草稿' : `确认创建${documentEditorLabel}`,
        content: createAsDraft
          ? `模板：${documentTemplates[documentTemplateIndex].name}\n合同生效后再发布给需方，发布前仍可编辑。`
          : `模板：${documentTemplates[documentTemplateIndex].name}\n创建后将作为正式销售单发布给需方。`,
        success: result => resolve(!!result.confirm),
        fail: () => resolve(false)
      });
    });
    if (!confirmed) return;

    const { request } = require('../../utils/request');
    try {
      this.setData({ documentEditorSubmitting: true });
      wx.showLoading({ title: '创建中...' });
      await request({
        url: `/contracts/${this.data.contractId}/documents`,
        method: 'POST',
        data: {
          documentType: documentEditorType,
          templateId: documentTemplates[documentTemplateIndex].id,
          sourceType: documentTemplates[documentTemplateIndex].sourceType || 'TEMPLATE',
          content: {
            title: documentTitle.trim(),
            companyName: documentCompanyName.trim(),
            counterpartyName: documentCounterpartyName.trim(),
            contractNo: documentContractNo.trim(),
            date: documentDate,
            columns: documentColumns,
            rows: documentRows,
            blankRows: documentBlankRows,
            totalAmount: documentTotalAmount
          }
        }
      });
      await this.loadBusinessDocuments(documentEditorType);
      this.setData({ showDocumentEditor: false });
      wx.showToast({ title: createAsDraft ? '销售单草稿已保存' : `${documentEditorLabel}已发布`, icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '单据生成失败', icon: 'none' });
    } finally {
      wx.hideLoading();
      this.setData({ documentEditorSubmitting: false });
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
    const label = '销售单';
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

  openSalesOrderDetail(e) {
    const document = e.currentTarget.dataset.document;
    if (!document || !document.id) return;
    wx.navigateTo({
      url: `/pages/sales-order-detail/sales-order-detail?id=${document.id}&documentNo=${encodeURIComponent(document.documentNo || '')}`
    });
  },

  /* 列表项点击 → 显示详情面板 */
  onItemTap(e) {
    const { type, item } = e.currentTarget.dataset;
    if (type === '发票') {
      this.showInvoiceDetail(item);
    }
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
