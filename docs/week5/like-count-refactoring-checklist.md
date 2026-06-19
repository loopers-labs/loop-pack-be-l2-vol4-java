# like_count 개선 구현 체크리스트

> 상세 설계: [like-count-refactoring-plan.md](like-count-refactoring-plan.md)

---

## Phase 1: product_stats 집계 테이블 분리

### 1. ProductStatsModel 엔티티

- [ ] `domain/product/ProductStatsModel.java` 생성
  - [ ] `productId`, `likeCount` 필드
  - [ ] `BaseEntity` 상속
  - [ ] `@Table(name = "product_stats")` + unique constraint `(product_id)` + index `(deleted_at, like_count)`

### 2. ProductStatsRepository

- [ ] `domain/product/ProductStatsRepository.java` 생성
  - [ ] `save(ProductStatsModel)`
  - [ ] `findByProductId(Long productId)`
  - [ ] `increaseLikeCount(Long productId)` — 원자적 UPDATE, 동시성 주석 필수
  - [ ] `decreaseLikeCount(Long productId)` — 원자적 UPDATE, 동시성 주석 필수
  - [ ] `findPageOrderByLikeCountDesc(Pageable pageable)` — A3용
  - [ ] `findPageByProductIdsOrderByLikeCountDesc(List<Long> productIds, Pageable pageable)` — B3용

### 3. 인프라 구현

- [ ] `infrastructure/product/ProductStatsJpaRepository.java` 생성
  - [ ] `increaseLikeCount`: `@Modifying @Query` 원자적 UPDATE
  - [ ] `decreaseLikeCount`: `@Modifying @Query` 원자적 UPDATE
- [ ] `infrastructure/product/ProductStatsRepositoryImpl.java` 생성
  - [ ] A3 쿼리: `product_stats` 단독 `(deleted_at, like_count)` 인덱스 활용
  - [ ] B3 쿼리: `product` 드라이빙 조인, `(brand_id, deleted_at)` 선필터링 후 like_count 정렬

### 4. ProductStatsService

- [ ] `domain/product/ProductStatsService.java` 생성
  - [ ] `create(Long productId)` — 상품 생성 시 초기 row 삽입
  - [ ] `softDelete(Long productId)` — 상품 soft delete 시 연동
  - [ ] `increaseLikeCount(Long productId)`
  - [ ] `decreaseLikeCount(Long productId)`
  - [ ] `getByProductId(Long productId)` — NOT_FOUND 예외
  - [ ] `getMapByProductIds(Set<Long> productIds)`
  - [ ] `findPage(Pageable pageable)` — A3용
  - [ ] `findPageByProductIds(List<Long> productIds, Pageable pageable)` — B3용

### 5. ProductModel 변경

- [ ] `likeCount` 필드 및 getter 제거
- [ ] 인덱스 변경
  - [ ] `idx_product_deleted_at_like_count` 제거
  - [ ] `idx_product_brand_id_deleted_at_like_count` 제거
  - [ ] `idx_product_brand_id_deleted_at` 추가
- [ ] `ProductRepository.increaseLikeCount` / `decreaseLikeCount` 제거
- [ ] `ProductJpaRepository` / `ProductRepositoryImpl` 해당 구현 제거

### 6. 상품 생성 / 삭제 연동

- [ ] `ProductService.create()` 또는 `ProductFacade` — 상품 생성 시 `productStatsService.create()` 호출
- [ ] `ProductService.delete()` 또는 `ProductFacade` — 상품 soft delete 시 `productStatsService.softDelete()` 같은 트랜잭션에서 호출

### 7. LikeFacade 변경

- [ ] `like()`: `productService.increaseLikeCount()` → `productStatsService.increaseLikeCount()`
- [ ] `unlike()`: `productService.decreaseLikeCount()` → `productStatsService.decreaseLikeCount()`

### 8. 읽기 경로 변경

- [ ] `ProductInfoAssembler.toInfoList()` — `productStatsService.getMapByProductIds()`로 likeCount 조립
- [ ] `ProductInfo.from()` — `product.getLikeCount()` → `stats.getLikeCount()`
- [ ] `ProductFacade.findProducts()` — 정렬 조건 분기
  - [ ] 브랜드 필터 없음 + LIKES_DESC (A3): `productStatsService.findPage()` 경유
  - [ ] 브랜드 필터 있음 + LIKES_DESC (B3): `productStatsService.findPageByProductIds()` 경유

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

- [ ] `ProductStatsServiceTest` — 신규 단위 테스트
- [ ] `LikeFacadeIntegrationTest` — given에 `ProductStatsModel` 초기 데이터 추가, `productStatsRepository`로 결과 검증
- [ ] `LikeConcurrencyIntegrationTest` — 검증 대상을 `product_stats.like_count`로 변경
- [ ] `ProductFacadeIntegrationTest`
  - [ ] like_count 정렬 시나리오 검증 경로 변경
  - [ ] 상품 soft delete 시 product_stats도 함께 soft delete 검증
- [ ] `ProductModelTest` — `likeCount` 관련 테스트 케이스 제거

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
