# 좋아요 수(likeCount) Redis 버퍼링 + 배치 동기화 설계

## 배경 및 문제

좋아요 기능은 동시 트래픽이 집중될 수 있는 대표적인 시나리오다.  
기존 방식대로 매 요청마다 `UPDATE product SET like_count = like_count + 1` 을 실행하면 다음 문제가 발생한다.

- 동일 row에 대한 쓰기 충돌 → 잠금 경합 증가
- 좋아요/취소가 반복되면 DB I/O가 그대로 증가
- 상품 목록 조회 시 `likeCount` 정렬을 위해 해당 컬럼에 의존하므로, 쓰기 빈도가 높을수록 인덱스 핫스팟 발생

## 해결 방향

**Redis의 원자적 INCR/DECR로 delta를 누적한 뒤, 스케줄러가 주기적으로 DB에 반영한다.**

- 쓰기 요청은 Redis만 건드리므로 DB 잠금 경합 제거
- Redis INCR/DECR 자체가 원자적 → 별도 동기화 없이 동시성 안전
- 실제 DB 반영은 배치 주기(60초)로 제한 → DB 부하 분산

## 흐름

```
[POST /likes]
    │
    ▼
LikeFacade.like()          ← @Transactional
    │  likeService.like() → DB에 ProductLike 레코드 저장 (PK 중복 → 멱등)
    │  publishEvent(LikeCountChangedEvent(productId, increase=true))
    │
    ▼ (트랜잭션 커밋 후)
LikeCountEventListener     ← @TransactionalEventListener(AFTER_COMMIT)
    │
    ▼
ProductService.incrementLikeCount()
    │  Redis: INCR product:like:pending:{productId}
    │
    ▼ (5분마다)
LikeCountSyncScheduler.productLikeSync()
    │  keys("product:like:pending:*") 조회
    │  getAndDelete(key) → delta 획득 (원자적)
    │  adjustLikeCount(productId, delta) → DB: UPDATE product SET like_count = GREATEST(0, like_count + :amount)
```

### 이벤트를 AFTER_COMMIT에 처리하는 이유

`LikeFacade` 트랜잭션이 커밋되기 전에 Redis를 업데이트하면, DB 롤백 시 Redis와 DB 간 불일치가 발생한다.  
`@TransactionalEventListener(phase = AFTER_COMMIT)`은 트랜잭션 커밋 성공 후에만 리스너가 실행됨을 보장한다.

### getAndDelete를 사용하는 이유

스케줄러가 delta를 읽는 동안 새로운 INCR이 들어올 수 있다.  
`getAndDelete`로 값을 원자적으로 가져오고 즉시 키를 삭제하면, 해당 시점 이후의 INCR은 새 키로 누적되므로 유실 없이 처리된다.

## Redis 키 구조

| 키 | 타입 | 의미 |
|---|---|---|
| `product:like:pending:{productId}` | String (정수) | DB에 아직 반영되지 않은 likeCount 변화량 (양수/음수 모두 가능) |

## DB 반영 쿼리

```sql
UPDATE product
SET like_count = GREATEST(0, like_count + :amount)
WHERE id = :id
```

- `amount`가 음수이면 likeCount 감소
- `GREATEST(0, ...)` 로 음수 방지 (좋아요 취소가 선행 반영보다 먼저 배치될 경우 등 방어)

## 트레이드오프

| 항목 | 내용 |
|---|---|
| 일관성 | likeCount는 최대 5분 지연될 수 있음 (상품 목록 조회 기준 정렬이므로 허용 범위) |
| 내구성 | Redis 장애 시 미반영 delta 유실 가능. 단, `incrementLikeCount`는 예외를 삼켜 서비스 가용성 우선 |
| 정확성 | 좋아요 레코드(ProductLike)는 DB에 즉시 저장되므로 실제 좋아요 수와 likeCount는 결과적으로 일치 |
| 동시성 | Redis INCR/DECR 원자성으로 race condition 없음 (동시성 테스트로 검증) |

## 동시성 테스트 시나리오

| 시나리오 | 스레드 수 | 기대 결과 |
|---|---|---|
| 서로 다른 유저 동시 좋아요 | 30 | Redis delta == 30, ProductLike 레코드 30개 |
| 같은 유저 동시 좋아요 | 30 | Redis delta == 1, ProductLike 레코드 1개 (PK 중복 멱등) |
| 유저 30명 각 5회 toggle (like→unlike 반복, 홀수 종료) | 30 | Redis delta == 실제 좋아요 레코드 수 |
