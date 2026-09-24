package com.lifeline.docking.router;

import com.lifeline.docking.model.RouteKey;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已由新平台接管的业务类别。
 *
 * <p>19 个接口条目共用同一 URL，路由必须看 apiCmd + tag + operationType。
 * 第一版集合为空：所有业务都转发老平台，由老平台 code=0 判定成功。
 * 后续按类别影子比对通过后再加入本集合。</p>
 */
@Component
public class RouteRegistry {

    /** 两个 apiCmd 固定值。注意原文拼写 lifeline_data_batch_acces 少一个 s，不可擅自改正。 */
    public static final String CMD_LIFELINE_BATCH = "lifeline_data_batch_acces";
    public static final String CMD_INSPECTION = "inspection_third_party_data_access";

    private final Set<RouteKey> takenOver = ConcurrentHashMap.newKeySet();

    /** 该类别是否已由新平台自行处理。 */
    public boolean isTakenOver(RouteKey key) {
        return takenOver.contains(key);
    }

    /** 切换某类业务为新平台正式处理；切换前须完成字段、成功判定、幂等与回退核对。 */
    public void takeOver(RouteKey key) {
        takenOver.add(key);
    }

    public void rollback(RouteKey key) {
        takenOver.remove(key);
    }

    public Set<RouteKey> takenOverKeys() {
        return Set.copyOf(takenOver);
    }
}
