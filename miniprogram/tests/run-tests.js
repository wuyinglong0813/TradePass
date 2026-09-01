'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const {
  numberToChineseCurrency,
  calcTableTotal,
  calcFeeTotal,
  normalizeContractTable,
  toChineseNum,
  reorderClauses
} = require('../utils/chineseCurrency');
const dict = require('../utils/dict');

const tests = [];
function test(name, fn) {
  tests.push({ name, fn });
}

test('numberToChineseCurrency handles invalid, integer and decimal amounts', () => {
  assert.strictEqual(numberToChineseCurrency(null), '零元整');
  assert.strictEqual(numberToChineseCurrency(-1), '零元整');
  assert.strictEqual(numberToChineseCurrency(0), '零元整');
  assert.strictEqual(numberToChineseCurrency(12345.67), '壹万贰仟叁佰肆拾伍元陆角柒分');
  assert.strictEqual(numberToChineseCurrency(10.05), '壹拾元零伍分');
});

test('calcTableTotal normalizes rows and rounds currency values', () => {
  const result = calcTableTotal([
    ['商品A', 'S', '件', '2', '3.335', ''],
    ['商品B', '', '', 'bad', '10', '']
  ]);
  assert.strictEqual(result.totalAmount, 6.67);
  assert.strictEqual(result.rows[0][5], '6.67');
  assert.strictEqual(result.rows[1][5], '0');
  assert.strictEqual(result.totalAmountCn, '陆元陆角柒分');
});

test('contract table appends remarks without losing them and keeps fees separate', () => {
  const normalized = normalizeContractTable(
    ['产品名称', '规格型号', '单位', '数量', '单价(元)', '金额(元)'],
    [['商品A', 'S', '件', '2', '3', '6']]
  );
  assert.deepStrictEqual(normalized.columns.slice(-2), ['金额(元)', '备注']);
  normalized.rows[0][6] = '加急送货';
  const calculated = calcTableTotal(normalized.rows, normalized.columns);
  assert.strictEqual(calculated.rows[0][6], '加急送货');
  assert.strictEqual(calcFeeTotal([{ amount: '12.30' }, { amount: 'bad' }]), 12.3);
});

test('clause helpers strip old prefixes and produce stable labels', () => {
  assert.strictEqual(toChineseNum(1), '一');
  assert.strictEqual(toChineseNum(11), '十一');
  assert.strictEqual(toChineseNum(31), '31');
  assert.deepStrictEqual(reorderClauses([
    { title: '一、 交付', content: '约定' },
    { title: '验收' }
  ]), [
    { title: '交付', content: '约定', _num: '一', _label: '一、' },
    { title: '验收', content: '', _num: '二', _label: '二、' }
  ]);
});

test('dict returns known semantic values and safe fallback', () => {
  assert.deepStrictEqual(dict.certification('VERIFIED'), { text: '已认证', color: '#2f86e6' });
  assert.deepStrictEqual(dict.member('UNKNOWN'), { text: 'UNKNOWN', color: '#9ca3af' });
  assert.deepStrictEqual(dict.step(), { text: '-', color: '#9ca3af' });
});

function loadComponent(relativePath) {
  let definition;
  global.Component = value => { definition = value; };
  const modulePath = require.resolve(relativePath);
  delete require.cache[modulePath];
  require(relativePath);
  return definition;
}

test('shared components emit stable UI events', () => {
  const searchBar = loadComponent('../components/search-bar/search-bar');
  const events = [];
  const context = { triggerEvent: (name, detail) => events.push({ name, detail }) };
  searchBar.methods.onInput.call(context, { detail: { value: '合同' } });
  searchBar.methods.onConfirm.call(context, { detail: { value: '合同' } });
  searchBar.methods.onClear.call(context);
  assert.deepStrictEqual(events, [
    { name: 'input', detail: { value: '合同' } },
    { name: 'confirm', detail: { value: '合同' } },
    { name: 'input', detail: { value: '' } },
    { name: 'clear', detail: undefined }
  ]);

  const emptyState = loadComponent('../components/empty-state/empty-state');
  let tapped = false;
  emptyState.methods.onTap.call({ triggerEvent: name => { tapped = name === 'tap'; } });
  assert.strictEqual(tapped, true);
});

function loadPage(relativePath) {
  let definition;
  global.Page = value => { definition = value; };
  const modulePath = require.resolve(relativePath);
  delete require.cache[modulePath];
  require(relativePath);
  return definition;
}

test('logistics images get readable names and file sizes', () => {
  const contractPreview = loadPage('../pages/contract-preview/contract-preview');
  assert.match(
    contractPreview.buildLogisticsFileName('wxfile://tmp/photo.PNG'),
    /^物流单-\d{8}-\d{6}\.png$/
  );
  assert.strictEqual(contractPreview.formatFileSize(2048), '2.0KB');
  assert.strictEqual(contractPreview.formatFileSize(2 * 1024 * 1024), '2.0MB');
});

test('business document editor maps contract products into the selected template', () => {
  const contractPreview = loadPage('../pages/contract-preview/contract-preview');
  const context = {
    data: {
      sData: {
        sections: [{
          type: 'table',
          columns: ['产品名称', '规格型号', '单位', '数量', '单价(元)', '金额(元)'],
          rows: [['商品A', 'A-1', '件', '2', '3.5', '7']]
        }]
      }
    },
    valueForDocumentColumn: contractPreview.valueForDocumentColumn
  };
  const columns = ['序号', '品名', '规格', '单位', '数量', '单价', '金额', '备注'];
  const rows = contractPreview.buildDocumentRows.call(context, columns);

  assert.deepStrictEqual(rows, [['1', '商品A', 'A-1', '件', '2', '3.5', '7', '']]);
  assert.strictEqual(contractPreview.calculateDocumentTotal(columns, rows, 0), '7');
});

test('contract details and snapshots use the entered contract name', () => {
  const signScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'sign-contract', 'sign-contract.js'),
    'utf8'
  );
  const previewScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.js'),
    'utf8'
  );
  assert.ok(signScript.includes('title: contractName.trim()'));
  assert.ok(previewScript.includes(
    "const pdfTitle = contract.name || (sData && sData.title) || this.data.contractName || '购销合同'"
  ));
});

test('pending approval opens the structured contract preview instead of rendering raw JSON', () => {
  const approvalDir = path.join(__dirname, '..', 'pages', 'contract-approval');
  const approvalScript = fs.readFileSync(path.join(approvalDir, 'contract-approval.js'), 'utf8');
  const approvalTemplate = fs.readFileSync(path.join(approvalDir, 'contract-approval.wxml'), 'utf8');
  const previewTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.wxml'), 'utf8'
  );
  assert.ok(approvalScript.includes('/pages/contract-preview/contract-preview?contractId='));
  assert.ok(approvalTemplate.includes('查看合同'));
  assert.ok(!approvalTemplate.includes('{{item.terms}}'));
  assert.ok(previewTemplate.includes('contractTableRows'));
  assert.ok(previewTemplate.includes('contractClauses'));
});

test('experience build uses one native tab bar and size-safe file transfer', () => {
  const config = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'app.json'), 'utf8'));
  assert.strictEqual(config.tabBar.custom, false);
  const scripts = [
    'pages/contract-preview/contract-preview.js',
    'pages/sales-order-detail/sales-order-detail.js',
    'pages/reconciliation/reconciliation.js'
  ].map(relative => fs.readFileSync(path.join(__dirname, '..', relative), 'utf8')).join('\n');
  assert.ok(!scripts.includes('wx.downloadFile'));
  assert.ok(!scripts.includes('wx.uploadFile'));
  assert.ok(scripts.includes('downloadApiFile'));
  assert.ok(scripts.includes('uploadMultipartApiFile'));
  assert.ok(!scripts.includes('/attachments/base64'));
  const transfer = fs.readFileSync(path.join(__dirname, '..', 'utils', 'fileTransfer.js'), 'utf8');
  assert.ok(transfer.includes('wx.uploadFile'));
  assert.ok(transfer.includes("header['X-Company-Id']"));
  assert.ok(!transfer.includes('contentBase64 }'));
});

test('home exposes the ordered approval center with a message indicator', () => {
  const homeScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'index', 'index.js'), 'utf8'
  );
  const homeTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'index', 'index.wxml'), 'utf8'
  );
  assert.ok(!homeScript.includes("item.type === 'SALES_ORDER'"));
  assert.ok(!homeTemplate.includes('pendingSalesOrderTodo'));
  assert.ok(homeTemplate.includes('data-key="approval"'));
  assert.ok(homeTemplate.includes('data-key="inventory"'));
  assert.ok(homeTemplate.includes('审批中心'));
  assert.ok(homeTemplate.includes('approvalHasMessage'));
  assert.ok(homeScript.includes('/approvals/summary'));
  const workbenchKeys = [...homeTemplate.matchAll(/data-key="(contracts|approval|reconciliation|inventory)"/g)]
    .map(match => match[1]);
  assert.deepStrictEqual(workbenchKeys.slice(-4), [
    'contracts', 'approval', 'reconciliation', 'inventory'
  ]);
  const approvalScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-approval', 'contract-approval.js'), 'utf8'
  );
  const approvalTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-approval', 'contract-approval.wxml'), 'utf8'
  );
  assert.ok(approvalScript.includes('/approvals/fulfillment'));
  assert.ok(approvalScript.includes('/approvals/results'));
  assert.ok(approvalScript.includes("label: '待我处理'"));
  assert.ok(approvalScript.includes("label: '处理记录'"));
  assert.ok(approvalScript.includes('sales-order-detail'));
  assert.ok(approvalScript.includes("label: '合同'"));
  assert.ok(approvalScript.includes("label: '履约资料'"));
  assert.ok(approvalScript.includes('substring(0, 19)'));
  assert.ok(approvalTemplate.includes('result-reason'));
  assert.ok(!approvalTemplate.includes('approval-hero'));
  const approvalStyles = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-approval', 'contract-approval.wxss'), 'utf8'
  );
  assert.ok(approvalStyles.includes('border-bottom-color: #2185e8'));
});

test('contract ledger gives each company group a distinct section boundary', () => {
  const styles = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-center', 'contract-center.wxss'), 'utf8'
  );
  assert.ok(styles.includes('.company-group + .company-group'));
  assert.ok(styles.includes('border-left: 7rpx solid #2e91ea'));
  assert.ok(styles.includes('background: #e5f1fd'));
});

test('contract numeric cells clear zero on focus and normalize leading zeros', () => {
  const signContract = loadPage('../pages/sign-contract/sign-contract');
  let focusedPatch;
  signContract.onNumberCellFocus.call({
    data: { tableRows: [['商品', '', '件', '0', '0', '0']] },
    setData: patch => { focusedPatch = patch; }
  }, { currentTarget: { dataset: { row: 0, col: 3 } } });
  assert.deepStrictEqual(focusedPatch, { 'tableRows[0][3]': '' });

  let cellPatch;
  const tableContext = {
    data: {
      tableSection: { columns: ['产品名称', '规格型号', '单位', '数量', '单价(元)', '金额(元)'] },
      tableRows: [['商品', '', '件', '0', '0', '0']],
      inventoryProducts: []
    },
    tableColumnIndex: signContract.tableColumnIndex,
    setData: patch => { cellPatch = patch; }
  };
  const normalized = signContract.onTableCellChange.call(tableContext, {
    currentTarget: { dataset: { row: 0, col: 3 } },
    detail: { value: '0100' }
  });
  assert.strictEqual(normalized, '100');
  assert.strictEqual(cellPatch['tableRows[0][3]'], '100');

  const signTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'sign-contract', 'sign-contract.wxml'), 'utf8'
  );
  const templateEditor = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-template-detail', 'contract-template-detail.wxml'), 'utf8'
  );
  assert.ok(signTemplate.includes('bindfocus="onNumberCellFocus"'));
  assert.ok(signTemplate.includes('bindblur="onNumberCellBlur"'));
  assert.ok(templateEditor.includes('bindfocus="onNumberCellFocus"'));
});

test('company search confirmation does not depend on sensitive company fields', () => {
  const companyBind = loadPage('../pages/company-bind/company-bind');
  let modal;
  wx.showModal = options => { modal = options; };
  companyBind.confirmCompany.call({
    data: { selectedCompany: { id: '3', name: '测试企业', maskedCreditCode: '9113**********4567' } }
  });
  assert.strictEqual(modal.title, '企业已入驻');
  assert.match(modal.content, /企业管理员/);
  assert.strictEqual(modal.showCancel, false);
});

test('project ledger formats contract totals and remains an optional enterprise entry', () => {
  const projectLedger = require('../pages/project-ledger/project-ledger');
  assert.strictEqual(projectLedger.money('1288.5'), '1288.50');
  assert.strictEqual(projectLedger.money(null), '0.00');
  assert.deepStrictEqual(projectLedger.decorateProject({
    id: 1,
    purchaseCost: 300,
    salesIncome: 500,
    estimatedProfit: 200
  }), {
    id: 1,
    purchaseCost: 300,
    salesIncome: 500,
    estimatedProfit: 200,
    purchaseCostText: '300.00',
    salesIncomeText: '500.00',
    estimatedProfitText: '200.00'
  });
  const companyView = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'company', 'company.wxml'),
    'utf8'
  );
  assert.ok(companyView.includes('项目账套'));
  assert.ok(companyView.includes('bindtap="goProjectLedger"'));
});

test('signed contracts prompt managers to choose or create a project ledger', () => {
  const previewScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.js'),
    'utf8'
  );
  const previewTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.wxml'),
    'utf8'
  );
  const projectScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'project-ledger', 'project-ledger.js'),
    'utf8'
  );
  assert.ok(previewScript.includes('/project-ledgers/contracts/${contract.id || this.data.contractId}/assignment'));
  assert.ok(previewTemplate.includes('project-ledger-prompt'));
  assert.ok(previewTemplate.includes('加入已有项目账套'));
  assert.ok(previewTemplate.includes('新建账套并加入'));
  assert.ok(previewTemplate.includes('本合同不再提示'));
  assert.ok(previewScript.includes('/dismiss'));
  assert.ok(projectScript.includes('assignPendingContract(project.id)'));
});

test('signed contract detail renders the provider archive page instead of a fake completed seal', () => {
  const previewDir = path.join(__dirname, '..', 'pages', 'contract-preview');
  const previewScript = fs.readFileSync(path.join(previewDir, 'contract-preview.js'), 'utf8');
  const previewTemplate = fs.readFileSync(path.join(previewDir, 'contract-preview.wxml'), 'utf8');
  assert.ok(previewScript.includes('/signed-preview-chunk-data'));
  assert.ok(previewTemplate.includes('src="{{signedPreviewFilePath}}"'));
  assert.ok(previewTemplate.includes('真实签署文件'));
  assert.ok(!previewTemplate.includes('电子签章'));
  assert.ok(!previewTemplate.includes('已完成'));
});

const app = {
  globalData: {
    baseUrl: 'https://api.example.test',
    isLocalDevelopment: true,
    token: 'token-1',
    currentCompanyId: 'company-3'
  }
};
global.getApp = () => app;
global.wx = {};
const { request } = require('../utils/request');
const {
  downloadChunkedApiFile,
  localFileReady,
  uploadMultipartApiFile
} = require('../utils/fileTransfer');
const {
  clearHomeSnapshots,
  readHomeSnapshot,
  snapshotKey,
  writeHomeSnapshot
} = require('../utils/homeSnapshot');
const { setTabBarHidden, syncTabBar, tabIndicatorTransform } = require('../utils/tabBar');

test('multipart upload sends auth, tenant and form fields without base64 packaging', async () => {
  let captured;
  wx.getStorageSync = () => '';
  wx.uploadFile = options => {
    captured = options;
    options.success({ statusCode: 200, data: '{"code":0,"message":"ok","data":{"id":18}}' });
  };

  const result = await uploadMultipartApiFile(
    '/contracts/12/attachments', '/tmp/invoice.jpg',
    { category: 'INVOICE', invoiceAmount: 88.5, ignored: null }
  );

  assert.deepStrictEqual(result, { id: 18 });
  assert.strictEqual(captured.url, 'https://api.example.test/contracts/12/attachments');
  assert.strictEqual(captured.filePath, '/tmp/invoice.jpg');
  assert.strictEqual(captured.name, 'file');
  assert.deepStrictEqual(captured.header, {
    Authorization: 'token-1',
    'X-Company-Id': 'company-3'
  });
  assert.deepStrictEqual(captured.formData, { category: 'INVOICE', invoiceAmount: '88.5' });
  assert.ok(!Object.prototype.hasOwnProperty.call(captured.formData, 'contentBase64'));
});

test('large contract files download in bounded cloud-container chunks', async () => {
  const requestedUrls = [];
  const savedChunks = [];
  const source = Buffer.from('abcdefghij');
  let inFlight = 0;
  let maxInFlight = 0;
  wx.getStorageSync = () => '';
  wx.getFileSystemManager = () => ({
    writeFile: options => {
      savedChunks.length = 0;
      savedChunks.push(Buffer.from(options.data, 'base64'));
      options.success();
    },
    appendFile: options => {
      savedChunks.push(Buffer.from(options.data, 'base64'));
      options.success();
    }
  });
  wx.request = options => {
    requestedUrls.push(options.url);
    const offset = Number(options.url.match(/[?&]offset=(\d+)/)[1]);
    const size = Number(options.url.match(/[?&]size=(\d+)/)[1]);
    const chunk = source.subarray(offset, Math.min(source.length, offset + size));
    inFlight += 1;
    maxInFlight = Math.max(maxInFlight, inFlight);
    setTimeout(() => {
      inFlight -= 1;
      options.success({ statusCode: 200, data: { code: 0, message: 'ok', data: {
        offset,
        length: chunk.length,
        totalSize: source.length,
        eof: offset + chunk.length === source.length,
        contentBase64: chunk.toString('base64')
      } } });
    }, offset === 3 ? 8 : 1);
  };

  const result = await downloadChunkedApiFile(
    '/contract-attachments/19/content-chunk-data', '/user-data/资料-19.pdf', 10, 3
  );

  assert.strictEqual(result.filePath, '/user-data/资料-19.pdf');
  assert.strictEqual(result.fileSize, 10);
  assert.strictEqual(Buffer.concat(savedChunks).toString(), 'abcdefghij');
  assert.strictEqual(maxInFlight, 3);
  assert.ok(requestedUrls[0].endsWith(
    '/contract-attachments/19/content-chunk-data?offset=0&size=3'));
  assert.ok(requestedUrls[1].endsWith(
    '/contract-attachments/19/content-chunk-data?offset=3&size=3'));
  assert.ok(requestedUrls[2].endsWith(
    '/contract-attachments/19/content-chunk-data?offset=6&size=3'));
  assert.ok(requestedUrls[3].endsWith(
    '/contract-attachments/19/content-chunk-data?offset=9&size=3'));
});

test('downloaded immutable files are reused only when their local size matches', () => {
  wx.getFileSystemManager = () => ({ statSync: () => ({ size: 8192 }) });
  assert.strictEqual(localFileReady('/user-data/file.pdf', 8192), true);
  assert.strictEqual(localFileReady('/user-data/file.pdf', 4096), false);
  wx.getFileSystemManager = () => ({ statSync: () => { throw new Error('missing'); } });
  assert.strictEqual(localFileReady('/user-data/missing.pdf', 8192), false);
});

test('home snapshots are isolated by user, company, role and period and expire safely', () => {
  const storage = {};
  wx.getStorageSync = key => storage[key] || '';
  wx.setStorageSync = (key, value) => { storage[key] = value; };
  wx.removeStorageSync = key => { delete storage[key]; };
  const now = 2_000_000_000_000;
  const context = { userId: '7', companyId: '8', role: 'supplier', period: 'year' };
  const snapshot = writeHomeSnapshot(context, {
    companyDisplayName: '测试企业',
    stats: { totalAmount: '99', totalOrders: 2, counterpartyCount: 1 }
  }, now);

  assert.ok(snapshotKey(context).includes('_7_8_supplier_year'));
  assert.strictEqual(readHomeSnapshot(context, now + 1000).payload.companyDisplayName, '测试企业');
  assert.strictEqual(readHomeSnapshot({ ...context, userId: '9' }, now + 1000), null);
  assert.strictEqual(readHomeSnapshot({ ...context, companyId: '10' }, now + 1000), null);
  assert.strictEqual(readHomeSnapshot({ ...context, role: 'buyer' }, now + 1000), null);
  assert.strictEqual(readHomeSnapshot({ ...context, period: 'month' }, now + 1000), null);
  assert.strictEqual(readHomeSnapshot(context, now + 31 * 24 * 60 * 60 * 1000), null);

  writeHomeSnapshot(context, { companyDisplayName: '测试企业' }, now);
  clearHomeSnapshots();
  assert.strictEqual(storage[snapshotKey(context)], undefined);
});

test('custom tab bar moves its indicator by one slot per navigation item', () => {
  assert.strictEqual(tabIndicatorTransform(0), 'translate3d(0%, 0, 0)');
  assert.strictEqual(tabIndicatorTransform(1), 'translate3d(100%, 0, 0)');
  assert.strictEqual(tabIndicatorTransform(2), 'translate3d(200%, 0, 0)');

  app.globalData.activeTabIndex = 2;
  let movement;
  syncTabBar({
    getTabBar: () => ({
      moveTo() {},
      moveFromTo: (from, to) => { movement = { from, to }; }
    })
  }, 1);
  assert.deepStrictEqual(movement, { from: 2, to: 1 });
  assert.strictEqual(app.globalData.activeTabIndex, 1);

  const componentScript = fs.readFileSync(
    path.join(__dirname, '..', 'custom-tab-bar', 'index.js'),
    'utf8'
  );
  assert.ok(!componentScript.includes('setTimeout'));
});

test('privacy prompt hides both native and custom tab bars', () => {
  let customHidden;
  let nativeHidden = false;
  let nativeShown = false;
  wx.hideTabBar = () => { nativeHidden = true; };
  wx.showTabBar = () => { nativeShown = true; };
  const page = {
    getTabBar: () => ({ setHidden: hidden => { customHidden = hidden; } })
  };

  setTabBarHidden(page, true);
  assert.strictEqual(app.globalData.tabBarHidden, true);
  assert.strictEqual(customHidden, true);
  assert.strictEqual(nativeHidden, true);

  setTabBarHidden(page, false);
  assert.strictEqual(app.globalData.tabBarHidden, false);
  assert.strictEqual(customHidden, false);
  assert.strictEqual(nativeShown, true);
});

test('payment voucher requires a manually entered amount before upload', () => {
  const contractPreview = loadPage('../pages/contract-preview/contract-preview');
  let uploaded;
  const context = {
    data: {
      attachmentUploading: false,
      paymentAmount: '',
      pendingPaymentAttachment: null
    },
    setData(changes, callback) {
      Object.assign(this.data, changes);
      if (callback) callback();
    },
    uploadAttachment: (category, filePath, originalName, metadata) => {
      uploaded = { category, filePath, originalName, metadata };
    }
  };

  contractPreview.prepareAttachmentUpload.call(
    context,
    'PAYMENT_VOUCHER',
    '/tmp/voucher.pdf',
    '转款凭证.pdf'
  );

  assert.strictEqual(context.data.showPaymentAmountEditor, true);
  assert.deepStrictEqual(context.data.pendingPaymentAttachment, {
    filePath: '/tmp/voucher.pdf',
    originalName: '转款凭证.pdf'
  });
  contractPreview.onPaymentAmountInput.call(context, { detail: { value: '1288.5' } });
  contractPreview.confirmPaymentAttachmentUpload.call(context);
  assert.deepStrictEqual(uploaded, {
    category: 'PAYMENT_VOUCHER',
    filePath: '/tmp/voucher.pdf',
    originalName: '转款凭证.pdf',
    metadata: { voucherAmount: '1288.50', voucherDate: context.data.paymentDate }
  });
  assert.strictEqual(context.data.showPaymentAmountEditor, false);
});

test('payment voucher viewing is separate from handwritten confirmation', () => {
  const approvalDir = path.join(__dirname, '..', 'pages', 'contract-approval');
  const approvalScript = fs.readFileSync(path.join(approvalDir, 'contract-approval.js'), 'utf8');
  const approvalTemplate = fs.readFileSync(path.join(approvalDir, 'contract-approval.wxml'), 'utf8');
  assert.ok(approvalScript.includes("if (item.approvalType === 'PAYMENT_VOUCHER')"));
  assert.ok(approvalScript.includes("'signature'"));
  assert.ok(approvalTemplate.includes('查看资料时不会要求签名'));
  assert.ok(approvalTemplate.includes('签字并确认通过'));
});

test('invoice requires date and amount while the server generates its number', () => {
  const contractPreview = loadPage('../pages/contract-preview/contract-preview');
  let uploaded;
  const context = {
    data: { attachmentUploading: false, pendingInvoiceAttachment: null },
    setData(changes, callback) {
      Object.assign(this.data, changes);
      if (callback) callback();
    },
    uploadAttachment: (category, filePath, originalName, metadata) => {
      uploaded = { category, filePath, originalName, metadata };
    }
  };
  contractPreview.prepareAttachmentUpload.call(context, 'INVOICE', '/tmp/invoice.pdf', '发票.pdf');
  assert.strictEqual(context.data.showInvoiceEditor, true);
  contractPreview.onInvoiceAmountInput.call(context, { detail: { value: '88.5' } });
  const invoiceDate = context.data.invoiceDate;
  contractPreview.confirmInvoiceUpload.call(context);
  assert.deepStrictEqual(uploaded, {
    category: 'INVOICE',
    filePath: '/tmp/invoice.pdf',
    originalName: '发票.pdf',
    metadata: { invoiceDate, invoiceAmount: '88.50' }
  });
  const template = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.wxml'), 'utf8'
  );
  assert.ok(!template.includes('发票号码'));
  assert.ok(!template.includes('无需录入发票号码'));
});

test('contract attachments download to a safe persistent path for every category', () => {
  const contractPreview = loadPage('../pages/contract-preview/contract-preview');
  wx.env = { USER_DATA_PATH: '/user-data' };

  assert.strictEqual(contractPreview.attachmentDownloadPath({
    id: 18,
    originalName: '../转款:凭证.pdf',
    contentType: 'application/pdf'
  }), '/user-data/_转款_凭证-18.pdf');
  assert.strictEqual(contractPreview.attachmentDownloadPath({
    id: 19,
    originalName: '其它资料',
    contentType: 'image/png'
  }), '/user-data/其它资料-19.png');

  const script = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.js'),
    'utf8'
  );
  assert.ok(!script.includes("download && attachment.category === 'INVOICE'"));
  assert.ok(script.includes('/contract-attachments/${attachment.id}/content-chunk-data'));
  assert.ok(script.includes('downloadChunkedApiFile'));
  assert.ok(script.includes('localFileReady(localFilePath, fileSize)'));
  assert.ok(!script.includes('downloadBinaryApiFile'));
});

test('contract detail renders first and defers fulfillment plus remote signing refresh', () => {
  const script = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.js'),
    'utf8'
  );
  assert.ok(script.includes("if (this.data.activeTab === 'fulfillment')"));
  assert.ok(script.includes('syncContractSigningInBackground()'));
  assert.ok(script.includes('页面先使用本地签署状态'));
  assert.ok(!script.includes("signing${syncSigning ? '/sync' : ''}"));
});

test('personal certification refreshes terminal results safely and formats Beijing time', () => {
  const pageDir = path.join(__dirname, '..', 'pages', 'personal-cert');
  const script = fs.readFileSync(path.join(pageDir, 'personal-cert.js'), 'utf8');
  const template = fs.readFileSync(path.join(pageDir, 'personal-cert.wxml'), 'utf8');
  assert.ok(script.includes("identity.status !== 'VERIFIED'"));
  assert.ok(script.includes('formatBeijingTime(identity.verifiedAt)'));
  assert.ok(script.includes("title: status === 'VERIFIED' ? '认证结果已更新'"));
  assert.ok(!template.includes('敏感信息安全处理'));
  assert.ok(!template.includes('商签通不保存完整证件号或人脸照片'));
});

test('profile exposes legal, help, about and safe account cancellation without a settings layer', () => {
  const appConfig = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'app.json'), 'utf8'));
  [
    'pages/legal-document/legal-document',
    'pages/help-center/help-center',
    'pages/about/about',
    'pages/account-cancel/account-cancel'
  ].forEach(page => assert.ok(appConfig.pages.includes(page)));
  assert.ok(!appConfig.pages.includes('pages/settings/settings'));

  const meScript = fs.readFileSync(path.join(__dirname, '..', 'pages', 'me', 'me.js'), 'utf8');
  const meTemplate = fs.readFileSync(path.join(__dirname, '..', 'pages', 'me', 'me.wxml'), 'utf8');
  assert.ok(!meScript.includes('/pages/settings/settings'));
  assert.ok(!meTemplate.includes('设置中心'));
  assert.ok(!meScript.includes("title: '关于商签通'"));
  assert.ok(!meScript.includes("title: '用户许可使用协议'"));
  assert.ok(meTemplate.includes('账号与身份'));
  assert.ok(meTemplate.includes('个人信息收集清单'));
  assert.ok(meTemplate.includes('第三方信息共享清单'));
  assert.ok(!meTemplate.includes('系统权限管理'));
  assert.ok(meTemplate.includes('帮助与反馈'));
  assert.ok(meTemplate.includes('账号注销'));
  assert.ok(meTemplate.includes('/images/icons/info-collection.svg'));
  assert.ok(meTemplate.includes('/images/icons/info-sharing.svg'));
  assert.ok(meTemplate.includes('/images/icons/account-cancel.svg'));
  assert.ok(!meTemplate.includes('setting-symbol'));

  const companyTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'company', 'company.wxml'), 'utf8'
  );
  assert.ok(!companyTemplate.includes('<view class="count-pill">{{todos.length}}</view>'));
  assert.ok(!companyTemplate.includes('state-pill attention'));
  assert.ok(companyTemplate.includes('<view class="state-pill" wx:if="{{item.count}}">'));

  const legalTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'templates', 'legal-content.wxml'), 'utf8'
  );
  assert.ok(legalTemplate.includes('userAgreementContent'));
  assert.ok(legalTemplate.includes('collectionListContent'));
  assert.ok(legalTemplate.includes('sharingListContent'));
  assert.ok(legalTemplate.includes('法大大电子签服务'));

  const helpTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'help-center', 'help-center.wxml'), 'utf8'
  );
  assert.ok(helpTemplate.includes('open-type="contact"'));
  assert.ok(helpTemplate.includes('常见问题'));

  const cancelScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'account-cancel', 'account-cancel.js'), 'utf8'
  );
  const cancelTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'account-cancel', 'account-cancel.wxml'), 'utf8'
  );
  assert.ok(cancelTemplate.includes('联系平台支持申请注销'));
  assert.ok(cancelTemplate.includes('不提供一键删除'));
  assert.ok(!cancelScript.includes("method: 'DELETE'"));
  assert.ok(!cancelScript.includes('/account/cancel'));

  const aboutScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'about', 'about.js'), 'utf8'
  );
  const aboutTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'about', 'about.wxml'), 'utf8'
  );
  assert.ok(aboutScript.includes('wx.getAccountInfoSync()'));
  assert.ok(aboutTemplate.includes('当前版本'));
  assert.ok(!aboutTemplate.includes('核心能力'));
  assert.ok(!aboutTemplate.includes('用户服务协议'));
  assert.ok(!aboutTemplate.includes('帮助与反馈'));
});

test('login agreement links reuse the dedicated document reader', () => {
  ['login', 'phone-login'].forEach(pageName => {
    const pageDir = path.join(__dirname, '..', 'pages', pageName);
    const script = fs.readFileSync(path.join(pageDir, `${pageName}.js`), 'utf8');
    const template = fs.readFileSync(path.join(pageDir, `${pageName}.wxml`), 'utf8');
    assert.ok(script.includes('/pages/legal-document/legal-document?type=user'));
    assert.ok(script.includes('/pages/legal-document/legal-document?type=privacy'));
    assert.ok(!template.includes('agreementType'));
  });
});

test('request injects auth and tenant headers and unwraps API data', async () => {
  let captured;
  wx.getStorageSync = () => '';
  wx.request = options => {
    captured = options;
    options.success({ statusCode: 200, data: { code: 0, data: { id: 9 } } });
  };

  const result = await request({ url: '/orders', method: 'POST', data: { amount: 10 } });
  assert.deepStrictEqual(result, { id: 9 });
  assert.strictEqual(captured.url, 'https://api.example.test/orders');
  assert.strictEqual(captured.header.Authorization, 'token-1');
  assert.strictEqual(captured.header['X-Company-Id'], 'company-3');
  assert.strictEqual(captured.method, 'POST');
});

test('request exposes business and network failures', async () => {
  wx.getStorageSync = () => '';
  wx.request = options => options.success({ statusCode: 400, data: { code: 400, message: '参数错误' } });
  await assert.rejects(request({ url: '/orders' }), /参数错误/);

  wx.request = options => options.fail(new Error('offline'));
  await assert.rejects(request({ url: '/orders' }), /offline/);
});

test('request clears session and redirects after unauthorized response', async () => {
  const removedKeys = [];
  let redirectUrl;
  wx.getStorageSync = () => '';
  wx.removeStorageSync = key => { removedKeys.push(key); };
  wx.reLaunch = options => { redirectUrl = options.url; };
  wx.request = options => options.success({ statusCode: 401, data: {} });

  await assert.rejects(request({ url: '/orders' }), /登录已失效/);
  assert.strictEqual(app.globalData.token, '');
  assert.strictEqual(app.globalData.currentCompanyId, '');
  assert.ok(removedKeys.includes('tradepass_token'));
  assert.ok(removedKeys.includes('tradepass_company_id'));
  assert.strictEqual(redirectUrl, '/pages/index/index');
});

function loadAppDefinition(platform = 'devtools') {
  let definition;
  global.App = value => { definition = value; };
  wx.getSystemInfoSync = () => ({ platform });
  const modulePath = require.resolve('../app');
  delete require.cache[modulePath];
  require('../app');
  return definition;
}

test('app detects desktop WeChat without treating it as local development', () => {
  const definition = loadAppDefinition('windows');
  assert.strictEqual(definition.globalData.isDesktopWechat, true);
  assert.strictEqual(definition.globalData.isLocalDevelopment, false);
});

function appInstance(definition) {
  return Object.assign({}, definition, { globalData: Object.assign({}, definition.globalData) });
}

test('app restores, switches and clears tenant-aware session state', async () => {
  const definition = loadAppDefinition();
  const instance = appInstance(definition);
  const storage = {
    tradepass_token: 'stored-token',
    tradepass_company_id: '8'
  };
  const removed = [];
  wx.getStorageSync = key => storage[key] || '';
  wx.setStorageSync = (key, value) => { storage[key] = value; };
  wx.removeStorageSync = key => { removed.push(key); delete storage[key]; };
  wx.reLaunch = () => {};
  instance.loadMe = () => Promise.resolve();

  instance.onLaunch.call(instance);
  assert.strictEqual(instance.globalData.token, 'stored-token');
  assert.strictEqual(instance.globalData.currentCompanyId, '8');

  instance.setCurrentCompany.call(instance, 9);
  assert.strictEqual(storage.tradepass_company_id, '9');
  instance.setCurrentCompany.call(instance, null);
  assert.strictEqual(instance.globalData.currentCompanyId, '');

  instance.globalData.userInfo = { id: 1 };
  instance.globalData.memberInfo = { roleCode: 'ADMIN' };
  instance.globalData.companies = [{ companyId: '8' }];
  global.getApp = () => instance;
  wx.request = options => options.success({ statusCode: 200, data: { code: 0, data: null } });
  await instance.logout.call(instance);
  assert.strictEqual(instance.globalData.token, '');
  assert.strictEqual(instance.globalData.userInfo, null);
  assert.deepStrictEqual(instance.globalData.companies, []);
  assert.ok(removed.includes('tradepass_token'));
});

test('app retries a failed cold-start profile restore before home decides company state', async () => {
  const instance = appInstance(loadAppDefinition());
  const storage = {
    tradepass_token: 'stored-token',
    tradepass_company_id: '8'
  };
  wx.getStorageSync = key => storage[key] || '';
  wx.setStorageSync = (key, value) => { storage[key] = value; };
  wx.removeStorageSync = key => { delete storage[key]; };
  let attempts = 0;
  instance.loadMe = () => {
    attempts += 1;
    if (attempts === 1) return Promise.reject(new Error('container cold start'));
    return Promise.resolve(instance.applyMePayload.call(instance, {
      user: { id: '7', currentCompanyId: '8' },
      member: { roleCode: 'ADMIN', memberStatus: 'ACTIVE' },
      companies: [{ companyId: '8', companyName: '测试企业' }]
    }));
  };

  instance.onLaunch.call(instance);
  assert.strictEqual(await instance.ensureSessionReady.call(instance), null);
  await instance.refreshSession.call(instance);
  assert.strictEqual(attempts, 2);
  assert.strictEqual(instance.globalData.userInfo.currentCompanyId, '8');
  assert.strictEqual(instance.globalData.memberInfo.roleCode, 'ADMIN');

  const indexScript = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'index', 'index.js'), 'utf8'
  );
  const indexTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'index', 'index.wxml'), 'utf8'
  );
  assert.ok(indexScript.includes('loggedIn && !user'));
  assert.ok(indexScript.includes('scheduleSessionRestore()'));
  assert.ok(indexTemplate.includes('wx:elif="{{sessionRestoring && !homeHasSnapshot}}"'));
  assert.ok(!indexTemplate.includes('home-snapshot-notice'));
  assert.ok(indexScript.indexOf('this.restoreHomeSnapshot()')
    < indexScript.indexOf('await app.ensureSessionReady()'));
});

test('app switchCompany sends target tenant header and updates global profile', async () => {
  const instance = appInstance(loadAppDefinition());
  global.getApp = () => instance;
  instance.globalData.token = 'token';
  wx.setStorageSync = () => {};
  wx.removeStorageSync = () => {};
  let captured;
  wx.request = options => {
    captured = options;
    options.success({ statusCode: 200, data: { code: 0, data: {
      user: { id: '7', currentCompanyId: '9' },
      member: { roleCode: 'ADMIN' },
      companies: [{ companyId: '9' }]
    } } });
  };

  const result = await instance.switchCompany.call(instance, 9);
  assert.strictEqual(captured.header['X-Company-Id'], '9');
  assert.strictEqual(captured.data.companyId, 9);
  assert.strictEqual(instance.globalData.currentCompanyId, '9');
  assert.strictEqual(result.member.roleCode, 'ADMIN');
});

test('contract sales documents only show records bound to the current contract', () => {
  const pageDir = path.join(__dirname, '..', 'pages', 'contract-preview');
  const script = fs.readFileSync(path.join(pageDir, 'contract-preview.js'), 'utf8');
  const template = fs.readFileSync(path.join(pageDir, 'contract-preview.wxml'), 'utf8');

  assert.ok(script.includes('/contracts/${this.data.contractId}/documents'));
  assert.ok(!script.includes('/orders?counterpartyName'));
  assert.ok(!script.includes('loadSalesOrders'));
  assert.ok(!template.includes('关联销售订单'));
  assert.ok(!template.includes('salesList'));
});

test('business documents require an editable template flow and do not auto-open after creation', () => {
  const script = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.js'),
    'utf8'
  );
  assert.ok(script.includes('documentTemplateIndex: -1'));
  assert.ok(script.includes('showDocumentEditor: true'));
  assert.ok(script.includes('content: {'));
  assert.ok(!script.includes('this.downloadBusinessDocumentFile(document, false)'));
});

test('contract collaboration groups sales orders and uploadable invoices in fulfillment', () => {
  const pageDir = path.join(__dirname, '..', 'pages', 'contract-preview');
  const script = fs.readFileSync(path.join(pageDir, 'contract-preview.js'), 'utf8');
  const template = fs.readFileSync(path.join(pageDir, 'contract-preview.wxml'), 'utf8');
  assert.ok(script.includes("{ key: 'detail', label: '合同' }"));
  assert.ok(script.includes("{ key: 'fulfillment', label: '履约资料' }"));
  assert.ok(!script.includes("{ key: 'sales', label: '销售单' }"));
  assert.ok(!script.includes("{ key: 'payment'"));
  assert.ok(template.indexOf('>销售单<') < template.indexOf('>物流单<'));
  assert.ok(template.indexOf('>发票<') < template.indexOf('>其它<'));
  assert.ok(template.includes('data-category="INVOICE"'));
  assert.ok(script.includes('/attachments?category=INVOICE'));
  assert.ok(script.includes('showPaymentAmountEditor: true'));
  assert.ok(script.includes('voucherAmount: normalizedAmount'));
  assert.ok(!script.includes('DELIVERY_NOTE'));
  assert.ok(!template.includes('送货单'));
  assert.ok(template.includes('我的进展'));
  assert.ok(template.includes('仅当前账号可见'));
});

test('all sales orders start as editable drafts before counterpart confirmation', () => {
  const contractPreview = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'contract-preview', 'contract-preview.js'), 'utf8'
  );
  const salesDetail = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'sales-order-detail', 'sales-order-detail.js'), 'utf8'
  );
  assert.ok(contractPreview.includes("contract.status === 'PENDING'"));
  assert.ok(contractPreview.includes("salesOrderCreateText: '创建草稿'"));
  assert.ok(contractPreview.includes('提交并经需方确认后才会进入对账'));
  assert.ok(salesDetail.includes('/trade-documents/${this.data.id}/draft'));
  assert.ok(salesDetail.includes('/trade-documents/${this.data.id}/publish'));
});

test('live reconciliation and sales-order confirmation pages are wired', () => {
  const appConfig = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'app.json'), 'utf8'));
  const reconciliation = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'reconciliation', 'reconciliation.js'), 'utf8'
  );
  const salesDetail = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'sales-order-detail', 'sales-order-detail.js'), 'utf8'
  );
  assert.ok(appConfig.pages.includes('pages/sales-order-detail/sales-order-detail'));
  assert.ok(appConfig.pages.includes('pages/inventory/inventory'));
  assert.ok(reconciliation.includes('/reconciliation-accounts'));
  assert.ok(reconciliation.includes('/pdf-data'));
  assert.ok(!reconciliation.includes('/workbook-data'));
  assert.ok(!reconciliation.includes('/reconciliation-statements'));
  assert.ok(salesDetail.includes("this.openSignatureEditor('RECEIVE_ONLY')"));
  assert.ok(salesDetail.includes("this.openSignatureEditor('INBOUND', warehouse.id)"));
  assert.ok(salesDetail.includes('`/trade-documents/${this.data.id}/receive`'));
  assert.ok(salesDetail.includes("this.submitReceive('REJECT'"));
  const template = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'sales-order-detail', 'sales-order-detail.wxml'), 'utf8'
  );
  assert.ok(template.includes('id="salesOrderSignatureCanvas"'));
  assert.ok(template.includes('签名会自动填写到{{documentLabel}} PDF'));
  const reconciliationTemplate = fs.readFileSync(
    path.join(__dirname, '..', 'pages', 'reconciliation', 'reconciliation.wxml'), 'utf8'
  );
  assert.ok(reconciliationTemplate.includes('查看明细'));
  assert.ok(reconciliationTemplate.includes('下载 PDF'));
});

test('inventory balance displays unit price and inventory amount', () => {
  const pageDir = path.join(__dirname, '..', 'pages', 'inventory');
  const script = fs.readFileSync(path.join(pageDir, 'inventory.js'), 'utf8');
  const template = fs.readFileSync(path.join(pageDir, 'inventory.wxml'), 'utf8');
  assert.ok(script.includes('unitPriceText'));
  assert.ok(script.includes('inventoryAmountText'));
  assert.ok(template.includes('库存单价'));
  assert.ok(template.includes('库存金额'));
});

test('home company switching uses the custom switcher instead of a native action sheet', () => {
  const script = fs.readFileSync(path.join(__dirname, '..', 'pages', 'index', 'index.js'), 'utf8');
  const template = fs.readFileSync(path.join(__dirname, '..', 'pages', 'index', 'index.wxml'), 'utf8');
  assert.ok(script.includes('showCompanySwitcher: true'));
  assert.ok(script.includes('if (companies.length === 0) return'));
  assert.ok(!script.includes('wx.showActionSheet'));
  assert.ok(template.includes('company-switch-sheet'));
  assert.ok(template.includes('company-switch-action primary'));
  assert.ok(template.includes('添加并管理另一家公司'));
  assert.ok(template.includes('输入邀请码加入'));
  assert.ok(template.includes('加入管理员邀请的企业空间'));
});

test('profile only displays the current company and cannot switch tenants', () => {
  const script = fs.readFileSync(path.join(__dirname, '..', 'pages', 'me', 'me.js'), 'utf8');
  const template = fs.readFileSync(path.join(__dirname, '..', 'pages', 'me', 'me.wxml'), 'utf8');
  assert.ok(!script.includes('openCompanySwitcher'));
  assert.ok(!template.includes('切换企业'));
  assert.ok(!template.includes('bindtap="openCompanySwitcher"'));
});

test('enterprise management owns company switching and has no duplicate member invite action', () => {
  const script = fs.readFileSync(path.join(__dirname, '..', 'pages', 'company', 'company.js'), 'utf8');
  const template = fs.readFileSync(path.join(__dirname, '..', 'pages', 'company', 'company.wxml'), 'utf8');
  assert.ok(script.includes('showCompanySwitcher: true'));
  assert.ok(!script.includes('wx.showActionSheet'));
  assert.ok(template.includes('enterprise-switch-sheet'));
  assert.ok(template.includes('enterprise-switch-action primary'));
  assert.ok(template.includes('加入管理员邀请的企业空间'));
  assert.ok(template.indexOf('企业管理') < template.indexOf('我的企业'));
  assert.ok(!template.includes('邀请企业成员'));
  assert.ok(!template.includes('switchCompanyFromRow'));
});

test('home partner list is driven only by bound enterprise relations', () => {
  const script = fs.readFileSync(path.join(__dirname, '..', 'pages', 'index', 'index.js'), 'utf8');
  const detail = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order-detail', 'order-detail.js'), 'utf8');

  assert.ok(script.includes('relations.forEach(item =>'));
  assert.ok(!script.includes('relations.concat(ranking)'));
  assert.ok(!script.includes('ranking.concat(relations)'));
  assert.ok(script.includes("if (!name || !counterpartyCompanyId"));
  assert.ok(detail.includes("canSignContract: !!counterpartyCompanyId && hasPerm('contract_sign')"));
});

(async () => {
  let failed = 0;
  for (const { name, fn } of tests) {
    try {
      await fn();
      process.stdout.write(`✓ ${name}\n`);
    } catch (error) {
      failed += 1;
      process.stderr.write(`✗ ${name}\n${error.stack}\n`);
    }
  }
  process.stdout.write(`\n${tests.length - failed}/${tests.length} tests passed\n`);
  if (failed > 0) process.exitCode = 1;
})();
