# ADR-003: 좋아요 등록에 INSERT IGNORE 채택

## 상태
Accepted

## 배경

ADR-002 에서 좋아요 도메인은 **hard delete + `(user_id, product_id)` unique 제약**으로 전환하기로 결정했다.
이 결정의 후속으로, 좋아요 등록 시 **중복 요청을 어떻게 원자적·멱등적으로 처리할 것인가**를 결정해야 한다.

unique 제약이 걸려 있으므로, 같은 `(user_id, product_id)` 를 두 번 insert 하면 제약 위반이 발생한다.
이를 어떻게 처리할지가 이 결정의 핵심이다.

또한 `like_count` 비정규화 컬럼을 관리하기 때문에, 실제로 새 row 가 삽입됐는지 여부를 반환값으로 구분해야 한다.

## 결정

좋아요 등록에 MySQL 네이티브 쿼리 `INSERT IGNORE` 를 사용하며, 영향받은 row 수(1 = 삽입 성공, 0 = 이미 존재)로 실제 삽입 여부를 반환한다.

```sql
INSERT IGNORE INTO likes (user_id, product_id, created_at, updated_at)
VALUES (:userId, :productId, NOW(6), NOW(6))
```

## 선택지

### Option A: `exists()` 체크 후 insert

등록 전 `existsByUserIdAndProductId()` 로 존재 여부를 확인하고, 없으면 `save()` 를 호출한다.

- **장점**: Spring Data JPA 표준 방식, 특정 DB 벤더에 종속되지 않음
- **단점**: check-then-act 구조라 **같은 `(user_id, product_id)` 에 대한 동시 요청 시 race condition** 발생.
  두 요청이 모두 `exists = false` 를 확인한 뒤 각자 insert 하면 두 트랜잭션 중 하나에서 unique 제약 위반 예외가 발생한다.
  발생 범위는 동일 유저가 동일 상품에 동시 요청하는 경우(버튼 중복 클릭, 네트워크 재전송)로 좁지만, 중복 요청 중 하나가 예외로 실패하여 일관된 응답을 보장할 수 없다.

### Option B: `save()` + `DataIntegrityViolationException` catch

insert 를 시도하고, unique 제약 위반 예외를 catch 해 `false` 를 반환한다.

- **장점**: Spring Data JPA 표준 방식, 특정 DB 벤더에 종속되지 않음
- **단점**:
  - `@Transactional` 메서드 내에서 예외가 발생하면 Spring 이 트랜잭션을 **rollback-only** 로 마킹한다.
    예외를 catch 해도 이 마킹은 해제되지 않아, 커밋 시점에 `UnexpectedRollbackException` 이 발생한다.
  - 이를 우회하려면 `Propagation.REQUIRES_NEW` 로 별도 트랜잭션을 분리해야 하는데, 이는 커넥션 풀에서 커넥션을 추가 점유하고 중첩 트랜잭션 관리 복잡도를 높인다.
  - 더 근본적으로, `REQUIRES_NEW` 로 like 삽입이 먼저 커밋된 뒤 외부 트랜잭션(like_count 증가)이 롤백되면 like row 는 남고 like_count 는 반영되지 않은 **데이터 불일치**가 발생한다. 단일 트랜잭션으로 원자성을 간단히 보장할 수 있는 이점을 잃고, 보상 트랜잭션으로 정합성을 사후에 맞춰야 하는 복잡도를 감수해야 한다.
  - 결국 rollback-only 문제를 피하려면 Option A(`exists` 체크) 로 돌아오게 되어 race condition 이 재등장한다.

### Option C: `INSERT IGNORE` (채택)

MySQL 전용 `INSERT IGNORE` 를 `@Query(nativeQuery = true)` 로 선언한다.
unique 제약 위반 시 예외 없이 무시하고, 영향받은 row 수로 삽입 여부를 구분한다.

- **장점**:
  - DB 단 단일 쿼리로 원자적 처리 → race condition 없음
  - 예외를 발생시키지 않으므로 트랜잭션 rollback-only 문제가 처음부터 없음
  - 삽입 성공(1) / 이미 존재(0) 를 반환값으로 명확히 구분
- **단점**: MySQL 전용 문법으로 다른 DB(PostgreSQL, H2 기본 모드 등)에서는 동작하지 않음

## 근거

**원자성·멱등성·단순함을 동시에 만족하는 표준 SQL 방법이 없다.**

- Option A 는 race condition 을 해소할 수 없다.
- Option B 는 트랜잭션 rollback-only 문제로 인해 단독으로는 동작하지 않으며, 이를 우회하면 결국 Option A 로 귀착된다.
- Option C 만이 세 가지 요구를 DB 레벨 단일 쿼리로 충족한다.

race condition 이 발생할 수 있는 범위는 **동일 `(user_id, product_id)` 에 대한 동시 요청**으로, 다른 유저의 요청은 서로 다른 row 를 다루므로 충돌이 없다.
버튼 중복 클릭이나 네트워크 재전송처럼 확률이 낮은 케이스이지만, 해결하지 않으면 중복 요청 중 하나가 예외로 실패하여 일관된 응답을 보장할 수 없다.

벤더 종속이라는 비용은 실재하지만, 이 프로젝트는 MySQL 8.0 을 명시적으로 고정하고 있고 테스트도 Testcontainers(real MySQL) 를 사용하므로 이식성 포기의 실질적 영향이 없다.

## 결과

- **긍정**: 좋아요 등록이 원자적·멱등적으로 처리되며, 동시 중복 요청에 대해 DB 수준에서 정합성이 보장된다. 트랜잭션 롤백 문제가 없다.
- **부정**: MySQL 전용 쿼리로 다른 DB 로 교체 시 해당 메서드를 함께 수정해야 한다.
- **추후 고려**: DB 교체가 필요해질 경우 PostgreSQL 의 `INSERT ... ON CONFLICT DO NOTHING` 으로 대응한다.

## 참고

- 선행 결정: [ADR-002: 좋아요 도메인의 삭제 전략으로 hard delete 채택](ADR-002-like-hard-delete.md)
- 관련 파일:
  - `domain/like/LikeRepository.java`
  - `infrastructure/like/LikeJpaRepository.java`
  - `infrastructure/like/LikeRepositoryImpl.java`
