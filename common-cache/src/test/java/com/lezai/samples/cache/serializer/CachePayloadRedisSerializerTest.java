package com.lezai.samples.cache.serializer;

import com.lezai.samples.cache.core.CacheWrapper;
import com.lezai.samples.cache.config.CacheSerializerProperties;
import com.lezai.samples.cache.sync.CacheSyncMessageImpl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CachePayloadRedisSerializerTest {

    record User(String name, int age) {}

    // ---- 复杂类型还原（HIGH #2）----

    @Test
    void wrapperWithComplexType_roundTripsAsOriginalType() {
        CachePayloadRedisSerializer ser = new CachePayloadRedisSerializer(List.of("com.lezai"));
        CacheSerializerProperties props = new CacheSerializerProperties();
        props.setAllowedPackages(List.of("com.lezai", "com.lezai.samples.cache.serializer"));
        CachePayloadRedisSerializer serWithUser = new CachePayloadRedisSerializer(props);

        CacheWrapper<User> original = CacheWrapper.of(new User("ming", 18), 60_000L);
        byte[] bytes = serWithUser.serialize(original);
        Object result = serWithUser.deserialize(bytes);

        assertThat(result).isInstanceOf(CacheWrapper.class);
        CacheWrapper<?> cw = (CacheWrapper<?>) result;
        assertThat(cw.getData()).isInstanceOf(User.class);
        assertThat(cw.getData()).isEqualTo(new User("ming", 18));
        assertThat(cw.getExpireTime()).isEqualTo(original.getExpireTime());
    }

    @Test
    void wrapperWithComplexType_degradesToMap_whenNotWhitelisted() {
        CachePayloadRedisSerializer strictSer = new CachePayloadRedisSerializer(List.of("org.other"));
        CacheWrapper<User> original = CacheWrapper.of(new User("ming", 18), 60_000L);
        byte[] bytes = strictSer.serialize(original);
        Object result = strictSer.deserialize(bytes);

        assertThat(result).isInstanceOf(CacheWrapper.class);
        CacheWrapper<?> cw = (CacheWrapper<?>) result;
        assertThat(cw.getData()).isInstanceOf(Map.class);
    }

    // ---- 移除 e 字段（LOW #5）----

    @Test
    void envelopeDoesNotContainEField() {
        CachePayloadRedisSerializer ser = new CachePayloadRedisSerializer(List.of("com.lezai"));
        byte[] bytes = ser.serialize("hello");
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("\"e\"");
    }

    @Test
    void wrapperEnvelopeDoesNotContainEField() {
        CachePayloadRedisSerializer ser = new CachePayloadRedisSerializer(List.of("com.lezai"));
        CacheWrapper<String> w = CacheWrapper.of("v", 60_000L);
        byte[] bytes = ser.serialize(w);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("\"e\"");
    }

    // ---- 兼容：标量/CacheSyncMessageImpl 行为不变 ----

    @Test
    @SuppressWarnings("unchecked")
    void wrapperWithScalarData_roundTrips() {
        CachePayloadRedisSerializer ser = new CachePayloadRedisSerializer(List.of("com.lezai"));
        CacheWrapper<String> w = CacheWrapper.of("payload", 60_000L);
        byte[] bytes = ser.serialize(w);
        CacheWrapper<?> back = (CacheWrapper<?>) ser.deserialize(bytes);
        assertThat(back.getData()).isEqualTo("payload");
        assertThat(back.getExpireTime()).isEqualTo(w.getExpireTime());
    }

    @Test
    void syncMessage_roundTrips() {
        CachePayloadRedisSerializer ser = new CachePayloadRedisSerializer(List.of("com.lezai"));
        CacheSyncMessageImpl out = new CacheSyncMessageImpl("CAT", "CAT:k1", 60_000L);
        byte[] bytes = ser.serialize(out);
        Object back = ser.deserialize(bytes);
        assertThat(back).isInstanceOf(CacheSyncMessageImpl.class);
        CacheSyncMessageImpl bm = (CacheSyncMessageImpl) back;
        assertThat(bm.getCategory()).isEqualTo("CAT");
        assertThat(bm.getKey()).isEqualTo("CAT:k1");
        assertThat(bm.getSourceId()).isEqualTo(out.getSourceId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonEnvelopeObject_roundTrips() {
        CachePayloadRedisSerializer ser = new CachePayloadRedisSerializer(List.of("com.lezai"));
        byte[] bytes = ser.serialize("hello");
        Object back = ser.deserialize(bytes);
        assertThat(back).isEqualTo("hello");
    }
}
