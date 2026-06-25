const { request } = require('../../utils/request');

Page({
  data: {
    form: {
      name: '',
      creditCode: '',
      legalPersonName: ''
    },
    submitting: false
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field;
    this.setData({
      [`form.${field}`]: event.detail.value
    });
  },

  async submitBind() {
    const form = this.data.form;
    if (!form.name || !form.creditCode || !form.legalPersonName) {
      wx.showToast({ title: '请完整填写公司信息', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    try {
      const company = await request({
        url: '/companies',
        method: 'POST',
        data: form
      });

      await request({
        url: '/me/company',
        method: 'POST',
        data: company
      });

      wx.showToast({ title: '绑定成功', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 600);
    } catch (error) {
      wx.showToast({ title: error.message, icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  }
});
