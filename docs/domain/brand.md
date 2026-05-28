# Brand 도메인 비즈니스 규칙

## 브랜드 등록

### 비즈니스 규칙
- 이름은 1자 이상 20자 이하
- 이름은 공백만으로 구성될 수 없다
- 이름은 중복될 수 없다

### 유스케이스 흐름
1. 이름 형식 검증 (길이, 공백)
2. 이름 중복 검증
3. Brand 생성 후 저장
4. 저장된 Brand 반환

### 트랜잭션 경계
- `validateDuplicateName()` + `save()` 가 하나의 트랜잭션으로 묶여야 한다
- 중복 체크와 저장 사이에 다른 요청이 끼어들어 동일 이름이 저장되는 것을 방지

### 접근 제어
- 어드민 전용 (`X-Loopers-Ldap: loopers.admin` 헤더)

---

## 브랜드 수정

### 입력 필드
- `brandId`: Path variable, 존재하는 브랜드 ID
- `name`: null/blank 불가, 1자 이상 20자 이하

### 비즈니스 규칙 (Entity)
- `name`: null/blank 불가, 20자 초과 시 `BAD_REQUEST`

### 비즈니스 규칙 (DomainService)
- 존재하지 않거나 삭제된 브랜드 → `NOT_FOUND`
- 수정하려는 이름이 **다른 브랜드**에 이미 존재하면 → `CONFLICT` (자기 자신은 제외)

### 유스케이스 흐름 (ApplicationService)
1. `BrandDomainService.getBrand(brandId)` — 브랜드 존재 확인
2. `BrandDomainService.validateDuplicateName(name, brandId)` — 타 브랜드 중복 검증
3. `brand.update(name)` — 이름 수정
4. JPA Dirty Checking으로 자동 저장

### 트랜잭션 경계
- `@Transactional` — 중복 체크와 수정이 하나의 트랜잭션

### 접근 제어
- 어드민 전용 (`X-Loopers-Ldap: loopers.admin`)

### 응답 필드
- `id`, `name`, `createdAt`, `updatedAt`

---

## 브랜드 삭제

### 입력 필드
- `brandId`: Path variable

### 비즈니스 규칙 (DomainService)
- 존재하지 않거나 이미 삭제된 브랜드 → `NOT_FOUND` (멱등 처리 없음)
- 주문 중/배송 중인 주문 건이 있어도 삭제 가능 — `OrderItemSnapshot`으로 주문 이력 보존

### 유스케이스 흐름 (ApplicationService)
1. `BrandDomainService.getBrand(brandId)` — 브랜드 존재 확인 (없으면 NOT_FOUND)
2. `ProductRepository.softDeleteAllByBrandId(brandId)` — 소속 상품 일괄 소프트딜리트
3. `StockRepository.softDeleteAllByProductIds(productIds)` — 해당 상품들의 재고 소프트딜리트
4. `brand.delete()` — 브랜드 소프트딜리트

### 트랜잭션 경계
- 브랜드 + 상품 + 재고 삭제를 **하나의 트랜잭션**으로 묶음
- 이유: 상품 삭제 후 브랜드 삭제 실패 시 "상품 없는 활성 브랜드" 불일치 방지

### 접근 제어
- 어드민 전용 (`X-Loopers-Ldap: loopers.admin`)

---

## 브랜드 상세 조회 (고객)

### 입력 필드
- `brandId`: Path variable, 존재하는 브랜드 ID

### 비즈니스 규칙 (DomainService)
- 존재하지 않거나 삭제된 브랜드 → `NOT_FOUND`

### 유스케이스 흐름
1. `BrandDomainService.getBrand(brandId)` — 브랜드 존재 확인 및 조회
2. 결과 반환

### 트랜잭션 경계
- `@Transactional(readOnly = true)` — 단순 조회

### 접근 제어
- 인증 불필요 (비로그인 사용자도 접근 가능)

### 응답 필드
- `id`, `name`

---

## 브랜드 목록 조회 (어드민)

### 입력 필드
- `page`: 기본값 0
- `size`: 기본값 20

### 비즈니스 규칙
- 없음 (단순 페이징 조회)

### 유스케이스 흐름
1. `BrandRepository.findAll(pageable)` — 페이징 조회
2. 결과 반환

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 어드민 전용 (`X-Loopers-Ldap: loopers.admin`)

### 응답 필드
- `id`, `name`, `createdAt`, `updatedAt`

---

## 브랜드 상세 조회 (어드민)

### 입력 필드
- `brandId`: Path variable, 존재하는 브랜드 ID

### 비즈니스 규칙 (DomainService)
- 존재하지 않거나 삭제된 브랜드 → `NOT_FOUND`

### 유스케이스 흐름
1. `BrandDomainService.getBrand(brandId)` — 브랜드 존재 확인 및 조회
2. 결과 반환

### 트랜잭션 경계
- `@Transactional(readOnly = true)`

### 접근 제어
- 어드민 전용 (`X-Loopers-Ldap: loopers.admin`)

### 응답 필드
- `id`, `name`, `createdAt`, `updatedAt`
