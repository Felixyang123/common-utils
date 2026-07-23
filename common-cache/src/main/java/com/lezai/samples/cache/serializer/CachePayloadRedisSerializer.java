package com.lezai.samples.cache.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lezai.samples.cache.config.CacheSerializerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全的缓存载荷序列化器——使用信封 {t,e,dt,d} 格式：
 *   t  : 数据原始类型全名（String）
 *   e  : 逻辑过期时间（Long，毫秒时间戳，null 表示永不过期）
 *   dt : 数据类型标识（String，由写入方标记；用于区分 CacheWrapper 等信封语义）
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

    /** 注册允许还原的类型别名（线程安全）。 */
    public void registerAlias(Class<?> type) {
        if (type != null) {
            aliases.put(type.getName(), type);
        }
    }

    @Override
    public byte[] serialize(Object o) throws SerializationException {
        try {
            String typeName = (o == null) ? Void.class.getName() : o.getClass().getName();
            String json = (o == null) ? "null" : mapper.writeValueAsString(o);
            // 信封 {t,e,dt,d}：序列化场景下 e/dt 由调用方本体携带（如 CacheWrapper 自身字段），
            // 此处 t=外层类型，dt=同，d=JSON 化后的对象。
            String envelope = "{\"t\":\"" + escapeJson(typeName)
                    + "\",\"e\":null,\"dt\":\"" + escapeJson(typeName)
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
            Object data = map.get("d");
            if (type == null || data == null) {
                // 非信封格式：整体作为 Map 降级返回
                return map;
            }
            Class<?> targetType = resolveType(type);
            if (targetType == null || targetType == Object.class) {
                // 白名单未命中：降级为 Map，隔离坏消息
                if (log.isDebugEnabled()) {
                    log.debug("Type [{}] not in whitelist, degrading to Map", type);
                }
                return map;
            }
            // 将数据节点转为目标类型
            String dataJson = data instanceof String s ? mapper.writeValueAsString(map.get("d")) : mapper.writeValueAsString(data);
            return mapper.readValue(dataJson, targetType);
        } catch (SerializationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("CachePayloadRedisSerializer deserialize failed, degrading to String. reason: {}", e.getMessage());
            // 反序列化失败：错误隔离，返回原始字符串而非抛异常杀死订阅线程
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
