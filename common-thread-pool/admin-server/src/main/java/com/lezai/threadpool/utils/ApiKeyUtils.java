package com.lezai.threadpool.utils;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class ApiKeyUtils {

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
     * 计算 API Key 的 hash
     */
    public static String hashApiKey(String apiKey) {
        return DigestUtils.md5DigestAsHex(apiKey.getBytes(StandardCharsets.UTF_8));
    }
}
