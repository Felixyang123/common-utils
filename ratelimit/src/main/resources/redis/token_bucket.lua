-- 令牌桶算法 (Token Bucket)
-- 参数: rate (令牌生成速率), capacity (桶容量), now (当前时间戳), tokens_to_consume (每次消耗令牌数)
local key = KEYS[1]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local tokens_to_consume = tonumber(ARGV[4])

-- 获取当前令牌数和上次填充时间
local current_tokens = tonumber(redis.call('HGET', key, 'tokens')) or capacity
local last_fill_time = tonumber(redis.call('HGET', key, 'last')) or now

-- 计算已过时间 (秒)
local elapsed_mills = now - last_fill_time
local elapsed_seconds = math.floor(elapsed_mills / 1000)
-- 计算新增令牌数 (不超过容量)
local new_tokens = math.min(capacity, current_tokens + elapsed_seconds * rate)

-- 检查是否足够令牌
if new_tokens >= tokens_to_consume then
    -- 更新令牌数和上次填充时间
    redis.call('HSET', key, 'tokens', new_tokens - tokens_to_consume)
    redis.call('HSET', key, 'last', now)
    return 1
else
    return 0
end