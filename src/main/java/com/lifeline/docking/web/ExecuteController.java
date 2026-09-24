package com.lifeline.docking.web;

import com.lifeline.docking.dto.IngestResult;
import com.lifeline.docking.model.ApiResponse;
import com.lifeline.docking.service.IngestService;
import com.lifeline.docking.support.TokenCodec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * {@code POST /data-docking-api/execute/api}
 *
 * <p>18 个业务操作共用本路径，靠 apiCmd + tag + operationType 区分。</p>
 *
 * <p>成功判定只看老平台业务 {@code code=0}；HTTP 200 与 {@code data=true} 都不代表成功。</p>
 */
@RestController
public class ExecuteController {

    private final TokenController tokenController;
    private final IngestService ingestService;

    public ExecuteController(TokenController tokenController, IngestService ingestService) {
        this.tokenController = tokenController;
        this.ingestService = ingestService;
    }

    @PostMapping(value = "/data-docking-api/execute/api", consumes = "application/json")
    public ResponseEntity<ApiResponse> execute(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) String rawBody) {

        String token = TokenCodec.parseBearer(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure("缺少 Bearer 令牌"));
        }
        Optional<String> accessKey = tokenController.resolveAccessKey(token);
        if (accessKey.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure("令牌无效或已过期"));
        }
        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("请求体为空"));
        }

        IngestResult result = ingestService.ingest(accessKey.get(), rawBody, "API");
        return switch (result.getStatus()) {
            // 与老平台一致：业务失败也返回 HTTP 200 + code=1，由调用者看 code。
            case ACCEPTED_OLD, REJECTED_OLD -> ResponseEntity.ok(result.getResponse());
            // 结果未知：明确告诉调用者未确认，不能伪装成功。
            case UNKNOWN -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result.getResponse());
            // 新平台自身拒绝。
            default -> ResponseEntity.badRequest().body(result.getResponse());
        };
    }
}
