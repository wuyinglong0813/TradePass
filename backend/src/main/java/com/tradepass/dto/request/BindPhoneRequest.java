package com.tradepass.dto.request;

public record BindPhoneRequest(String phone, String phoneCode) {
    public BindPhoneRequest(String phone) {
        this(phone, null);
    }
}
