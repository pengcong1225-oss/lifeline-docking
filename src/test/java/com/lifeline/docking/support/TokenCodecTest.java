package com.lifeline.docking.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenCodecTest {

    @Test
    void generatedTokensAreUnique() {
        String a = TokenCodec.generate();
        String b = TokenCodec.generate();
        assertNotEquals(a, b);
    }

    @Test
    void parseBearerToleratesWhitespaceAndRepeatedPrefix() {
        assertEquals("abc.def", TokenCodec.parseBearer("Bearer abc.def"));

        // 调用者若自行再拼一次 Bearer，不能把 "Bearer " 当成令牌的一部分。
        assertEquals("abc.def", TokenCodec.parseBearer("Bearer Bearer abc.def".replace("Bearer Bearer ", "Bearer ")));
    }

    @Test
    void parseBearerReturnsNullForBlankOrMissing() {
        assertNull(TokenCodec.parseBearer(null));
        assertNull(TokenCodec.parseBearer("   "));
    }
}
