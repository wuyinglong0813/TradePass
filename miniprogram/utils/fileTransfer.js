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

function readBase64File(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().readFile({
      filePath,
      encoding: 'base64',
      success: result => resolve(result.data),
      fail: error => reject(new Error((error && error.errMsg) || '文件读取失败'))
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

async function uploadApiFile(url, filePath, data = {}) {
  const contentBase64 = await readBase64File(filePath);
  return request({
    url,
    method: 'POST',
    data: { ...data, contentBase64 },
    timeout: 60000
  });
}

module.exports = {
  downloadApiFile,
  uploadApiFile,
  readBase64File,
  writeBase64File
};
