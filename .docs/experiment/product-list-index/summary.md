# 상품 목록 조회 성능 개선: 브랜드 필터 + 좋아요순 정렬 인덱스 최적화

> 상품 10만 건에서 "브랜드 필터 + 좋아요순" 조회를 인덱스로 개선한 AS-IS / TO-BE 기록.

## TL;DR

복합 인덱스 `(brand_id, like_count)` 하나로 **서버 실행시간 55.4ms → 0.215ms (≈258×)**, 검사 행수 **100,000 → 21**, `filesort` 제거.
단, **wall-clock 은 1.56× 밖에 안 줄었다** — 그 괴리가 이 글의 핵심이다.

---

## 1. 문제

```http
GET /api/v1/products?brandId=1&sort=LIKES_DESC&page=0&size=20
```

대응 쿼리:
```sql
SELECT * FROM product
 WHERE deleted_at IS NULL AND brand_id = ?
 ORDER BY like_count DESC, id DESC
 LIMIT 0, 20;
```

`like_count` 비정규화·정렬 기능은 이미 있었지만, **인덱스가 PK뿐**이라 10만 건에서 느렸다.

**측정 환경**: MySQL 8.0 (실 DB, H2 아님) · 상품 100,000건 · `like_count` 멱법칙 분포 · `brand_id` 1~500 skew · soft-delete ~5% · 워밍업 후 측정.

---

## 2. AS-IS — 풀스캔 + filesort

```text
-> Limit: 20  (actual time=55.4..55.4 rows=20)
  -> Sort: like_count DESC, id DESC  (actual time=55.4..55.4 rows=20)        ← 전체 정렬
    -> Filter: brand_id=1 AND deleted_at is null  (actual rows=4226)
      -> Table scan on product  (actual rows=100000)                          ← 풀스캔
```

- `type = ALL` — 인덱스가 없어 **100,000행 전체 스캔**.
- `Using filesort` — `like_count` 정렬을 인덱스로 못 줘 **거른 행 전부를 정렬** 후 20개를 잘랐다. (LIMIT가 스캔을 못 줄임)
- **서버 실행시간 55.4ms.**

---

## 3. 원인 → 선택

문제는 두 가지: ① 브랜드 필터를 받칠 인덱스가 없다, ② 정렬을 받칠 인덱스가 없다(`filesort`).

**둘 다 한 인덱스로 해결한다 → `(brand_id, like_count)`.**

- **컬럼 순서**: `brand_id`(equality)를 앞, `like_count`(sort)를 뒤. equality로 `brand_id`를 고정하면 그 구간이 `like_count` 순으로 이미 정렬돼 있어 **filesort가 사라진다**.
- **DESC 처리**: 오름차순 인덱스를 **거꾸로 읽으면**(backward scan) `like_count DESC, id DESC`가 그대로 충족된다 (별도 DESC 인덱스 불필요). `id`는 InnoDB가 보조 인덱스에 PK를 암묵 포함하므로 명시 불필요.

```java
@Table(name = "product", indexes = {
    @Index(name = "idx_product_brand_like", columnList = "brand_id, like_count")
})
```

---

## 4. TO-BE — 인덱스 적용 후

```text
-> Limit: 20  (actual time=0.208..0.215 rows=20)
  -> Filter: deleted_at is null  (actual rows=20)
    -> Index lookup on product using idx_product_brand_like (brand_id=1) (reverse)  (actual rows=21)
```

- `Sort` 노드 **소멸** (filesort 제거).
- `(reverse)` — backward scan 으로 정렬 충족.
- `actual rows=21` — **early termination**. active 20개를 채우려 21행만 읽음. (baseline의 100,000행 대비)

### 전후 비교

| 지표 | AS-IS | TO-BE | 배율 |
|---|---|---|---|
| EXPLAIN `type` | `ALL` | `ref` | — |
| `Extra` | Using where; **Using filesort** | Using where | — |
| 검사 행수 (actual rows) | 100,000 | 21 | — |
| **서버 실행시간** (`EXPLAIN ANALYZE`) | 55.4ms | 0.215ms | **≈258×** |
| wall-clock 순수 SELECT (중앙값/5회) | 549ms | 351ms | ≈1.56× |

---

## 5. 핵심 통찰 — 258× 와 1.56× 의 괴리

쿼리 작업량은 258배 줄었는데(55ms→0.2ms), wall-clock 은 1.56배만 줄었다(549ms→351ms).

→ wall-clock 의 대부분(~350ms)은 **쿼리 실행이 아니라 고정 오버헤드**(커넥션·네트워크·결과 렌더링 등 측정 환경 비용)다. 인덱스는 이 바닥을 못 건드린다.

**그래서 "인덱스가 효과 있나?"는 서버 실행시간(`EXPLAIN ANALYZE`)으로 봐야 정직하다.**
- wall-clock 만 보면 → "인덱스 별거 없네"(과소평가).
- 서버시간만 보면 → "실사용도 258배"(과장).
- **둘 다 제시하고 차이를 설명**하는 것이 정확하다.

부수 통찰:
- **선택도(selectivity)**: `brand_id=1`은 대형 브랜드(active 4,226행)인데도 전체의 4.4%만 반환 → 충분히 selective → 인덱스가 그대로 채택됐다. (반환 비율이 큰 술어, 예 `deleted_at IS NULL` 95%, 에서만 옵티마이저가 인덱스를 버린다.)
- **유즈케이스마다 인덱스가 다르다**: 브랜드 없는 전체 좋아요순은 `(brand_id, like_count)`로 못 받쳐 `(like_count)` 별도 인덱스가 필요했다. 하나로 다 못 덮는다.
- **인덱스는 공짜가 아니다**: 좋아요 1건마다 `like_count`를 포함한 모든 인덱스가 재배치된다 → 읽기 가속의 대가로 쓰기 비용을 낸다.

---

## 6. 한계

- **측정 환경 wall-clock 바닥 ~350ms** 는 클라이언트/툴 오버헤드로 추정. 실 서비스(커넥션 풀·앱 서버·직렬화)에선 구성비가 다르다.
- **deep offset** (`LIMIT 100000, 20`)은 인덱스가 있어도 비용이 남는다 → keyset(seek) pagination 별도 검토.
- `SELECT *`(엔티티 조회)라 **back-to-table 이 항상 남는다.** 조회 전용 프로젝션이면 covering(`Using index`)으로 더 줄일 여지.
- 1페이지(offset 0) 단일 측정. 페이지 깊이·동시성 부하는 범위 밖.
