package com.lezai.threadpool.service;

import com.lezai.threadpool.bean.AdminUserContext;
import com.lezai.threadpool.controller.dto.request.AdminLoginRequest;
import com.lezai.threadpool.controller.dto.response.AdminLoginResponse;
import com.lezai.threadpool.exception.AuthenticationException;
import com.lezai.threadpool.storage.AdminUserStorage;
import com.lezai.threadpool.bean.AdminUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

/**
 * 管理员登录认证服务：校验用户名密码，签发/校验 JWT。
 * <p>
 * JWT payload 内含 username / role / nickname / iat 等 claims。
 * 校验 token 时返回 {@link AdminUserContext}，供拦截器构建上下文。
 * 方案 C：改密码后旧 token 失效（对比 iat vs passwordChangedAt）。
 */
@Slf4j
public class AdminAuthService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NICKNAME = "nickname";

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

        AdminUser adminUser = adminUserStorage.getByUsername(request.getUsername())
                .orElseThrow(() -> new AuthenticationException("User not found after validation"));

        Duration expiry = Duration.ofMinutes(tokenExpireMinutes);
        Date now = new Date();

        String token = Jwts.builder()
                .subject(request.getUsername())
                .claim(CLAIM_USERNAME, request.getUsername())
                .claim(CLAIM_ROLE, adminUser.getRole())
                .claim(CLAIM_NICKNAME, adminUser.getNickname() != null ? adminUser.getNickname() : request.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry.toMillis()))
                .signWith(signingKey)
                .compact();

        AdminLoginResponse response = new AdminLoginResponse();
        response.setToken(token);
        response.setUsername(request.getUsername());
        response.setExpiresInSeconds(expiry.toSeconds());

        log.info("Admin login successful: {} ({})", request.getUsername(), adminUser.getRole());
        return response;
    }

    /**
     * 校验 token 并返回当前登录用户上下文。
     * <p>
     * 方案 C：若用户密码最近修改时间晚于 JWT 签发时间，视为 token 已失效。
     *
     * @throws AuthenticationException token 无效、已过期、或密码已变更
     */
    public AdminUserContext validateToken(String token) {
        Claims claims = parseClaims(token);
        String username = claims.get(CLAIM_USERNAME, String.class);
        if (StringUtils.isBlank(username)) {
            throw new AuthenticationException("Invalid token: missing username");
        }

        // 查 DB 获取当前用户信息（含 passwordChangedAt）
        AdminUser adminUser = adminUserStorage.getByUsername(username)
                .orElseThrow(() -> new AuthenticationException("User not found: " + username));

        if (!adminUser.isEnabled()) {
            throw new AuthenticationException("User has been disabled: " + username);
        }

        // 方案 C：对比 iat vs passwordChangedAt
        Date iat = claims.getIssuedAt();
        if (iat != null && adminUser.getPasswordChangedAt() != null) {
            LocalDateTime changedAt = adminUser.getPasswordChangedAt();
            Date changedDate = Date.from(changedAt.atZone(ZoneId.systemDefault()).toInstant());
            if (changedDate.after(iat)) {
                throw new AuthenticationException("Token expired: password has been changed since token issued");
            }
        }

        return AdminUserContext.builder()
                .username(username)
                .role(adminUser.getRole())
                .nickname(adminUser.getNickname() != null ? adminUser.getNickname() : username)
                .passwordChangedAt(adminUser.getPasswordChangedAt())
                .build();
    }

    /**
     * 获取 token 剩余有效期（分钟），token 无效返回空
     */
    public Optional<Long> getRemainingMinutes(String token) {
        try {
            Claims claims = parseClaims(token);
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Optional.of(Math.max(0, remainingMs / 60000));
        } catch (AuthenticationException e) {
            return Optional.empty();
        }
    }

    /**
     * 滑动续期：为指定用户签发新 token，返回新 token 字符串
     */
    public String renew(String username, String role, String nickname) {
        Duration expiry = Duration.ofMinutes(tokenExpireMinutes);
        Date now = new Date();

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_NICKNAME, nickname)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry.toMillis()))
                .signWith(signingKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthenticationException("Invalid or expired token");
        }
    }
}