-- 대기열 앞에서 N명을 꺼내(ZPOPMIN) 각자에게 입장 토큰을 발급한다.
-- ZPOPMIN + SET 을 원자적으로 실행해, 꺼낸 뒤 발급 전 크래시로 인한 유실을 없앤다.
--
-- KEYS[1]      : 대기열 키 (waiting-queue)
-- ARGV[1]      : 꺼낼 인원 수 (count)
-- ARGV[2]      : 토큰 TTL(초)
-- ARGV[3]      : 토큰 키 접두사 (entry-token:)
-- ARGV[4..]    : 미리 생성된 토큰들 (최대 count 개, 실제 꺼낸 수만큼만 소비)
-- return       : 실제 발급된 userId 목록

local prefix = ARGV[3]
local popped = redis.call('ZPOPMIN', KEYS[1], ARGV[1])
local issued = {}
local tokenIdx = 4
local j = 1
-- ZPOPMIN 결과는 {member1, score1, member2, score2, ...} 평탄 배열 → 2칸씩 건너뛰며 member(userId)만 집는다.
for i = 1, #popped, 2 do
    redis.call('SET', prefix .. popped[i], ARGV[tokenIdx], 'EX', ARGV[2])
    issued[j] = popped[i]
    tokenIdx = tokenIdx + 1
    j = j + 1
end
return issued
