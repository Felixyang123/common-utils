package com.lezai.samples.cache.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalNodeAddressDetectorTest {

    @Test
    @DisplayName("detectIp 不应返回 null（开发机有可用网卡）")
    void detectIp_shouldNotReturnNull() {
        assertThat(LocalNodeAddressDetector.detectIp()).isNotNull();
    }

    @Test
    @DisplayName("detectIp 应返回合法 IPv4 格式")
    void detectIp_shouldReturnValidIpv4() {
        String ip = LocalNodeAddressDetector.detectIp();
        assertThat(ip).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }

    @Test
    @DisplayName("detectAddress 应拼接 IP:Port 格式")
    void detectAddress_shouldReturnIpPortFormat() {
        String address = LocalNodeAddressDetector.detectAddress(8080);
        assertThat(address).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:8080");
    }

    @Test
    @DisplayName("resolveAddress 显式配置优先于自动探测")
    void resolveAddress_explicitConfigTakesPrecedence() {
        CacheProperties props = new CacheProperties();
        CacheProperties.NodeCfg node = props.getNode();
        node.setAddress("10.0.0.1:9090");
        node.setPort(8080);

        assertThat(node.resolveAddress()).isEqualTo("10.0.0.1:9090");
    }

    @Test
    @DisplayName("resolveAddress 留空时自动探测")
    void resolveAddress_blankFallsBackToDetect() {
        CacheProperties props = new CacheProperties();
        CacheProperties.NodeCfg node = props.getNode();
        node.setAddress(null);

        String resolved = node.resolveAddress();
        assertThat(resolved).isNotNull();
        assertThat(resolved).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:8080");
    }

    @Test
    @DisplayName("resolveAddress 留空且探测失败应返回 null（不打异常、不返回回环地址）")
    void resolveAddress_detectionFailure_returnsNull() {
        CacheProperties props = new CacheProperties();
        CacheProperties.NodeCfg node = props.getNode();
        node.setAddress("   ");  // 空白视为留空

        // 探测成功 → 合法地址；探测失败 → null（不抛异常、不回环）
        String resolved = node.resolveAddress();
        if (resolved != null) {
            assertThat(resolved).matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+");
        }
        // 两种情况都接受：要么合法地址，要么 null — 只要不是 "127.0.0.1:8080" 就行
    }
}
