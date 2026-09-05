const { request, clearSession } = require('./utils/request');
const { USER_ID_KEY } = require('./utils/homeSnapshot');

// 本地后端地址（开发者工具模拟器用）
const LOCAL_API = 'http://127.0.0.1:9999/api';
// 生产文件上传地址；需在小程序后台配置 uploadFile 合法域名。
// 普通业务请求仍通过下方云环境和服务名调用 callContainer。
const CLOUD_API = 'https://sqt.org.cn/api';
// 云托管 callContainer 配置；生产请求经微信链路自动注入可信用户信息。
const CLOUD_ENV = 'prod-d7g9zrn5s7e6aab68';
const CLOUD_SERVICE = 'tradepass';

function isLocalDevelopment() {
  try {
    return wx.getSystemInfoSync().platform === 'devtools';
  } catch (e) {}
  return false;
}

const localDevelopment = isLocalDevelopment();

function isDesktopWechat() {
  try {
    const platform = wx.getSystemInfoSync().platform;
    return platform === 'windows' || platform === 'mac';
  } catch (e) {}
  return false;
}

const desktopWechat = isDesktopWechat();

App({
  globalData: {
    baseUrl: localDevelopment ? LOCAL_API : CLOUD_API,
    isLocalDevelopment: localDevelopment,
    isDesktopWechat: desktopWechat,
    cloudEnv: CLOUD_ENV,
    cloudService: CLOUD_SERVICE,
    token: '',
    currentCompanyId: '',
    userInfo: null,
    memberInfo: null,
    companies: [],
    pendingInvite: null,
    activeTabIndex: null,
    tabBarTransition: null,
    tabBarHidden: false
  },

  onLaunch() {
    if (!localDevelopment && wx.cloud) {
      wx.cloud.init({ env: CLOUD_ENV, traceUser: true });
    }
    this.restoreStoredSession();
    this._sessionReady = this.globalData.token
      ? this.refreshSession() : Promise.resolve(null);
  },

  onShow() {
    this.restoreStoredSession();
    if (this.globalData.token) this._sessionReady = this.refreshSession();
  },

  restoreStoredSession() {
    const token = wx.getStorageSync('tradepass_token');
    const companyId = wx.getStorageSync('tradepass_company_id');
    if (companyId) this.globalData.currentCompanyId = String(companyId);
    if (token) this.globalData.token = token;
  },

  ensureSessionReady() {
    this.restoreStoredSession();
    if (!this.globalData.token) return Promise.resolve(null);
    if (this._sessionRefresh) return this._sessionRefresh;
    if (this.globalData.userInfo) return Promise.resolve(this.globalData.userInfo);
    return this.refreshSession();
  },

  refreshSession() {
    if (!this.globalData.token) return Promise.resolve(null);
    if (this._sessionRefresh) return this._sessionRefresh;
    const refresh = Promise.resolve()
      .then(() => this.loadMe())
      .catch(() => null)
      .finally(() => {
        if (this._sessionRefresh === refresh) this._sessionRefresh = null;
      });
    this._sessionRefresh = refresh;
    this._sessionReady = refresh;
    return refresh;
  },

  getCurrentCompanyId() {
    return this.globalData.currentCompanyId
      || (this.globalData.userInfo && this.globalData.userInfo.currentCompanyId)
      || '';
  },

  setCurrentCompany(companyId) {
    const cid = companyId ? String(companyId) : '';
    this.globalData.currentCompanyId = cid;
    if (cid) wx.setStorageSync('tradepass_company_id', cid);
    else wx.removeStorageSync('tradepass_company_id');
  },

  applyMePayload(payload) {
    if (!payload) return null;
    this.globalData.userInfo = payload.user || null;
    this.globalData.memberInfo = payload.member || null;
    this.globalData.companies = payload.companies || [];
    const cid = payload.user && payload.user.currentCompanyId;
    const userId = payload.user && payload.user.id;
    if (userId) wx.setStorageSync(USER_ID_KEY, String(userId));
    this.setCurrentCompany(cid || '');
    return payload;
  },

  async establishSession(session) {
    if (!session || !session.token) throw new Error('登录响应缺少会话信息');
    this.globalData.token = session.token;
    wx.setStorageSync('tradepass_token', session.token);
    const cid = session.user && session.user.currentCompanyId;
    const userId = session.user && session.user.id;
    if (userId) wx.setStorageSync(USER_ID_KEY, String(userId));
    if (cid) this.setCurrentCompany(cid);
    this._sessionReady = this.loadMe();
    await this._sessionReady;
    return session;
  },

  async logout() {
    const hasToken = !!this.globalData.token;
    try {
      if (hasToken) {
        await request({ url: '/auth/logout', method: 'POST', handleUnauthorized: false });
      }
    } catch (e) {
      // 即使服务端暂时不可达，也必须清理本地会话。
    } finally {
      clearSession(this);
      this._sessionReady = Promise.resolve(null);
      this._sessionRefresh = null;
      wx.reLaunch({ url: '/pages/index/index' });
    }
  },

  doLogin() {
    wx.showLoading({ title: '登录中...' });
    const login = data => request({
      url: '/auth/wechat-login',
      method: 'POST',
      data,
      auth: false,
      withCompany: false
    }).then(session => this.establishSession(session))
      .then(() => wx.showToast({ title: '登录成功', icon: 'success' }))
      .catch(error => wx.showToast({ title: error.message || '登录失败', icon: 'none' }))
      .finally(() => wx.hideLoading());

    if (!localDevelopment) {
      login({});
      return;
    }
    wx.login({
      success: ({ code }) => login({ code }),
      fail: () => {
        wx.hideLoading();
        wx.showToast({ title: '微信登录失败', icon: 'none' });
      }
    });
  },

  async loadMe() {
    if (!this.globalData.token) return null;
    const payload = await request({ url: '/me' });
    return this.applyMePayload(payload);
  },

  async switchCompany(companyId) {
    const payload = await request({
      url: '/me/switch-company',
      method: 'POST',
      data: { companyId },
      companyId
    });
    return this.applyMePayload(payload);
  },

  async switchUser(userId) {
    const session = await request({
      url: '/dev/switch-user',
      method: 'POST',
      data: { userId },
      withCompany: false
    });
    return this.establishSession(session);
  }
});
