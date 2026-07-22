'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');
const {
  numberToChineseCurrency,
  calcTableTotal,
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

test('login guard opens the phone quick-login page', () => {
  const loginGuard = loadComponent('../components/login-guard/login-guard');
  let url;
  wx.navigateTo = options => { url = options.url; };
  loginGuard.methods.goLogin();
  assert.strictEqual(url, '/pages/login/login');
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

const app = {
  globalData: {
    baseUrl: 'https://api.example.test',
    token: 'token-1',
    currentCompanyId: 'company-3'
  }
};
global.getApp = () => app;
global.wx = {};
const { request } = require('../utils/request');

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

function loadAppDefinition() {
  let definition;
  global.App = value => { definition = value; };
  wx.getSystemInfoSync = () => ({ platform: 'devtools' });
  const modulePath = require.resolve('../app');
  delete require.cache[modulePath];
  require('../app');
  return definition;
}

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

test('contract sales tab only shows documents bound to the current contract', () => {
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

test('home company switching uses the custom switcher instead of a native action sheet', () => {
  const script = fs.readFileSync(path.join(__dirname, '..', 'pages', 'index', 'index.js'), 'utf8');
  const template = fs.readFileSync(path.join(__dirname, '..', 'pages', 'index', 'index.wxml'), 'utf8');
  assert.ok(script.includes('showCompanySwitcher: true'));
  assert.ok(!script.includes('wx.showActionSheet'));
  assert.ok(template.includes('company-switch-sheet'));
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
