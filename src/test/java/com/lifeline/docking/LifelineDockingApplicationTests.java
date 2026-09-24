package com.lifeline.docking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Spring 上下文加载测试：验证全部 bean 能正确装配、`schema.sql` 能执行。
 *
 * <p>本项目的上下文依赖 MySQL，而数据库口令按安全约定不写进仓库
 * （见 {@code lifeline-docking/docs/kb/08-部署与运行.md}）。
 * 因此数据库不可用时<b>跳过</b>本测试，而不是让整条构建失败；
 * 数据库可用时（设置好 {@code LIFELINE_DB_PASSWORD}）则真实执行。</p>
 */
@SpringBootTest
@EnabledIf("databaseAvailable")
class LifelineDockingApplicationTests {

    static boolean databaseAvailable() {
        String user = System.getenv().getOrDefault("LIFELINE_DB_USER", "root");
        String password = System.getenv().getOrDefault("LIFELINE_DB_PASSWORD", "");
        String url = System.getenv().getOrDefault("LIFELINE_DB_URL",
                "jdbc:mysql://localhost:3306/lifeline_docking"
                        + "?createDatabaseIfNotExist=true&useSSL=false"
                        + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (Exception e) {
            System.out.println("[跳过] 数据库不可用，跳过 Spring 上下文加载测试。");
            System.out.println("        设置 LIFELINE_DB_PASSWORD 后本测试会真实执行。原因：" + e.getMessage());
            return false;
        }
    }

    @Test
    void contextLoads() {
    }
}
