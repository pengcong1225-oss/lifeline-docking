package com.lifeline.docking.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCatalogTest {

    private final ApiCatalog catalog = new ApiCatalog(new ObjectMapper());

    @Test
    void loadsAllNineteenEntries() {
        assertEquals(19, catalog.all().size(), "1 个令牌接口 + 18 个业务操作");
        assertEquals(18, catalog.business().size());
    }

    @Test
    void tokenEntryIsIdentified() {
        long tokenCount = catalog.all().stream().filter(ApiCatalogEntry::isToken).count();
        assertEquals(1, tokenCount);
        assertEquals("/data-docking-api/token", catalog.bySeq(1).orElseThrow().getPath());
    }

    @Test
    void everyBusinessEntrySharesTheSameExecutePath() {
        for (ApiCatalogEntry entry : catalog.business()) {
            assertEquals("/data-docking-api/execute/api", entry.getPath(),
                    "seq " + entry.getSeq() + " 的路径与文档不符");
            assertNotNull(entry.getApiCmd());
            assertNotNull(entry.getTag());
        }
    }

    @Test
    void routeLookupDistinguishesSameTagWithDifferentOperationType() {
        // inspector 的保存与删除共用 tag，只能靠 operationType 区分
        assertTrue(catalog.byRoute("inspection_third_party_data_access", "inspector", "I").isPresent());
        assertTrue(catalog.byRoute("inspection_third_party_data_access", "inspector", "D").isPresent());
        assertNotEqualsSeq(
                catalog.byRoute("inspection_third_party_data_access", "inspector", "I").orElseThrow().getSeq(),
                catalog.byRoute("inspection_third_party_data_access", "inspector", "D").orElseThrow().getSeq());
    }

    private static void assertNotEqualsSeq(int a, int b) {
        assertTrue(a != b, "两个路由应指向不同条目，实际同为 " + a);
    }

    @Test
    void duplicateRiskRouteIsAKnownDocumentationConflict() {
        // 条目 4（归类“用户信息”）与条目 18（归类“第三方巡检隐患管理”）的路由键完全相同：
        // inspection_third_party_data_access / risk / I，且两者的请求模型高度重合。
        // 源文档没有说明二者区别，这里把冲突固化成测试，提醒联调时必须向平台确认真实用途。
        List<ApiCatalogEntry> riskEntries = catalog.business().stream()
                .filter(e -> "risk".equals(e.getTag()))
                .toList();
        assertEquals(2, riskEntries.size(), "risk 应有两个条目");
        assertEquals(1, riskEntries.stream().map(ApiCatalogEntry::getApiCmd).distinct().count(),
                "两条 risk 目前属于同一 apiCmd —— 这正是文档冲突所在");
        assertEquals(2, riskEntries.stream().map(ApiCatalogEntry::getSeq).distinct().count(),
                "两条 risk 是不同的条目");

        // byRoute 只能命中其中一个，业务上无法区分这两类数据。
        assertTrue(catalog.byRoute("inspection_third_party_data_access", "risk", "I").isPresent());
    }

    @Test
    void apiCmdSpellingIsPreservedFromSourceDocument() {
        // 源文档写作 lifeline_data_batch_acces（少一个 s），不能擅自改正
        assertTrue(catalog.business().stream()
                .anyMatch(e -> "lifeline_data_batch_acces".equals(e.getApiCmd())));
        assertFalse(catalog.business().stream()
                .anyMatch(e -> "lifeline_data_batch_access".equals(e.getApiCmd())));
    }

    @Test
    void everyBusinessEntryHasDataFieldsAndSamples() {
        for (ApiCatalogEntry entry : catalog.business()) {
            assertFalse(entry.getDataFields().isEmpty(),
                    "seq " + entry.getSeq() + " 没有解析出数据字段");
            for (ApiField field : entry.getDataFields()) {
                assertNotNull(field.getName());
                assertNotNull(field.getSample(),
                        entry.getSeq() + " 的字段 " + field.getName() + " 缺少示例值，调试台会报错");
            }
        }
    }

    @Test
    void requiredFieldsAreMarked() {
        ApiCatalogEntry realtime = catalog.byRoute("lifeline_data_batch_acces", "ycrqqy_realtime", "").orElseThrow();
        assertTrue(realtime.getDataFields().stream()
                        .anyMatch(f -> "lsh".equals(f.getName()) && f.isRequired()),
                "lsh 应为必填");
    }

    @Test
    void unknownRouteReturnsEmpty() {
        assertTrue(catalog.byRoute("no_such_cmd", "no_such_tag", "").isEmpty());
    }
}
