-- KEYS[1] = 대기열 ZSET 키 (queue:waiting-queue)
-- ARGV[1] = 발급할 최대 인원(count)
-- ARGV[2] = 입장 토큰 TTL(초)
-- ARGV[3] = 입장 토큰 키 접두사 (queue:entry-token:)
-- ARGV[4..] = 미리 생성된 토큰 목록 (최대 count개)
--
-- ZPOPMIN + SET ... EX 를 하나의 스크립트로 묶어, 대기열에서 빠졌지만 토큰은
-- 없는 중간 상태가 관측되지 않도록 원자적으로 실행한다.

local waitingQueueKey = KEYS[1]
local count = tonumber(ARGV[1])
local ttlSeconds = tonumber(ARGV[2])
local entryTokenKeyPrefix = ARGV[3]

local popped = redis.call('ZPOPMIN', waitingQueueKey, count)

local result = {}
local tokenIndex = 4
local i = 1
while i <= #popped do
    local userId = popped[i]
    local token = ARGV[tokenIndex]
    redis.call('SET', entryTokenKeyPrefix .. userId, token, 'EX', ttlSeconds)
    table.insert(result, userId)
    table.insert(result, token)
    i = i + 2
    tokenIndex = tokenIndex + 1
end

return result
