package com.lifeline.docking.client;

import com.lifeline.docking.config.DockingProperties;
import com.lifeline.docking.model.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OldPlatformClientTest {

    private final OldPlatformClient client = new OldPlatformClient(new DockingProperties());

    @Test
    void successCodeIsParsed() {
        assertEquals(ApiResponse.CODE_SUCCESS, client.parseCode("{\"code\":0,\"msg\":\"成功\",\"data\":true}"));
    }

    @Test
    void failureCodeIsParsed() {
        assertEquals(ApiResponse.CODE_FAILURE, client.parseCode("{\"code\":1,\"msg\":\"失败\"}"));
    }

    @Test
    void http200WithFailureCodeIsStillFailure() {
        // HTTP 200 不代表业务成功，只有 code=0 才算。
        String body = "{\"code\":1,\"msg\":\"参数错误\"}";
        assertEquals(ApiResponse.CODE_FAILURE, client.parseCode(body));
    }

    @Test
    void unparsableBodyIsTreatedAsUnknownRatherThanSuccess() {
        assertNull(client.parseCode(""));
        assertNull(client.parseCode("<html>502 Bad Gateway</html>"));
    }
}
