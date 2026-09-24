package com.lifeline.docking.web.admin;

import com.lifeline.docking.dto.IngestResult;
import com.lifeline.docking.service.DebugService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调试台发送入口。
 *
 * <p>走与调用者推送完全相同的链路：留存 → 入库 → 用该凭证的老令牌转发老平台。
 * 不提供“跳过留存”或“直接转发”的旁路。</p>
 */
@RestController
@RequestMapping("/admin/api/debug")
@ConditionalOnProperty(name = "lifeline.debug.enabled", havingValue = "true", matchIfMissing = true)
public class DebugController {

    private final DebugService debugService;

    public DebugController(DebugService debugService) {
        this.debugService = debugService;
    }

    /**
     * @param accessKey 用哪个已登记凭证转发
     * @param body      完整业务报文（apiCmd + apiBody）
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody DebugSendRequest request) {
        if (request.accessKey() == null || request.accessKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请选择要使用的凭证"));
        }
        if (request.body() == null || request.body().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "报文不能为空"));
        }
        IngestResult result = debugService.send(request.accessKey(), request.body());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recordId", result.getRecordId());
        body.put("status", result.getStatus().name());
        body.put("message", result.getMessage());
        body.put("routeKnown", result.isRouteKnown());
        body.put("dataCount", result.getDataCount());
        body.put("storedCount", result.getStoredCount());
        body.put("duplicateCount", result.getDuplicateCount());
        body.put("response", result.getResponse());
        return ResponseEntity.ok(body);
    }

    /** 调试台发送入参。 */
    public record DebugSendRequest(String accessKey, String body) {
    }
}
