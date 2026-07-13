package com.lezai.threadpool.service;

import com.lezai.threadpool.exception.AuthenticationException;
import com.lezai.threadpool.pojo.bean.AdminUser;
import com.lezai.threadpool.pojo.bean.AdminUserContext;
import com.lezai.threadpool.pojo.request.AdminLoginRequest;
import com.lezai.threadpool.pojo.response.AdminLoginResponse;
import com.lezai.threadpool.storage.AdminUserStorage;
import com.lezai.threadpool.utils.PasswordUtils;
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

    public AdminLoginResponse login(AdminLoginRequest request) {
        AdminUser adminUser = adminUserStorage.getByUsername(request.getUsername())
                .orElseThrow(() -> new AuthenticationException("User not found: " + request.getUsername()));

        if (!adminUser.isEnabled()) {
            throw new AuthenticationException("User has been disabled: " + request.getUsername());
        }

        if (!PasswordUtils.matches(request.getPassword(), adminUser.getPasswordHash())) {
            throw new AuthenticationException("Invalid password");
        }

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

    public AdminUserContext validateToken(String token) {
        Claims claims = parseClaims(token);
        String username = claims.get(CLAIM_USERNAME, String.class);
        if (StringUtils.isBlank(username)) {
            throw new AuthenticationException("Invalid token: missing username");
        }

        AdminUser adminUser = adminUserStorage.getByUsername(username)
                .orElseThrow(() -> new AuthenticationException("User not found: " + username));

        if (!adminUser.isEnabled()) {
            throw new AuthenticationException("User has been disabled: " + username);
        }

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

    public Optional<Long> getRemainingMinutes(String token) {
        try {
            Claims claims = parseClaims(token);
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Optional.of(Math.max(0, remainingMs / 60000));
        } catch (AuthenticationException e) {
            return Optional.empty();
        }
    }

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
