package com.lifeline.docking.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 全部 19 个接口条目的目录（1 个令牌接口 + 18 个业务操作）。
 *
 * <p>数据来自 {@code docking-api-catalog.json}，由源文档《接口字段明细》生成；
 * 不是运行时从老平台拉取，接口变更需重新生成该文件。</p>
 */
@Component
public class ApiCatalog {

    private final CatalogSnapshot snapshot;

    public ApiCatalog(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("docking-api-catalog.json").getInputStream()) {
            this.snapshot = objectMapper.readValue(in, CatalogSnapshot.class);
        } catch (Exception e) {
            // 接口清单缺失或损坏属于打包错误，启动即失败，不留到运行时才发现。
            throw new IllegalStateException("无法加载接口清单 docking-api-catalog.json，请运行 tools/build-catalog.mjs 重新生成", e);
        }
        this.snapshot.getEntries().sort(Comparator.comparingInt(ApiCatalogEntry::getSeq));
    }

    /** 条目总数（1 个令牌接口 + 18 个业务操作）。 */
    public int size() {
        return snapshot.getEntries().size();
    }

    public String getSource() { return snapshot.getSource(); }

    public String getGeneratedAt() { return snapshot.getGeneratedAt(); }

    public List<ApiCatalogEntry> all() { return List.copyOf(snapshot.getEntries()); }

    /** 仅业务操作（18 个）。 */
    public List<ApiCatalogEntry> business() {
        return snapshot.getEntries().stream().filter(e -> !e.isToken()).toList();
    }

    public Optional<ApiCatalogEntry> bySeq(int seq) {
        return snapshot.getEntries().stream().filter(e -> e.getSeq() == seq).findFirst();
    }

    /** 按路由键定位接口。operationType 为空时按“未定义 operationType”匹配。 */
    public Optional<ApiCatalogEntry> byRoute(String apiCmd, String tag, String operationType) {
        String op = operationType == null ? "" : operationType;
        return snapshot.getEntries().stream()
                .filter(e -> !e.isToken())
                .filter(e -> Objects.equals(e.getApiCmd(), apiCmd))
                .filter(e -> Objects.equals(e.getTag(), tag))
                .filter(e -> Objects.equals(e.getOperationType() == null ? "" : e.getOperationType(), op))
                .findFirst();
    }
}
