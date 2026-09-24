package com.lifeline.docking;

import com.lifeline.docking.config.DockingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 城市生命线数据上收开放平台（新平台）启动类。
 *
 * <p>第一版目标：调用者只替换接口地址，继续使用老平台分配的 accessKey/secretKey 与原请求协议；
 * 新平台从第一天起独立对外验签、签发自己的令牌，业务请求先可靠留存，再以调用者原凭证在内部
 * 取得老令牌并同步转发；只有老平台业务 {@code code=0} 才向调用者报告成功。</p>
 *
 * <p>后续按 apiCmd + tag + operationType 逐类接管业务，切换前须完成字段、成功判定、
 * 幂等、超时与回退能力核对。</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(DockingProperties.class)
public class LifelineDockingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifelineDockingApplication.class, args);
    }
}
