# Like 도메인

## UC-04. 좋아요 등록

### 입력 필드
- `productId`: Not null (Path Variable)
- `loginId`: Not null (Header: X-Loopers-LoginId)

### 비즈니스 규칙 (Entity)
- `userId` / `productId` 복합 유니크 제약 (`(userId, productId)` 쌍이 유일)
- 하드딜리트 (deleted_at 없음, 물리 삭제)

### 비즈니스 규칙 (DomainService)
- `(userId, productId)` 쌍이 이미 존재하면 저장 없이 `false` 반환 (멱등)
- 저장에 성공하면 `true` 반환

### 유스케이스 흐름 (ApplicationService)
1. `MemberService.getMember(loginId)` → memberId 조회 (없으면 404)
2. `ProductDomainService.getProduct(productId)` → 상품 존재 확인 (없으면 404)
3. `LikeDomainService.addLike(memberId, productId)` → boolean 반환
4. true이면 `ProductDomainService.incrementLikeCount(productId)` 호출

### 트랜잭션 경계
- `LikeApplicationService.addLike()` → `@Transactional`
- Like 저장 + Product likeCount 증가가 하나의 원자적 단위
- 멱등(false 반환) 시에도 롤백 없이 정상 종료

### 접근 제어
- 회원만 호출 가능 (`X-Loopers-LoginId` 헤더 필수)
- 헤더 없으면 → 401 Unauthorized (미구현, 향후 추가)

---

## UC-05. 좋아요 취소

### 입력 필드
- `productId`: Not null (Path Variable)
- `loginId`: Not null (Header: X-Loopers-LoginId)

### 비즈니스 규칙 (DomainService)
- `(userId, productId)` 쌍이 존재하지 않으면 삭제 없이 `false` 반환 (멱등)
- 삭제에 성공하면 `true` 반환

### 유스케이스 흐름 (ApplicationService)
1. `MemberService.getMember(loginId)` → memberId 조회 (없으면 404)
2. `ProductDomainService.getProduct(productId)` → 상품 존재 확인 (없으면 404)
3. `LikeDomainService.removeLike(memberId, productId)` → boolean 반환
4. true이면 `ProductDomainService.decrementLikeCount(productId)` 호출

### 트랜잭션 경계
- `LikeApplicationService.removeLike()` → `@Transactional`
- Like 삭제 + Product likeCount 감소가 하나의 원자적 단위

### 접근 제어
- 회원만 호출 가능 (`X-Loopers-LoginId` 헤더 필수)

---

## UC-06. 내 좋아요 목록 조회

### 입력 필드
- `userId`: Not null (Path Variable — DB PK)
- `loginId`: Not null (Header: X-Loopers-LoginId)

### 비즈니스 규칙 (DomainService)
- 헤더의 loginId로 조회한 memberId ≠ Path의 userId → `FORBIDDEN` 403

### 유스케이스 흐름 (Facade)
1. `MemberService.getMember(loginId)` → memberId 조회 (없으면 404)
2. `LikeDomainService.getLikes(memberId, targetUserId)` → 403 체크 + Like 목록 반환
3. 각 Like의 productId로 `ProductDomainService.getProduct()` 호출 → 상품 정보
4. 각 Product의 brandId로 `BrandDomainService.getBrand()` 호출 → 브랜드명
5. `LikeInfo` 목록 조합 후 반환

### 트랜잭션 경계
- 읽기 전용, `@Transactional` 불필요 (또는 `readOnly = true`)

### 접근 제어
- 회원만 호출 가능 (`X-Loopers-LoginId` 헤더 필수)
- 타인의 좋아요 목록 조회 시 → 403 Forbidden

---

## 응답 구조 (LikeInfo)

```
LikeInfo {
    productId   : Long
    productName : String
    price       : Long
    brandName   : String
}
```

---

## 변경이 필요한 기존 도메인

### ProductDomainService (추가)
- `getProduct(Long productId): Product` — 없으면 NOT_FOUND
- `incrementLikeCount(Long productId)` — getProduct 후 product.incrementLikeCount() + save
- `decrementLikeCount(Long productId)` — getProduct 후 product.decrementLikeCount() + save

### MemberService (추가)
- `getMember(String loginId): Member` — 없으면 NOT_FOUND (비밀번호 검증 없는 단순 조회)

---

## 레이어 구조

```
LikeV1Controller
    ↓ loginId (String), productId/userId (Long)
LikeFacade
    ├── MemberService.getMember()       ← loginId → memberId 해소
    ├── LikeApplicationService          ← 등록/취소 위임
    └── getLikes() 내부에서
        ├── LikeDomainService.getLikes() ← 403 체크
        ├── ProductDomainService.getProduct()
        └── BrandDomainService.getBrand()
LikeApplicationService (@Transactional)
    ├── ProductDomainService.getProduct()
    ├── LikeDomainService.addLike() / removeLike()
    └── ProductDomainService.incrementLikeCount() / decrementLikeCount()
LikeDomainService
    └── LikeRepository
```
