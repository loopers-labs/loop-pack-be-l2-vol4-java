# like_count 개선 구현 체크리스트

> 상세 설계: [like-count-refactoring-plan.md](like-count-refactoring-plan.md)

---

## Phase 1: product_stats 집계 테이블 분리

### 1. ProductStatsModel 엔티티

- [x] `domain/product/ProductStatsModel.java` 생성
  - [x] `likeCount` 필드 (`product`는 `@OneToOne ProductModel`로 구현)
  - [x] `BaseEntity` 상속
  - [x] `@Table(name = "product_stats")` + unique constraint `(product_id)` + index `(deleted_at, like_count)`

### 2. ProductStatsRepository

- [x] `domain/product/ProductStatsRepository.java` 생성
  - [x] `save(ProductStatsModel)`
  - [x] `findByProductId(Long productId)`
  - [x] `increaseLikeCount(Long productId)` — 원자적 UPDATE, 동시성 주석 필수
  - [x] `decreaseLikeCount(Long productId)` — 원자적 UPDATE, 동시성 주석 필수
  - [x] `findPageOrderByLikeCountDesc(Pageable pageable)` — A3용
  - [x] `findPageByProductIdsOrderByLikeCountDesc(List<Long> productIds, Pageable pageable)` — B3용

### 3. 인프라 구현

- [x] `infrastructure/product/ProductStatsJpaRepository.java` 생성
  - [x] `increaseLikeCount`: `@Modifying @Query` 원자적 UPDATE
  - [x] `decreaseLikeCount`: `@Modifying @Query` 원자적 UPDATE
- [x] `infrastructure/product/ProductStatsRepositoryImpl.java` 생성
  - [x] A3 쿼리: `product_stats` 단독 `(deleted_at, like_count)` 인덱스 활용
  - [x] B3 쿼리: IN 쿼리로 구현 (`product_stats.product.id in (productIds)`). `ProductFacade`에서 brandId로 productIds 먼저 조회 후 위임하는 2-step 방식

### 4. ProductStatsService

- [x] `domain/product/ProductStatsService.java` 생성
  - [x] `create(ProductModel product)` — 상품 생성 시 초기 row 삽입
  - [x] `softDelete(Long productId)` — 상품 soft delete 시 연동
  - [x] `increaseLikeCount(Long productId)`
  - [x] `decreaseLikeCount(Long productId)`
  - [x] `getByProductId(Long productId)` — NOT_FOUND 예외
  - [x] `getMapByProductIds(Set<Long> productIds)`
  - [x] `findPage(Pageable pageable)` — A3용
  - [x] `findPageByProductIds(List<Long> productIds, Pageable pageable)` — B3용

### 5. ProductModel 변경

- [x] `likeCount` 필드 및 getter 제거
- [x] 인덱스 변경
  - [x] `idx_product_deleted_at_like_count` 제거
  - [x] `idx_product_brand_id_deleted_at_like_count` 제거
  - [x] `idx_product_brand_id_deleted_at` 추가
- [x] `ProductRepository.increaseLikeCount` / `decreaseLikeCount` 제거
- [x] `ProductJpaRepository` / `ProductRepositoryImpl` 해당 구현 제거

### 6. 상품 생성 / 삭제 연동

- [x] `ProductService.create()` — 상품 생성 시 `productStatsService.create()` 호출
- [x] `ProductService.delete()` — 상품 soft delete 시 `productStatsService.softDelete()` 같은 트랜잭션에서 호출 (product soft delete 전에 먼저 호출해야 JOIN 조건 충족)

### 7. LikeFacade 변경

- [x] `like()`: `productService.increaseLikeCount()` → `productStatsService.increaseLikeCount()`
- [x] `unlike()`: `productService.decreaseLikeCount()` → `productStatsService.decreaseLikeCount()`

### 8. 읽기 경로 변경

- [x] `ProductInfoAssembler.toInfoList()` — `productStatsService.getMapByProductIds()`로 likeCount 조립
- [x] `ProductInfo.from()` — stats 파라미터 추가, `stats.getLikeCount()` 사용
- [x] `ProductFacade.getProducts()` — 정렬 조건 분기
  - [x] 브랜드 필터 없음 + LIKES_DESC (A3): `productStatsService.findPage()` 경유
  - [x] 브랜드 필터 있음 + LIKES_DESC (B3): `productStatsService.findPageByProductIds()` 경유

### 9. 데이터 이행

- [ ] 백필 SQL 작성 및 검증
  ```sql
  INSERT INTO product_stats (product_id, like_count, created_at, updated_at)
  SELECT id, like_count, NOW(), NOW()
  FROM product
  ON DUPLICATE KEY UPDATE like_count = VALUES(like_count);
  ```
- [ ] `product.like_count` 컬럼 제거는 별도 배포로 분리

### 10. 테스트

- [x] `ProductStatsServiceTest` — 신규 단위 테스트
- [x] `LikeFacadeIntegrationTest` — given에 `ProductStatsModel` 초기 데이터 추가, `productStatsRepository`로 결과 검증
- [x] `LikeConcurrencyIntegrationTest` — 검증 대상을 `product_stats.like_count`로 변경
- [x] `ProductFacadeIntegrationTest`
  - [x] like_count 정렬 시나리오 검증 경로 변경
  - [x] 상품 soft delete 시 product_stats도 함께 soft delete 검증
- [x] `ProductModelTest` — `likeCount` 관련 테스트 케이스 제거

---

## Phase 2: 비동기 Outbox 패턴

> Phase 1 완료 후 진행

### 1. LikeOutboxModel 엔티티

- [ ] `domain/like/LikeOutboxModel.java` 생성
  - [ ] `productId`, `delta`(+1/-1), `status`(PENDING/DONE/FAILED) 필드
  - [ ] `BaseEntity` 상속

### 2. LikeOutboxRepository

- [ ] `domain/like/LikeOutboxRepository.java` 생성
  - [ ] `save(LikeOutboxModel)`
  - [ ] `findAllByStatus(OutboxStatus status)`
- [ ] `infrastructure/like/LikeOutboxJpaRepository.java` 생성
- [ ] `infrastructure/like/LikeOutboxRepositoryImpl.java` 생성

### 3. LikeOutboxService

- [ ] `domain/like/LikeOutboxService.java` 생성
  - [ ] `record(Long productId, int delta)` — PENDING 레코드 저장
  - [ ] `findPending()` — 미처리 목록 조회

### 4. LikeFacade 변경

- [ ] `like()`: `productStatsService.increaseLikeCount()` → `likeOutboxService.record(productId, +1)`
- [ ] `unlike()`: `productStatsService.decreaseLikeCount()` → `likeOutboxService.record(productId, -1)`

### 5. LikeOutboxProcessor

- [ ] `application/like/LikeOutboxProcessor.java` 생성
  - [ ] `@Scheduled(fixedDelay = 1000)`
  - [ ] PENDING outbox 조회 → `productStatsService.increase/decreaseLikeCount()` → 상태 DONE 업데이트
  - [ ] at-least-once 보장: DONE 마킹 후 삭제 또는 영구 보관 결정

### 6. 테스트

- [ ] `LikeFacadeIntegrationTest` — like 후 outbox PENDING 레코드 생성 확인
- [ ] `LikeOutboxProcessorIntegrationTest` — 프로세서 실행 후 `product_stats.like_count` 반영 및 outbox DONE 전환 확인
