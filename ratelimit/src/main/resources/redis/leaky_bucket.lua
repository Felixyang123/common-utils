-- 漏斗算法 (Leaky Bucket)
-- 参数: rate (漏水速率), capacity (桶容量), now (当前时间戳), requests_to_consume (每次消耗请求数)
local key = KEYS[1]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requests_to_consume = tonumber(ARGV[4])

-- 获取当前桶内请求数和上次漏水时间
local current = tonumber(redis.call('HGET', key, 'current')) or 0
local last_leak_time = tonumber(redis.call('HGET', key, 'last')) or now

-- 计算已漏水请求数
local elapsed = now - last_leak_time
local leaked = elapsed * rate
-- 更新桶内请求数 (确保不小于0)
local new_current = math.max(0, current - leaked)

-- 检查是否允许请求
if new_current + requests_to_consume <= capacity then
    -- 更新桶内请求数和上次漏水时间
    redis.call('HSET', key, 'current', new_current + requests_to_consume)
    redis.call('HSET', key, 'last', now)
    return 1
else
    return 0
end