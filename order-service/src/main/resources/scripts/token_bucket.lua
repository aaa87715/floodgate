-- KEYS[1] = bucket key，例如 ratelimit:orders:203.0.113.99
-- ARGV[1] = capacity            桶子容量
-- ARGV[2] = refill_rate_per_ms  每毫秒補幾個 token（小數）
-- ARGV[3] = ttl_ms              key 的存活時間
-- 回傳 { allowed(0|1), remaining(int), retry_after_ms(int) }


local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local ttl_ms = tonumber(ARGV[3])

-- 1. 從 Redis 取得現在時間（毫秒）—— 唯一的時間來源，不用各實例的時鐘
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)


local status = redis.call("HMGET",KEYS[1],'tokens','ts')


local token 
if  status[1] == false then 
    token = capacity
else
    token = tonumber(status[1])
end

local ts 
if  status[2] == false then 
    ts = now
else
    ts = tonumber(status[2])
end

token = math.min(capacity ,token +  (now - ts) * rate )
local allowed = 0
local retry_after_ms = 0

if token >= 1 then 
    allowed = 1
    token = token-1
    retry_after_ms = 0
else
    allowed = 0
    retry_after_ms = math.ceil((1 - token) / rate)
end

redis.call("HSET", KEYS[1] ,'tokens', token , 'ts', now)
redis.call('PEXPIRE', KEYS[1], ttl_ms)

return {allowed,math.floor(token) ,retry_after_ms}