# Loopers 이커머스 — 시퀀스 다이어그램 (개발자용)

> **이 문서는 개발자를 위한 기술 구현 다이어그램이다.**  
> Controller → Facade → Service → Repository 레이어 상세를 포함한다.  
> 서비스 흐름 개요(기획자·비개발자용)는 [`02-sequence-diagrams-overview.md`](./02-sequence-diagrams-overview.md)를 참고한다.
>
> `00-domain-spec.md` · `01-requirements.md` 기반. 레이어 의존 방향은 `ArchitectureTest.java` 준수.

## 레이어 구조

```
interfaces          application         domain                  infrastructure
Controller    →     Facade        →     Service           ←     RepositoryImpl
                    (*Info 반환)         (*Model 반환)             (JPA 구현체)
                                        Repository (interface)
```

- Controller → Facade만 호출 (Service 직접 호출 금지)
- Facade → 단일 또는 복수 Service 조율
- Service → Repository 인터페이스만 의존

---

## API 전체 목록 (24개)

| # | Method | URI | 인증 | 단위 | 설명 |
|---|--------|-----|------|------|------|
| 1 | POST | `/api/v1/users` | X | 로직 | 회원가입 |
| 2 | GET | `/api/v1/users/me` | O | 로직 | 내 정보 조회 |
| 3 | PUT | `/api/v1/users/password` | O | 로직 | 비밀번호 변경 |
| 4 | GET | `/api/v1/brands/{brandId}` | X | 로직 | 브랜드 상세 조회 |
| 5 | GET | `/api/v1/products` | X | 로직 | 상품 목록 조회 (필터·정렬) |
| 6 | GET | `/api/v1/products/{productId}` | X | 로직 | 상품 상세 조회 |
| 7 | GET | `/api-admin/v1/brands` | Admin | 로직 | 브랜드 목록 조회 |
| 8 | GET | `/api-admin/v1/brands/{brandId}` | Admin | 로직 | 브랜드 상세 조회 |
| 9 | POST | `/api-admin/v1/brands` | Admin | 로직 | 브랜드 등록 |
| 10 | PUT | `/api-admin/v1/brands/{brandId}` | Admin | 로직 | 브랜드 수정 |
| 11 | DELETE | `/api-admin/v1/brands/{brandId}` | Admin | 로직 | 브랜드 삭제 (상품 cascade) |
| 12 | GET | `/api-admin/v1/products` | Admin | 로직 | 상품 목록 조회 |
| 13 | GET | `/api-admin/v1/products/{productId}` | Admin | 로직 | 상품 상세 조회 |
| 14 | POST | `/api-admin/v1/products` | Admin | 로직 | 상품 등록 (브랜드 유효성 포함) |
| 15 | PUT | `/api-admin/v1/products/{productId}` | Admin | 로직 | 상품 수정 (brandId 수정 불가) |
| 16 | DELETE | `/api-admin/v1/products/{productId}` | Admin | 로직 | 상품 삭제 |
| 17 | POST | `/api/v1/products/{productId}/likes` | O | **어그리거트** | 좋아요 등록 |
| 18 | DELETE | `/api/v1/products/{productId}/likes` | O | **어그리거트** | 좋아요 취소 |
| 19 | GET | `/api/v1/users/{userId}/likes` | O | 로직 | 좋아요 상품 목록 |
| 20 | POST | `/api/v1/orders` | O | **어그리거트** | 주문 요청 |
| 21 | GET | `/api/v1/orders` | O | 로직 | 주문 목록 조회 |
| 22 | GET | `/api/v1/orders/{orderId}` | O | 로직 | 주문 상세 조회 |
| 23 | GET | `/api-admin/v1/orders` | Admin | 로직 | 전체 주문 목록 |
| 24 | GET | `/api-admin/v1/orders/{orderId}` | Admin | 로직 | 주문 상세 조회 |

---

## 1. 유저

### 1-1. 회원가입 [로직 단위]

`POST /api/v1/users` — 인증 불필요

```mermaid
sequenceDiagram
    actor Client
    participant UC as UserController
    participant UF as UserFacade
    participant US as UserService
    participant UR as UserRepository

    Client->>UC: POST /api/v1/users<br/>{loginId, password, name}

    UC->>UF: createUser(loginId, password, name)

    UF->>US: checkLoginIdDuplication(loginId)
    US->>UR: existsByLoginId(loginId)
    UR-->>US: boolean
    alt 중복 LoginId
        US-->>UF: CoreException (DuplicateLoginId)
        UF-->>UC: 예외 전파
        UC-->>Client: 409 Conflict
    end

    Note over UF,US: UserModel.of() 내부에서<br/>LoginId 형식·password 8자 이상 검증 수행
    UF->>US: createUserModel(loginId, name, password)
    US->>UR: save(UserModel)
    UR-->>US: UserModel {userId}
    US-->>UF: UserModel

    UF->>UF: hashPassword(password) → encrypted
    UF->>US: changePassword(userModel, encrypted)
    US->>UR: save(UserModel)
    UR-->>US: ok

    Note over UF: UserRegistered 이벤트 발행<br/>(웰컴 쿠폰 자동 발급 — 확장 포인트)

    UF-->>UC: UserInfo
    UC-->>Client: 201 Created {userId, loginId, name}
```

---

### 1-2. 내 정보 조회 [로직 단위]

`GET /api/v1/users/me` — 유저 인증 필요

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant UC as UserController
    participant UF as UserFacade
    participant US as UserService
    participant UR as UserRepository

    User->>AF: GET /api/v1/users/me<br/>X-Loopers-LoginId + X-Loopers-LoginPw

    AF->>UR: findByLoginId(loginId)
    alt 유저 미존재 또는 비밀번호 불일치
        AF-->>User: 401 Unauthorized
    end
    AF->>UC: 요청 전달 (userId)

    UC->>UF: getUserInfo(userId)
    UF->>US: getUserModel(userId)
    US->>UR: findById(userId)
    UR-->>US: UserModel
    US-->>UF: UserModel

    UF-->>UC: UserInfo
    UC-->>User: 200 OK {userId, loginId, name}
```

---

### 1-3. 비밀번호 변경 [로직 단위]

`PUT /api/v1/users/password` — 유저 인증 필요

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant UC as UserController
    participant UF as UserFacade
    participant US as UserService
    participant UR as UserRepository

    User->>AF: PUT /api/v1/users/password<br/>X-Loopers-LoginId + X-Loopers-LoginPw<br/>{currentPassword, newPassword}

    AF->>UR: findByLoginId(loginId)
    alt 인증 실패
        AF-->>User: 401 Unauthorized
    end
    AF->>UC: 요청 전달 (userId)

    UC->>UF: changePassword(userId, currentPassword, newPassword)
    UF->>US: getUserModel(userId)
    US->>UR: findById(userId)
    UR-->>US: UserModel {hashedPassword}
    US-->>UF: UserModel

    UF->>UF: userModel.validPasswordChange(currentPassword, bcrypt::matches)
    alt 현재 비밀번호 불일치
        UF-->>UC: CoreException (PasswordMismatch)
        UC-->>User: 400 Bad Request
    end

    UF->>UF: hashPassword(newPassword) → encrypted
    UF->>US: changePassword(userModel, encrypted)
    US->>UR: save(UserModel)
    UR-->>US: ok

    UF-->>UC: ok
    UC-->>User: 200 OK
```

---

## 2. 브랜드·상품 — 대고객

### 2-1. 브랜드 상세 조회 [로직 단위]

`GET /api/v1/brands/{brandId}` — 인증 불필요

```mermaid
sequenceDiagram
    actor Client
    participant BC as BrandController
    participant BF as BrandFacade
    participant BS as BrandService
    participant BR as BrandRepository

    Client->>BC: GET /api/v1/brands/{brandId}

    BC->>BF: getBrand(brandId)
    BF->>BS: getBrandModel(brandId)
    BS->>BR: findById(brandId)
    alt 브랜드 미존재
        BR-->>BS: empty
        BS-->>BF: CoreException (BrandNotFound)
        BF-->>BC: 예외 전파
        BC-->>Client: 404 Not Found
    end
    BR-->>BS: BrandModel
    BS-->>BF: BrandModel

    BF-->>BC: BrandInfo
    BC-->>Client: 200 OK {brandId, name}
```

---

### 2-2. 상품 목록 조회 [로직 단위]

`GET /api/v1/products` — 인증 불필요, 필터·정렬·페이지 지원

```mermaid
sequenceDiagram
    actor Client
    participant PC as ProductController
    participant PF as ProductFacade
    participant PS as ProductService
    participant PR as ProductRepository

    Client->>PC: GET /api/v1/products<br/>?category=BACKEND&level=INTERMEDIATE<br/>&brandId=1&sort=latest&page=0&size=20

    PC->>PF: findProducts(category?, level?, brandId?, sort, pageable)
    PF->>PS: findProducts(category?, level?, brandId?, sort, pageable)

    Note over PS: sort 기본값: latest<br/>선택: price_asc / likes_desc

    PS->>PR: findAllByFilter(category?, level?, brandId?, sort, pageable)
    PR-->>PS: Page<ProductModel>
    PS-->>PF: Page<ProductModel>

    Note over PF: 대고객 DTO 변환<br/>stock · isbn · status 제외

    PF-->>PC: Page<ProductInfo>
    PC-->>Client: 200 OK {content[], totalElements, totalPages}
```

---

### 2-3. 상품 상세 조회 [로직 단위]

`GET /api/v1/products/{productId}` — 인증 불필요

```mermaid
sequenceDiagram
    actor Client
    participant PC as ProductController
    participant PF as ProductFacade
    participant PS as ProductService
    participant PR as ProductRepository

    Client->>PC: GET /api/v1/products/{productId}

    PC->>PF: getProduct(productId)
    PF->>PS: getProductModel(productId)
    PS->>PR: findById(productId)
    alt 상품 미존재 또는 DELETED
        PR-->>PS: empty
        PS-->>PF: CoreException (ProductNotFound)
        PF-->>PC: 예외 전파
        PC-->>Client: 404 Not Found
    end
    PR-->>PS: ProductModel
    PS-->>PF: ProductModel

    Note over PF: 대고객 DTO 변환<br/>stock · isbn · status 제외

    PF-->>PC: ProductInfo
    PC-->>Client: 200 OK {productId, name, author, category, level, price, likeCount, brandId, brandName}
```

---

## 3. 브랜드·상품 — 어드민

> 모든 어드민 API는 `X-Loopers-Ldap: loopers.admin` 헤더를 `AdminAuthFilter`가 검증한다.  
> 인증 실패 시 401 반환. 이후 다이어그램에서는 인증 통과를 전제로 표기한다.

### 3-1. 브랜드 목록 조회 [로직 단위]

`GET /api-admin/v1/brands?page=0&size=20` — 어드민 인증

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant BC as AdminBrandController
    participant BF as AdminBrandFacade
    participant BS as BrandService
    participant BR as BrandRepository

    Admin->>AF: GET /api-admin/v1/brands?page=0&size=20<br/>X-Loopers-Ldap: loopers.admin
    alt 인증 실패
        AF-->>Admin: 401 Unauthorized
    end
    AF->>BC: 요청 전달

    BC->>BF: getBrands(pageable)
    BF->>BS: getBrands(pageable)
    BS->>BR: findAll(pageable)
    BR-->>BS: Page<BrandModel>
    BS-->>BF: Page<BrandModel>

    BF-->>BC: Page<BrandInfo>
    BC-->>Admin: 200 OK {content[], totalElements, totalPages}
```

---

### 3-2. 브랜드 상세 조회 [로직 단위]

`GET /api-admin/v1/brands/{brandId}` — 어드민 인증

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant BC as AdminBrandController
    participant BF as AdminBrandFacade
    participant BS as BrandService
    participant BR as BrandRepository

    Admin->>AF: GET /api-admin/v1/brands/{brandId}<br/>X-Loopers-Ldap: loopers.admin
    AF->>BC: 인증 통과

    BC->>BF: getBrand(brandId)
    BF->>BS: getBrandModel(brandId)
    BS->>BR: findById(brandId)
    alt 브랜드 미존재
        BR-->>BS: empty
        BS-->>BF: CoreException (BrandNotFound)
        BF-->>BC: 404 Not Found
    end
    BR-->>BS: BrandModel
    BS-->>BF: BrandModel

    BF-->>BC: BrandInfo
    BC-->>Admin: 200 OK {brandId, name, status}
```

---

### 3-3. 브랜드 등록 [로직 단위]

`POST /api-admin/v1/brands` — 어드민 인증

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant BC as AdminBrandController
    participant BF as AdminBrandFacade
    participant BS as BrandService
    participant BR as BrandRepository

    Admin->>AF: POST /api-admin/v1/brands<br/>X-Loopers-Ldap: loopers.admin<br/>{name}
    AF->>BC: 인증 통과

    BC->>BF: createBrand(name)
    BF->>BS: createBrandModel(name)

    Note over BS: BrandModel.of(name) — 이름 형식 검증
    BS->>BR: save(BrandModel)
    BR-->>BS: BrandModel {brandId}
    BS-->>BF: BrandModel

    BF-->>BC: BrandInfo
    BC-->>Admin: 201 Created {brandId, name}
```

---

### 3-4. 브랜드 수정 [로직 단위]

`PUT /api-admin/v1/brands/{brandId}` — 어드민 인증

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant BC as AdminBrandController
    participant BF as AdminBrandFacade
    participant BS as BrandService
    participant BR as BrandRepository

    Admin->>AF: PUT /api-admin/v1/brands/{brandId}<br/>X-Loopers-Ldap: loopers.admin<br/>{name}
    AF->>BC: 인증 통과

    BC->>BF: updateBrand(brandId, name)
    BF->>BS: getBrandModel(brandId)
    BS->>BR: findById(brandId)
    alt 브랜드 미존재
        BR-->>BS: empty
        BS-->>BF: CoreException (BrandNotFound)
        BF-->>BC: 404 Not Found
    end
    BR-->>BS: BrandModel
    BS-->>BF: BrandModel

    BF->>BS: updateBrand(brandModel, name)
    Note over BS: brandModel.update(name)
    BS->>BR: save(BrandModel)
    BR-->>BS: BrandModel
    BS-->>BF: BrandModel

    BF-->>BC: BrandInfo
    BC-->>Admin: 200 OK {brandId, name}
```

---

### 3-5. 브랜드 삭제 [로직 단위] — 소속 상품 cascade 삭제

`DELETE /api-admin/v1/brands/{brandId}` — 어드민 인증, R2: 소속 상품 전체 삭제

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant BC as AdminBrandController
    participant BF as AdminBrandFacade
    participant BS as BrandService
    participant PS as ProductService
    participant LS as LikeService
    participant BR as BrandRepository
    participant PR as ProductRepository
    participant LR as LikeRepository

    Admin->>AF: DELETE /api-admin/v1/brands/{brandId}<br/>X-Loopers-Ldap: loopers.admin
    AF->>BC: 인증 통과

    BC->>BF: deleteBrand(brandId)
    BF->>BS: getBrandModel(brandId)
    BS->>BR: findById(brandId)
    alt 브랜드 미존재
        BR-->>BS: empty
        BS-->>BF: CoreException (BrandNotFound)
        BF-->>BC: 404 Not Found
    end
    BR-->>BS: BrandModel
    BS-->>BF: BrandModel

    Note over BF,LR: 순서: 상품 소프트 삭제 → Like 소프트 삭제 → 브랜드 소프트 삭제 (A1)
    BF->>PS: softDeleteAllByBrandId(brandId)
    PS->>PR: softDeleteAllByBrandId(brandId)
    PR-->>PS: ok
    PS-->>BF: productIds (삭제된 상품 ID 목록)

    BF->>LS: softDeleteByProductIds(productIds)
    LS->>LR: softDeleteByProductIds(productIds)
    LR-->>LS: ok
    LS-->>BF: ok

    BF->>BS: softDeleteBrand(brandModel)
    Note over BS: brandModel.softDelete()<br/>status=DELETED + deletedAt 기록
    BS->>BR: save(brandModel)
    BR-->>BS: ok
    BS-->>BF: ok

    BF-->>BC: ok
    BC-->>Admin: 204 No Content
```

---

### 3-6. 상품 목록 조회 (어드민) [로직 단위]

`GET /api-admin/v1/products?page=0&size=20&brandId={brandId}` — 어드민 인증

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant PC as AdminProductController
    participant PF as AdminProductFacade
    participant PS as ProductService
    participant PR as ProductRepository

    Admin->>AF: GET /api-admin/v1/products?page=0&size=20&brandId=1<br/>X-Loopers-Ldap: loopers.admin
    AF->>PC: 인증 통과

    PC->>PF: findProducts(brandId?, pageable)
    PF->>PS: findProducts(brandId?, pageable)
    PS->>PR: findAllByBrandId(brandId?, pageable)
    PR-->>PS: Page<ProductModel>
    PS-->>PF: Page<ProductModel>

    Note over PF: 어드민 DTO 변환<br/>stock · isbn · status 모두 포함

    PF-->>PC: Page<AdminProductInfo>
    PC-->>Admin: 200 OK {content[], totalElements, totalPages}
```

---

### 3-7. 상품 상세 조회 (어드민) [로직 단위]

`GET /api-admin/v1/products/{productId}` — 어드민 인증

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant PC as AdminProductController
    participant PF as AdminProductFacade
    participant PS as ProductService
    participant PR as ProductRepository

    Admin->>AF: GET /api-admin/v1/products/{productId}<br/>X-Loopers-Ldap: loopers.admin
    AF->>PC: 인증 통과

    PC->>PF: getProduct(productId)
    PF->>PS: getProductModel(productId)
    PS->>PR: findById(productId)
    alt 상품 미존재
        PR-->>PS: empty
        PS-->>PF: CoreException (ProductNotFound)
        PF-->>PC: 404 Not Found
    end
    PR-->>PS: ProductModel
    PS-->>PF: ProductModel

    Note over PF: 어드민 DTO 변환<br/>stock · isbn · status 포함

    PF-->>PC: AdminProductInfo
    PC-->>Admin: 200 OK {productId, brandId, isbn, name, author, category, level, price, stock, likeCount, status}
```

---

### 3-8. 상품 등록 (어드민) [로직 단위]

`POST /api-admin/v1/products` — 어드민 인증, R3: Brand ACTIVE 확인, R5: ISBN 중복 확인

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant PC as AdminProductController
    participant PF as AdminProductFacade
    participant BS as BrandService
    participant PS as ProductService
    participant BR as BrandRepository
    participant PR as ProductRepository

    Admin->>AF: POST /api-admin/v1/products<br/>X-Loopers-Ldap: loopers.admin<br/>{brandId, isbn, name, author, category, level, price, stock}
    AF->>PC: 인증 통과

    PC->>PF: createProduct(brandId, isbn, name, author, category, level, price, stock)

    PF->>BS: getBrandModel(brandId)
    BS->>BR: findById(brandId)
    alt 브랜드 미존재
        BR-->>BS: empty
        BS-->>PF: CoreException (BrandNotFound)
        PF-->>PC: 404 Not Found
    end
    BR-->>BS: BrandModel {status}
    BS-->>PF: BrandModel

    alt brand.status != ACTIVE
        PF-->>PC: CoreException (BrandNotActive)
        PC-->>Admin: 400 Bad Request
    end

    PF->>PS: checkIsbnDuplication(isbn)
    PS->>PR: existsByIsbn(isbn)
    alt ISBN 중복
        PR-->>PS: true
        PS-->>PF: CoreException (DuplicateIsbn)
        PF-->>PC: 409 Conflict
    end

    PF->>PS: createProduct(brandId, isbn, name, author, category, level, price, stock)
    PS->>PR: save(ProductModel)
    PR-->>PS: ProductModel {productId}
    PS-->>PF: ProductModel

    PF-->>PC: AdminProductInfo
    PC-->>Admin: 201 Created
```

---

### 3-9. 상품 수정 (어드민) [로직 단위]

`PUT /api-admin/v1/products/{productId}` — 어드민 인증, R4: brandId 수정 불가

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant PC as AdminProductController
    participant PF as AdminProductFacade
    participant PS as ProductService
    participant PR as ProductRepository

    Admin->>AF: PUT /api-admin/v1/products/{productId}<br/>X-Loopers-Ldap: loopers.admin<br/>{name, author, category, level, price, stock}
    Note over PC: 요청에 brandId 포함 시 무시 (R4)
    AF->>PC: 인증 통과

    PC->>PF: updateProduct(productId, name, author, category, level, price, stock)
    PF->>PS: updateProduct(productId, name, author, category, level, price, stock)
    PS->>PR: findById(productId)
    alt 상품 미존재
        PR-->>PS: empty
        PS-->>PF: CoreException (ProductNotFound)
        PF-->>PC: 404 Not Found
    end
    PR-->>PS: ProductModel

    PS->>PS: productModel.update(name, author, category, level, price, stock)
    PS->>PR: save(ProductModel)
    PR-->>PS: ProductModel
    PS-->>PF: ProductModel

    PF-->>PC: AdminProductInfo
    PC-->>Admin: 200 OK
```

---

### 3-10. 상품 삭제 (어드민) [로직 단위]

`DELETE /api-admin/v1/products/{productId}` — 어드민 인증

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant PC as AdminProductController
    participant PF as AdminProductFacade
    participant PS as ProductService
    participant PR as ProductRepository

    Admin->>AF: DELETE /api-admin/v1/products/{productId}<br/>X-Loopers-Ldap: loopers.admin
    AF->>PC: 인증 통과

    PC->>PF: deleteProduct(productId)
    PF->>PS: deleteProduct(productId)
    PS->>PR: findById(productId)
    alt 상품 미존재
        PR-->>PS: empty
        PS-->>PF: CoreException (ProductNotFound)
        PF-->>PC: 404 Not Found
    end
    PR-->>PS: ProductModel

    PS->>PR: delete(productModel)
    PR-->>PS: ok
    PS-->>PF: ok

    PF-->>PC: ok
    PC-->>Admin: 204 No Content
```

---

## 4. 좋아요

### 4-1. 좋아요 등록 [어그리거트 단위]

`POST /api/v1/products/{productId}/likes` — 유저 인증  
`LikeFacade`가 `LikeService`(Like 생성)와 `ProductService`(likeCount 증가)를 조율한다.

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant LC as LikeController
    participant LF as LikeFacade
    participant LS as LikeService
    participant PS as ProductService
    participant LR as LikeRepository
    participant PR as ProductRepository

    User->>AF: POST /api/v1/products/{productId}/likes<br/>X-Loopers-LoginId + X-Loopers-LoginPw
    AF->>LC: userId

    LC->>LF: addLike(userId, productId)

    LF->>PS: getProductModel(productId)
    PS->>PR: findById(productId)
    alt 상품 미존재
        PR-->>PS: empty
        PS-->>LF: CoreException (ProductNotFound)
        LF-->>LC: 404 Not Found
    end
    PR-->>PS: ProductModel
    PS-->>LF: ProductModel

    LF->>LS: checkDuplicateLike(userId, productId)
    LS->>LR: existsByUserIdAndProductId(userId, productId)
    alt 이미 좋아요 등록됨
        LR-->>LS: true
        LS-->>LF: CoreException (AlreadyLiked)
        LF-->>LC: 409 Conflict
    end

    LF->>LS: createLike(userId, productId)
    LS->>LR: save(LikeModel {userId, productId, likedAt})
    LR-->>LS: LikeModel
    LS-->>LF: LikeModel

    LF->>PS: incrementLikeCount(productModel)
    Note over PS: productModel.incrementLikeCount() → likeCount + 1
    PS->>PR: save(ProductModel)
    PR-->>PS: ok
    PS-->>LF: ok

    Note over LF: LikeAdded 이벤트 발행<br/>(UserActivity 기록 — 확장 포인트)

    LF-->>LC: ok
    LC-->>User: 201 Created
```

---

### 4-2. 좋아요 취소 [어그리거트 단위]

`DELETE /api/v1/products/{productId}/likes` — 유저 인증  
`LikeFacade`가 `LikeService`(Like 삭제)와 `ProductService`(likeCount 감소)를 조율한다.

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant LC as LikeController
    participant LF as LikeFacade
    participant LS as LikeService
    participant PS as ProductService
    participant LR as LikeRepository
    participant PR as ProductRepository

    User->>AF: DELETE /api/v1/products/{productId}/likes<br/>X-Loopers-LoginId + X-Loopers-LoginPw
    AF->>LC: userId

    LC->>LF: removeLike(userId, productId)

    LF->>LS: getLikeModel(userId, productId)
    LS->>LR: findByUserIdAndProductId(userId, productId)
    alt 좋아요 없음
        LR-->>LS: empty
        LS-->>LF: CoreException (LikeNotFound)
        LF-->>LC: 404 Not Found
    end
    LR-->>LS: LikeModel
    LS-->>LF: LikeModel

    LF->>LS: deleteLike(likeModel)
    LS->>LR: delete(likeModel)
    LR-->>LS: ok
    LS-->>LF: ok

    LF->>PS: decrementLikeCount(productId)
    PS->>PR: findById(productId)
    PR-->>PS: ProductModel
    Note over PS: productModel.decrementLikeCount()<br/>likeCount - 1 (최솟값 0)
    PS->>PR: save(ProductModel)
    PR-->>PS: ok
    PS-->>LF: ok

    LF-->>LC: ok
    LC-->>User: 204 No Content
```

---

### 4-3. 좋아요 상품 목록 조회 [로직 단위]

`GET /api/v1/users/{userId}/likes` — 유저 인증, R4: 본인 것만 조회 가능

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant LC as LikeController
    participant LF as LikeFacade
    participant LS as LikeService
    participant LR as LikeRepository

    User->>AF: GET /api/v1/users/{userId}/likes<br/>X-Loopers-LoginId + X-Loopers-LoginPw
    AF->>LC: requestUserId

    LC->>LF: findLikes(requestUserId, pathUserId)

    alt requestUserId != pathUserId
        LF-->>LC: CoreException (Forbidden)
        LC-->>User: 403 Forbidden
    end

    LF->>LS: findLikesByUserId(requestUserId)
    LS->>LR: findByUserId(requestUserId)
    LR-->>LS: List<LikeModel with ProductSnapshot>
    LS-->>LF: List<LikeModel>

    LF-->>LC: List<LikedProductInfo>
    LC-->>User: 200 OK [{productId, name, category, level, price, likeCount, likedAt}, ...]
```

---

## 5. 주문 — 대고객

### 5-1. 주문 요청 [어그리거트 단위]

`POST /api/v1/orders` — 유저 인증  
`OrderFacade`가 `ProductService`(재고 확인·차감)와 `OrderService`(주문 생성)를 3단계로 조율한다.

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant OC as OrderController
    participant OF as OrderFacade
    participant PS as ProductService
    participant OS as OrderService
    participant PR as ProductRepository
    participant OR as OrderRepository

    User->>AF: POST /api/v1/orders<br/>X-Loopers-LoginId + X-Loopers-LoginPw<br/>{items: [{productId, quantity}, ...]}
    AF->>OC: userId

    OC->>OF: placeOrder(userId, items)

    Note over OF: R1: items 1개 이상 검증
    alt items 비어있음
        OF-->>OC: CoreException (EmptyOrderItems)
        OC-->>User: 400 Bad Request
    end

    rect rgb(224, 242, 254)
        Note over OF,PR: Step 1 — 상품 존재 확인
        loop 각 OrderItem
            OF->>PS: getProductModel(productId)
            PS->>PR: findById(productId)
            alt 상품 미존재
                PR-->>PS: empty
                PS-->>OF: CoreException (ProductNotFound)
                OF-->>OC: 404 Not Found
            end
            PR-->>PS: ProductModel {name, price, stock}
            PS-->>OF: ProductModel
        end
    end

    rect rgb(254, 243, 199)
        Note over OF,PR: Step 2 — 재고 확인 및 차감 (R2, R3)<br/>한 건이라도 실패 시 주문 전체 실패
        loop 각 ProductModel
            OF->>PS: validateAndReduceStock(productModel, quantity)
            alt 재고 부족
                PS-->>OF: CoreException (InsufficientStock)
                OF-->>OC: 400 Bad Request
            end
            Note over PS: productModel.reduceStock(quantity)
            PS->>PR: save(ProductModel)
            PR-->>PS: ok
            PS-->>OF: ok
        end
    end

    rect rgb(220, 252, 231)
        Note over OF,OR: Step 3 — 주문 생성 + 스냅샷 저장 (R4)
        OF->>OS: createOrder(userId, itemsWithSnapshots)
        Note over OS: OrderModel.of(userId,<br/>  [{productId, productNameSnapshot,<br/>    unitPriceSnapshot, quantity}])<br/>totalAmount = Σ(unitPriceSnapshot × quantity)
        OS->>OR: save(OrderModel)
        OR-->>OS: OrderModel {orderId}
        OS-->>OF: OrderModel

        Note over OF: OrderPlaced 이벤트 발행<br/>(UserActivity 기록 — 확장 포인트)
    end

    OF-->>OC: OrderInfo {orderId}
    OC-->>User: 201 Created {orderId}
```

---

### 5-2. 주문 목록 조회 [로직 단위]

`GET /api/v1/orders?startAt=...&endAt=...` — 유저 인증, R5·R6: 본인 주문·날짜 범위 필터

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant OC as OrderController
    participant OF as OrderFacade
    participant OS as OrderService
    participant OR as OrderRepository

    User->>AF: GET /api/v1/orders?startAt=2026-01-31&endAt=2026-02-10<br/>X-Loopers-LoginId + X-Loopers-LoginPw
    AF->>OC: userId

    OC->>OF: findOrders(userId, startAt, endAt)
    OF->>OS: findOrders(userId, startAt, endAt)

    Note over OS: R5: userId로 본인 주문만 필터링<br/>R6: startAt ~ endAt 날짜 범위 적용
    OS->>OR: findByUserIdAndOrderedAtBetween(userId, startAt, endAt)
    OR-->>OS: List<OrderModel>
    OS-->>OF: List<OrderModel>

    OF-->>OC: List<OrderSummaryInfo>
    OC-->>User: 200 OK [{orderId, totalAmount, status, orderedAt, itemCount}, ...]
```

---

### 5-3. 주문 상세 조회 [로직 단위]

`GET /api/v1/orders/{orderId}` — 유저 인증, R5: 본인 주문만 조회 가능

```mermaid
sequenceDiagram
    actor User
    participant AF as AuthFilter
    participant OC as OrderController
    participant OF as OrderFacade
    participant OS as OrderService
    participant OR as OrderRepository

    User->>AF: GET /api/v1/orders/{orderId}<br/>X-Loopers-LoginId + X-Loopers-LoginPw
    AF->>OC: userId

    OC->>OF: getOrder(userId, orderId)
    OF->>OS: getOrderModel(orderId)
    OS->>OR: findById(orderId)
    alt 주문 미존재
        OR-->>OS: empty
        OS-->>OF: CoreException (OrderNotFound)
        OF-->>OC: 404 Not Found
    end
    OR-->>OS: OrderModel {userId, items[]}
    OS-->>OF: OrderModel

    alt orderModel.userId != requestUserId
        OF-->>OC: CoreException (Forbidden)
        OC-->>User: 403 Forbidden
    end

    OF-->>OC: OrderDetailInfo
    OC-->>User: 200 OK {orderId, totalAmount, status, orderedAt,<br/>items: [{productId, productNameSnapshot, unitPriceSnapshot, quantity}]}
```

---

## 6. 주문 — 어드민

### 6-1. 전체 주문 목록 조회 [로직 단위]

`GET /api-admin/v1/orders?page=0&size=20` — 어드민 인증, R7: 관리자만 전체 조회 가능

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant OC as AdminOrderController
    participant OF as AdminOrderFacade
    participant OS as OrderService
    participant OR as OrderRepository

    Admin->>AF: GET /api-admin/v1/orders?page=0&size=20<br/>X-Loopers-Ldap: loopers.admin
    alt 인증 실패
        AF-->>Admin: 401 Unauthorized
    end
    AF->>OC: 인증 통과

    OC->>OF: findAllOrders(pageable)
    OF->>OS: findAllOrders(pageable)

    Note over OS: userId 필터 없음 — 전체 주문 조회
    OS->>OR: findAll(pageable)
    OR-->>OS: Page<OrderModel>
    OS-->>OF: Page<OrderModel>

    OF-->>OC: Page<AdminOrderSummaryInfo>
    OC-->>Admin: 200 OK {content[], totalElements, totalPages}
```

---

### 6-2. 주문 상세 조회 (어드민) [로직 단위]

`GET /api-admin/v1/orders/{orderId}` — 어드민 인증, 유저 소유권 확인 없음

```mermaid
sequenceDiagram
    actor Admin
    participant AF as AdminAuthFilter
    participant OC as AdminOrderController
    participant OF as AdminOrderFacade
    participant OS as OrderService
    participant OR as OrderRepository

    Admin->>AF: GET /api-admin/v1/orders/{orderId}<br/>X-Loopers-Ldap: loopers.admin
    AF->>OC: 인증 통과

    OC->>OF: getOrder(orderId)
    OF->>OS: getOrderModel(orderId)
    OS->>OR: findById(orderId)
    alt 주문 미존재
        OR-->>OS: empty
        OS-->>OF: CoreException (OrderNotFound)
        OF-->>OC: 404 Not Found
    end
    OR-->>OS: OrderModel
    OS-->>OF: OrderModel

    Note over OF: 어드민은 userId 소유권 확인 없이 모든 주문 조회 가능

    OF-->>OC: AdminOrderDetailInfo
    OC-->>Admin: 200 OK {orderId, userId, totalAmount, status, orderedAt,<br/>items: [{productId, productNameSnapshot, unitPriceSnapshot, quantity}]}
```
