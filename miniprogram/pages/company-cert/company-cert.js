const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    // 企业信息（从搜索页传入或从 me 加载）
    companyName: '',
    creditCode: '',
    legalPersonName: '',
    hasCompany: false,

    // 已有企业的认证步骤
    actions: [],
    certStatusText: '',

    // Step1：协议确认
    step: 'agreement',
    agreed: false,

    // Step2：认证方式
    authMethod: 'face',
    phone: '',
    smsCode: '',
    smsText: '获取验证码',
    smsCounting: false,
    submitting: false,
    isDev: false
  },

  onLoad(options) {
    this.setData({ isDev: !!app.globalData.isLocalDevelopment });
    if (options.name) {
      // 从搜索页跳来，新企业流程
      this.setData({
        hasCompany: false,
        step: 'agreement',
        companyName: decodeURIComponent(options.name),
        creditCode: decodeURIComponent(options.creditCode || ''),
        legalPersonName: decodeURIComponent(options.legalPersonName || '')
      });
    } else {
      // 已有企业，加载认证状态
      this.setData({ hasCompany: true });
      this.loadCompany();
    }
  },

  onShow() {
    if (this.data.hasCompany) this.loadCompany();
  },

  async loadCompany() {
    try {
      const me = await request({ url: '/me' });
      const company = me.company || {};
      const dict = require('../../utils/dict');
      const cert = dict.certification(company.certificationStatus || 'NOT_SUBMITTED');
      const realName = dict.step(company.realNameStatus || 'NOT_STARTED');
      const face = dict.step(company.faceStatus || 'NOT_STARTED');
      const seal = dict.step(company.sealStatus || 'NOT_UPLOADED');
      this.setData({
        companyName: company.name || '',
        legalPersonName: company.legalPersonName || '',
        certStatusText: cert.text,
        actions: [
          { key: 'company', title: '上传公司信息', desc: '营业执照等企业资质', done: company.certificationStatus === 'VERIFIED', statusText: cert.text, statusColor: cert.color },
          { key: 'realName', title: '实名认证', desc: '法人实名信息核验', done: company.realNameStatus === 'VERIFIED', statusText: realName.text, statusColor: realName.color },
          { key: 'face', title: '人脸录入', desc: '法人人脸信息采集', done: company.faceStatus === 'VERIFIED', statusText: face.text, statusColor: face.color },
          { key: 'seal', title: '上传电子章', desc: '企业电子印章上传', done: company.sealStatus === 'UPLOADED', statusText: seal.text, statusColor: seal.color }
        ]
      });
    } catch (e) {}
  },

  // ===== Step1：协议确认 =====
  toggleAgree() {
    this.setData({ agreed: !this.data.agreed });
  },

  openAgreement(e) {
    const type = e.currentTarget.dataset.type;
    if (type === 'privacy') {
      wx.navigateTo({ url: '/pages/privacy/privacy' });
      return;
    }
    wx.showToast({ title: '该协议内容待法务审核后开放', icon: 'none' });
  },

  goAgreeNext() {
    if (!this.data.agreed) {
      wx.showToast({ title: '请先阅读并同意相关协议', icon: 'none' });
      return;
    }
    this.setData({ step: 'auth' });
  },

  // ===== Step2：认证方式 =====
  selectAuthMethod(e) {
    this.setData({ authMethod: e.currentTarget.dataset.method });
  },

  onPhoneInput(e) { this.setData({ phone: e.detail.value }); },
  onSmsInput(e) { this.setData({ smsCode: e.detail.value }); },

  sendSms() {
    if (this.data.smsCounting) return;
    if (!this.data.phone) {
      wx.showToast({ title: '请先输入手机号', icon: 'none' });
      return;
    }
    if (!this.data.isDev) {
      wx.showToast({ title: '短信认证服务暂未接入', icon: 'none' });
      return;
    }
    wx.showToast({ title: '开发环境验证码已发送', icon: 'none' });
    this.setData({ smsCounting: true, smsText: '60s' });
    let sec = 60;
    const timer = setInterval(() => {
      sec--;
      if (sec <= 0) {
        clearInterval(timer);
        this.setData({ smsCounting: false, smsText: '获取验证码' });
      } else {
        this.setData({ smsText: sec + 's' });
      }
    }, 1000);
  },

  // 提交认证
  async submitAuth() {
    const { companyName, creditCode, legalPersonName, authMethod, phone, smsCode } = this.data;
    if (!this.data.isDev) {
      wx.showModal({
        title: '认证服务尚未接入',
        content: '生产环境不会模拟实名认证结果。请先配置实名或人脸认证服务商后再提交。',
        showCancel: false
      });
      return;
    }
    if (authMethod === 'phone' && (!phone || !smsCode)) {
      wx.showToast({ title: '请填写手机号和验证码', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    try {
      // 1. 创建/更新企业
      const created = await request({
        url: '/companies',
        method: 'POST',
        data: { name: companyName, creditCode, legalPersonName }
      });

      // 2. 绑定当前用户为法人
      await request({
        url: '/me/company',
        method: 'POST',
        data: {
          id: created.id,
          name: created.name,
          creditCode: created.creditCode,
          legalPersonName: created.legalPersonName
        }
      });

      // 3. 提交实名认证 + 人脸（根据选择的方式）
      await request({
        url: '/verifications/real-name',
        method: 'POST',
        data: { companyId: created.id }
      });
      if (authMethod === 'face') {
        await request({
          url: '/verifications/face',
          method: 'POST',
          data: { companyId: created.id }
        });
      }

      // 4. 刷新全局状态
      await app.loadMe();

      wx.showToast({ title: '认证已提交，绑定成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/company/company' });
      }, 1000);
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  // ===== 已有企业：点击其他认证步骤 =====
  async handleAction(e) {
    const key = e.currentTarget.dataset.key;
    if (!this.data.isDev) {
      wx.showModal({
        title: '能力尚未接入',
        content: '生产环境已关闭模拟认证和模拟文件上传，请配置对应服务商后再操作。',
        showCancel: false
      });
      return;
    }
    const companyId = app.getCurrentCompanyId();
    if (!companyId) {
      wx.showToast({ title: '请先选择企业', icon: 'none' });
      return;
    }
    try {
      if (key === 'face') {
        await request({ url: '/verifications/face', method: 'POST', data: { companyId } });
      } else if (key === 'seal') {
        await request({ url: '/seals', method: 'POST', data: { companyId, fileUrl: 'dev://demo-seal.png', usage: '合同签署' } });
      } else {
        await request({ url: `/companies/${companyId}/certifications`, method: 'POST', data: {} });
      }
      wx.showToast({ title: '已提交', icon: 'success' });
      this.loadCompany();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  }
});
