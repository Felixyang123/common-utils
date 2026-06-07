package com.lezai.threadpool.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

public class ApiKeyUtils {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 生成随机 API Key
     *
     * @return 32 字符的随机字符串
     */
    public static String generateRandomApiKey() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(32);
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 计算 API Key 的 BCrypt 哈希
     * 相比 MD5，BCrypt 具有盐值和计算成本，能有效抵抗彩虹表攻击
     */
    public static String hashApiKey(String apiKey) {
        return ENCODER.encode(apiKey);
    }

    /**
     * 验证 API Key 是否匹配哈希值
     *
     * @param rawApiKey 原始 API Key
     * @param hashedApiKey 存储的哈希值
     * @return 是否匹配
     */
    public static boolean validateApiKey(String rawApiKey, String hashedApiKey) {
        if (rawApiKey == null || hashedApiKey == null) {
            return false;
        }
        return ENCODER.matches(rawApiKey, hashedApiKey);
    }
}
