# ADR-0007: API Key 哈希从 BCrypt 改为 HMAC-SHA256

> **Status**: Accepted
> **Date**: 2026-07-20
> **Context**: admin-server Open API 客户端认证（`/open/api/**`，`X-App-Id` + `X-API-Key`）

## 背景

admin-server 有两套独立的凭据体系：

- **管理员密码**（`AdminAuthService`）：人脑记忆、低熵（通常 < 50 bit），暴力破解物理可行。
- **API Key**（`ApiKeyUtils`）：`SecureRandom` 生成的 32 字符随机串，从 62 字符集取，熵约 190 bit。

两者原本共用同一套 `BCryptPasswordEncoder`（`PasswordUtils` 与 `ApiKeyUtils` 注释互引"哈希逻辑一致，抽出复用"）。但两者的威胁模型完全不同。

## 问题

1. **热路径成本**：每个 Open API 请求都跑一次 `BCrypt.matches()`（刻意慢，~100ms 量级）作为认证。BCrypt 的慢哈希设计目的是拖慢离线暴力破解**低熵密码**——攻击者拿到 hash 后离线穷举字典。对于 190 bit 高熵的 API Key，离线穷举在物理上不可能（`2^190` 次 BCrypt），BCrypt 的慢哈希收益约等于零，**反而把成本转嫁到每个合法请求的认证热路径上**。缓存（`CachedStorageSupport`）只缓存 `ApiKey` 对象（含 hash），不缓存校验结果，无法绕过——每次请求带的 rawKey 不同，必须用本次 key 匹配缓存的 hash。

2. **阻碍暴力破解防护**：第 5 题决定为 API Key 加失败请求独立限流（防 DoS）。BCrypt 的 100ms 会让每个失败请求拖 100ms，DoS 放大。

## 决策

API Key 哈希从 BCrypt 改为 **HMAC-SHA256**（服务端密钥），管理员密码保持 BCrypt。

理由：
- 高熵 key 不需要慢哈希，HMAC-SHA256 认证从 ~100ms 降到 μs 级。
- HMAC 比裸 SHA-256 更防"同 key 跨实例 hash 一致"的对照攻击（服务端密钥参与）。
- 为失败限流（第 5 题）腾出成本空间。

## 被否的替代

- **保持 BCrypt**：统一密码学栈、避免引入 HMAC 密钥管理。但吞吐被 BCrypt 限死，且与 API Key 的威胁模型不匹配。否决。
- **裸 SHA-256 + per-key 盐**：省去 HMAC 密钥管理。但服务端无密钥参与，对照攻击面更大。否决。

## 迁移

admin-server 尚未上线生产（`DB_PASSWORD`/JWT secret 均为 dev 默认值，见 ADR 相关安全校验）。存量 API Key 仅在开发/测试环境，**强制重建**——开发环境重新 `createApp` 即可。BCrypt 是单向哈希，无法从存量 hash 反推明文重新哈希，故无静默迁移路径，也不需要。

## 后果

- `ApiKeyUtils.hashApiKey` / `validateApiKey` 改用 HMAC-SHA256，引入服务端密钥配置（可独立配置或复用 JWT secret）。
- `PasswordUtils`（管理员密码）仍用 BCrypt，不受影响。
- 新建/轮换 API Key 走新算法；存量 key 失效，需重新创建。
