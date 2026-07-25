package com.lezai.threadpool.utils;

import com.lezai.threadpool.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 工具类
 *
 * <p>API Key 为 SecureRandom 生成的高熵（~190 bit）随机串，离线穷举物理不可能，
 * 因此使用 HMAC-SHA256（服务端密钥）替代 BCrypt：认证从 ~100ms 降到 μs 级，
 * 同时为失败限流（防 DoS）腾出成本空间。详见 ADR-0007。
 *
 * <p>管理员密码（低熵、人脑记忆）仍由 {@code PasswordUtils} 使用 BCrypt，两者威胁模型不同。
 */
@Slf4j
@Component
public class ApiKeyUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 服务端 HMAC 密钥，由 {@code threadpool.admin.apikey.hmac-secret} 注入。
     * <p>通过 setter 写入静态字段，使静态工具方法（{@link #hashApiKey} / {@link #validateApiKey}）
     * 无需实例即可被接口默认方法调用；同时实例本身也可注入给需要显式依赖的调用方。
     * <p>字段持有与 {@code @Value} 默认值一致的兜底值，确保未走 Spring 注入的场景
     * （如纯 Mockito 单元测试）仍可正常计算 HMAC；生产环境由启动校验（B4）拦截默认值。
     */
    private static volatile String HMAC_SECRET =
            "threadpool-admin-apikey-hmac-default-secret-please-change";

    @Value("${threadpool.admin.apikey.hmac-secret:threadpool-admin-apikey-hmac-default-secret-please-change}")
    public void setHmacSecret(String hmacSecret) {
        HMAC_SECRET = hmacSecret;
        log.info("ApiKeyUtils HMAC secret initialized (length: {})", hmacSecret != null ? hmacSecret.length() : 0);
    }

    /**
     * 生成随机 API Key（32 字符，62 字符集，熵约 190 bit）
     *
     * @return 32 字符的随机字符串
     */
    public static String generateRandomApiKey() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 计算 API Key 的 HMAC-SHA256 哈希（Base64URL 无填充编码）
     *
     * @param apiKey 明文 API Key
     * @return HMAC-SHA256 哈希值
     */
    public static String hashApiKey(String apiKey) {
        return hmac(apiKey);
    }

    /**
     * 验证 API Key 是否匹配哈希值（恒定时间比较，防时序攻击）
     *
     * @param rawApiKey   原始 API Key
     * @param hashedApiKey 存储的哈希值
     * @return 是否匹配
     */
    public static boolean validateApiKey(String rawApiKey, String hashedApiKey) {
        if (rawApiKey == null || hashedApiKey == null) {
            return false;
        }
        String computed = hmac(rawApiKey);
        return constantTimeEquals(computed, hashedApiKey);
    }

    private static String hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(getSecretBytes(), HMAC_ALGORITHM));
            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            // HmacSHA256 为 JRE 必实现算法，不会到达此处
            throw new BusinessException(500, "HmacSHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            // 密钥长度不合法（如为空）时到达此处，属于配置错误
            throw new BusinessException(500, "Invalid HMAC secret key", e);
        }
    }

    private static byte[] getSecretBytes() {
        String secret = HMAC_SECRET;
        if (secret == null || secret.isEmpty()) {
            throw new BusinessException(500, "ApiKeyUtils HMAC secret not initialized. " +
                    "Please configure 'threadpool.admin.apikey.hmac-secret'.");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 恒定时间比较，防时序攻击
     */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
