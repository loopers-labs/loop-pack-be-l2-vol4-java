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
