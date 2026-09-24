package com.lifeline.docking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 新平台对接配置。
 *
 * <p>注意：{@code old-platform.base-url} 只是文档给出的测试地址，不是生产地址，
 * 也不应未经网络安全评估直接在生产沿用明文 HTTP。</p>
 */
@ConfigurationProperties(prefix = "lifeline")
public class DockingProperties {

    private final OldPlatform oldPlatform = new OldPlatform();
    private final Token token = new Token();
    private final Signature signature = new Signature();
    private final Crypto crypto = new Crypto();
    private final Debug debug = new Debug();

    public OldPlatform getOldPlatform() { return oldPlatform; }
    public Token getToken() { return token; }
    public Signature getSignature() { return signature; }
    public Crypto getCrypto() { return crypto; }
    public Debug getDebug() { return debug; }

    public static class OldPlatform {
        private String baseUrl;
        private String tokenPath = "/data-docking-api/token";
        private String executePath = "/data-docking-api/execute/api";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 10000;
        /**
         * 取老令牌时参数传递方式：query 或 form。
         * 接口页写 query，接入说明写 multipart/form-data，联调时用这个开关比对。
         */
        private String tokenParamStyle = "query";

        public String getTokenParamStyle() { return tokenParamStyle; }
        public void setTokenParamStyle(String tokenParamStyle) { this.tokenParamStyle = tokenParamStyle; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getTokenPath() { return tokenPath; }
        public void setTokenPath(String tokenPath) { this.tokenPath = tokenPath; }
        public String getExecutePath() { return executePath; }
        public void setExecutePath(String executePath) { this.executePath = executePath; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }

    public static class Token {
        private long ttlSeconds = 7200;
        private long oldTokenRefreshAheadSeconds = 120;
        /** 老令牌本地缓存时长（秒）。源文档未给出真实有效期，先按保守窗口缓存。 */
        private long oldTokenCacheSeconds = 1800;

        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public long getOldTokenRefreshAheadSeconds() { return oldTokenRefreshAheadSeconds; }
        public void setOldTokenRefreshAheadSeconds(long v) { this.oldTokenRefreshAheadSeconds = v; }
        public long getOldTokenCacheSeconds() { return oldTokenCacheSeconds; }
        public void setOldTokenCacheSeconds(long v) { this.oldTokenCacheSeconds = v; }
    }

    public static class Signature {
        /**
         * 签名原文的参数顺序策略。
         * <ul>
         *   <li>{@code sorted}：按参数名升序拼接（默认，仅便于联调比对）</li>
         *   <li>{@code given}：按文档书写顺序 accessKey,timestamp</li>
         * </ul>
         * 源文档未定义顺序，联调确认后固定为其中一种。
         */
        private String paramOrder = "sorted";
        private long timestampToleranceSeconds = 300;

        public String getParamOrder() { return paramOrder; }
        public void setParamOrder(String paramOrder) { this.paramOrder = paramOrder; }
        public long getTimestampToleranceSeconds() { return timestampToleranceSeconds; }
        public void setTimestampToleranceSeconds(long v) { this.timestampToleranceSeconds = v; }
    }

    public static class Crypto {
        /** Base64 编码的 32 字节 AES 主密钥。留空则使用临时密钥。 */
        private String masterKey = "";

        public String getMasterKey() { return masterKey; }
        public void setMasterKey(String masterKey) { this.masterKey = masterKey; }
    }

    public static class Debug {
        /** 调试台开关。生产应关闭。 */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
