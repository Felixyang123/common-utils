package com.lezai.samples.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 缓存序列化器安全配置。
 * 反序列化时仅允许白名单包/已注册别名的类型还原为具体类，
 * 其余降级为 Map，避免 Jackson 默认多态带来的 RCE 面并隔离坏消息。
 */
@Data
@ConfigurationProperties(prefix = "cache.serializer")
public class CacheSerializerProperties {

    /**
     * 允许反序列化为具体类型的包前缀白名单。
     * 默认仅允许 com.lezai 自身类型。
     */
    private List<String> allowedPackages = List.of("com.lezai");
}
