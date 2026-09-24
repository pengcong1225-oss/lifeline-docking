package com.lifeline.docking.web.admin;

import com.lifeline.docking.entity.DataItemEntity;
import com.lifeline.docking.entity.RequestRecordEntity;
import com.lifeline.docking.mapper.DataItemMapper;
import com.lifeline.docking.mapper.RequestRecordMapper;
import com.lifeline.docking.model.RequestStatus;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 * 上收数据与留存记录查询。用于核对“收到了什么、存了什么、老平台怎么答的”。
 */
@RestController
@RequestMapping("/admin/api")
public class RecordQueryController {

    private final RequestRecordMapper recordMapper;
    private final DataItemMapper dataItemMapper;

    public RecordQueryController(RequestRecordMapper recordMapper, DataItemMapper dataItemMapper) {
        this.recordMapper = recordMapper;
        this.dataItemMapper = dataItemMapper;
    }

    @GetMapping("/records")
    public Map<String, Object> records(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String tag,
                                       @RequestParam(defaultValue = "50") int limit) {
        QueryWrapper<RequestRecordEntity> wrapper = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        if (tag != null && !tag.isBlank()) {
            wrapper.eq("tag", tag);
        }
        wrapper.orderByDesc("received_at").last("LIMIT " + Math.max(1, Math.min(limit, 500)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", recordMapper.selectList(wrapper));
        return body;
    }

    @GetMapping("/records/{recordId}")
    public ResponseEntity<Map<String, Object>> record(@PathVariable String recordId) {
        RequestRecordEntity record = recordMapper.selectById(recordId);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        List<DataItemEntity> items = dataItemMapper.selectList(
                new QueryWrapper<DataItemEntity>().eq("record_id", recordId).orderByAsc("item_index"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("record", record);
        body.put("items", items);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", recordMapper.selectCount(null));
        body.put("storedItems", dataItemMapper.selectCount(null));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) {
            byStatus.put(status.name(),
                    recordMapper.selectCount(new QueryWrapper<RequestRecordEntity>().eq("status", status.name())));
        }
        body.put("byStatus", byStatus);
        return body;
    }
}
