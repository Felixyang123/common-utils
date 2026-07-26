package com.lezai.samples.cache.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lezai.samples.cache.config.CacheSerializerProperties;
import com.lezai.samples.cache.core.CacheWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全的缓存载荷序列化器——使用信封 {t,dt,d} 格式：
 *   t  : 数据原始类型全名（String）
 *   dt : 数据类型标识（String）；对 CacheWrapper 为内层 data 类型，否则同 t
 *   d  : 数据本体（JSON）
 *
 * 安全约束：
 * - 禁用 Jackson 默认多态（enableDefaultTyping），避免反序列化 RCE。
 * - 反序列化时仅当目标类型位于白名单包或已注册别名时还原为具体类，
 *   否则降级为 Map（错误隔离——坏消息不会打死订阅线程）。
 */
@Slf4j
public class CachePayloadRedisSerializer implements RedisSerializer<Object> {

    public static final String TYPE_CACHE_WRAPPER = "CacheWrapper";

    private final ObjectMapper mapper;
    private final CacheSerializerProperties properties;
    private final ConcurrentHashMap<String, Class<?>> aliases = new ConcurrentHashMap<>();

    public CachePayloadRedisSerializer(CacheSerializerProperties properties) {
        this.properties = properties;
        this.mapper = new ObjectMapper();
        // 显式禁用默认多态：仅靠白名单/别名还原类型，杜绝 RCE
    }

    /** 便捷构造：直接指定白名单包列表（主要用于测试）。 */
    public CachePayloadRedisSerializer(List<String> allowedPackages) {
        CacheSerializerProperties props = new CacheSerializerProperties();
        props.setAllowedPackages(allowedPackages);
        this.properties = props;
        this.mapper = new ObjectMapper();
    }

    /** 注册允许还原的类型别名（线程安全）。 */
    public void registerAlias(Class<?> type) {
        if (type != null) {
            aliases.put(type.getName(), type);
        }
    }

    @Override
    public byte[] serialize(Object o) throws SerializationException {
        if (o == null) {
            return new byte[0];
        }
        try {
            String outerType = o.getClass().getName();
            String dt = outerType; // 默认 dt = 外层类型

            if (o instanceof CacheWrapper<?> w) {
                Object data = w.getData();
                if (data != null) {
                    dt = data.getClass().getName(); // 内层类型
                }
            }

            String json = mapper.writeValueAsString(o);
            String envelope = "{\"t\":\"" + escapeJson(outerType)
                    + "\",\"dt\":\"" + escapeJson(dt)
                    + "\",\"d\":" + json + "}";
            return envelope.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SerializationException("CachePayloadRedisSerializer serialize failed", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            String envelope = new String(bytes, StandardCharsets.UTF_8);
            Map<String, Object> map = mapper.readValue(envelope, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            if (map == null) {
                return null;
            }
            String type = (String) map.get("t");
            String dt = (String) map.get("dt");
            Object data = map.get("d");

            if (type == null || data == null) {
                return map; // 非信封格式降级为 Map
            }

            // CacheWrapper 特殊处理：提取内层 data 节点，按 dt（内层类型）还原
            if (TYPE_CACHE_WRAPPER.equals(type) || type.endsWith(".CacheWrapper")) {
                if (data instanceof Map<?, ?> dataMap) {
                    Object rawData = dataMap.get("data");
                    Object rawExpireTime = dataMap.get("expireTime");
                    Long expireTime = rawExpireTime instanceof Number n ? n.longValue() : null;

                    Object typedData = rawData;
                    if (rawData != null && dt != null) {
                        Class<?> dataClass = resolveType(dt);
                        if (dataClass != null && !dataClass.equals(Object.class)) {
                            com.fasterxml.jackson.databind.JsonNode dataNode = mapper.valueToTree(rawData);
                            typedData = mapper.treeToValue(dataNode, dataClass);
                        }
                    }
                    return CacheWrapper.builder().data(typedData).expireTime(expireTime).build();
                }
                return map;
            }

            // 通用处理：用 t 还原外层类型
            Class<?> targetType = resolveType(type);
            if (targetType == null || targetType.equals(Object.class)) {
                // 标量类型（String 等）直接返回 d 值，无需白名单
                if (data instanceof String s) {
                    return s;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Type [{}] not in whitelist, degrading to Map", type);
                }
                return map;
            }
            String dataJson = mapper.writeValueAsString(data);
            return mapper.readValue(dataJson, targetType);
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("CachePayloadRedisSerializer deserialize failed, degrading to String. reason: {}",
                    e.getMessage());
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * 解析类型：白名单包或已注册别名命中则返回具体类，否则返回 null（降级为 Map）。
     */
    private Class<?> resolveType(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return null;
        }
        List<String> allowed = properties.getAllowedPackages();
        if (allowed != null) {
            for (String pkg : allowed) {
                if (pkg != null && typeName.startsWith(pkg + ".")) {
                    try {
                        return Class.forName(typeName);
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                }
            }
        }
        return aliases.get(typeName);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
