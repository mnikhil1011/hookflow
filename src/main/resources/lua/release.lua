-- Frees a delivery's slot, adjusts the endpoint's limit, and gives free slots to waiting deliveries.
--
-- Returns a list: the current limit, followed by the IDs of deliveries that were just given a slot.
-- The caller must send those deliveries.
--
-- KEYS[1] state hash        KEYS[2] in-flight sorted set     KEYS[3] waiting sorted set
-- ARGV[1] delivery id       ARGV[2] signal: OK, CONGESTED or NONE
-- ARGV[3] slot lease in ms  ARGV[4] starting limit           ARGV[5] adaptive (1 or 0)
-- ARGV[6] min limit         ARGV[7] max limit                ARGV[8] decrease factor
-- ARGV[9] decrease cooldown in ms                            ARGV[10] max pause in ms

local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)

redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)

local limit = tonumber(ARGV[4])

if ARGV[5] == '1' then
    limit = tonumber(redis.call('HGET', KEYS[1], 'limit') or ARGV[4])
    local min_limit = tonumber(ARGV[6])
    local max_limit = tonumber(ARGV[7])

    if ARGV[2] == 'OK' then
        -- Additive increase: the limit grows by about 1 for every full round of successes.
        limit = math.min(max_limit, limit + 1 / limit)
        redis.call('HSET', KEYS[1], 'limit', tostring(limit))
        redis.call('HDEL', KEYS[1], 'blocked_until', 'pause_ms')

    elseif ARGV[2] == 'CONGESTED' then
        if limit <= min_limit then
            -- Already at the floor and still failing: pause the endpoint, twice as long each time.
            local previous_pause = tonumber(redis.call('HGET', KEYS[1], 'pause_ms') or '500')
            local pause = math.min(tonumber(ARGV[10]), previous_pause * 2)
            redis.call('HSET', KEYS[1], 'pause_ms', pause, 'blocked_until', now + pause)
        else
            -- Multiplicative decrease, at most once per cooldown. When an endpoint goes bad, every
            -- request in flight fails together; counting each one would collapse the limit at once.
            local last_decrease = tonumber(redis.call('HGET', KEYS[1], 'last_decrease') or '0')
            if now - last_decrease >= tonumber(ARGV[9]) then
                limit = math.max(min_limit, limit * tonumber(ARGV[8]))
                redis.call('HSET', KEYS[1], 'limit', tostring(limit), 'last_decrease', now)
            end
        end
    end
end

-- Hand-off: give every free slot to the delivery that has waited longest.
local result = { tostring(limit) }
local paused = tonumber(redis.call('HGET', KEYS[1], 'blocked_until') or '0') > now

if not paused then
    local free_slots = math.floor(limit) - redis.call('ZCARD', KEYS[2])
    if free_slots > 0 then
        local next_in_line = redis.call('ZRANGE', KEYS[3], 0, free_slots - 1)
        for _, delivery_id in ipairs(next_in_line) do
            redis.call('ZREM', KEYS[3], delivery_id)
            redis.call('ZADD', KEYS[2], now + tonumber(ARGV[3]), delivery_id)
            result[#result + 1] = delivery_id
        end
    end
end

return result
