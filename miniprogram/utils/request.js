const app = getApp();

let redirecting = false;

function request(options) {
  return new Promise((resolve, reject) => {
    const header = {
      Authorization: app.globalData.token || wx.getStorageSync('tradepass_token') || '',
      'content-type': 'application/json'
    };
    const companyId = app.globalData.currentCompanyId || '';
    if (companyId) header['X-Company-Id'] = companyId;

    wx.request({
      url: `${app.globalData.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header,
      success: ({ statusCode, data }) => {
        // token 失效：清理并跳登录
        if (statusCode === 401 || (data && data.code === 401)) {
          handleUnauthorized();
          reject(new Error('登录已失效'));
          return;
        }
        if (data && data.code === 0) {
          resolve(data.data);
          return;
        }
        reject(new Error((data && data.message) || '请求失败'));
      },
      fail: reject
    });
  });
}

function handleUnauthorized() {
  if (redirecting) return;
  redirecting = true;
  app.globalData.token = '';
  app.globalData.currentCompanyId = '';
  wx.removeStorageSync('tradepass_token');
  wx.reLaunch({
    url: '/pages/login/login',
    complete: () => { setTimeout(() => { redirecting = false; }, 1000); }
  });
}

module.exports = {
  request
};
