package com.lifeline.docking.service;

import com.lifeline.docking.catalog.ApiCatalogEntry;
import com.lifeline.docking.dto.IngestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调试台的报文构造与发送。
 *
 * <p>调试发送走的是与调用者推送<b>完全相同</b>的处理链路（留存 → 入库 → 转发），
 * 因此调试即联调，不会绕过留存或成功判定。</p>
 */
@Service
public class DebugService {

    private final ObjectMapper objectMapper;
    private final IngestService ingestService;

    public DebugService(ObjectMapper objectMapper, IngestService ingestService) {
        this.objectMapper = objectMapper;
        this.ingestService = ingestService;
    }

    /**
     * 按接口清单生成请求报文模板。
     *
     * <p>示例值只用于占位，来自源文档说明；发送前必须人工确认为真实测试数据，
     * 否则等于向老平台推送伪造数据。</p>
     */
    public String buildSampleBody(ApiCatalogEntry entry, int count) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("apiCmd", entry.getApiCmd());
        ObjectNode apiBody = root.putObject("apiBody");
        apiBody.put("tag", entry.getTag());
        if (entry.getOperationType() != null && !entry.getOperationType().isBlank()) {
            apiBody.put("operationType", entry.getOperationType());
        }
        ArrayNode data = apiBody.putArray("data");
        int n = Math.max(1, Math.min(count, 100));
        for (int i = 0; i < n; i++) {
            ObjectNode item = data.addObject();
            for (var field : entry.getDataFields()) {
                putSample(item, field.getName(), field.getSample(), field.getType(), i);
            }
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("生成报文模板失败", e);
        }
    }

    private void putSample(ObjectNode item, String name, Object sample, String type, int index) {
        if (sample == null) {
            item.putNull(name);
            return;
        }
        String text = String.valueOf(sample);
        // 同一批次内让流水号不重复，避免调试时被自身唯一键拦住。
        if ("lsh".equals(name) && index > 0) {
            text = text + "0" + index;
        }
        if ("integer".equals(type) || "int32".equals(type)) {
            try {
                item.put(name, Integer.parseInt(text));
                return;
            } catch (NumberFormatException ignored) {
                item.put(name, 0);
                return;
            }
        }
        if ("number".equals(type)) {
            try {
                item.put(name, Double.parseDouble(text));
                return;
            } catch (NumberFormatException ignored) {
                item.put(name, 0);
                return;
            }
        }
        item.put(name, text);
    }

    /**
     * 直接发送一段报文（调试台用）。
     *
     * @param accessKey 使用哪个已登记凭证的老令牌转发
     */
    public IngestResult send(String accessKey, String rawBody) {
        return ingestService.ingest(accessKey, rawBody, "DEBUG");
    }

    /** 便于前端展示的字段摘要。 */
    public Map<String, Object> describe(ApiCatalogEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("seq", entry.getSeq());
        map.put("category", entry.getCategory());
        map.put("name", entry.getName());
        map.put("apiCmd", entry.getApiCmd());
        map.put("tag", entry.getTag());
        map.put("operationType", entry.getOperationType());
        map.put("routeLabel", entry.getRouteLabel());
        map.put("fieldCount", entry.getDataFields().size());
        return map;
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
