package com.lifeline.docking;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库构建产物必须随包发布，否则 {@code /kb/} 会 404。
 *
 * <p>知识库源文件在 {@code docs/kb/}，由 {@code tools/build-kb.mjs} 生成到
 * {@code src/main/resources/static/kb/index.html}。这里锁定"构建产物存在且内容完整"。</p>
 */
class KnowledgeBaseTest {

    @Test
    void knowledgeBasePageIsPackaged() throws Exception {
        ClassPathResource resource = new ClassPathResource("static/kb/index.html");
        assertTrue(resource.exists(), "缺少 static/kb/index.html，请先运行 node tools/build-kb.mjs");

        String html;
        try (InputStream in = resource.getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(html.contains("知识库"), "知识库页面标题缺失");
    }

    @Test
    void knowledgeBaseCoversAllTopics() throws Exception {
        String html;
        try (InputStream in = new ClassPathResource("static/kb/index.html").getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // 12 个主题：索引 + 10 个编号主题 + FAQ
        for (String topic : new String[]{"接入总览", "接口清单", "路由规则与陷阱", "公共字段约定",
                "数据字典", "平台架构", "数据库设计", "部署与运行", "联调阻塞项", "验证记录", "FAQ"}) {
            assertTrue(html.contains(topic), "知识库缺少主题：" + topic);
        }
        assertNotNull(html);
    }

    @Test
    void interfaceCatalogIsPackaged() {
        assertTrue(new ClassPathResource("docking-api-catalog.json").exists(),
                "缺少 docking-api-catalog.json，请先运行 node tools/build-catalog.mjs");
    }
}
