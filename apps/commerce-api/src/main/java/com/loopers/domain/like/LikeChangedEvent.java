package com.loopers.domain.like;

/**
 * 좋아요 수 변경 도메인 이벤트. 좋아요 "전이가 실제로 일어났을 때"만(멱등 no-op 제외) 발행된다.
 *
 * <p>hot row contention 완화를 위해 카운터(product.likes_count) 증감을 좋아요 트랜잭션에서 분리한다.
 * 같은 상품에 좋아요가 몰리면 동기 {@code UPDATE ... likes_count = likes_count + 1}는 그 한 행의
 * X-lock에 직렬화된다. 이 이벤트를 큐로 흘려보내고 컨슈머가 상품별 델타를 합산해 UPDATE를 1회로
 * 합치면(coalescing), DB 행 잠금 획득 횟수가 N→1로 줄어 경합이 풀린다.
 *
 * <p>도메인은 Kafka를 모른다. 발행은 {@code ApplicationEventPublisher}로 하고, Kafka 전송은
 * 인프라({@code LikeEventKafkaPublisher})가 트랜잭션 커밋 이후에 담당한다.
 *
 * @param productId 대상 상품
 * @param delta     +1(좋아요) / -1(취소). 컨슈머에서 합산되므로 순서 무관(교환법칙).
 */
public record LikeChangedEvent(Long productId, long delta) {

    public static LikeChangedEvent liked(Long productId) {
        return new LikeChangedEvent(productId, +1);
    }

    public static LikeChangedEvent unliked(Long productId) {
        return new LikeChangedEvent(productId, -1);
    }
}
