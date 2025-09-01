package com.lezai.token;

/**
 * token上下文，保存生成token所需的参数信息和结果
 */
public interface TokenContext {
    String getToken();

    void setToken(String token);
}
