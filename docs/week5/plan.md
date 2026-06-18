# 작업 계획서 (상품 목록 필터 및 정렬)

## 1. 개요
상품 목록 조회 API(`/api/v1/products`)에 브랜드 필터링 및 '좋아요 순' 정렬 기능을 추가하기 위한 작업 계획입니다.

## 2. 작업 단계

### Step 1: QueryDSL 의존성 및 설정 점검
- [ ] `build.gradle.kts`에 QueryDSL 의존성 추가 (이미 설정되어 있다면 스킵)
- [ ] QueryDSL Config 클래스 설정 확인 (`JPAQueryFactory` 빈 등록)

### Step 2: DTO 및 쿼리 조건 클래스 정의
- [ ] `ProductSearchCondition` (또는 `ProductSearchDto`) 생성
  - 필드: `Long brandId`, `String sort`
- [ ] `ProductResponseDto` 수정 또는 신규 생성
  - 필드: `id`, `brandId`, `brandName`, `name`, `price`, `likeCount`, `createdAt`

### Step 3: QueryDSL Repository 인터페이스 및 구현체 작성
- [ ] `ProductQueryRepository` 인터페이스 생성 (`findProductsByCondition`)
- [ ] `ProductQueryRepositoryImpl` 구현
  - `product`와 `product_likes` LEFT JOIN
  - `brandId` 조건 처리 (동적 쿼리)
  - `GROUP BY product.id` 적용
  - 정렬 조건 분기 (`latest`, `likes_desc`)
  - `likes_desc`인 경우 `ORDER BY COUNT(productLike.id) DESC, product.createdAt DESC` 적용

### Step 4: Application Layer (Facade) 및 Service 수정
- [ ] `ProductService`에서 `ProductQueryRepository`를 호출하도록 로직 변경

### Step 5: Controller 계층 수정
- [ ] `ProductController`의 GET `/api/v1/products` 파라미터에 `brandId`, `sort` 추가 매핑

### Step 6: 테스트 코드 작성
- [ ] **Repository 테스트:** QueryDSL 동적 쿼리 동작 검증 (Left Join, 그룹핑 카운트 정확성, 동점자 2차 정렬)
- [ ] **Service 테스트:** 조건별 올바른 Repository 메서드 호출 및 변환 검증
- [ ] **Controller 테스트 (통합/E2E):** API 응답 포맷 및 페이징 작동 확인

---

## 3. 작업 계획: 좋아요 수 비정규화(`like_count`) 및 동기화 반영

기존 2. 작업 단계의 `LEFT JOIN` 및 동적 카운트 정렬 방식을 대체하고, week4 의사결정에 따른 비관적 락 기반 단일 트랜잭션 동기화 로직을 구현합니다.

### Step 1: 스키마 및 엔티티 수정
- [ ] `schema.sql` (또는 마이그레이션 스크립트)의 `product` 테이블에 `like_count INT DEFAULT 0` 컬럼 추가
- [ ] `Product` 엔티티에 `int likeCount` 필드 추가
- [ ] `Product` 엔티티에 상태 변경 메서드 `increaseLikeCount()`, `decreaseLikeCount()` 추가

### Step 2: 상품 목록 조회 최적화 (QueryDSL 수정)
- [ ] `ProductQueryRepositoryImpl` 내 기존 `product_likes` LEFT JOIN 및 `GROUP BY` 구문 제거
- [ ] 정렬 조건 분기에서 `likes_desc`일 때 `product.likeCount.desc(), product.createdAt.desc()`로 단순화하여 정렬하도록 수정

### Step 3: Repository 내 비관적 락 조회 메서드 추가
- [ ] `ProductRepository` (및 JPA Repository)에 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 적용한 조회 메서드 추가 
  - 예: `Optional<Product> findByIdForUpdate(Long id)`

### Step 4: 좋아요 등록/취소 파사드(Facade) 동기화 로직 구현
- [ ] **좋아요 등록 로직 수정 (`LikeFacade.addLike` 등)**
  - `@Transactional` 적용
  - `Product`를 `findByIdForUpdate`로 조회 (비관적 락 획득)
  - 좋아요 이력 선조회 (멱등성 보장)
  - 신규 등록 시 `ProductLike` 저장 및 `product.increaseLikeCount()` 호출 (Dirty Checking 기반 UPDATE)
- [ ] **좋아요 취소 로직 수정 (`LikeFacade.removeLike` 등)**
  - `@Transactional` 적용
  - `Product`를 `findByIdForUpdate`로 조회 (비관적 락 획득)
  - 좋아요 이력 선조회 (멱등성 보장)
  - 기존 등록 건일 경우 `ProductLike` 삭제 및 `product.decreaseLikeCount()` 호출

### Step 5: 테스트 코드 수정 및 추가
- [ ] **Repository 테스트:** LEFT JOIN 제거 후 단순화된 조회 쿼리 정렬 검증
- [ ] **동시성 테스트 (Concurrency Test):** 10개의 스레드가 동시에 같은 상품에 좋아요 등록/취소를 요청했을 때, `like_count`가 정확하게 일치하는지 (Lost Update가 없는지) 검증