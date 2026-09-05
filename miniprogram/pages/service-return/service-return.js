const { request } = require('../../utils/request');

Page({
  data: {
    title: '正在确认处理结果',
    message: '请稍候，正在同步最新状态',
    loading: true,
    failed: false,
    options: {}
  },

  onLoad(options) {
    this.setData({ options });
    this.syncResult();
  },

  async syncResult() {
    if (this._syncing) return;
    this._syncing = true;
    this.setData({ loading: true });
    const options = this.data.options || {};
    try {
      let result;
      if (options.scene === 'personal') {
        result = await this.readAuthenticationResult('/fadada/users/me/identity', options.scene);
      } else if (options.scene === 'company' || options.scene === 'seal') {
        result = await this.readAuthenticationResult(
          `/fadada/companies/${options.companyId}/identity`, options.scene);
      } else if (options.scene === 'contract' || options.scene === 'abolish') {
        result = await request({ url: `/contracts/${options.contractId}/signing/sync`, method: 'POST' });
      }
      if (['personal', 'company', 'seal'].includes(options.scene)
          && !this.authenticationCompleted(result, options.scene)) {
        this.setData({ loading: false, failed: true,
          title: result && result.status === 'FAILED' ? '认证未通过' : '处理结果待确认',
          message: (result && (result.failureReason || result.statusText)) || '结果尚未更新，请稍后刷新' });
        return;
      }
      this.setData({
        loading: false,
        failed: false,
        title: '处理结果已同步',
        message: (result && (result.statusText || result.status)) || '你可以返回业务页面继续操作'
      });
      setTimeout(() => this.goBusinessPage(), 700);
    } catch (error) {
      this.setData({
        loading: false,
        failed: true,
        title: '结果同步暂未完成',
        message: error.message || '稍后返回业务页面刷新即可'
      });
    } finally {
      this._syncing = false;
    }
  },

  authenticationCompleted(result, scene) {
    return scene === 'seal'
      ? Number(result && result.enabledSealCount || 0) > 0
      : !!result && result.status === 'VERIFIED';
  },

  async readAuthenticationResult(url, scene) {
    const options = { url, withCompany: scene !== 'personal' };
    const current = await request(options);
    if (this.authenticationCompleted(current, scene)) return current;
    try {
      return await request({ ...options, url: `${url}/sync`, method: 'POST' });
    } catch (error) {
      // A callback or another request may have committed success while this sync failed.
      try {
        const latest = await request(options);
        if (this.authenticationCompleted(latest, scene)) return latest;
      } catch (readError) {}
      throw error;
    }
  },

  goBusinessPage() {
    const options = this.data.options || {};
    if (options.scene === 'personal') {
      wx.redirectTo({ url: '/pages/personal-cert/personal-cert' });
      return;
    }
    if (options.scene === 'company' || options.scene === 'seal') {
      wx.redirectTo({ url: '/pages/company-cert/company-cert' });
      return;
    }
    if (options.contractId) {
      wx.redirectTo({ url: `/pages/contract-preview/contract-preview?contractId=${options.contractId}` });
      return;
    }
    wx.switchTab({ url: '/pages/index/index' });
  }
});
