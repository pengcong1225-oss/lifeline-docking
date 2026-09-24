package com.lifeline.docking.router;

import com.lifeline.docking.model.RouteKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteRegistryTest {

    @Test
    void nothingIsTakenOverInFirstVersionSoAllTrafficGoesToOldPlatform() {
        RouteRegistry registry = new RouteRegistry();
        String cmd = RouteRegistry.CMD_LIFELINE_BATCH;
        assertFalse(registry.isTakenOver(RouteKey.of(cmd, "construction_info")));
    }

    @Test
    void operationTypeIsPartOfRouteKey() {
        RouteRegistry registry = new RouteRegistry();
        String cmd = RouteRegistry.CMD_INSPECTION;

        registry.takeOver(RouteKey.of(cmd, "inspector", "I"));
        assertTrue(registry.isTakenOver(RouteKey.of(cmd, "inspector", "I")));
        assertFalse(registry.isTakenOver(RouteKey.of(cmd, "inspector", "D")),
                "同一 tag 的删除与保存必须能分别切换");
    }

    @Test
    void rollbackRestoresForwardingToOldPlatform() {
        RouteRegistry registry = new RouteRegistry();
        RouteKey key = RouteKey.of(RouteRegistry.CMD_INSPECTION, "district", "I");

        registry.takeOver(key);
        assertTrue(registry.isTakenOver(key));

        registry.rollback(key);
        assertFalse(registry.isTakenOver(key));
    }

    @Test
    void apiCmdSpellingIsPreservedAsDocumented() {
        assertEquals("lifeline_data_batch_acces", RouteRegistry.CMD_LIFELINE_BATCH);
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
