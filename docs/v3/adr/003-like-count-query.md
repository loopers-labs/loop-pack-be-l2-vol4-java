# ADR-003: 좋아요 수 COUNT 쿼리

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

상품의 좋아요 수(`likeCount`)는 `Product` 테이블에 별도 컬럼을 두지 않고, `LikeRepository.countByProductId(productId)` COUNT 쿼리로 계산한다.

## 근거

현 단계에서는 구현 단순성을 우선한다. `like_count` 캐시 컬럼을 두면 좋아요/취소 시 동시성 이슈(`Lost Update`)가 발생할 수 있어 추가적인 낙관적/비관적 락 처리가 필요하다.

## 향후 고려사항

트래픽 증가로 COUNT 쿼리가 성능 병목이 될 경우 아래 방식으로 전환한다.

- `product` 테이블에 `like_count` 컬럼 추가 + 낙관적 락 또는 Redis 기반 카운터
