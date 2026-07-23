package com.lezai.samples.cache.serializer;

import com.lezai.samples.cache.config.CacheSerializerProperties;
import com.lezai.samples.cache.core.CacheWrapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CachePayloadRedisSerializerTest {

    private CachePayloadRedisSerializer newSerializer(List<String> allowed) {
        CacheSerializerProperties props = new CacheSerializerProperties();
        props.setAllowedPackages(allowed);
        return new CachePayloadRedisSerializer(props);
    }

    @Test
    void serialize_usesEnvelope_withTypeAndData() {
        CachePayloadRedisSerializer ser = newSerializer(List.of("com.lezai"));
        byte[] bytes = ser.serialize("hello");
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).contains("\"t\":\"java.lang.String\"").contains("\"d\":\"hello\"");
    }

    @Test
    void deserialize_whitelistedType_returnsConcreteInstance() {
        CachePayloadRedisSerializer ser = newSerializer(List.of("com.lezai"));
        byte[] bytes = ser.serialize(new com.lezai.samples.cache.sync.CacheSyncMessageImpl("c", "k", 60_000L));
        Object out = ser.deserialize(bytes);
        assertThat(out).isInstanceOf(com.lezai.samples.cache.sync.CacheSyncMessageImpl.class);
    }

    @Test
    void deserialize_nonWhitelistedType_degradesToMap() {
        CachePayloadRedisSerializer ser = newSerializer(List.of("com.lezai"));
        // java.util.ArrayList 不在默认白名单内
        byte[] bytes = ser.serialize(new java.util.ArrayList<>(List.of("a", "b")));
        Object out = ser.deserialize(bytes);
        assertThat(out).isInstanceOf(Map.class);
    }

    @Test
    void deserialize_registeredAlias_returnsConcreteInstance() {
        CachePayloadRedisSerializer ser = newSerializer(List.of("com.lezai"));
        ser.registerAlias(CacheWrapper.class);
        byte[] bytes = ser.serialize(CacheWrapper.of("v", 60_000L));
        Object out = ser.deserialize(bytes);
        assertThat(out).isInstanceOf(CacheWrapper.class);
        assertThat(((CacheWrapper<?>) out).getData()).isEqualTo("v");
    }

    @Test
    void deserialize_nullOrEmpty_returnsNull() {
        CachePayloadRedisSerializer ser = newSerializer(List.of("com.lezai"));
        assertThat(ser.deserialize(null)).isNull();
        assertThat(ser.deserialize(new byte[0])).isNull();
    }

    @Test
    void deserialize_badBytes_degradesToRawString_noException() {
        CachePayloadRedisSerializer ser = newSerializer(List.of("com.lezai"));
        // 损坏/恶意消息不应抛异常杀死订阅线程
        Object out = ser.deserialize("not-json-{{{".getBytes(StandardCharsets.UTF_8));
        assertThat(out).isInstanceOf(String.class);
    }

    @Test
    void noDefaultTyping_noRceSurface() {
        // 默认多态禁用：序列化输出中不应出现 @class 这类类型元数据
        CachePayloadRedisSerializer ser = newSerializer(List.of("com.lezai"));
        byte[] bytes = ser.serialize("probe");
        assertThat(new String(bytes, StandardCharsets.UTF_8)).doesNotContain("@class");
    }
}
