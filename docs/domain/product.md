# Product 도메인 비즈니스 규칙

## 상품 등록

### 입력 필드

| 필드 | 타입 | 제약조건 |
|---|---|---|
| brandId | Long | null 불가, 존재하는 브랜드여야 함 |
| name | String | null/blank 불가, 최대 50자 |
| description | String | null/blank 불가, 최대 200자 |
| price | Long | null 불가, 1 이상 (price > 0) |
| initialStock | Integer | null 불가, 0 이상 (stock >= 0) |

> `likeCount`는 등록 시 0으로 초기화. 입력값 아님.

### 비즈니스 규칙 (Entity)

- `name`: null/blank 불가, 50자 초과 시 `BAD_REQUEST`
- `description`: null/blank 불가, 200자 초과 시 `BAD_REQUEST`
- `price`: null 불가, 0 이하 시 `BAD_REQUEST`
- `likeCount`: 초기값 0, 0 미만 불가 (`BAD_REQUEST`)
- 상품명 중복: **허용** (브랜드가 다르든 같든 동일 이름 등록 가능)

### 비즈니스 규칙 (DomainService)

- `brandId`가 실제 존재하는(삭제되지 않은) 브랜드인지 확인 → `NOT_FOUND`

### 유스케이스 흐름 (ApplicationService)

1. `BrandDomainService.getBrand(brandId)` — 브랜드 존재 확인
2. `Product.create(brandId, name, description, price)` — 상품 엔티티 생성 (Entity 내부 검증)
3. `ProductRepository.save(product)` — 상품 저장
4. `StockService.createStock(product.getId(), initialStock)` — 재고 생성

### 트랜잭션 경계

- 상품 저장 + 재고 생성을 **하나의 트랜잭션**으로 묶음
- 이유: 상품만 저장되고 재고가 없으면 시스템 불일치 상태 발생
- `@Transactional` 위치: `ProductApplicationService.createProduct()`

### 접근 제어

- 어드민만 가능 (`X-Loopers-Ldap: loopers.admin`)
- `AdminInterceptor`가 `/api-admin/**` 경로에서 일괄 처리

---

## 상품 목록 조회 (고객)

### 입력 필드
- `brandId`: 선택, 특정 브랜드 상품만 필터링
- `sort`: 필수, `latest`(기본값) / `price_asc` / `likes_desc`
- `page`: 기본값 0
- `size`: 기본값 20

### 비즈니스 규칙
- 없음 (단순 페이징 조회)
- `sort` 값이 정의된 값 외의 경우 → `BAD_REQUEST`

### 유스케이스 흐름
1. `ProductRepository.findAllWithBrand(brandId, sort, pageable)` — JOIN 쿼리로 브랜드명 포함 조회
2. 각 상품의 재고 조회 → `inStock = stock.quantity > 0`
3. 결과 반환

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 인증 불필요

### 응답 필드
- `id`, `name`, `description`, `price`, `brandName`, `likeCount`, `inStock`

---

## 상품 상세 조회 (고객)

### 입력 필드
- `productId`: Path variable

### 비즈니스 규칙 (DomainService)
- 존재하지 않거나 삭제된 상품 → `NOT_FOUND`

### 유스케이스 흐름
1. `ProductRepository.findByIdWithBrand(productId)` — JOIN 쿼리로 브랜드명 포함 조회
2. 재고 조회 → `inStock = stock.quantity > 0`
3. 결과 반환

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 인증 불필요

### 응답 필드
- `id`, `name`, `description`, `price`, `brandName`, `likeCount`, `inStock`

---

## 상품 목록 조회 (어드민)

### 입력 필드
- `brandId`: 선택, 특정 브랜드 상품만 필터링
- `page`: 기본값 0
- `size`: 기본값 20

### 비즈니스 규칙
- 없음

### 유스케이스 흐름
1. `ProductRepository.findAllWithBrand(brandId, pageable)` — JOIN 쿼리로 브랜드명 포함 조회
2. 각 상품의 재고 수량 조회
3. 결과 반환

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 어드민 전용 (`X-Loopers-Ldap: loopers.admin`)

### 응답 필드
- `id`, `name`, `description`, `price`, `brandId`, `brandName`, `likeCount`, `stock`(수량), `createdAt`, `updatedAt`

---

## 상품 상세 조회 (어드민)

### 입력 필드
- `productId`: Path variable

### 비즈니스 규칙 (DomainService)
- 존재하지 않거나 삭제된 상품 → `NOT_FOUND`

### 유스케이스 흐름
1. `ProductRepository.findByIdWithBrand(productId)` — JOIN 쿼리로 브랜드명 포함 조회
2. 재고 수량 조회
3. 결과 반환

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 어드민 전용 (`X-Loopers-Ldap: loopers.admin`)

### 응답 필드
- `id`, `name`, `description`, `price`, `brandId`, `brandName`, `likeCount`, `stock`(수량), `createdAt`, `updatedAt`
