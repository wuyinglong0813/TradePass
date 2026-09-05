package com.tradepass.integration.fadada;

import com.fasc.open.api.v5_1.req.corp.GetCorpReq;
import com.tradepass.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SdkFadadaCompanyGatewayTest {
    @Test
    void sendsExactlyOneCompanyIdentifier() {
        GetCorpReq known = new GetCorpReq();
        SdkFadadaCompanyGateway.applyCompanyLookup(known, "local-1", "open-1");
        assertThat(known.getOpenCorpId()).isEqualTo("open-1");
        assertThat(known.getClientCorpId()).isNull();
        GetCorpReq pending = new GetCorpReq();
        SdkFadadaCompanyGateway.applyCompanyLookup(pending, "local-1", " ");
        assertThat(pending.getClientCorpId()).isEqualTo("local-1");
        assertThat(pending.getOpenCorpId()).isNull();
        assertThatThrownBy(() -> SdkFadadaCompanyGateway.applyCompanyLookup(new GetCorpReq(), null, null))
                .isInstanceOf(BusinessException.class);
    }
}
