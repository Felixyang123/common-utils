package com.lezai.token;

/**
 * token生成器
 * @param <T>
 */
public interface TokenGenerator<T extends TokenContext> {
    /**
     * 生成token
     * @param ctx
     * @return 具体token
     */
    String generateToken(T ctx);
}
