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

### 9. 테스트

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

### 0. 사전 변경

- [x] `CommerceApiApplication`에 `@EnableScheduling` 추가
- [x] `ProductStatsService.increaseLikeCount()` / `decreaseLikeCount()`에 `@Transactional` 추가

### 1. LikeEventType enum

- [x] `domain/like/LikeEventType.java` 생성
  - [x] `LIKED_EVENT`, `UNLIKED_EVENT`

### 2. OutboxStatus enum

- [x] `domain/like/OutboxStatus.java` 생성
  - [x] `PENDING`, `DONE`

### 3. LikeOutboxModel 엔티티

- [x] `domain/like/LikeOutboxModel.java` 생성
  - [x] `productId`, `eventType`(LikeEventType), `status`(OutboxStatus) 필드
  - [x] `BaseEntity` 상속

### 4. LikeOutboxRepository

- [x] `domain/like/LikeOutboxRepository.java` 생성
  - [x] `save(LikeOutboxModel)`
  - [x] `findAllByStatusOrderByIdAsc(OutboxStatus)` — id ASC 정렬로 발생 순서 보장
  - [x] `markDoneIfPending(Long id)` — `UPDATE WHERE status='PENDING'`, 멱등성 보장
- [x] `infrastructure/like/LikeOutboxJpaRepository.java` 생성
- [x] `infrastructure/like/LikeOutboxRepositoryImpl.java` 생성

### 5. LikeOutboxService

- [x] `domain/like/LikeOutboxService.java` 생성
  - [x] `record(Long productId, LikeEventType eventType)` — PENDING 레코드 저장
  - [x] `findPending()` — 미처리 목록 조회
  - [x] `markDoneIfPending(Long id)` — 멱등성 가드, `@Transactional`

### 6. LikeFacade 변경

- [x] `like()`: `productStatsService.increaseLikeCount()` → `likeOutboxService.record(productId, LikeEventType.LIKED_EVENT)`
- [x] `unlike()`: `productStatsService.decreaseLikeCount()` → `likeOutboxService.record(productId, LikeEventType.UNLIKED_EVENT)`

### 7. LikeOutboxProcessor (릴레이)

- [x] `application/like/LikeOutboxProcessor.java` 생성
  - [x] `@Scheduled(fixedDelay = 1000)`
  - [x] PENDING outbox 조회 → `ApplicationEventPublisher.publishEvent(LikeCountChangedEvent)` 발행만 담당

### 8. LikeCountChangedEvent

- [x] `application/like/LikeCountChangedEvent.java` 생성
  - [x] `record LikeCountChangedEvent(Long outboxId, Long productId, LikeEventType eventType)`

### 9. LikeOutboxEventListener (컨슈머)

- [x] `application/like/LikeOutboxEventListener.java` 생성
  - [x] `@EventListener` + `@Transactional`
  - [x] `markDoneIfPending()` false면 조기 종료 — 중복 이벤트 멱등 처리
  - [x] `productStatsService.increase/decreaseLikeCount()` — markDoneIfPending과 같은 트랜잭션으로 원자적 커밋

### 10. 테스트

- [x] `LikeFacadeIntegrationTest` — like 후 `processor.process()` 호출로 `product_stats.like_count` 반영 검증
- [x] `LikeOutboxProcessorIntegrationTest` — `processor.process()` 직접 호출 후 `product_stats.like_count` 반영 및 outbox DONE 전환 확인
