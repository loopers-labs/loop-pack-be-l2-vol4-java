package com.loopers.infrastructure.waitingqueue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.waitingqueue.TokenIssuer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 방류형 입장 토큰 발급 배치의 원자 실행 어댑터({@link TokenIssuer}).
 *
 * <p>고정 윈도우 레이트리밋(이번 M초 윈도우 방류 여유분) + 안전망 상한(hardMax − 현재활성, 선택 B) 중 작은 쪽으로
 * 방류량 결정 → 대기열 앞에서 ZPOPMIN → pass/user-pass(String+TTL) + active:users(ZSET) 기록 → 윈도우 카운터
 * 증가를 <b>단일 Lua 스크립트</b>로 원자 실행한다. Redis는 스크립트를 싱글스레드로 원자 처리하므로, 여러 인스턴스가 같은 윈도우에 동시 호출해도
 * 방류 총합이 N을 넘지 않는다(ShedLock 등 분산 락 불필요). 정합성이 걸린 연산이라 마스터 템플릿으로만 실행한다(04 §0).
 *
 * <p>정원제와의 차이: 활성 점유량(ZCARD)으로 여유분을 정하지 않는다. 활성 상한이 없고, 방류량은
 * "이번 윈도우에 아직 안 쓴 N의 잔여분"으로만 정해지는 고정 레이트다.
 */
@Repository
public class RedisTokenIssuer implements TokenIssuer {

    private static final String QUEUE_KEY = "waiting:queue";
    private static final String ACTIVE_KEY = "active:users";
    private static final String WINDOW_PREFIX = "waiting:release:";
    private static final String PASS_PREFIX = "pass:";
    private static final String USER_PASS_PREFIX = "user-pass:";

    /**
     * KEYS[1]=waiting:queue, KEYS[2]=active:users.
     * ARGV[1]=now(ms), ARGV[2]=releaseSize(N), ARGV[3]=intervalMs(M*1000), ARGV[4]=ttlSeconds,
     * ARGV[5]=hardMaxActive(0=비활성), ARGV[6]=windowPrefix, ARGV[7]=passPrefix, ARGV[8]=userPassPrefix,
     * ARGV[9..]=후보 토큰(방류분만 소비). 반환=방류된 userId 목록.
     */
    private static final String ISSUE_SCRIPT =
        "local now = tonumber(ARGV[1]) " +
        "local n = tonumber(ARGV[2]) " +
        "local intervalMs = tonumber(ARGV[3]) " +
        "local ttl = tonumber(ARGV[4]) " +
        "local hardMax = tonumber(ARGV[5]) " +
        "local windowPrefix = ARGV[6] " +
        "local passPrefix = ARGV[7] " +
        "local userPassPrefix = ARGV[8] " +
        "local windowKey = windowPrefix .. math.floor(now / intervalMs) " +   // 고정 윈도우(M초 단위)
        "local already = tonumber(redis.call('GET', windowKey) or '0') " +
        "local budget = n - already " +                                       // ① 윈도우 방류 여유분(레이트)
        "if budget <= 0 then return {} end " +
        "redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, now) " +                  // 만료 활성 청소
        "if hardMax > 0 then " +                                              // ② 안전망: 활성 상한 여유분(선택 B)
        "  local active = redis.call('ZCARD', KEYS[2]) " +
        "  local headroom = hardMax - active " +
        "  if headroom < budget then budget = headroom end " +               //   둘 중 작은 쪽으로 조임
        "  if budget <= 0 then return {} end " +
        "end " +
        "local popped = redis.call('ZPOPMIN', KEYS[1], budget) " +           // 앞에서 최대 budget명(FIFO)
        "local cnt = #popped / 2 " +                                          // popped = {member, score, ...}
        "if cnt == 0 then return {} end " +
        "local expireAt = now + ttl * 1000 " +
        "local issued = {} " +
        "for i = 1, cnt do " +
        "  local userId = popped[(i - 1) * 2 + 1] " +
        "  local token = ARGV[8 + i] " +
        "  redis.call('SET', passPrefix .. token, userId, 'EX', ttl) " +
        "  redis.call('SET', userPassPrefix .. userId, token, 'EX', ttl) " +
        "  redis.call('ZADD', KEYS[2], expireAt, userId) " +
        "  issued[i] = userId " +
        "end " +
        "redis.call('INCRBY', windowKey, cnt) " +                            // 윈도우 방류 누계
        "redis.call('PEXPIRE', windowKey, intervalMs * 3) " +                // 윈도우 지나면 자동 소멸
        "return issued";

    private final RedisTemplate<String, String> redis;
    private final RedisScript<List> script;

    public RedisTokenIssuer(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.redis = masterRedisTemplate;
        this.script = new DefaultRedisScript<>(ISSUE_SCRIPT, List.class);
    }

    @Override
    public List<Long> issueFront(int releaseSize, int intervalSeconds, int ttlSeconds, int hardMaxActive, List<String> tokens) {
        if (releaseSize <= 0 || intervalSeconds <= 0) {
            return List.of();
        }
        List<String> args = new ArrayList<>(8 + tokens.size());
        args.add(Long.toString(System.currentTimeMillis()));
        args.add(Integer.toString(releaseSize));
        args.add(Long.toString(intervalSeconds * 1000L));
        args.add(Integer.toString(ttlSeconds));
        args.add(Integer.toString(hardMaxActive));
        args.add(WINDOW_PREFIX);
        args.add(PASS_PREFIX);
        args.add(USER_PASS_PREFIX);
        args.addAll(tokens);

        @SuppressWarnings("unchecked")
        List<String> issued = redis.execute(script, List.of(QUEUE_KEY, ACTIVE_KEY), args.toArray());
        if (issued == null || issued.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>(issued.size());
        for (String userId : issued) {
            result.add(Long.valueOf(userId));
        }
        return result;
    }
}
