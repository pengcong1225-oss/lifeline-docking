package com.lifeline.docking.service;

import com.lifeline.docking.catalog.ApiCatalog;
import com.lifeline.docking.client.OldPlatformClient;
import com.lifeline.docking.dto.IngestResult;
import com.lifeline.docking.entity.RequestRecordEntity;
import com.lifeline.docking.mapper.DataItemMapper;
import com.lifeline.docking.mapper.RequestRecordMapper;
import com.lifeline.docking.model.ApiRequest;
import com.lifeline.docking.model.ApiResponse;
import com.lifeline.docking.model.RequestStatus;
import com.lifeline.docking.model.RouteKey;
import com.lifeline.docking.router.RouteRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 业务请求上收主流程。
 *
 * <pre>
 * 验令牌（由控制器完成） → 识别调用者
 *   → 可靠留存原始请求（失败则绝不转发）
 *   → 数据项按 lsh 判重入库
 *   → 按 apiCmd + tag + operationType 选择处理方
 *       ├─ 已接管：新平台自行处理（当前未实现）
 *       └─ 未接管：用调用者老凭证内部取老令牌并同步转发
 *                 只有老平台 code=0 才报成功
 * </pre>
 */
@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final RequestRecordMapper recordMapper;
    private final DataItemMapper dataItemMapper;
    private final RouteRegistry routeRegistry;
    private final OldPlatformTokenService oldTokenService;
    private final OldPlatformClient oldPlatformClient;
    private final ApiCatalog catalog;
    private final ObjectMapper objectMapper;
    private final RecordPersister persister;

    public IngestService(RequestRecordMapper recordMapper,
                         DataItemMapper dataItemMapper,
                         RouteRegistry routeRegistry,
                         OldPlatformTokenService oldTokenService,
                         OldPlatformClient oldPlatformClient,
                         ApiCatalog catalog,
                         ObjectMapper objectMapper,
                         RecordPersister persister) {
        this.recordMapper = recordMapper;
        this.dataItemMapper = dataItemMapper;
        this.routeRegistry = routeRegistry;
        this.oldTokenService = oldTokenService;
        this.oldPlatformClient = oldPlatformClient;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
        this.persister = persister;
    }

    /**
     * @param accessKey 调用者（来自新平台令牌）
     * @param rawBody   原始请求体，原样留存与原样转发
     * @param source    API=调用者推送，DEBUG=调试台
     */
    public IngestResult ingest(String accessKey, String rawBody, String source) {
        ApiRequest request;
        try {
            request = objectMapper.readValue(rawBody, ApiRequest.class);
        } catch (Exception e) {
            // 报文非法：留存原文，新平台拒绝，不转发。
            RequestRecordEntity record = newRecord(accessKey, "", "", "", rawBody, source);
            record.setStatus(RequestStatus.REJECTED_LOCAL.name());
            record.setMessage("请求体不是合法 JSON，未转发老平台");
            safeInsert(record);
            return build(record, RequestStatus.REJECTED_LOCAL,
                    ApiResponse.failure("请求体不是合法 JSON"), false);
        }

        String apiCmd = request.getApiCmd() == null ? "" : request.getApiCmd().trim();
        String tag = (request.getApiBody() == null || request.getApiBody().getTag() == null)
                ? "" : request.getApiBody().getTag().trim();
        String operationType = request.getApiBody() == null || request.getApiBody().getOperationType() == null
                ? "" : request.getApiBody().getOperationType().trim();

        if (apiCmd.isEmpty() || tag.isEmpty()) {
            RequestRecordEntity record = newRecord(accessKey, apiCmd, tag, operationType, rawBody, source);
            record.setStatus(RequestStatus.REJECTED_LOCAL.name());
            record.setMessage("缺少 apiCmd 或 apiBody.tag，无法路由，未转发老平台");
            safeInsert(record);
            return build(record, RequestStatus.REJECTED_LOCAL,
                    ApiResponse.failure("apiCmd 与 apiBody.tag 不能为空"), false);
        }

        RouteKey routeKey = RouteKey.of(apiCmd, tag, operationType);
        boolean routeKnown = catalog.byRoute(apiCmd, tag, operationType).isPresent();
        List<Map<String, Object>> data = request.getApiBody() == null || request.getApiBody().getData() == null
                ? List.of() : request.getApiBody().getData();

        RequestRecordEntity record = newRecord(accessKey, apiCmd, tag, operationType, rawBody, source);
        record.setDataCount(data.size());
        record.setStatus(RequestStatus.RECEIVED.name());
        record.setMessage(routeKnown ? "已留存" : "已留存；该路由不在接口清单中，仍按原样转发");

        int stored = 0;
        int duplicates = 0;
        try {
            int[] counts = persister.persist(record, data);
            stored = counts[0];
            duplicates = counts[1];
        } catch (Exception e) {
            // 留存失败：绝不继续转发。
            log.error("请求留存失败 accessKey={} route={}", accessKey, routeKey, e);
            record.setStatus(RequestStatus.REJECTED_LOCAL.name());
            record.setMessage("留存失败，未转发：" + e.getMessage());
            safeInsert(record);
            return build(record, RequestStatus.REJECTED_LOCAL,
                    ApiResponse.failure("请求留存失败，未转发"), routeKnown);
        }
        record.setStoredCount(stored);
        record.setDuplicateCount(duplicates);
        update(record);

        // 已接管类别由新平台自行处理；第一版集合为空。
        if (routeRegistry.isTakenOver(routeKey)) {
            record.setStatus(RequestStatus.REJECTED_LOCAL.name());
            record.setMessage("该类别已标记接管，但自处理逻辑尚未实现");
            update(record);
            return build(record, RequestStatus.REJECTED_LOCAL,
                    ApiResponse.failure("该业务类别的自处理逻辑尚未实现"), routeKnown);
        }

        // 未接管：用调用者老凭证在内部取老令牌后同步转发。
        OldPlatformClient.TokenResult tokenResult = oldTokenService.getToken(accessKey);
        if (!tokenResult.ok()) {
            record.setStatus(RequestStatus.UNKNOWN.name());
            record.setMessage("取老平台令牌失败，未转发：" + tokenResult.message());
            update(record);
            return build(record, RequestStatus.UNKNOWN,
                    ApiResponse.failure("无法获取老平台令牌，本次业务未提交"), routeKnown);
        }

        OldPlatformClient.ForwardResult forward = oldPlatformClient.forward(tokenResult.token(), rawBody);
        if (!forward.httpReached()) {
            // 结果未知：不伪装成功，也不自动重放。
            record.setStatus(RequestStatus.UNKNOWN.name());
            record.setMessage(forward.message());
            record.setOldResponse(forward.rawBody());
            update(record);
            return build(record, RequestStatus.UNKNOWN,
                    ApiResponse.failure("老平台结果未知，请稍后核对"), routeKnown);
        }

        Integer code = oldPlatformClient.parseCode(forward.rawBody());
        record.setOldResponse(forward.rawBody());
        record.setOldPlatformCode(code);
        record.setForwardedAt(LocalDateTime.now());

        if (code == null) {
            record.setStatus(RequestStatus.UNKNOWN.name());
            record.setMessage("无法解析老平台业务码");
            update(record);
            return build(record, RequestStatus.UNKNOWN,
                    ApiResponse.failure("无法解析老平台响应"), routeKnown);
        }

        if (code != ApiResponse.CODE_SUCCESS) {
            // 令牌可能已失效，清掉缓存让下次请求重新获取。
            oldTokenService.invalidate(accessKey);
            record.setStatus(RequestStatus.REJECTED_OLD.name());
            record.setMessage("老平台拒绝，code=" + code);
            update(record);
            return build(record, RequestStatus.REJECTED_OLD,
                    ApiResponse.failure("老平台返回失败：code=" + code), routeKnown);
        }

        record.setStatus(RequestStatus.ACCEPTED_OLD.name());
        record.setMessage("老平台已接受");
        update(record);
        return build(record, RequestStatus.ACCEPTED_OLD, ApiResponse.success(Boolean.TRUE), routeKnown);
    }


    private RequestRecordEntity newRecord(String accessKey, String apiCmd, String tag,
                                          String operationType, String rawBody, String source) {
        RequestRecordEntity record = new RequestRecordEntity();
        record.setRecordId(UUID.randomUUID().toString());
        record.setAccessKey(accessKey);
        record.setApiCmd(apiCmd);
        record.setTag(tag);
        record.setOperationType(operationType == null ? "" : operationType);
        record.setRawBody(rawBody);
        record.setSource(source);
        record.setDataCount(0);
        record.setStoredCount(0);
        record.setDuplicateCount(0);
        record.setMessage("");
        record.setReceivedAt(LocalDateTime.now());
        return record;
    }

    private void safeInsert(RequestRecordEntity record) {
        try {
            recordMapper.insert(record);
        } catch (Exception e) {
            log.error("留存记录写入失败 recordId={}", record.getRecordId(), e);
        }
    }

    private void update(RequestRecordEntity record) {
        try {
            recordMapper.updateById(record);
        } catch (Exception e) {
            log.error("留存记录更新失败 recordId={}", record.getRecordId(), e);
        }
    }

    private IngestResult build(RequestRecordEntity record, RequestStatus status,
                               ApiResponse response, boolean routeKnown) {
        return new IngestResult(record.getRecordId(), status, response,
                record.getDataCount() == null ? 0 : record.getDataCount(),
                record.getStoredCount() == null ? 0 : record.getStoredCount(),
                record.getDuplicateCount() == null ? 0 : record.getDuplicateCount(),
                record.getMessage(), routeKnown);
    }
}
