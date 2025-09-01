package com.lezai.token;

/**
 * token管理器
 * @param <T>
 */
public interface TokenManager<T extends TokenContext> {

    /**
     * 生成token
     * @param ctx
     * @return
     */
    String generateToken(T ctx);

    /**
     * 校验token有效性
     * @param ctx
     * @return
     */
    boolean checkToken(T ctx);

    /**
     * 删除token
     * @param ctx
     * @return
     */
    String deleteToken(T ctx);
}
