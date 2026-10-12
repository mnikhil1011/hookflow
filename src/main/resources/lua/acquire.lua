-- Tries to take a slot on one endpoint for one delivery.
--
-- Returns  1  slot granted
--          0  no slot free; the delivery now waits in line
--          2  this delivery already holds a slot (another worker is sending it)
--
-- KEYS[1] state hash        KEYS[2] in-flight sorted set     KEYS[3] waiting sorted set
-- ARGV[1] delivery id       ARGV[2] slot lease in ms         ARGV[3] starting limit
-- ARGV[4] adaptive (1 or 0) ARGV[5] max waiting deliveries

-- Use the Redis clock, so every worker agrees on what "now" is.
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)

-- A slot whose lease ran out belonged to a worker that crashed. Free it.
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)

if redis.call('ZSCORE', KEYS[2], ARGV[1]) then
    return 2
end

local limit = tonumber(ARGV[3])
if ARGV[4] == '1' then
    limit = tonumber(redis.call('HGET', KEYS[1], 'limit') or ARGV[3])
end
local paused = tonumber(redis.call('HGET', KEYS[1], 'blocked_until') or '0') > now

if not paused and redis.call('ZCARD', KEYS[2]) < math.floor(limit) then
    redis.call('ZADD', KEYS[2], now + tonumber(ARGV[2]), ARGV[1])
    redis.call('ZREM', KEYS[3], ARGV[1])
    return 1
end

-- No slot: wait in line. NX keeps the original position if the delivery is already waiting.
if redis.call('ZCARD', KEYS[3]) < tonumber(ARGV[5]) then
    redis.call('ZADD', KEYS[3], 'NX', now, ARGV[1])
end
return 0
