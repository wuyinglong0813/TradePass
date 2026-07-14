// 本地后端地址（开发者工具模拟器用）
const LOCAL_API = 'http://127.0.0.1:9999/api';
// 云托管公网访问地址（真机/体验版/正式版用），请替换成你云托管的实际域名
const CLOUD_API = 'https://tradepass-274155-4-1446724178.sh.run.tcloudbase.com/api';

// 开发者工具和开发版均优先使用本地后端。
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
    currentCompanyId: '',   // 当前操作企业，随请求头 X-Company-Id 上送
    userInfo: null,
    memberInfo: null,
    companies: [],
    pendingInvite: null  // 待处理的邀请 { code, type }
  },

  onLaunch() {
    console.info('TradePass API:', this.globalData.baseUrl);
    // 已同意隐私协议则直接进首页，否则由隐私页做 reLaunch
    if (wx.getStorageSync('privacy_agreed')) {
      // 清除隐私页的历史栈，直接用首页替换
      // 注意：这里不调用 reLaunch，因为隐私页才是 entry page，
      // 隐私页在 onLoad 中检测标记后会自行跳转。
    }

    const token = wx.getStorageSync('tradepass_token');
    const companyId = wx.getStorageSync('tradepass_company_id');
    if (companyId) this.globalData.currentCompanyId = companyId;
    if (token) {
      this.globalData.token = token;
      this.loadMe();
    }
  },

  /** 统一设置当前企业（同步内存+缓存，供请求头使用） */
  setCurrentCompany(companyId) {
    const cid = companyId ? String(companyId) : '';
    this.globalData.currentCompanyId = cid;
    if (cid) wx.setStorageSync('tradepass_company_id', cid);
    else wx.removeStorageSync('tradepass_company_id');
  },

  /** 退出登录：清理会话并回到登录页 */
  logout() {
    this.globalData.token = '';
    this.globalData.currentCompanyId = '';
    this.globalData.userInfo = null;
    this.globalData.memberInfo = null;
    this.globalData.companies = [];
    wx.removeStorageSync('tradepass_token');
    wx.removeStorageSync('tradepass_company_id');
    wx.reLaunch({ url: '/pages/login/login' });
  },

  doLogin() {
    wx.showLoading({ title: '登录中...' });
    wx.login({
      success: ({ code }) => {
        console.log('wx.login 成功, code:', code);
        wx.request({
          url: `${this.globalData.baseUrl}/auth/wechat-login`,
          method: 'POST',
          data: { code: code },
          success: ({ data }) => {
            wx.hideLoading();
            console.log('登录接口返回:', JSON.stringify(data));
            if (data && data.code === 0 && data.data && data.data.token) {
              this.globalData.token = data.data.token;
              wx.setStorageSync('tradepass_token', data.data.token);
              wx.showToast({ title: '登录成功', icon: 'success' });
              this.loadMe();
            } else {
              wx.showToast({ title: '登录失败: ' + (data && data.message || '未知'), icon: 'none' });
            }
          },
          fail: (err) => {
            wx.hideLoading();
            console.error('登录请求失败:', JSON.stringify(err));
            wx.showToast({ title: '网络错误，请重试', icon: 'none' });
          }
        });
      },
      fail: (err) => {
        wx.hideLoading();
        console.error('wx.login 失败:', JSON.stringify(err));
        wx.showToast({ title: '微信登录失败', icon: 'none' });
      }
    });
  },

  loadMe() {
    return new Promise((resolve) => {
      wx.request({
        url: `${this.globalData.baseUrl}/me`,
        method: 'GET',
        header: { Authorization: this.globalData.token },
        success: ({ data }) => {
          if (data && data.code === 0 && data.data) {
            this.globalData.userInfo = data.data.user;
            this.globalData.memberInfo = data.data.member;
            this.globalData.companies = data.data.companies || [];
            const cid = data.data.user && data.data.user.currentCompanyId;
            if (cid) this.setCurrentCompany(cid);
          }
          resolve();
        },
        fail: () => resolve()
      });
    });
  },

  /** 切换当前操作的企业 */
  async switchCompany(companyId) {
    // 先设置企业头，再请求 me，确保后端按目标企业返回
    this.setCurrentCompany(companyId);
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}/me/switch-company`,
        method: 'POST',
        data: { companyId },
        header: { Authorization: this.globalData.token, 'X-Company-Id': String(companyId) },
        success: ({ data }) => {
          if (data && data.code === 0 && data.data) {
            this.globalData.userInfo = data.data.user;
            this.globalData.memberInfo = data.data.member;
            this.globalData.companies = data.data.companies || [];
            const cid = data.data.user && data.data.user.currentCompanyId;
            if (cid) this.setCurrentCompany(cid);
            resolve(data.data);
          } else {
            reject(new Error('切换失败'));
          }
        },
        fail: reject
      });
    });
  },

  /** 切换 dev 用户：后端返回新 token，换 token 后刷新全局状态 */
  async switchUser(userId) {
    return new Promise((resolve, reject) => {
      wx.request({
        url: `${this.globalData.baseUrl}/dev/switch-user`,
        method: 'POST',
        data: { userId },
        header: { Authorization: this.globalData.token },
        success: ({ data }) => {
          if (data && data.code === 0 && data.data && data.data.token) {
            this.globalData.token = data.data.token;
            wx.setStorageSync('tradepass_token', data.data.token);
            const cid = data.data.user && data.data.user.currentCompanyId;
            this.setCurrentCompany(cid || '');
            this.loadMe().then(() => resolve(data.data));
          } else {
            reject(new Error('切换失败'));
          }
        },
        fail: reject
      });
    });
  }
});
