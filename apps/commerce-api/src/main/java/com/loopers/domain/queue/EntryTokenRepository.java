package com.loopers.domain.queue;

import java.time.Duration;
import java.util.Optional;

/**
 * 입장 토큰 저장소. Redis String으로 구현된다.
 * <ul>
 *   <li>키 {@code entry-token:{userId}}, 값 = 발급한 UUID</li>
 *   <li>TTL이 지나면 Redis가 키를 자동 삭제한다(별도 만료 로직 불필요)</li>
 * </ul>
 */
public interface EntryTokenRepository {

    /** 토큰을 TTL과 함께 저장한다(SET key value EX ttl). 같은 userId면 덮어쓴다. */
    void save(Long userId, String token, Duration ttl);

    /** 저장된 토큰을 조회한다(GET). 없거나 만료됐으면 empty. */
    Optional<String> find(Long userId);

    /** 토큰을 즉시 삭제한다(DEL). 사용 완료/무효화 시 호출. */
    void delete(Long userId);
}
