# Sequence Diagrams

이 문서는 주요 유스케이스의 책임 흐름을 DDD 설계 관점에서 확인하기 위해 작성한다. 다이어그램은 실제 구현과 연결하기 쉽도록 주요 컴포넌트 이름을 사용하되, DTO나 JPA 구현체 같은 세부사항은 제외한다.

## Users

### 회원가입

이 다이어그램은 회원가입 과정에서 사용자 식별자 중복 확인과 비밀번호 정책 검증이 선행된 뒤, 새로운 사용자가 생성되는 흐름을 확인하기 위한 것이다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Controller as UserController
    participant Service as UserService
    participant Policy as UserPasswordPolicy
    participant User as User
    participant Repository as UserRepository

    Client->>Controller: 회원가입 요청
    Controller->>Service: 회원가입 처리
    Service->>Repository: loginId 중복 확인
    Repository-->>Service: 중복 여부
    Service->>Policy: 비밀번호 정책 확인
    Policy-->>Service: 정책 충족 여부
    Service->>User: 사용자 생성
    Service->>Repository: 사용자 저장
    Service-->>Controller: 회원가입 결과
    Controller-->>Client: 응답
```

여기서 핵심은 `User` 생성 전에 식별자 중복과 비밀번호 정책이 먼저 검증된다는 것이다. 비밀번호 인코딩 같은 기술 세부사항은 다이어그램에서 제외하고, 도메인 규칙의 흐름만 표현한다.

### 내 정보 조회

이 다이어그램은 인증된 사용자가 자신의 정보를 조회하는 흐름을 확인하기 위한 것이다. 인증 방식 자체보다, 조회 대상이 인증된 사용자 자신이라는 점이 중요하다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as UserController
    participant Service as UserService
    participant Repository as UserRepository

    Client->>Auth: 내 정보 조회 요청 (인증 헤더 전달)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 내 정보 조회
    Service->>Repository: userId로 사용자 조회
    Repository-->>Service: 사용자 정보
    Service-->>Controller: 사용자 정보
    Controller-->>Client: 마스킹된 사용자 정보
```

여기서 `userId`는 인증 결과로 전달된다. 관리자는 별도 인증 시스템의 주체이므로 일반 `User` 조회 흐름에 포함하지 않는다.

### 비밀번호 변경

이 다이어그램은 비밀번호 변경 과정에서 현재 비밀번호 확인과 새 비밀번호 정책 검증이 완료된 뒤 사용자 비밀번호가 변경되는 흐름을 확인하기 위한 것이다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as UserController
    participant Service as UserService
    participant Repository as UserRepository
    participant Policy as UserPasswordPolicy
    participant User as User

    Client->>Auth: 비밀번호 변경 요청 (인증 헤더 전달)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 비밀번호 변경 처리
    Service->>Repository: 사용자 조회
    Repository-->>Service: 사용자
    Service->>Service: 현재 비밀번호 확인
    Service->>Policy: 새 비밀번호 정책 확인
    Policy-->>Service: 정책 충족 여부
    Service->>User: 비밀번호 변경
    Service->>Repository: 변경된 사용자 저장
    Service-->>Controller: 비밀번호 변경 결과
    Controller-->>Client: 응답
```

여기서 핵심은 비밀번호 변경이 인증된 사용자 본인에게만 적용되고, 변경 전에 현재 비밀번호 확인과 새 비밀번호 정책 검증이 필요하다는 것이다.

## Brands

### 브랜드 조회

이 다이어그램은 사용자가 브랜드 정보를 조회하는 흐름을 확인하기 위한 것이다. 브랜드 조회는 인증 없이 가능하며, 상품이 어떤 브랜드에 속하는지 확인하는 기준 정보로 사용된다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Controller as BrandController
    participant Service as BrandService
    participant Repository as BrandRepository

    Client->>Controller: 브랜드 조회 요청
    Controller->>Service: 브랜드 조회
    Service->>Repository: brandId로 브랜드 조회
    Repository-->>Service: 브랜드 정보
    Service-->>Controller: 브랜드 정보
    Controller-->>Client: 응답
```

브랜드 조회는 상품이나 주문의 상태를 변경하지 않는다. `Brand`는 상품이 소속되는 기준 정보로 사용된다.

### 관리자 브랜드 조회

이 다이어그램은 관리자가 등록된 브랜드 목록이나 상세 정보를 조회하는 흐름을 확인하기 위한 것이다. 관리자 조회는 일반 브랜드 조회와 같은 브랜드 정보를 다루지만, 관리자 권한이 필요한 별도 진입점으로 본다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 관리자
    participant Auth as AdminAuthentication
    participant Controller as BrandAdminController
    participant Service as BrandAdminService
    participant Repository as BrandRepository

    Admin->>Auth: 브랜드 관리 조회 요청 (관리자 인증 헤더 전달)
    Auth->>Controller: 요청 전달 (LDAP 인증 가정)
    Controller->>Service: 브랜드 관리 조회
    Service->>Repository: 브랜드 목록 또는 상세 조회
    Repository-->>Service: 브랜드 정보
    Service-->>Controller: 브랜드 정보
    Controller-->>Admin: 응답
```

관리자는 브랜드를 변경하지 않고 조회만 수행한다. 고객 조회와 관리자 조회의 응답 정보는 현재 범위에서 동일하게 본다.

### 브랜드 삭제

이 다이어그램은 브랜드 삭제 시 해당 브랜드에 속한 상품도 함께 삭제되어야 한다는 정책을 확인하기 위한 것이다. `Brand`와 `Product`는 별도 Aggregate로 보며, 삭제 유스케이스에서 application service가 두 Aggregate의 생명주기를 조율한다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 관리자
    participant Auth as AdminAuthentication
    participant Controller as BrandAdminController
    participant Service as BrandAdminService
    participant BrandRepository as BrandRepository
    participant ProductRepository as ProductRepository

    Admin->>Auth: 브랜드 삭제 요청 (관리자 인증 헤더 전달)
    Auth->>Controller: 요청 전달 (LDAP 인증 가정)
    Controller->>Service: 브랜드 삭제
    Note over Service,BrandRepository: soft delete는 deleted_at을 채우는 UPDATE이므로 대상 존재를 먼저 확인
    Service->>BrandRepository: 브랜드 조회 (존재 확인)
    BrandRepository-->>Service: 브랜드 (없으면 404)
    Service->>ProductRepository: brandId에 속한 상품 삭제
    Service->>BrandRepository: 브랜드 삭제
    Service-->>Controller: 삭제 결과
    Controller-->>Admin: 응답
```

여기서 하위 상품 삭제는 `Brand`가 `Product`를 소유한다는 의미가 아니다. 브랜드 삭제라는 유스케이스 안에서 관련 Product Aggregate들을 먼저 정리한 뒤, 브랜드를 삭제하는 정책으로 본다.

## Products

### 상품 조회

이 다이어그램은 사용자가 상품 정보를 조회하는 흐름을 확인하기 위한 것이다. 상품은 브랜드 식별자를 통해 브랜드와 연결되지만, `Product`와 `Brand`는 별도 Aggregate로 본다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Controller as ProductController
    participant Service as ProductService
    participant ProductRepository as ProductRepository

    Client->>Controller: 상품 조회 요청
    Controller->>Service: 상품 조회
    Note over Service,ProductRepository: 사용자 조회는 판매중지(SUSPENDED)·삭제 상품을 제외
    Service->>ProductRepository: 상품 조회
    ProductRepository-->>Service: 상품 정보
    Service-->>Controller: 상품 정보
    Controller-->>Client: 응답
```

상품 조회는 상품 상태를 변경하지 않는다. 브랜드 정보가 함께 필요하더라도 상품은 브랜드 Aggregate를 직접 소유하지 않는다.

### 관리자 상품 조회

이 다이어그램은 관리자가 상품 목록이나 상세 정보를 조회할 때 재고 정보까지 함께 확인하는 흐름을 확인하기 위한 것이다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 관리자
    participant Auth as AdminAuthentication
    participant Controller as ProductAdminController
    participant Service as ProductAdminService
    participant ProductRepository as ProductRepository

    Admin->>Auth: 상품 관리 조회 요청 (관리자 인증 헤더 전달)
    Auth->>Controller: 요청 전달 (LDAP 인증 가정)
    Controller->>Service: 상품 관리 조회
    Service->>ProductRepository: 상품과 재고 조회
    ProductRepository-->>Service: 상품과 재고 정보
    Service-->>Controller: 상품과 재고 정보
    Controller-->>Admin: 응답
```

관리자 상품 조회는 고객 상품 조회와 달리 재고 정보를 포함한다. 재고는 `ProductStock`으로 관리되지만, 조회 유스케이스에서는 상품 정보와 함께 제공된다.

### 상품 등록

이 다이어그램은 관리자가 상품을 등록할 때 연결할 브랜드가 먼저 확인되어야 한다는 흐름을 확인하기 위한 것이다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 관리자
    participant Auth as AdminAuthentication
    participant Controller as ProductAdminController
    participant Service as ProductAdminService
    participant BrandRepository as BrandRepository
    participant ProductRepository as ProductRepository

    Admin->>Auth: 상품 등록 요청 (관리자 인증 헤더 전달)
    Auth->>Controller: 요청 전달 (LDAP 인증 가정)
    Controller->>Service: 상품 등록
    Service->>BrandRepository: 브랜드 존재 확인
    BrandRepository-->>Service: 브랜드 존재 여부
    Note over Service,ProductRepository: 초기 status=ON_SALE, 대표 이미지·초기 재고 함께 저장
    Service->>ProductRepository: 상품과 초기 재고 저장
    ProductRepository-->>Service: 저장된 상품
    Service-->>Controller: 등록 결과
    Controller-->>Admin: 응답
```

여기서 브랜드 확인은 상품이 유효한 브랜드에 소속되어야 한다는 규칙을 위한 것이다. 상품 등록 이후에는 상품이 `brandId`를 통해 브랜드를 참조하고, 초기 재고는 `ProductStock`으로 관리한다.

### 상품 삭제

이 다이어그램은 관리자가 상품을 삭제하는 흐름을 확인하기 위한 것이다. 삭제 방식은 공통 삭제 정책을 따른다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 관리자
    participant Auth as AdminAuthentication
    participant Controller as ProductAdminController
    participant Service as ProductAdminService
    participant ProductRepository as ProductRepository

    Admin->>Auth: 상품 삭제 요청 (관리자 인증 헤더 전달)
    Auth->>Controller: 요청 전달 (LDAP 인증 가정)
    Controller->>Service: 상품 삭제
    Note over Service,ProductRepository: soft delete는 deleted_at을 채우는 UPDATE이므로 대상 존재를 먼저 확인
    Service->>ProductRepository: 상품 조회 (존재 확인)
    ProductRepository-->>Service: 상품 (없으면 404)
    Service->>ProductRepository: 상품 삭제
    Service-->>Controller: 삭제 결과
    Controller-->>Admin: 응답
```

상품 삭제는 기존 주문 이력을 제거한다는 의미가 아니다. 주문은 주문 시점의 상품 정보를 별도 스냅샷으로 보관한다.

## Likes

### 좋아요 등록

이 다이어그램은 사용자가 상품에 좋아요를 등록하는 흐름을 확인하기 위한 것이다. 좋아요는 사용자와 상품 사이의 별도 관계로 관리한다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as LikeController
    participant Service as LikeService
    participant ProductRepository as ProductRepository
    participant LikeRepository as LikeRepository

    Client->>Auth: 좋아요 등록 요청 (인증 헤더 전달)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 좋아요 등록
    Service->>ProductRepository: 상품 조회
    ProductRepository-->>Service: 상품
    Note over Service: 삭제·SUSPENDED 상품이면 좋아요 등록 거부
    Service->>LikeRepository: 좋아요 조회
    LikeRepository-->>Service: 좋아요 상태
    Service->>LikeRepository: 좋아요 생성 또는 복구
    Service-->>Controller: 처리 결과
    Controller-->>Client: 응답
```

이미 좋아요한 상품에 다시 좋아요를 요청해도 같은 상태를 유지한다. 취소된 좋아요가 있으면 기존 좋아요를 복구한다. 응답은 신규 등록·이미 활성 상태 모두 `200 OK`로 통일하고, 현재 좋아요 상태를 본문에 담는다.

### 좋아요 취소

이 다이어그램은 사용자가 상품 좋아요를 취소하는 흐름을 확인하기 위한 것이다. 좋아요 취소는 사용자와 상품 사이의 선호 관계를 비활성화하는 동작으로 본다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as LikeController
    participant Service as LikeService
    participant LikeRepository as LikeRepository

    Client->>Auth: 좋아요 취소 요청 (인증 헤더 전달)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 좋아요 취소
    Service->>LikeRepository: 좋아요 조회
    LikeRepository-->>Service: 좋아요 상태
    Service->>LikeRepository: 좋아요 취소
    Service-->>Controller: 처리 결과
    Controller-->>Client: 응답
```

이미 취소된 좋아요를 다시 취소해도 같은 상태를 유지한다. 응답은 활성 좋아요 취소·이미 취소된 상태 모두 `200 OK`로 통일하고, 현재 좋아요 상태를 본문에 담는다.

### 좋아요 목록 조회

이 다이어그램은 사용자가 자신이 좋아요한 상품 목록을 조회하는 흐름을 확인하기 위한 것이다. 삭제된 상품은 목록에서 제외하고, 판매중지(`SUSPENDED`) 상품은 '판매중지' 표시로 노출한다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as LikeController
    participant Service as LikeService
    participant LikeRepository as LikeRepository
    participant ProductRepository as ProductRepository

    Client->>Auth: 좋아요 목록 조회 요청 (인증 헤더 전달)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 좋아요 목록 조회
    Service->>LikeRepository: userId 기준 좋아요 조회
    LikeRepository-->>Service: 좋아요 목록
    Service->>ProductRepository: 좋아요한 상품 조회
    ProductRepository-->>Service: 상품 목록
    Note over Service,ProductRepository: 삭제 상품 제외, SUSPENDED는 '판매중지' 표시로 노출
    Service-->>Controller: 상품 목록
    Controller-->>Client: 응답
```

여기서 `Like`는 상품을 직접 소유하지 않는다. 좋아요 목록 조회는 Like 관계를 기준으로 상품을 다시 조회하며, 삭제된 상품은 제외하고 판매중지(`SUSPENDED`) 상품은 '판매중지' 상태로 표시한다.

## Orders

### 주문 생성

이 다이어그램은 주문 생성 시 상품 조회, 재고 확인/차감, 주문 스냅샷 저장이 하나의 유스케이스 안에서 처리되는 흐름을 확인하기 위한 것이다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as OrderController
    participant Service as OrderService
    participant ProductRepository as ProductRepository
    participant ProductStock as ProductStock
    participant Order as Order
    participant OrderRepository as OrderRepository

    Client->>Auth: 주문 요청 (인증 헤더 전달)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 주문 생성

    Note over Service,ProductRepository: 재고 락은 productId 오름차순으로 획득 (deadlock 방지)
    Service->>ProductRepository: 주문 상품과 재고 조회 (SELECT ... FOR UPDATE)
    ProductRepository-->>Service: Product / ProductStock 목록

    loop 주문 상품마다
        Service->>ProductStock: decrease(quantity)
        ProductStock-->>Service: 차감 완료 (재고 부족 시 예외)
    end

    Service->>Order: create(userId, 상품 스냅샷 정보, ShippingDestination)
    Note over Order: orderNumber 생성(yyyyMMdd+일별 시퀀스)<br/>OrderItem 스냅샷(productId, productName, brandId, brandName, price, quantity)<br/>ShippingDestination 저장, totalAmount 계산, status=PENDING
    Order-->>Service: 주문 Aggregate

    Service->>OrderRepository: 주문 저장
    OrderRepository-->>Service: 주문
    Service-->>Controller: 주문 생성 결과 (PENDING)
    Controller-->>Client: 응답
```

주문 생성이 성공했다는 것은 재고가 확보되었다는 뜻이다. 여러 상품 중 하나라도 주문할 수 없으면 전체 주문을 생성하지 않는다.

재고 락은 deadlock을 피하기 위해 `productId` 오름차순으로 잡고, `SELECT ... FOR UPDATE`로 차감 직전까지 보유한다. 재고 차감과 주문 스냅샷 저장은 하나의 트랜잭션 안에서 함께 처리한다. 결제는 이 트랜잭션과 분리된 별도 단계(Payment 도메인)이므로 주문 생성 API는 `PENDING`으로 끝난다(아래 Payments 참고).

### 주문 목록 조회

이 다이어그램은 사용자가 본인의 주문 목록을 조회하는 흐름을 확인하기 위한 것이다. 기간 조건은 선택값이며, 없으면 최신 주문부터 조회한다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as OrderController
    participant Service as OrderService
    participant OrderRepository as OrderRepository

    Client->>Auth: 주문 목록 조회 요청 (인증 헤더 전달)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 주문 목록 조회
    Service->>OrderRepository: userId 기준 주문 조회
    OrderRepository-->>Service: 주문 목록
    Service-->>Controller: 주문 목록
    Controller-->>Client: 응답
```

일반 사용자는 자신의 주문만 조회한다. 관리자는 별도 관리자 조회 흐름에서 전체 주문을 조회할 수 있다.

### 관리자 주문 목록 조회

이 다이어그램은 관리자가 전체 주문을 조회하는 흐름을 확인하기 위한 것이다. 일반 사용자 조회와 달리 특정 userId로 범위를 제한하지 않는다.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 관리자
    participant Auth as AdminAuthentication
    participant Controller as OrderAdminController
    participant Service as OrderAdminService
    participant OrderRepository as OrderRepository

    Admin->>Auth: 주문 관리 조회 요청 (관리자 인증 헤더 전달)
    Auth->>Controller: 요청 전달 (LDAP 인증 가정)
    Controller->>Service: 전체 주문 조회
    Service->>OrderRepository: 전체 주문 조회
    OrderRepository-->>Service: 주문 목록
    Service-->>Controller: 주문 목록
    Controller-->>Admin: 응답
```

관리자는 전체 주문을 조회한다. 주문 자체의 소유자는 여전히 일반 사용자이며, 관리자 조회는 주문 Aggregate의 소유 관계를 바꾸지 않는다.

## Payments

### 결제 요청

이 다이어그램은 사용자가 생성된 주문에 결제를 요청하는 흐름을 확인하기 위한 것이다. 주문 생성과 결제는 분리된 단계이며, 결제 금액은 클라이언트가 아니라 주문에서 가져온다.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 사용자
    participant Auth as HeaderAuthenticationFilter
    participant Controller as PaymentController
    participant Service as PaymentService
    participant OrderReader as OrderReader
    participant PaymentRepository as PaymentRepository
    participant Gateway as PaymentGateway

    Client->>Auth: 결제 요청 (인증 헤더, orderNumber)
    Auth->>Controller: 요청 전달 (인증된 userId)
    Controller->>Service: 결제 요청
    Service->>OrderReader: orderNumber 로 주문 조회
    OrderReader-->>Service: 주문 (PENDING·본인 검증, totalAmount)
    Note over Service: 결제 금액 = 주문 totalAmount
    Service->>PaymentRepository: Payment 저장 (status=REQUESTED)
    Service->>Gateway: 결제 요청 (외부 PG stub, DB 트랜잭션 밖)
    Gateway-->>Service: 접수 (결과는 별도 webhook)
    Service-->>Controller: 결제 요청 결과 (REQUESTED)
    Controller-->>Client: 응답
```

여기서 핵심은 결제 요청 시 `Payment`가 `REQUESTED`로 생성되고, 실제 승인/실패는 PG webhook 콜백으로 확정된다는 것이다. 주문이 `PENDING`이 아니거나 본인 주문이 아니면 결제를 요청할 수 없다.

### 결제 결과 콜백 (webhook)

이 다이어그램은 외부 PG가 결제 결과를 webhook으로 통지했을 때 결제·주문 상태를 확정하는 흐름을 확인하기 위한 것이다.

```mermaid
sequenceDiagram
    autonumber
    participant PG as 외부 결제 시스템
    participant Controller as PaymentController
    participant Service as PaymentService
    participant PaymentRepository as PaymentRepository
    participant OrderService as OrderService

    PG->>Controller: 결제 결과 콜백 (서명 검증)
    Controller->>Service: 결제 결과 반영
    Service->>PaymentRepository: Payment 조회
    PaymentRepository-->>Service: Payment
    Note over Service: 멱등 — 이미 PAID/FAILED 로 확정된 결제·주문이면 무시

    alt 승인
        Service->>Service: Payment.approve() (PAID)
        Service->>OrderService: markPaid(orderNumber)
        OrderService-->>Service: 주문 PAID 전이
    else 실패
        Service->>Service: Payment.fail() (FAILED)
        Service->>OrderService: markFailed(orderNumber)
        Note over OrderService: 재고 복구 + 주문 FAILED 전이 (종료)
        OrderService-->>Service: 보상 완료
    end
    Controller-->>PG: 200 OK
```

여기서 봐야 할 점은 결제 결과 확정과 주문 상태 전이가 분리되어 있다는 것이다. Payment는 결과를 받아 자신의 상태를 확정하고, 주문 상태 전이·재고 복구는 `Order`(OrderService)가 소유한다. 콜백은 중복 수신될 수 있으므로 멱등하게 처리하고, 결제 실패는 종료로 보아 재구매는 새 주문으로 한다.
