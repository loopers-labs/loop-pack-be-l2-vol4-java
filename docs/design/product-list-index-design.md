# 상품 목록 조회 인덱스 설계

## 배경

상품 목록 API는 브랜드 필터(optional) × 정렬 조건(LATEST / PRICE_ASC / LIKES_DESC) 조합 6가지를 지원한다.
10만 건 기준 인덱스 없이는 전 조합에서 풀스캔 + filesort가 발생했다.

`@SQLRestriction("deleted_at IS NULL")`로 인해 `deleted_at IS NULL` 조건이 모든 쿼리에 자동으로 붙는다.

## 제약 조건

**Leftmost Prefix Rule**: MySQL 복합 인덱스는 왼쪽 컬럼부터 순서대로 활용한다.
중간 컬럼을 등치 조건 없이 건너뛰면 이후 컬럼은 인덱스에서 활용되지 않는다.

**Partial Index 미지원**: MySQL은 `WHERE deleted_at IS NULL` 조건부 인덱스를 지원하지 않는다.
PostgreSQL이라면 인덱스 컬럼에서 `deleted_at`을 제외할 수 있지만, 이 환경에서는 컬럼으로 직접 포함하는 것이 유일한 방법이다.

## 설계 원칙

**인덱스가 WHERE 조건과 ORDER BY를 동시에 커버해야 한다.**

이 조건이 충족되면 MySQL은 인덱스를 순방향 또는 역순(Backward index scan)으로 읽어 LIMIT만큼만 가져오고 멈춘다. filesort가 사라진다.

## 인덱스 구성

필터 유무에 따라 인덱스 구조가 달라진다.

### 브랜드 필터 없음

`(deleted_at, 정렬컬럼)` 구조.

`brand_id`를 인덱스에 포함하면 브랜드 필터 없는 쿼리에서 `brand_id`를 건너뛰게 된다.
Leftmost Prefix Rule에 의해 `brand_id` 이후의 정렬컬럼을 인덱스에서 활용할 수 없어 filesort가 잔존한다.
따라서 `deleted_at`으로 스캔 범위를 좁힌 뒤 바로 정렬컬럼 순서로 읽는 구조로 설계한다.

```sql
CREATE INDEX idx_product_deleted_at_created_at ON product (deleted_at, created_at);
CREATE INDEX idx_product_deleted_at_price       ON product (deleted_at, price);
CREATE INDEX idx_product_deleted_at_like_count  ON product (deleted_at, like_count);
```

### 브랜드 필터 있음

`(brand_id, deleted_at, 정렬컬럼)` 구조.

`brand_id` 등치 조건 → `deleted_at IS NULL` → 정렬컬럼 순서로 세 조건 모두 인덱스 안에서 처리한다.

`deleted_at`을 제외하고 `(brand_id, 정렬컬럼)`만 두면, brand_id로 걸러낸 행마다 `deleted_at IS NULL`을 테이블에서 확인해야 한다.
`(brand_id, deleted_at, 정렬컬럼)`으로 구성하면 인덱스 안에서 LIMIT만큼 채우는 순간 바로 종료된다.

```sql
CREATE INDEX idx_product_brand_id_deleted_at_created_at ON product (brand_id, deleted_at, created_at);
CREATE INDEX idx_product_brand_id_deleted_at_price      ON product (brand_id, deleted_at, price);
CREATE INDEX idx_product_brand_id_deleted_at_like_count ON product (brand_id, deleted_at, like_count);
```

## 트레이드오프

인덱스 6개로 쓰기 오버헤드가 증가한다. `like_count` 변경(좋아요 등록/취소) 시 관련 인덱스 2개가 함께 갱신된다.

## 결과

6개 조합 전체에서 filesort 제거. API 응답시간 19~61% 단축 (`docs/week5/performance.md` 참고).

## 참고

- `docs/week5/performance.md` — 인덱스 적용 전후 EXPLAIN 및 API 응답시간 실측 결과