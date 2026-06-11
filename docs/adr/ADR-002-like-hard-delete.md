# ADR-002: 좋아요 도메인의 삭제 전략으로 hard delete 채택

## 상태
Accepted

## 배경

좋아요(`LikeModel`) 도메인은 다른 엔티티와 동작 특성이 다르다.

- **toggle 성격**: 한 사용자가 같은 상품에 좋아요 → 취소 → 재좋아요를 반복한다.
- **멱등성 민감**: 등록/취소가 여러 번 호출돼도 결과가 같아야 한다.

상품별 좋아요 수는 조회 성능을 위해 설계 단계에서 `product.like_count` 컬럼으로 비정규화하기로 별도 결정했다.
따라서 likes 테이블을 직접 집계하는 count 성능은 본 결정의 고려 대상이 아니다.

초기 구현은 코드베이스 컨벤션에 따라 `BaseEntity`를 상속해 soft delete(`deleted_at` + `@SQLRestriction("deleted_at IS NULL")`)를 사용했고,
취소는 `like.delete()`, 등록은 `exists` 체크 후 새 row insert 방식이었다.

```java
// 초기 구현 (soft delete)
@Entity
@SQLRestriction("deleted_at IS NULL")
public class LikeModel extends BaseEntity { ... }

public void register(Long userId, Long productId) {
    if (likeRepository.existsByUserIdAndProductId(userId, productId)) return; // deleted_at IS NULL 기준
    likeRepository.save(new LikeModel(userId, productId));
}
```

이 구조에서 `exists`는 `@SQLRestriction` 때문에 소프트 삭제된 row 를 "없음"으로 보고,
재좋아요 시 같은 `(user_id, product_id)`를 가진 **새 row 를 insert** 한다.
toggle 이 잦은 좋아요 특성상 취소된 dead row 가 무한히 쌓이는 문제가 있다.

## 결정

좋아요 도메인에 한해 `BaseEntity` soft delete 에서 빼고,
**hard delete + `(user_id, product_id)` unique 제약**으로 전환한다.
likes 테이블은 항상 활성 좋아요만 보관하는 lean 한 상태 테이블로 둔다.

```java
// 변경 방향 (hard delete)
public void cancel(Long userId, Long productId) {
    likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(likeRepository::delete);  // 물리 삭제
}
```

## 선택지

### Option A: soft delete (`BaseEntity` 기본)

`BaseEntity`의 `deleted_at` 기반 소프트 삭제를 그대로 사용한다.

- **장점**:
  - 코드베이스 전체가 soft delete 로 통일돼 있어 컨벤션이 일관된다.
  - `BaseEntity`를 그대로 상속하므로 추가 작업이 없다.
  - 취소된 row 가 물리적으로 남아 "복원" 여지가 있다.
- **단점**:
  - 재좋아요 시 새 row 가 insert 되어 dead row 가 **무한히 증가**한다. 좋아요는 고volume·고churn 테이블이라 영향이 크다.
  - 취소된 row 가 `(user_id, product_id)` 키를 그대로 점유하므로 **unique 제약과 충돌**한다. 재좋아요 insert 가 중복 키로 실패한다.
  - 양립시키려면 등록을 insert 가 아닌 "소프트 삭제된 row 의 restore"로 바꿔야 하는데, 그러면 매 cycle 마다 같은 row 를 덮어써 **이력도 결국 남지 않는다**. 어중간한 절충이다.

### Option B: hard delete + unique 제약 (채택)

취소 시 row 를 물리 삭제하고, `(user_id, product_id)`에 unique 제약을 건다.

- **장점**:
  - 항상 활성 좋아요만 보관 → 테이블 크기가 활성 좋아요 수로 **bounded** 된다.
  - unique 제약이 멱등성을 **DB 차원에서 보장**한다. 동시 요청으로 인한 중복 insert 도 막힌다.
  - toggle·멱등성이라는 좋아요 도메인 특성과 잘 맞는다.
- **단점**:
  - 코드베이스의 soft delete 통일 컨벤션에서 이 도메인만 예외가 된다.
  - 좋아요/취소 이력이 남지 않는다. (현재 이력 요구사항은 없음)

## 근거

핵심은 좋아요 도메인의 동작 특성(toggle·멱등성)과 soft delete 가 잘 맞지 않는다는 점이다.

특히 **soft delete 와 `(user_id, product_id)` unique 제약은 양립할 수 없다**. 취소된 row 가 같은 키를 점유한 채 남아있어 재좋아요 insert 가 중복 키로 실패하기 때문이다. 멱등성을 DB 로 보장하려면 unique 제약이 필요하고, 그러려면 hard delete 가 자연스럽다.

이력 보존은 soft delete 를 정당화하는 유일한 근거였으나, 현재 이력 추적 요구사항이 없다. 또한 이력이 필요해지더라도 soft delete 의 "restore 후 덮어쓰기"로는 완전한 이력이 남지 않으므로, 그때는 append-only 이벤트 로그가 정석이다. 즉 이력 요구는 soft delete 채택의 근거가 되지 못한다.

코드베이스 컨벤션 일관성을 깨는 비용보다, 좋아요 도메인 특성에 맞는 깔끔한 상태 모델과 DB 차원의 멱등성 보장 이점이 더 크다고 판단했다.

## 결과

- **긍정**: likes 테이블이 활성 좋아요만 보관해 크기가 bounded 된다. unique 제약으로 중복·동시성 문제가 DB 에서 차단된다.
- **부정**: soft delete 통일 컨벤션에서 좋아요 도메인만 예외가 된다. 좋아요/취소 이력은 보존되지 않는다.
- **추후 고려**: 이력 추적 요구가 생기면 likes(현재 상태)와 분리된 **append-only 이벤트 로그**(`like_histories`)를 두고, 상태가 실제로 전이될 때만(실 insert/delete 시) 이벤트를 기록한다. 분석 파이프라인으로 확장할 경우 outbox + Kafka(`commerce-streamer`)로 비동기 적재하는 경로를 검토한다.

## 참고

- 관련 파일:
  - `domain/like/LikeModel.java`
  - `domain/like/LikeRepository.java`
  - `application/like/LikeService.java`
  - `infrastructure/like/LikeJpaRepository.java`
- 구현 메모: hard delete 전환을 적용했다. `BaseEntity` 는 `id`/생성·수정 시각 관리용으로 유지하되 `@SQLRestriction` 을 제거했다. 취소는 `LikeRepository.delete()` 로 물리 삭제하며, `(user_id, product_id)` unique 제약을 추가했다.
