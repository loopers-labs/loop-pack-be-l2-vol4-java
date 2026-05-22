# ADR-008: likes 테이블 UNIQUE 제약 및 삭제 방식

- 날짜: 2026-05-21 (수정)
- 상태: 승인됨

## 결정

1. `likes` 테이블에 `(user_id, product_id)` 복합 UNIQUE 제약을 유지한다.
2. 좋아요 취소는 **Soft Delete** (deleted_at 설정), 재좋아요는 **Restore** (deleted_at = null) 패턴을 사용한다.

## 근거

### UNIQUE 제약 유지

좋아요 중복 방지를 애플리케이션 레벨(409 Conflict)만으로 보장하면, 동시 요청 시 두 트랜잭션이 동시에 중복 검사를 통과해 같은 `(user_id, product_id)` 행이 두 개 INSERT될 수 있다. 애플리케이션 레벨 검사는 UX를 위한 명확한 오류 응답을 제공하고, DB 레벨 UNIQUE 제약은 동시성 상황의 최후 방어선 역할을 한다. 두 계층을 함께 사용한다.

---

### 고려한 대안

#### Option 1. Hard Delete

좋아요 취소 시 해당 행을 DB에서 물리적으로 완전히 삭제하는 방식이다.

```
좋아요 취소: DELETE FROM likes WHERE user_id = ? AND product_id = ?
재좋아요:   INSERT INTO likes (user_id, product_id, ...)
```

- **장점**: 구현이 가장 단순하다. `UNIQUE(user_id, product_id)` 제약이 자연스럽게 유지되고, 재좋아요 시 항상 신규 INSERT만 수행하면 된다.
- **단점**: 좋아요 취소 이력이 DB에 전혀 남지 않는다. 향후 "유저가 좋아요를 눌렀다가 취소한 상품" 패턴을 분석하거나 추천 시스템에 활용하려 할 때 데이터가 없어 대응할 수 없다. 또한 프로젝트 전반의 Soft Delete 원칙에서 Like만 예외가 되므로 일관성이 깨진다.

---

#### Option 2. Soft Delete + UNIQUE(user_id, product_id, deleted_at)

UNIQUE 제약을 `(user_id, product_id, deleted_at)` 3컬럼 복합으로 변경하고, 취소 시 `deleted_at`을 설정하여 Soft Delete하는 방식이다. 재좋아요 시 기존 soft-deleted 행이 있어도 새로운 행을 INSERT할 수 있다.

```
(userId=1, productId=1, deleted_at=NULL)         ← 현재 활성
(userId=1, productId=1, deleted_at='2026-05-20') ← 과거 취소 이력
(userId=1, productId=1, deleted_at=NULL)         ← 재좋아요 (허용됨)
```

- **장점**: 좋아요/취소 이력이 행 단위로 누적 보존된다. 재좋아요 시 항상 신규 INSERT만 수행하므로 로직이 단순하다.
- **단점**: MySQL UNIQUE 인덱스는 NULL을 서로 다른 값으로 취급하기 때문에, `deleted_at = NULL`인 행은 복수 INSERT가 허용된다. 즉 동시에 두 요청이 들어오면 두 개의 활성 좋아요 행이 생성될 수 있다. DB 레벨에서 활성 좋아요 중복을 막을 수 없어 동시성 결함을 애플리케이션에만 의존하게 된다.

---

#### Option 3. Soft Delete + Restore (채택)

취소 시 Soft Delete(`deleted_at = now()`), 재좋아요 시 기존 soft-deleted 레코드를 복구(`deleted_at = null`)하는 패턴이다.

```
좋아요 등록 시:
  findByUserIdAndProductId (deleted_at 포함 전체 조회)
  → active(deleted_at=null) 존재: 409 Conflict
  → soft-deleted 존재: restore() [deleted_at = null]
  → 없음: save(new LikeModel)
```

- **장점**: `UNIQUE(user_id, product_id)` 제약을 그대로 유지할 수 있다. DB 레벨 중복 방지도 보장되므로 동시성 결함이 없다. Soft Delete 원칙을 지키면서 단순성과 DB 무결성을 모두 확보한다.
- **단점**: 좋아요 등록 로직이 단순 INSERT에서 조회 → 분기(restore vs insert)로 복잡해진다. 취소 이력이 별도 행으로 남지 않고 기존 행의 상태만 변경되므로, 좋아요/취소 횟수나 시점 이력은 추적할 수 없다.

---

#### Option 4. Generated Column + Partial Unique Index

MySQL 8.0+의 가상 컬럼(Generated Column)을 추가하고 해당 컬럼에 UNIQUE 인덱스를 거는 방식이다. `deleted_at IS NULL`인 경우에만 `"userId_productId"` 형태의 고유 값을 갖도록 설정하여, 취소된 행은 NULL이 되어 UNIQUE 체크를 받지 않는다.

```sql
ADD COLUMN unique_key VARCHAR(50)
  GENERATED ALWAYS AS (
    IF(deleted_at IS NULL, CONCAT(user_id, '_', product_id), NULL)
  ) VIRTUAL;

CREATE UNIQUE INDEX uq_likes_active ON likes(unique_key);
```

- **장점**: 이력 보존과 DB 레벨 중복 방지를 동시에 만족하는 가장 완전한 방식이다. 좋아요/취소를 반복해도 이력 행이 누적되며 UNIQUE 보장도 유지된다.
- **단점**: 가상 컬럼과 별도 인덱스 추가로 DB 스키마 복잡도가 높아지고, MySQL 8.0+ 문법에 종속된다. 현재 이력 분석 요구사항이 없는 단계에서 이 복잡도를 도입하는 것은 오버엔지니어링이다.

---

### Restore 패턴 동작

```
좋아요 등록 시:
  findByUserIdAndProductId (deleted_at 포함 전체 조회)
  → active(deleted_at=null) 존재: 409 Conflict
  → soft-deleted 존재: restore() [deleted_at = null]
  → 없음: save(new LikeModel)

좋아요 취소 시:
  findByUserIdAndProductId (active만 조회, deleted_at IS NULL)
  → 존재: like.delete() [deleted_at = now()]
  → 없으면: 404 Not Found
```

## 향후 고려사항

추천/랭킹 기능 추가 시 좋아요 이력(등록/취소 시점, 횟수)이 필요해질 수 있다. 이 경우 대안 4(Generated Column + Partial Unique Index)로 마이그레이션하면 기존 UNIQUE 제약 보장을 유지하면서 이력 보존이 가능해진다.
