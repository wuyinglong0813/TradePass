const { request, clearSession } = require('./utils/request');

// 本地后端地址（开发者工具模拟器用）
const LOCAL_API = 'http://127.0.0.1:9999/api';
// 云托管公网访问地址（真机/体验版/正式版用）
const CLOUD_API = 'https://tradepass-274155-4-1446724178.sh.run.tcloudbase.com/api';

function isLocalDevelopment() {
  try {
    if (wx.getSystemInfoSync().platform === 'devtools') return true;
  } catch (e) {}
  try {
    const account = wx.getAccountInfoSync();
    return account && account.miniProgram && account.miniProgram.envVersion === 'develop';
  } catch (e) {
    return false;
  }
}

const localDevelopment = isLocalDevelopment();

App({
  globalData: {
    baseUrl: localDevelopment ? LOCAL_API : CLOUD_API,
    isLocalDevelopment: localDevelopment,
    token: '',
    currentCompanyId: '',
    userInfo: null,
    memberInfo: null,
    companies: [],
    pendingInvite: null
  },

  onLaunch() {
    const token = wx.getStorageSync('tradepass_token');
    const companyId = wx.getStorageSync('tradepass_company_id');
    if (companyId) this.globalData.currentCompanyId = String(companyId);
    if (token) this.globalData.token = token;
    this._sessionReady = token ? this.loadMe().catch(() => null) : Promise.resolve(null);
  },

  ensureSessionReady() {
    return this._sessionReady || Promise.resolve(null);
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
    this.setCurrentCompany(cid || '');
    return payload;
  },

  async establishSession(session) {
    if (!session || !session.token) throw new Error('登录响应缺少会话信息');
    this.globalData.token = session.token;
    wx.setStorageSync('tradepass_token', session.token);
    const cid = session.user && session.user.currentCompanyId;
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
      wx.reLaunch({ url: '/pages/login/login' });
    }
  },

  doLogin() {
    wx.showLoading({ title: '登录中...' });
    wx.login({
      success: async ({ code }) => {
        try {
          const session = await request({
            url: '/auth/wechat-login',
            method: 'POST',
            data: { code },
            auth: false,
            withCompany: false
          });
          await this.establishSession(session);
          wx.showToast({ title: '登录成功', icon: 'success' });
        } catch (error) {
          wx.showToast({ title: error.message || '登录失败', icon: 'none' });
        } finally {
          wx.hideLoading();
        }
      },
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
