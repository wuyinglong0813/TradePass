const { request } = require('./request');

function writeBase64File(filePath, contentBase64) {
  return new Promise((resolve, reject) => {
    if (!filePath || !contentBase64) {
      reject(new Error('文件内容为空'));
      return;
    }
    wx.getFileSystemManager().writeFile({
      filePath,
      data: contentBase64,
      encoding: 'base64',
      success: () => resolve(filePath),
      fail: error => reject(new Error((error && error.errMsg) || '文件保存失败'))
    });
  });
}

async function downloadApiFile(url, filePath) {
  const payload = await request({ url, timeout: 60000 });
  await writeBase64File(filePath, payload && payload.contentBase64);
  return {
    filePath,
    fileName: (payload && payload.fileName) || '',
    contentType: (payload && payload.contentType) || ''
  };
}

function downloadBinaryApiFile(url, filePath) {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    wx.downloadFile({
      url: `${app.globalData.baseUrl}${url}`,
      filePath,
      header,
      timeout: 60000,
      success: result => {
        if (result.statusCode >= 200 && result.statusCode < 300) {
          resolve({ filePath: result.filePath || result.tempFilePath || filePath });
          return;
        }
        reject(new Error(result.statusCode === 401
          ? '登录已失效' : `文件下载失败（${result.statusCode || '未知状态'}）`));
      },
      fail: error => reject(new Error((error && error.errMsg) || '文件下载失败'))
    });
  });
}

function uploadMultipartApiFile(url, filePath, data = {}, fileFieldName = 'file') {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const token = app.globalData.token || wx.getStorageSync('tradepass_token') || '';
    const companyId = app.globalData.currentCompanyId || '';
    const header = {};
    if (token) header.Authorization = token;
    if (companyId) header['X-Company-Id'] = String(companyId);
    const formData = Object.entries(data || {}).reduce((result, [key, value]) => {
      if (value !== undefined && value !== null) result[key] = String(value);
      return result;
    }, {});

    wx.uploadFile({
      url: `${app.globalData.baseUrl}${url}`,
      filePath,
      name: fileFieldName,
      formData,
      header,
      timeout: 60000,
      success: ({ statusCode, data: rawData }) => {
        let payload = null;
        try {
          payload = typeof rawData === 'string' ? JSON.parse(rawData) : rawData;
        } catch (error) {
          reject(new Error('上传响应格式不正确'));
          return;
        }
        if (statusCode === 401 || (payload && payload.code === 401)) {
          reject(new Error('登录已失效'));
          return;
        }
        if (statusCode >= 200 && statusCode < 300 && payload && payload.code === 0) {
          resolve(payload.data);
          return;
        }
        reject(new Error((payload && payload.message) || `上传失败（${statusCode || '未知状态'}）`));
      },
      fail: error => reject(new Error((error && error.errMsg) || '文件上传失败'))
    });
  });
}

module.exports = {
  downloadApiFile,
  downloadBinaryApiFile,
  uploadMultipartApiFile,
  writeBase64File
};
