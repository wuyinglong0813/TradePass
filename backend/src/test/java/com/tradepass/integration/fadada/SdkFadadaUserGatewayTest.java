package com.tradepass.integration.fadada;

import com.fasc.open.api.v5_1.req.user.GetUserReq;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SdkFadadaUserGatewayTest {

    @Test
    void queriesProviderWithOnlyOneUserIdentifier() {
        GetUserReq verifiedRequest = new GetUserReq();
        SdkFadadaUserGateway.applyUserLookup(
                verifiedRequest, "tradepass-user-8", "open-user-8");
        assertThat(verifiedRequest.getOpenUserId()).isEqualTo("open-user-8");
        assertThat(verifiedRequest.getClientUserId()).isNull();

        GetUserReq pendingRequest = new GetUserReq();
        SdkFadadaUserGateway.applyUserLookup(
                pendingRequest, "tradepass-user-8", null);
        assertThat(pendingRequest.getClientUserId()).isEqualTo("tradepass-user-8");
        assertThat(pendingRequest.getOpenUserId()).isNull();
    }
}
