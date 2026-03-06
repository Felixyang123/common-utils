-- 滑动时间窗口 (Sliding Window)
-- 参数: window_size (窗口大小, 秒), max_requests (最大请求数), now (当前时间戳)
local key = KEYS[1]
local window_size = tonumber(ARGV[1])
local max_requests = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local request_id = ARGV[4]

-- 移除过期请求 (时间戳 < now - window_size)
redis.call('ZREMRANGEBYSCORE', key, 0, now - window_size)

-- 获取当前窗口内请求数
local count = redis.call('ZCARD', key)

-- 检查是否超过限制
if count + 1 <= max_requests then
-- 添加当前请求时间戳 (分数=时间戳)
    redis.call('ZADD', key, now, request_id)
    return 1
else
    return 0
end