package com.loopers.application.like;

/**
 * 좋아요 변경 이벤트 — 집계(like_count) 분리용.
 *
 * <p>좋아요 등록/취소가 <strong>likes 테이블을 실제로 변경한 경우에만</strong> 발행된다
 * (이미 좋아요/이미 없음/동시요청 UK위반 등 무변경 경로에선 발행하지 않는다).
 * {@link LikeCountEventListener}가 커밋 이후 별도 트랜잭션에서 like_count 를 갱신하므로,
 * 집계 실패와 무관하게 좋아요 자체는 성공한다(최종 일관성).
 */
public record LikeChangedEvent(Long productId, Type type) {

    public enum Type { LIKED, UNLIKED }

    public static LikeChangedEvent liked(Long productId) {
        return new LikeChangedEvent(productId, Type.LIKED);
    }

    public static LikeChangedEvent unliked(Long productId) {
        return new LikeChangedEvent(productId, Type.UNLIKED);
    }
}
