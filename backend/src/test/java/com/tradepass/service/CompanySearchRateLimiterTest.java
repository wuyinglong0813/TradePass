package com.tradepass.service;

import com.tradepass.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanySearchRateLimiterTest {
    @Test
    void limitsEachAuthenticatedUserIndependently() {
        CompanySearchRateLimiter limiter = new CompanySearchRateLimiter(2);

        assertThatCode(() -> limiter.check(7L)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("企业搜索过于频繁，请稍后再试");
        assertThatCode(() -> limiter.check(8L)).doesNotThrowAnyException();
    }
}
