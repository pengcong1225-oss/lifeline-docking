package com.lifeline.docking.support;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureSupportTest {

    @Test
    void sortedSignIsStableAndDeterministic() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timestamp", "1700000000");
        params.put("accessKey", "ak-test-0001");

        String a = SignatureSupport.signSorted(params, "sk-test-secret");
        String b = SignatureSupport.signSorted(params, "sk-test-secret");
        assertEquals(a, b, "同样的入参必须得到同样的签名");
        assertEquals(64, a.length(), "HMAC-SHA256 hex 应为 64 位");
    }

    @Test
    void paramOrderChangesSignatureSoItMustBeConfirmedWithRealSamples() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessKey", "ak-test-0001");
        params.put("timestamp", "1700000000");

        String sorted = SignatureSupport.signSorted(params, "sk-test-secret");
        String given = SignatureSupport.signInOrder(java.util.List.of("timestamp", "accessKey"), params, "sk-test-secret");
        assertNotEquals(sorted, given, "参数顺序影响签名：联调前必须用真实样本确认顺序");
    }

    @Test
    void matchesIgnoresSurroundingWhitespaceAndIsCaseSensitiveForHex() {
        String sig = SignatureSupport.signSorted(Map.of("a", "1"), "secret");
        assertTrue(SignatureSupport.matches(sig, "  " + sig + " "));
        assertFalse(SignatureSupport.matches(sig, sig.toUpperCase()));
        assertFalse(SignatureSupport.matches(sig, null));
    }
}
