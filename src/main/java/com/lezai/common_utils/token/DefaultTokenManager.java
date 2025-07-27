package com.lezai.common_utils.token;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class DefaultTokenManager implements TokenManager<DefaultTokenContext> {
    private TokenStorage<String, String> tokenStorage;
    private TokenGenerator<DefaultTokenContext> tokenGenerator;

    @Override
    public String generateToken(DefaultTokenContext ctx) {
        String token = tokenGenerator.generateToken(ctx);
        if (StringUtils.isNotBlank(token) && tokenStorage.setToken(token, token)) {
            ctx.setToken(token);
            return token;
        }
        return null;
    }

    @Override
    public boolean checkToken(DefaultTokenContext ctx) {
        return tokenStorage.deleteToken(ctx.getToken()) != null;
    }

    @Override
    public String deleteToken(DefaultTokenContext ctx) {
        return tokenStorage.deleteToken(ctx.getToken());
    }
}
