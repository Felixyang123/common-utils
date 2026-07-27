# common-cache

`common-cache` 是一个 Spring Boot 缓存抽象库，提供本地 + Redis 多级缓存与跨节点失效同步。本文件记录该上下文的领域语言，不含实现细节。

## Language

**加速层 (acceleration layer)**:
缓存的定位——非权威、可失效、可重建，数据库才是权威数据源。所有故障语义都以此定位推演。
_Avoid_: 缓存即存储

**L1 (local cache)**:
进程内缓存层，读取最快，容量最小，跨节点不共享。
_Avoid_: 本地缓存、一级缓存

**L2 (remote cache)**:
跨节点共享的 Redis 缓存层，是多级缓存中的权威回退来源。
_Avoid_: 远程缓存、二级缓存、分布式缓存

**serve-stale**:
降级或限流时返回"过期但存在"的旧值，而非报错或返回 null。仅当既无缓存值又被限流时才失败。
_Avoid_: 返回旧数据（过于宽泛）

**单飞 (single-flight)**:
对同一 key 的并发回源请求合并为一次实际加载，其余等待并共享结果。
_Avoid_: 防击穿锁（是实现手段，不是语义）

**缓存雪崩 (cache avalanche)**:
大量**不同** key 在同一时刻失效（如冷启动、批量过期），流量集体涌向数据库。
_Avoid_: 与"击穿""穿透"混用

**缓存击穿 (cache breakdown)**:
**单个热点** key 失效瞬间，并发请求争抢回源，造成对同一 key 的重复加载。
_Avoid_: 与"雪崩"混用

**缓存穿透 (cache penetration)**:
查询**不存在**的 key，每次都回源且永远 miss，恶意或异常流量可借此压垮数据库。
_Avoid_: 与"击穿"混用

**缓存一致性窗口 (consistency window)**:
失效消息丢失后，L1 脏数据继续存活的最长时间，由 L1 短 TTL 兜底收敛。
_Avoid_: 最终一致（未指明上界）
