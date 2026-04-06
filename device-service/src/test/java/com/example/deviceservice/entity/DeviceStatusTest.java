package com.example.deviceservice.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DeviceStatus 枚举测试
 */
class DeviceStatusTest {

    @Test
    void testAllStatusValues() {
        // 验证所有6种状态都存在
        assertEquals(6, DeviceStatus.values().length);
    }

    @Test
    void testNormalStatus() {
        DeviceStatus status = DeviceStatus.NORMAL;
        assertEquals("正常运行", status.getDescription());
        assertEquals("success", status.getColor());
        assertEquals("NORMAL", status.name());
    }

    @Test
    void testRunningStatus() {
        DeviceStatus status = DeviceStatus.RUNNING;
        assertEquals("运行中", status.getDescription());
        assertEquals("primary", status.getColor());
    }

    @Test
    void testStandbyStatus() {
        DeviceStatus status = DeviceStatus.STANDBY;
        assertEquals("待机", status.getDescription());
        assertEquals("info", status.getColor());
    }

    @Test
    void testMaintenanceStatus() {
        DeviceStatus status = DeviceStatus.MAINTENANCE;
        assertEquals("维护中", status.getDescription());
        assertEquals("warning", status.getColor());
    }

    @Test
    void testFaultStatus() {
        DeviceStatus status = DeviceStatus.FAULT;
        assertEquals("故障", status.getDescription());
        assertEquals("danger", status.getColor());
    }

    @Test
    void testOfflineStatus() {
        DeviceStatus status = DeviceStatus.OFFLINE;
        assertEquals("离线", status.getDescription());
        assertEquals("info", status.getColor());
    }

    @Test
    void testFromCode_Found() {
        assertEquals(DeviceStatus.NORMAL, DeviceStatus.fromCode("NORMAL"));
        assertEquals(DeviceStatus.RUNNING, DeviceStatus.fromCode("RUNNING"));
        assertEquals(DeviceStatus.STANDBY, DeviceStatus.fromCode("STANDBY"));
        assertEquals(DeviceStatus.MAINTENANCE, DeviceStatus.fromCode("MAINTENANCE"));
        assertEquals(DeviceStatus.FAULT, DeviceStatus.fromCode("FAULT"));
        assertEquals(DeviceStatus.OFFLINE, DeviceStatus.fromCode("OFFLINE"));
    }

    @Test
    void testFromCode_NotFound() {
        assertNull(DeviceStatus.fromCode("INVALID"));
        assertNull(DeviceStatus.fromCode(""));
        assertNull(DeviceStatus.fromCode(null));
    }

    @Test
    void testGetters() {
        for (DeviceStatus status : DeviceStatus.values()) {
            assertNotNull(status.getDescription());
            assertNotNull(status.getColor());
            // 验证 description 不为空
            assertFalse(status.getDescription().isEmpty());
            // 验证 color 不为空
            assertFalse(status.getColor().isEmpty());
        }
    }
}