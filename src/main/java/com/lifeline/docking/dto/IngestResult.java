package com.lifeline.docking.dto;

import com.lifeline.docking.model.ApiResponse;
import com.lifeline.docking.model.RequestStatus;

/** 一次业务请求的完整处理结果。 */
public class IngestResult {

    private final String recordId;
    private final RequestStatus status;
    private final ApiResponse response;
    private final int dataCount;
    private final int storedCount;
    private final int duplicateCount;
    private final String message;
    private final boolean routeKnown;

    public IngestResult(String recordId, RequestStatus status, ApiResponse response,
                        int dataCount, int storedCount, int duplicateCount,
                        String message, boolean routeKnown) {
        this.recordId = recordId;
        this.status = status;
        this.response = response;
        this.dataCount = dataCount;
        this.storedCount = storedCount;
        this.duplicateCount = duplicateCount;
        this.message = message;
        this.routeKnown = routeKnown;
    }

    public String getRecordId() { return recordId; }
    public RequestStatus getStatus() { return status; }
    public ApiResponse getResponse() { return response; }
    public int getDataCount() { return dataCount; }
    public int getStoredCount() { return storedCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public String getMessage() { return message; }
    public boolean isRouteKnown() { return routeKnown; }
}
