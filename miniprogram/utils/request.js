const app = getApp();

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app.globalData.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        Authorization: app.globalData.token || wx.getStorageSync('tradepass_token') || '',
        'content-type': 'application/json'
      },
      success: ({ data }) => {
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

module.exports = {
  request
};
