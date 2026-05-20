# ADR-003: 좋아요 수 COUNT 쿼리

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

상품의 좋아요 수(`likeCount`)는 `Product` 테이블에 별도 컬럼을 두지 않고 COUNT 쿼리로 계산한다.

- **단건 조회**: `LikeRepository.countByProductId(productId)` — COUNT 1회
- **목록 조회**: `LikeRepository.countByProductIdIn(List<Long> productIds)` — `SELECT product_id, COUNT(*) … GROUP BY product_id` 1회로 `Map<productId, count>` 반환. N+1 쿼리 방지.

## 근거

현 단계에서는 구현 단순성을 우선한다. `like_count` 캐시 컬럼을 두면 좋아요/취소 시 동시성 이슈(`Lost Update`)가 발생할 수 있어 추가적인 낙관적/비관적 락 처리가 필요하다.

## 추가 결정: UNIQUE 제약 및 소유권 검증

**`UNIQUE(user_id, product_id)`**: `likes` 테이블에 복합 UNIQUE 제약을 추가한다. 좋아요 중복 방지를 애플리케이션 레벨(409 Conflict)뿐 아니라 DB 레벨에서도 보장한다.

**userId 소유권 검증**: `GET /api/v1/users/{userId}/likes` 요청 시, path의 `userId`가 인증된 사용자 ID와 다르면 403 Forbidden을 반환한다. 타 유저의 좋아요 목록 조회를 차단하여 개인정보를 보호한다.

## 향후 고려사항

트래픽 증가로 COUNT 쿼리가 성능 병목이 될 경우 아래 방식으로 전환한다.

- `product` 테이블에 `like_count` 컬럼 추가 + 낙관적 락 또는 Redis 기반 카운터
