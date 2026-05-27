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
