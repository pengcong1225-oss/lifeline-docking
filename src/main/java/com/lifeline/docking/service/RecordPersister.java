package com.lifeline.docking.service;

import com.lifeline.docking.entity.DataItemEntity;
import com.lifeline.docking.entity.RequestRecordEntity;
import com.lifeline.docking.mapper.DataItemMapper;
import com.lifeline.docking.mapper.RequestRecordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 留存写入。独立成 bean 以保证 {@code @Transactional} 真正生效
 * （同类内部调用不会经过 Spring 代理）。留存失败时整体回滚，调用方不得继续转发。
 */
@Service
public class RecordPersister {

    private final RequestRecordMapper recordMapper;
    private final DataItemMapper dataItemMapper;
    private final ObjectMapper objectMapper;

    public RecordPersister(RequestRecordMapper recordMapper,
                           DataItemMapper dataItemMapper,
                           ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.dataItemMapper = dataItemMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存请求信封与全部数据项。
     *
     * @return {@code [成功入库条数, 重复条数]}
     */
    @Transactional(rollbackFor = Exception.class)
    public int[] persist(RequestRecordEntity record, List<Map<String, Object>> data) throws Exception {
        recordMapper.insert(record);
        int stored = 0;
        int duplicates = 0;
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.get(i);
            DataItemEntity entity = new DataItemEntity();
            entity.setRecordId(record.getRecordId());
            entity.setAccessKey(record.getAccessKey());
            entity.setApiCmd(record.getApiCmd());
            entity.setTag(record.getTag());
            entity.setOperationType(record.getOperationType());
            entity.setItemIndex(i);
            Object lsh = item.get("lsh");
            entity.setLsh(lsh == null ? null : String.valueOf(lsh));
            entity.setPayload(objectMapper.writeValueAsString(item));
            entity.setCreatedAt(LocalDateTime.now());
            try {
                dataItemMapper.insert(entity);
                stored++;
            } catch (DuplicateKeyException dup) {
                // tag 内 lsh 已存在：老平台按流水号做唯一校验，这里只记录不覆盖。
                duplicates++;
            }
        }
        return new int[]{stored, duplicates};
    }
}
