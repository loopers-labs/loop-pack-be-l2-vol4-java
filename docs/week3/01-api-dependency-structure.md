# 01. API Dependency Structure

## 목적

이 문서는 현재 `commerce-api`의 API별 의존 관계를 정리한다.
화살표는 반환값 흐름이 아니라 **참조/import 또는 호출 의존 방향**만 의미한다.
따라서 응답 DTO나 결과 객체가 되돌아오는 방향은 화살표로 표현하지 않는다.

## 작성 기준

- 의존 방향은 단방향으로만 표현한다.
- 양방향 화살표가 생기면 순환 참조 또는 다이어그램 표현 오류로 본다.
- 기본 레이어 방향은 `interfaces -> application -> domain -> infrastructure`이다.
- `interfaces -> domain` 직접 참조는 허용한다. 예: Controller가 Domain Command를 만들거나 Domain Service 진입점을 직접 호출하는 경우.
- API의 진입점은 `*Controller`, Domain의 진입점은 단일 도메인 `*Service`이다.
- `*Service`는 Domain 진입점이고, Repository 접근은 조회 전용 `*Reader`와 생성/수정/삭제 전용 `*Writer`로 분리한다.
- `*Policy` 또는 `*Processor`는 Repository 없이 순수 규칙 처리를 담당한다.
- `ProductBrandProcessor`처럼 2개 이상의 도메인 객체를 조합하는 서비스는 책임이 섞인 도메인명을 함께 드러내고, Repository 없이 순수 조합 로직만 담당한다.

> 주의: 이 문서는 “의도한 레이어 의존 방향”을 표현한다. 현재 구현의 infrastructure adapter는 domain의 Repository interface와 Model을 구현/사용하기 때문에, strict import 기준으로 보면 `infrastructure -> domain` 참조가 남아 있다. 이를 완전히 제거하려면 persistence entity/mapper 분리가 별도 리팩터링으로 필요하다.

---

## 공통 인증 경계

```mermaid
flowchart LR
    subgraph interfaces
        AuthFilter
        LoginUserArgumentResolver
        AuthenticatedUser["@LoginUser AuthenticatedUser"]
    end

    subgraph application
        AuthFacade
        AuthenticatedUserInfo
    end

    subgraph domain
        UserService
        UserRepository
        UserModel
        PasswordPolicy
        PasswordHasher
    end

    subgraph infrastructure
        UserRepositoryImpl
        UserJpaRepository
        BCryptPasswordHasher
    end

    AuthFilter -->|"loginId, password"| AuthFacade
    AuthFacade -->|"loginId, password"| UserService
    UserService -->|"findByLoginId"| UserRepository
    UserService -->|"validate/authenticate"| PasswordPolicy
    UserService -->|"matches/encode"| PasswordHasher
    UserService -->|"credential target"| UserModel
    UserRepository -->|"persistence port"| UserRepositoryImpl
    UserRepositoryImpl -->|"Spring Data"| UserJpaRepository
    PasswordHasher -->|"hash implementation"| BCryptPasswordHasher
    LoginUserArgumentResolver -->|"request attribute"| AuthenticatedUser
```

---

## Product API

```mermaid
flowchart LR
    subgraph interfaces
        ProductV1Controller
        AdminProductV1Controller
        ProductDto["ProductDto.*.V1"]
    end

    subgraph application
        ProductFacade
        ProductInfo
    end

    subgraph domain
        ProductService
        ProductReader
        ProductWriter
        ProductRepository
        BrandRepository
        ProductBrandProcessor
        ProductSort
        PageCriteria
        ProductModel
        BrandModel
        ProductDetail
    end

    subgraph infrastructure
        ProductRepositoryImpl
        ProductJpaRepository
        BrandRepositoryImpl
        BrandJpaRepository
    end

    ProductV1Controller -->|"조회/목록 params"| ProductFacade
    AdminProductV1Controller -->|"생성/수정/삭제 params"| ProductFacade
    ProductV1Controller -->|"response mapping"| ProductDto
    AdminProductV1Controller -->|"request/response mapping"| ProductDto
    ProductDto -->|"from(ProductInfo)"| ProductInfo

    ProductFacade -->|"use case call"| ProductService
    ProductFacade -->|"from(ProductDetail)"| ProductInfo

    ProductService -->|"read entry"| ProductReader
    ProductService -->|"write entry"| ProductWriter
    ProductReader -->|"find/list"| ProductRepository
    ProductReader -->|"find brand"| BrandRepository
    ProductReader -->|"combine product + brand"| ProductBrandProcessor
    ProductReader -->|"sort parse"| ProductSort
    ProductReader -->|"page, size"| PageCriteria
    ProductWriter -->|"save/delete"| ProductRepository
    ProductWriter -->|"find existing"| ProductReader
    ProductWriter -->|"state change"| ProductModel
    ProductWriter -->|"combine product + brand"| ProductBrandProcessor
    ProductBrandProcessor -->|"read brand data"| BrandModel
    ProductBrandProcessor -->|"compose"| ProductDetail

    ProductRepository -->|"adapter"| ProductRepositoryImpl
    ProductRepositoryImpl -->|"Spring Data"| ProductJpaRepository
    BrandRepository -->|"adapter"| BrandRepositoryImpl
    BrandRepositoryImpl -->|"Spring Data"| BrandJpaRepository
```

### API별 의존 흐름

| API | 의존 흐름 |
| --- | --- |
| `GET /api/v1/products/{productId}` | `ProductV1Controller -> ProductFacade -> ProductService -> ProductReader -> ProductRepository/BrandRepository/ProductBrandProcessor` |
| `GET /api/v1/products` | `ProductV1Controller -> ProductFacade -> ProductService -> ProductReader -> ProductRepository/BrandRepository/ProductBrandProcessor` |
| `POST /api-admin/v1/products` | `AdminProductV1Controller -> ProductFacade -> ProductService -> ProductWriter -> ProductRepository/ProductReader/ProductBrandProcessor` |
| `PUT /api-admin/v1/products/{productId}` | `AdminProductV1Controller -> ProductFacade -> ProductService -> ProductWriter -> ProductReader/ProductRepository/ProductModel` |
| `DELETE /api-admin/v1/products/{productId}` | `AdminProductV1Controller -> ProductFacade -> ProductService -> ProductWriter -> ProductReader/ProductRepository/ProductModel` |

---

## Brand API

```mermaid
flowchart LR
    subgraph interfaces
        BrandV1Controller
        AdminBrandV1Controller
        BrandDto["BrandDto.*.V1"]
    end

    subgraph application
        BrandFacade
        BrandInfo
    end

    subgraph domain
        BrandService
        BrandRepository
        ProductRepository
        PageCriteria
        BrandModel
        ProductModel
    end

    subgraph infrastructure
        BrandRepositoryImpl
        BrandJpaRepository
        ProductRepositoryImpl
        ProductJpaRepository
    end

    BrandV1Controller -->|"brandId"| BrandFacade
    AdminBrandV1Controller -->|"admin params"| BrandFacade
    BrandV1Controller -->|"response mapping"| BrandDto
    AdminBrandV1Controller -->|"request/response mapping"| BrandDto
    BrandDto -->|"from(BrandInfo)"| BrandInfo

    BrandFacade -->|"use case call"| BrandService
    BrandFacade -->|"from(BrandModel)"| BrandInfo

    BrandService -->|"save/find/list"| BrandRepository
    BrandService -->|"find/save related products"| ProductRepository
    BrandService -->|"page, size"| PageCriteria
    BrandService -->|"state change"| BrandModel
    BrandService -->|"soft delete related products"| ProductModel

    BrandRepository -->|"adapter"| BrandRepositoryImpl
    BrandRepositoryImpl -->|"Spring Data"| BrandJpaRepository
    ProductRepository -->|"adapter"| ProductRepositoryImpl
    ProductRepositoryImpl -->|"Spring Data"| ProductJpaRepository
```

### API별 의존 흐름

| API | 의존 흐름 |
| --- | --- |
| `GET /api/v1/brands/{brandId}` | `BrandV1Controller -> BrandFacade -> BrandService -> BrandRepository` |
| `GET /api-admin/v1/brands` | `AdminBrandV1Controller -> BrandFacade -> BrandService -> BrandRepository` |
| `POST /api-admin/v1/brands` | `AdminBrandV1Controller -> BrandFacade -> BrandService -> BrandRepository` |
| `PUT /api-admin/v1/brands/{brandId}` | `AdminBrandV1Controller -> BrandFacade -> BrandService -> BrandRepository/BrandModel` |
| `DELETE /api-admin/v1/brands/{brandId}` | `AdminBrandV1Controller -> BrandFacade -> BrandService -> BrandRepository/ProductRepository/BrandModel/ProductModel` |

---

## Like API

```mermaid
flowchart LR
    subgraph interfaces
        ProductV1Controller
        UserV1Controller
        ProductDto["ProductDto.List.V1"]
        AuthenticatedUser
    end

    subgraph application
        ProductLikeFacade
        ProductInfo
    end

    subgraph domain
        ProductLikeService
        ProductLikeRepository
        ProductRepository
        BrandRepository
        ProductBrandProcessor
        ProductLikeModel
        ProductModel
        ProductLikeResult
        ProductDetail
    end

    subgraph infrastructure
        ProductLikeRepositoryImpl
        ProductLikeJpaRepository
        ProductRepositoryImpl
        ProductJpaRepository
        BrandRepositoryImpl
        BrandJpaRepository
    end

    ProductV1Controller -->|"loginId, productId"| ProductLikeFacade
    UserV1Controller -->|"loginId, userId"| ProductLikeFacade
    ProductV1Controller -->|"auth param"| AuthenticatedUser
    UserV1Controller -->|"auth param"| AuthenticatedUser
    UserV1Controller -->|"response mapping"| ProductDto
    ProductDto -->|"from(ProductInfo)"| ProductInfo

    ProductLikeFacade -->|"use case call"| ProductLikeService
    ProductLikeFacade -->|"from(ProductDetail)"| ProductInfo

    ProductLikeService -->|"save/find/delete"| ProductLikeRepository
    ProductLikeService -->|"find/save product"| ProductRepository
    ProductLikeService -->|"find brands"| BrandRepository
    ProductLikeService -->|"combine liked products"| ProductBrandProcessor
    ProductLikeService -->|"create/delete relation"| ProductLikeModel
    ProductLikeService -->|"increase/decrease count"| ProductModel
    ProductLikeService -->|"decision result"| ProductLikeResult
    ProductBrandProcessor -->|"compose"| ProductDetail

    ProductLikeRepository -->|"adapter"| ProductLikeRepositoryImpl
    ProductLikeRepositoryImpl -->|"Spring Data"| ProductLikeJpaRepository
    ProductRepository -->|"adapter"| ProductRepositoryImpl
    ProductRepositoryImpl -->|"Spring Data"| ProductJpaRepository
    BrandRepository -->|"adapter"| BrandRepositoryImpl
    BrandRepositoryImpl -->|"Spring Data"| BrandJpaRepository
```

### API별 의존 흐름

| API | 의존 흐름 |
| --- | --- |
| `POST /api/v1/products/{productId}/likes` | `ProductV1Controller -> ProductLikeFacade -> ProductLikeService -> ProductRepository/ProductLikeRepository/ProductModel/ProductLikeModel` |
| `DELETE /api/v1/products/{productId}/likes` | `ProductV1Controller -> ProductLikeFacade -> ProductLikeService -> ProductLikeRepository/ProductRepository/ProductModel` |
| `GET /api/v1/users/{userId}/likes` | `UserV1Controller -> ProductLikeFacade -> ProductLikeService -> ProductLikeRepository/ProductRepository/BrandRepository/ProductBrandProcessor` |

---

## Order API

```mermaid
flowchart LR
    subgraph interfaces
        OrderV1Controller
        AdminOrderV1Controller
        OrderDto["OrderDto.*.V1"]
        AuthenticatedUser
    end

    subgraph application
        OrderFacade
        OrderInfo
    end

    subgraph domain
        OrderService
        OrderReader
        OrderWriter
        OrderProcessor
        OrderRepository
        ProductRepository
        OrderProductCommand
        ProductModel
        OrderModel
        OrderLineModel
        OrderFailure
        OrderResult
        PageCriteria
    end

    subgraph infrastructure
        OrderRepositoryImpl
        OrderJpaRepository
        ProductRepositoryImpl
        ProductJpaRepository
    end

    OrderV1Controller -->|"request/auth/query params"| OrderFacade
    AdminOrderV1Controller -->|"admin query params"| OrderFacade
    OrderV1Controller -->|"request/response mapping"| OrderDto
    AdminOrderV1Controller -->|"response mapping"| OrderDto
    OrderV1Controller -->|"auth param"| AuthenticatedUser
    OrderDto -->|"toCommands"| OrderProductCommand
    OrderDto -->|"from(OrderInfo)"| OrderInfo

    OrderFacade -->|"use case call"| OrderService
    OrderFacade -->|"from(OrderResult/OrderModel)"| OrderInfo

    OrderService -->|"read entry"| OrderReader
    OrderService -->|"write entry"| OrderWriter
    OrderReader -->|"find/list"| OrderRepository
    OrderReader -->|"page, size"| PageCriteria
    OrderWriter -->|"save order"| OrderRepository
    OrderWriter -->|"find/save products"| ProductRepository
    OrderWriter -->|"create order result"| OrderProcessor
    OrderWriter -->|"deduct stock"| ProductModel
    OrderProcessor -->|"validate command"| OrderProductCommand
    OrderProcessor -->|"create order"| OrderModel
    OrderProcessor -->|"create line"| OrderLineModel
    OrderProcessor -->|"record failure"| OrderFailure
    OrderProcessor -->|"result wrapper"| OrderResult

    OrderRepository -->|"adapter"| OrderRepositoryImpl
    OrderRepositoryImpl -->|"Spring Data"| OrderJpaRepository
    ProductRepository -->|"adapter"| ProductRepositoryImpl
    ProductRepositoryImpl -->|"Spring Data"| ProductJpaRepository
```

### API별 의존 흐름

| API | 의존 흐름 |
| --- | --- |
| `POST /api/v1/orders` | `OrderV1Controller -> OrderFacade -> OrderService -> OrderWriter -> ProductRepository/OrderRepository/OrderProcessor/ProductModel/OrderModel` |
| `GET /api/v1/orders` | `OrderV1Controller -> OrderFacade -> OrderService -> OrderReader -> OrderRepository/PageCriteria` |
| `GET /api/v1/orders/{orderId}` | `OrderV1Controller -> OrderFacade -> OrderService -> OrderReader -> OrderRepository` |
| `GET /api-admin/v1/orders` | `AdminOrderV1Controller -> OrderFacade -> OrderService -> OrderReader -> OrderRepository/PageCriteria` |
| `GET /api-admin/v1/orders/{orderId}` | `AdminOrderV1Controller -> OrderFacade -> OrderService -> OrderReader -> OrderRepository` |

---

## User API

```mermaid
flowchart LR
    subgraph interfaces
        UserV1Controller
        UserDto["UserDto.*.V1"]
        AuthenticatedUser
    end

    subgraph application
        UserFacade
        UserInfo
    end

    subgraph domain
        UserService
        UserRepository
        UserModel
        PasswordPolicy
        PasswordHasher
    end

    subgraph infrastructure
        UserRepositoryImpl
        UserJpaRepository
        BCryptPasswordHasher
    end

    UserV1Controller -->|"request/auth params"| UserFacade
    UserV1Controller -->|"request/response mapping"| UserDto
    UserV1Controller -->|"auth param"| AuthenticatedUser
    UserDto -->|"from(UserInfo)"| UserInfo

    UserFacade -->|"use case call"| UserService
    UserFacade -->|"from(UserModel)"| UserInfo

    UserService -->|"save/find/exists"| UserRepository
    UserService -->|"create/change/authenticate"| UserModel
    UserService -->|"validate password"| PasswordPolicy
    UserService -->|"encode/matches"| PasswordHasher

    UserRepository -->|"adapter"| UserRepositoryImpl
    UserRepositoryImpl -->|"Spring Data"| UserJpaRepository
    PasswordHasher -->|"hash implementation"| BCryptPasswordHasher
```

### API별 의존 흐름

| API | 의존 흐름 |
| --- | --- |
| `POST /api/v1/users` | `UserV1Controller -> UserFacade -> UserService -> UserRepository/PasswordPolicy/PasswordHasher/UserModel` |
| `GET /api/v1/users/me` | `UserV1Controller -> UserFacade -> UserService -> UserRepository` |
| `PUT/PATCH /api/v1/users/password` | `UserV1Controller -> UserFacade -> UserService -> UserRepository/PasswordPolicy/PasswordHasher/UserModel` |

---

## 현재 구조에서 드러나는 판단

- 다이어그램 화살표는 모두 단방향 의존만 표현한다.
- `Facade`는 Repository를 직접 들지 않는다. Transaction은 `application` 레이어에서 열고, Repository 조회/저장은 `domain` 레이어의 단일 도메인 `*Service`가 담당한다.
- `ProductBrandProcessor`는 Product와 Brand 도메인 객체를 조합하는 순수 Processor로 유지하며 Repository를 의존하지 않는다.
- 응답 변환은 DTO의 `from(...)` 팩토리와 `Info` 객체 사이의 같은 방향 의존으로만 표현한다.
