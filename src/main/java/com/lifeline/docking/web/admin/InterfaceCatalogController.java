package com.lifeline.docking.web.admin;

import com.lifeline.docking.catalog.ApiCatalog;
import com.lifeline.docking.catalog.ApiCatalogEntry;
import com.lifeline.docking.service.DebugService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口清单查询（调试台用）。全部 19 个条目：1 个令牌接口 + 18 个业务操作。
 */
@RestController
@RequestMapping("/admin/api/interfaces")
@ConditionalOnProperty(name = "lifeline.debug.enabled", havingValue = "true", matchIfMissing = true)
public class InterfaceCatalogController {

    private final ApiCatalog catalog;
    private final DebugService debugService;

    public InterfaceCatalogController(ApiCatalog catalog, DebugService debugService) {
        this.catalog = catalog;
        this.debugService = debugService;
    }

    @GetMapping
    public Map<String, Object> list() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", catalog.getSource());
        body.put("generatedAt", catalog.getGeneratedAt());
        body.put("total", catalog.all().size());
        body.put("businessCount", catalog.business().size());
        body.put("entries", catalog.all());
        return body;
    }

    @GetMapping("/{seq}")
    public ResponseEntity<ApiCatalogEntry> detail(@PathVariable int seq) {
        return catalog.bySeq(seq).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 按字段 Schema 生成可编辑的报文模板。 */
    @GetMapping("/{seq}/sample")
    public ResponseEntity<Map<String, Object>> sample(@PathVariable int seq,
                                                      @RequestParam(defaultValue = "1") int count) {
        return catalog.bySeq(seq).map(entry -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("entry", debugService.describe(entry));
            body.put("body", debugService.buildSampleBody(entry, count));
            body.put("warning", "示例值为占位数据，发送前必须替换为真实测试数据");
            return ResponseEntity.ok(body);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
