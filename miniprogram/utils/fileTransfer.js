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

function appendBase64File(filePath, contentBase64, overwrite) {
  return new Promise((resolve, reject) => {
    if (!filePath || !contentBase64) {
      reject(new Error('文件内容为空'));
      return;
    }
    const options = {
      filePath,
      data: contentBase64,
      encoding: 'base64',
      success: () => resolve(filePath),
      fail: error => reject(new Error((error && error.errMsg) || '文件保存失败'))
    };
    const fileSystem = wx.getFileSystemManager();
    if (overwrite) fileSystem.writeFile(options);
    else fileSystem.appendFile(options);
  });
}

async function downloadChunkedApiFile(url, filePath, expectedSize, chunkSize = 512 * 1024) {
  const safeChunkSize = Math.max(1, Math.min(Number(chunkSize) || 0, 512 * 1024));
  const declaredSize = Number(expectedSize || 0);
  let offset = 0;
  let totalSize = declaredSize;
  let firstChunk = true;

  while (firstChunk || offset < totalSize) {
    const separator = url.includes('?') ? '&' : '?';
    const payload = await request({
      url: `${url}${separator}offset=${offset}&size=${safeChunkSize}`,
      timeout: 60000
    });
    const chunkOffset = Number(payload && payload.offset);
    const chunkLength = Number(payload && payload.length);
    const responseTotalSize = Number(payload && payload.totalSize);
    if (!payload || chunkOffset !== offset || !Number.isFinite(chunkLength)
      || chunkLength <= 0 || !Number.isFinite(responseTotalSize)
      || responseTotalSize <= 0 || offset + chunkLength > responseTotalSize) {
      throw new Error('文件分片数据异常，请重试');
    }
    await appendBase64File(filePath, payload.contentBase64, firstChunk);
    offset += chunkLength;
    totalSize = responseTotalSize;
    firstChunk = false;
    if (payload.eof === true) break;
  }

  if (offset !== totalSize || (declaredSize > 0 && declaredSize !== totalSize)) {
    throw new Error('文件下载不完整，请重试');
  }
  return {
    filePath,
    fileName: '',
    contentType: '',
    fileSize: totalSize
  };
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
  downloadChunkedApiFile,
  uploadMultipartApiFile,
  writeBase64File
};
