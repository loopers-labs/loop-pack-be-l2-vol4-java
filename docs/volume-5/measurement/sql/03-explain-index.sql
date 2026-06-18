-- Stage 3 인덱스 — illustrate용 EXPLAIN 관찰 스크립트
-- 측정 DB(perf, ddl-auto:none)에 인덱스를 직접 CREATE/DROP 하며 계획 변화를 관찰한다.
-- 실행: docker exec -i docker-mysql-1 mysql -uroot -proot loopers -t < 03-explain-index.sql
-- 각 절은 인덱스를 만들고 관찰 후 DROP 해 다음 절을 깨끗한 상태에서 시작한다.

-- =====================================================================
-- 3a. 단일 인덱스의 한계 — 정렬 OR 필터 중 하나만 해결한다
-- =====================================================================

-- --- 3a-1. 정렬 인덱스 (like_count) 단독 -------------------------------
CREATE INDEX idx_like_count ON products (like_count);

-- S1(전역 좋아요순): InnoDB 보조 인덱스는 (like_count)로 물리 정렬 →
-- Backward index scan 이 ORDER BY like_count DESC, id DESC 와 정확히 일치.
-- 관찰: filesort 제거, 인덱스에서 20행만 읽음. 62.5ms → 0.216ms.
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;

-- S2(브랜드 좋아요순): 정렬은 타지만 brand_id=847 필터를 못 좁힘 →
-- 정렬 순서대로 스캔하며 brand 다른 행을 버린다(scan-and-discard).
-- 관찰: 20행을 얻으려 1,916행을 읽음(actual rows=1916), 5.06ms.
-- brand 847이 like_count 상위라 일찍 멈췄을 뿐 — 비인기 브랜드면 비용 폭발.
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;

DROP INDEX idx_like_count ON products;

-- --- 3a-2. 필터 인덱스 (brand_id) 단독 --------------------------------
CREATE INDEX idx_brand_id ON products (brand_id);

-- S2: ref 로 brand 847을 1,134행으로 좁히지만, 그 1,134행을 like_count 로
-- 정렬해야 해 Using filesort 잔존. 관찰: 1,134행 → top-N sort, 2.46ms.
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;

-- S1(전역): brand 필터가 없어 (brand_id) 인덱스는 정렬·필터 어디에도 무용.
-- 옵티마이저가 brands ALL 스캔 + Using temporary; Using filesort 로 더 나쁜 계획.
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;

DROP INDEX idx_brand_id ON products;

-- 결론: 단일 인덱스는 정렬 또는 필터 하나만 해결한다.
--   (like_count) → 정렬 커버, 필터 못 좁힘(scan-and-discard)
--   (brand_id)   → 필터 좁힘, 정렬 못 커버(filesort 잔존)
-- 필터+정렬이 동시인 S2 는 복합 인덱스 (brand_id, like_count) 가 필요 → 3b.


-- =====================================================================
-- 3b. 복합 인덱스 + leftmost prefix — 필터와 정렬을 한 인덱스로
-- =====================================================================

-- --- 3b-1. 복합 (brand_id, like_count) : S2 필터+정렬 동시 해결 -----
-- 선두 brand_id=847 → ref 로 구간 집기, 구간 내 (like_count,id) 순 정렬돼
-- 역방향 스캔이 ORDER BY 와 일치 → filesort 제거. LIMIT 20 → 20행만 읽음.
-- 관찰: ref + Backward index scan, 실측 20행, 0.90ms.
CREATE INDEX idx_brand_like_id ON products (brand_id, like_count);
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
DROP INDEX idx_brand_like_id ON products;

-- --- 3b-2. 컬럼 순서 뒤집기 (like_count, brand_id) : leftmost prefix 깨짐 ---
-- brand_id 가 인덱스에 있어도 선두가 아니라 equality 필터로 못 씀 → ref 가
-- index(풀 인덱스 스캔)로 추락, brand_id scan-and-discard(1,907행), 5.59ms.
CREATE INDEX idx_like_brand ON products (like_count, brand_id);
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
DROP INDEX idx_like_brand ON products;

-- --- 3b-3. 최신순 (created_at) : S3 filesort 제거 ------------------
-- (created_at) 역방향 스캔이 ORDER BY created_at DESC, id DESC 와 일치.
-- 관찰: Backward index scan, 실측 20행, 0.176ms (was 46.8ms).
CREATE INDEX idx_created_id ON products (created_at);
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC LIMIT 20 OFFSET 0;
DROP INDEX idx_created_id ON products;

-- 결론: 복합 인덱스는 필터+정렬을 한 번에 해결한다.
--   (brand_id, like_count) → ref + 역방향, 20행, filesort 제거 (S2 0.90ms)
--   순서가 전부 — equality 필터 컬럼을 선두에 둬야 ref 로 좁힌다(leftmost prefix).
-- 남은 비용: 테이블 본체 조회(북마크 lookup) → 커버링 인덱스 3c.


-- =====================================================================
-- 3c. 커버링 인덱스 — 북마크 lookup 제거 (Using index)
-- =====================================================================

-- --- 3c-1. 비커버링 (like_count) : Using index 없음 → 북마크 lookup 발생 ---
-- products 노드 0.856ms (20행마다 테이블 본체로 되돌아가 name/price/stock/brand_id 조회).
CREATE INDEX idx_like_count ON products (like_count);
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
DROP INDEX idx_like_count ON products;

-- --- 3c-2. 커버링 (정렬키 + SELECT/WHERE 컬럼 전부) : Using index 등장 ---
-- Covering index scan → 북마크 lookup 제거, products 노드 0.0135ms.
-- 대가: key_len=439 (name varchar(100) 포함)으로 인덱스 비대 → 쓰기/공간 비용.
-- LIMIT 20 이라 절약은 20 lookup뿐 + brands 조인은 여전히 PK lookup(전체 index-only 아님).
CREATE INDEX idx_cover_s1 ON products (like_count, id, brand_id, name, price, stock, deleted_at);
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id AND b.deleted_at IS NULL
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
DROP INDEX idx_cover_s1 ON products;

-- 결론: 커버링 인덱스는 Using index 로 북마크 lookup 을 없앤다.
--   단 인덱스 비대(쓰기/공간 비용) + LIMIT 20 이라 단건 절약은 작다 →
--   진짜 가치는 동시성 랜덤 IO 경합 감소. 최종 채택은 k6 확인 후 마무리에서 판단.


-- =====================================================================
-- 3d. 옵티마이저·통계·인덱스 무력화
-- =====================================================================
CREATE INDEX idx_brand_id ON products (brand_id);

-- --- 3d-1. ANALYZE TABLE 통계 갱신 ------------------------------------
-- 카디널리티는 랜덤 다이브 샘플링 추정(참값 1000 근처에서 흔들림: 970↔1102).
-- equality ref 추정행(1134)은 값별 index dive 라 이미 정확 → 플랜 불변.
-- ANALYZE 의 가치는 통계가 망가졌을 때(대량 적재 직후·auto-recalc off).
SELECT CARDINALITY FROM information_schema.STATISTICS
WHERE TABLE_NAME='products' AND INDEX_NAME='idx_brand_id';
EXPLAIN SELECT p.id, p.name, p.price FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 847;
ANALYZE TABLE products;
SELECT CARDINALITY FROM information_schema.STATISTICS
WHERE TABLE_NAME='products' AND INDEX_NAME='idx_brand_id';
EXPLAIN SELECT p.id, p.name, p.price FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 847;

-- --- 3d-2. 인덱스 무력화 : 비선택적이면 Full Scan 이 더 싸다 -----------
-- brand_id BETWEEN 1 AND 500 → 약 50%(50,155행) 매칭.
-- 자연 선택: ALL(full scan) 23.7ms. FORCE INDEX: range scan 47.7ms(2배 느림).
-- 매칭이 많으면 보조인덱스 5만건 + 북마크 lookup(랜덤 IO) > 순차 풀스캔.
-- 옵티마이저가 인덱스를 버린 게 옳음 → 인덱스는 선택적일 때만 이득.
EXPLAIN SELECT p.id, p.name, p.price FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id BETWEEN 1 AND 500;
EXPLAIN ANALYZE SELECT p.id, p.name, p.price FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id BETWEEN 1 AND 500;
EXPLAIN ANALYZE SELECT p.id, p.name, p.price FROM products p FORCE INDEX (idx_brand_id)
WHERE p.deleted_at IS NULL AND p.brand_id BETWEEN 1 AND 500;

DROP INDEX idx_brand_id ON products;

-- 결론: 통계는 샘플링 추정이고, 옵티마이저는 그 추정으로 비용을 비교해
--   인덱스 vs 풀스캔을 고른다. 매칭이 많으면 풀스캔이 옳다 —
--   인덱스를 거는 것만큼 "언제 안 쓰이는지"를 아는 게 중요하다.


-- =====================================================================
-- 3e. 조인 함정 해결 — b.deleted_at IS NULL 제거로 인덱스 정상화
-- =====================================================================
-- 3a~3d 는 인덱스를 하나씩 걸고 봤지만, 3e 는 최종 인덱스 세트(6개)를 전부 건
-- 상태에서 본다. 전역 목록이 인덱스를 못 타던 원인은 인덱스가 아니라 '쿼리'였다 —
-- 아래 두 줄을 고친다. (채택한 해결형만 싣는다. 제거 전 함정 plan·측정 근거는 보고서)
--   ① content: p.deleted_at IS NULL 이 b.deleted_at IS NULL 을 이미 함의
--      (브랜드 삭제 시 같은 트랜잭션에서 상품도 삭제 — BrandFacade.deleteBrand)
--      → 중복 필터 b.deleted_at IS NULL 제거(JOIN 은 b.name 위해 유지).
--   ② count: COUNT(p.id) 는 brands 를 안 쓰므로 JOIN 통째 제거.
-- 최종 세트라 이 절은 DROP 하지 않는다(깨끗한 상태면 아래 6개를 먼저 만든다).
-- 정렬 타이브레이크 id 는 명시하지 않는다 — InnoDB 가 보조 인덱스 리프에 PK(id)를
-- 자동 부착하므로 (like_count) 만으로도 ORDER BY like_count DESC, id DESC 가 충족된다.
CREATE INDEX idx_products_like_count          ON products (like_count);
CREATE INDEX idx_products_brand_id_like_count ON products (brand_id, like_count);
CREATE INDEX idx_products_created_at          ON products (created_at);
CREATE INDEX idx_products_price               ON products (price);
CREATE INDEX idx_products_brand_id_created_at ON products (brand_id, created_at);
CREATE INDEX idx_products_brand_id_price      ON products (brand_id, price);

-- --- 3e-1. content : S1 전역 좋아요순 (b.deleted_at IS NULL 제거) -------
-- 필터를 빼면 brands 는 필터 효과 0% 인 1:1 PK 매칭이 돼 옵티마이저가 products 를
-- driving 으로 잡는다 → idx_products_like_count 역방향 20행, brands 는 eq_ref PK.
-- filesort 없음. 관찰: 0.15ms (필터 남겼을 때 brands-first 풀스캔 174ms 에서 회복).
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;

-- --- 3e-2. count : 전역 총 개수 (brands JOIN 제거) ---------------------
-- COUNT(p.id) 는 b.name 도 안 쓰므로 JOIN 무용 → 제거 → products 단독 풀스캔 COUNT.
-- 관찰: 21.3ms (JOIN 남겼을 때 10만 nested loop 77.6ms 에서 회복).
-- 남는 21ms 는 전역 10만 풀스캔 자체 — deleted_at 저선택도라 인덱스로 못 줄임(3d)
-- → 캐시 단계(04) 동기.
EXPLAIN
SELECT COUNT(p.id) FROM products p
WHERE p.deleted_at IS NULL;
EXPLAIN ANALYZE
SELECT COUNT(p.id) FROM products p
WHERE p.deleted_at IS NULL;

-- --- 3e-3. content : S3 전역 최신순 (동일 해결) ------------------------
-- created_at 정렬도 전역에선 같은 함정이었다 → 필터 제거로 idx_products_created_at
-- 역방향 20행, brands eq_ref PK. 관찰: 0.12ms.
EXPLAIN
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC LIMIT 20 OFFSET 0;

-- --- 3e-4. S2 브랜드 필터 : 애초에 함정 없음 (참고) -------------------
-- brand_id=847 이면 b 는 const 조인, p 는 복합 (brand_id, like_count) ref +
-- 역방향 → content 0.63ms(20행) · count 복합 ref 1,134행(1.4ms). 전역에서만 터지던
-- 함정이라 브랜드 필터엔 무관 — 해결형으로도 동일하게 잘 탄다.
EXPLAIN ANALYZE
SELECT p.id, p.name, b.id, b.name, p.price, p.stock, p.like_count
FROM products p JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL AND p.brand_id = 847
ORDER BY p.like_count DESC, p.id DESC LIMIT 20 OFFSET 0;
EXPLAIN ANALYZE
SELECT COUNT(p.id) FROM products p
WHERE p.deleted_at IS NULL AND p.brand_id = 847;

-- 결론: 전역 목록이 인덱스를 못 타던 건 인덱스 탓이 아니라 쿼리 탓이었다.
--   content 의 b.deleted_at IS NULL 중복 필터(brands-first 오판 174ms→0.15ms),
--   count 의 불필요한 brands JOIN(10만 nested loop 77.6ms→21.3ms) — 두 줄을 고쳐
--   6개 인덱스를 그대로 둔 채 전역·브랜드 정렬이 모두 산다.
--   남는 전역 count 10만 풀스캔(~21ms)은 인덱스로 못 줄여 캐시(04)로 넘긴다.
