package com.lezai.samples.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {
    private HashMapCacheCfg hashMapCacheCfg = new HashMapCacheCfg();
    private GlobalCfg globalCfg = new GlobalCfg();
    private CaffeineCfg caffeineCfg = new CaffeineCfg();
    private NodeCfg node = new NodeCfg();

    @Data
    public static class HashMapCacheCfg {
        private int cacheSize = 1000;

        private long ttl = 60 * 1000;
    }

    @Data
    public static class GlobalCfg {
        private int localCacheSize = 1000;

        private long localCacheTtl = 60 * 1000;

        private long redisCacheTtl = 60 * 60 * 1000;
    }

    @Data
    public static class NodeCfg {
        /**
         * 当前节点地址（IP:Port），用于分布式缓存同步注册。
         * <p>
         * 留空或 null 时自动探测本机非回环 IPv4 + 端口，探测失败打印日志并返回 null，调用方跳过后续操作。
         * 显式配置时直接使用（适配 NAT、Service Mesh 等场景）。
         */
        private String address;

        /**
         * 节点端口，自动探测时用于拼接地址。默认 8080。
         */
        private int port = 8080;

        /**
         * 解析最终节点地址：显式配置优先，否则自动探测。
         *
         * @return 节点地址，留空且自动探测失败返回 null
         */
        public String resolveAddress() {
            if (address != null && !address.isBlank()) {
                return address;
            }
            return LocalNodeAddressDetector.detectAddress(port);
        }
    }

    @Data
    public static class CaffeineCfg {
        /** 每个 Caffeine 缓存的最大条目数（真正的容量上限，修复 HashMapCache 无界）。 */
        private int cacheSize = 1000;

        /** 逻辑过期后物理保留的毫秒数，供 serve-stale；随后 Caffeine 驱逐释放内存。 */
        private long staleGraceMs = 30_000;
    }
}
