package com.lezai.threadpool.service;

import com.lezai.threadpool.controller.dto.request.AdminLoginRequest;
import com.lezai.threadpool.controller.dto.response.AdminLoginResponse;
import com.lezai.threadpool.exception.AuthenticationException;
import com.lezai.threadpool.storage.AdminUserStorage;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * 管理员登录认证服务：校验用户名密码，签发/校验 JWT。
 * <p>
 * 管理员（Admin User）是操作 admin-server 管理后台的人员身份，与客户端的
 * app-id + api-key 体系互相独立（见 CONTEXT.md「部署形态与身份」）。
 */
@Slf4j
public class AdminAuthService {

    private static final String CLAIM_USERNAME = "username";

    private final AdminUserStorage adminUserStorage;
    private final SecretKey signingKey;
    private final long tokenExpireMinutes;

    public AdminAuthService(AdminUserStorage adminUserStorage, String secret, long tokenExpireMinutes) {
        this.adminUserStorage = adminUserStorage;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenExpireMinutes = tokenExpireMinutes;
    }

    /**
     * 登录：校验用户名密码，成功则签发 JWT
     */
    public AdminLoginResponse login(AdminLoginRequest request) {
        if (!adminUserStorage.validateCredentials(request.getUsername(), request.getPassword())) {
            throw new AuthenticationException("Invalid username or password");
        }

        Duration expiry = Duration.ofMinutes(tokenExpireMinutes);
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expiry.toMillis());

        String token = Jwts.builder()
                .subject(request.getUsername())
                .claim(CLAIM_USERNAME, request.getUsername())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();

        AdminLoginResponse response = new AdminLoginResponse();
        response.setToken(token);
        response.setUsername(request.getUsername());
        response.setExpiresInSeconds(expiry.toSeconds());

        log.info("Admin login successful: {}", request.getUsername());
        return response;
    }

    /**
     * 校验 token，返回其中的用户名
     *
     * @throws AuthenticationException token 无效或已过期
     */
    public String validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get(CLAIM_USERNAME, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthenticationException("Invalid or expired token");
        }
    }
}
