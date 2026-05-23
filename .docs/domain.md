# Domain Glossary

이 문서는 설계 보조 문서다. volume-2 제출 커밋에는 포함하지 않는다.

## 문서 목적

- `.docs/design`의 4개 제출 문서에서 도메인명, 상태명, API명, 클래스명을 같은 이름으로 쓰기 위한 기준이다.
- 구현 단계에서 패키지, 클래스, 테스트 이름을 정할 때 이 문서를 먼저 확인한다.
- 이번 주차 설계에는 `Point`/포인트 도메인을 포함하지 않는다.
- 설계 문서에서 반복되는 API, 정합성, 운영 용어는 아래 "자주 쓰는 용어"를 기준으로 쓴다.

## 자주 쓰는 용어

| 용어 | 정의 | 사용 기준 |
| --- | --- | --- |
| 대고객 API | 일반 사용자가 호출하는 API | `/api/v1` prefix를 사용한다. |
| ADMIN API | 운영자 또는 어드민이 호출하는 API | `/api-admin/v1` prefix와 `X-Loopers-Ldap` 헤더를 사용한다. |
| `user_required` | 로그인 사용자 식별이 필요한 API | `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더를 필수로 받는다. |
| `ldap_required` | 어드민 식별이 필요한 API | `X-Loopers-Ldap: loopers.admin` 헤더를 필수로 받는다. |
| `X-Loopers-LoginId` | 로그인 ID 헤더 | volume-2에서는 기존 `identity` 모듈의 `userId` 참조로 사용한다. |
| `X-Loopers-LoginPw` | 로그인 비밀번호 헤더 | 기존 `identity` 인증 경계의 입력으로 보고, volume-2 신규 도메인에는 저장하지 않는다. |
| `X-Loopers-Ldap` | 어드민 식별 헤더 | 값은 `loopers.admin`이어야 한다. |
| `page` | 페이지 번호 | `service.md` 기준 0-based이며 기본값은 `0`이다. |
| `size` | 페이지 크기 | 기본값은 `20`이다. |
| `sort` | 상품 목록 정렬 기준 | `latest`, `price_asc`, `likes_desc`만 사용한다. |
| `latest` | 최신순 정렬 | 상품 목록 기본 정렬이다. |
| `price_asc` | 낮은 가격순 정렬 | 상품 목록 선택 정렬이다. |
| `likes_desc` | 좋아요 많은 순 정렬 | 상품 목록 선택 정렬이다. |
| 활성 브랜드 | 삭제되지 않아 조회와 신규 상품 등록에 사용할 수 있는 브랜드 | `Brand.deletedAt == null`인 브랜드를 의미한다. |
| 판매 가능 상품 | 사용자가 목록/상세에서 볼 수 있고 주문/좋아요 등록이 가능한 상품 | `ProductStatus.ON_SALE` 상품을 의미한다. |
| 숨김 상품 | 일반 상품 목록/상세에서 노출하지 않는 상품 | `SOLD_OUT`, `STOPPED` 상품을 의미한다. |
| 브랜드 soft delete | ADMIN 브랜드 삭제 요청을 물리 삭제 대신 삭제 시각 표시로 처리하는 정책 | `Brand.deletedAt`을 기록하고 관련 상품은 `ProductStatus.STOPPED`으로 전환한다. |
| 상품 soft delete | ADMIN 상품 삭제 요청을 물리 삭제 대신 상태 변경으로 처리하는 정책 | `ProductStatus.STOPPED`으로 전환해 과거 주문 이력과 FK 참조를 보호한다. |
| 내 좋아요 목록 | 로그인 사용자가 좋아요한 상품 목록 | `GET /api/v1/users/{userId}/likes`를 사용하고, path `userId`와 `X-Loopers-LoginId`가 같아야 한다. |
| 멱등성 | 같은 요청이 여러 번 와도 결과가 한 번 처리된 것과 같은 성질 | 좋아요 등록/취소, 결제 worker, outbox 전송에서 고려한다. |
| 주문 스냅샷 | 주문 시점의 상품명, 단가, 수량 저장 정보 | 상품 정보 변경이 과거 주문에 영향을 주지 않게 한다. |
| 재고 차감 | 주문 생성 시 주문 수량만큼 상품 재고를 줄이는 처리 | 비관적 락으로 음수 재고를 방지한다. |
| 재고 복구 | 결제 실패/취소/타임아웃 시 차감했던 재고를 되돌리는 처리 | 결제 결과 기록, 주문 상태 전이와 같은 트랜잭션에서 처리한다. |
| 결제 대기 | 주문 생성 후 외부 결제 결과를 기다리는 상태 | `OrderStatus.PAYMENT_PENDING` 상태이며 최대 1분으로 제한한다. |
| 결제 요청 row | 주문 생성 직후 조회 가능한 결제 상태 기준 데이터 | 주문 생성 트랜잭션에서 `payment(order_id, status=REQUESTED)`로 생성한다. |
| 타임아웃 | 결제 대기 시간이 주문 생성 시각 기준 1분을 초과한 실패 사유 | 별도 컬럼이 아니라 `failureReason=TIMEOUT` 값으로 기록한다. |
| Outbox | 외부 데이터 플랫폼으로 보낼 이벤트를 DB에 먼저 저장하는 패턴 | 주문 `PAID` 전이와 이벤트 저장을 같은 트랜잭션으로 묶는다. |
| `retry_count` | Outbox 전송 실패 횟수 | 실패 시 증가시키고, 최대 재시도 초과 시 `FAILED`로 확정한다. |
| 정합성 기준 데이터 | 불일치 복구 시 기준이 되는 원천 데이터 | 좋아요는 `product_like`, 주문은 `orders`/`order_line`을 기준으로 본다. |
| 조회용 카운터 | 조회 성능을 위해 중복 저장하는 수치 | `product.like_count`가 해당하며 `product_like` 기준으로 재집계할 수 있다. |
| 쿠폰 | 향후 주문 할인으로 확장될 수 있는 개념 | `service.md` 흐름에는 등장하지만 구체 API가 없어 이번 설계에서는 요청 DTO에 포함하지 않는다. |

## 아키텍처 기준

- 장기적으로 큰 서비스를 만든다는 전제로 도메인 우선 모듈러 모놀리스를 사용한다.
- 최상위 모듈은 `catalog`, `ordering`, `payment`, `event`로 나눈다.
- 각 모듈 내부에는 `interfaces`, `application`, `domain`, `infrastructure` 계층을 둔다.
- 자세한 아키텍처 결정은 `.docs/architecture.md`를 기준으로 한다.

## 모듈 기준

| 모듈 | 포함 도메인 | 책임 |
| --- | --- | --- |
| `catalog` | `Brand`, `Product`, `ProductLike` | 상품 탐색, 상품 상태, 재고 수량, 좋아요 |
| `ordering` | `Order`, `OrderLine` | 주문 생성, 주문 상태, 주문 항목 스냅샷 |
| `payment` | `Payment`, `PaymentGateway` | 결제 요청, 결제 결과, 결제 실패/취소 처리 |
| `event` | `OrderEventOutbox`, `DataPlatformClient` | 주문 성공 이벤트 저장, 외부 데이터 플랫폼 전송 |

## 확정된 설계 결정

| 결정 | 내용 |
| --- | --- |
| 아키텍처 | 도메인 우선 모듈러 모놀리스 |
| 상품 좋아요 소속 | `ProductLike`는 `catalog` 모듈에 둔다. |
| 포인트 도메인 | 이번 주차 설계 범위에서 제외한다. |
| 회원 경계 | `User`는 volume-2의 새 도메인이 아니라 기존 `identity` 모듈의 `userId` 참조로만 다룬다. |
| 재고 책임 | `StockService`는 `catalog` 모듈의 별도 도메인 서비스로 둔다. 현재는 `Product.stockQuantity`를 비관적 락으로 차감/복구한다. |
| 좋아요 컨트롤러명 | 도메인명과 맞춰 `ProductLikeController`로 둔다. |
| Outbox 실패 정책 | 전송 실패 시 `retryCount`를 증가시키고, 최대 재시도 초과 시 `FAILED`로 확정한다. |
| 상품 목록 페이징 | `service.md` 기준으로 0-based `page`, 기본 `size=20`을 사용한다. |
| 상품 목록 정렬 | `latest`, `price_asc`, `likes_desc`만 사용한다. |
| ADMIN API 경계 | 브랜드/상품/주문 ADMIN API는 `/api-admin/v1` 하위에 둔다. |
| ADMIN 브랜드 삭제 | 브랜드 row는 물리 삭제하지 않고 `Brand.deletedAt`을 기록하며, 관련 상품은 `ProductStatus.STOPPED`으로 전환한다. |
| ADMIN 상품 삭제 | 물리 삭제하지 않고 `ProductStatus.STOPPED`으로 전환한다. |
| 주문 생성 시 결제 row 생성 | 주문 생성 트랜잭션에서 `PaymentStatus.REQUESTED` 결제 row를 함께 만든다. |
| 인증 헤더 | 유저 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw`, ADMIN API는 `X-Loopers-Ldap`를 사용한다. |
| 쿠폰 | 이번 설계에서는 주문 확장 포인트로만 기록한다. |

## 외부 식별자

| 식별자 | 소유 모듈 | 사용 위치 | 기준 |
| --- | --- | --- | --- |
| `userId` | 기존 `identity` 모듈 | `ProductLike`, `Order` | volume-2에서는 문자열 식별자만 저장하고, `User` 내부 테이블/필드는 설계하지 않는다. |

## 도메인 용어

| 용어 | 한글명 | 책임 |
| --- | --- | --- |
| `Brand` | 브랜드 | 상품의 브랜드 정보를 표현한다. |
| `Product` | 상품 | 상품명, 가격, 판매 상태, 재고 수량, 좋아요 수를 가진다. |
| `ProductLike` | 상품 좋아요 | 사용자와 상품의 좋아요 관계를 표현한다. |
| `StockService` | 재고 서비스 | 주문 생성 시 재고 차감, 결제 실패/취소/타임아웃 시 재고 복구를 담당한다. |
| `Order` | 주문 | 주문 대표 상태와 총액을 관리한다. |
| `OrderLine` | 주문 항목 | 주문 당시 상품명, 단가, 수량 스냅샷을 보관한다. |
| `Payment` | 결제 | 결제 요청과 외부 결제 결과를 기록한다. |
| `OrderEventOutbox` | 주문 이벤트 아웃박스 | 외부 데이터 플랫폼으로 보낼 주문 이벤트를 저장한다. |
| `PaymentGateway` | 외부 결제 시스템 | 결제 승인, 매입, 승인 취소를 수행하는 외부 시스템이다. |
| `DataPlatformClient` | 데이터 플랫폼 클라이언트 | 주문 성공 이벤트를 외부 데이터 플랫폼으로 전송한다. |

## 상태명

### ProductStatus

| 상태 | 의미 |
| --- | --- |
| `ON_SALE` | 판매 가능 |
| `SOLD_OUT` | 품절 |
| `STOPPED` | 판매 중지 |

### OrderStatus

| 상태 | 의미 |
| --- | --- |
| `PAYMENT_PENDING` | 주문 생성 후 결제 결과 대기 |
| `PAID` | 결제 성공으로 주문 확정 |
| `PAYMENT_FAILED` | 결제 실패 또는 타임아웃 |
| `CANCELED` | 결제 취소 또는 주문 취소 |

### PaymentStatus

| 상태 | 의미 |
| --- | --- |
| `REQUESTED` | 주문 생성 후 외부 결제 요청 대기 |
| `SUCCESS` | 외부 결제 성공 |
| `FAILED` | 외부 결제 실패 |
| `CANCELED` | 외부 결제 취소 |

### OutboxStatus

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 전송 대기 또는 재시도 가능 |
| `SENT` | 전송 완료 |
| `FAILED` | 재시도 한도 초과 또는 수동 확인 필요 |

최대 재시도 횟수는 코드 상수 또는 설정값으로 두고, 설계 문서에서는 구체 숫자를 고정하지 않는다.

## 구현 이름 기준

| 유스케이스 | Controller | Facade/Service | Repository |
| --- | --- | --- | --- |
| 상품 목록/상세 조회 | `ProductV1Controller` | `ProductFacade`, `ProductService` | `ProductRepository` |
| 브랜드 ADMIN | `BrandAdminController` | `BrandService` | `BrandRepository` |
| 상품 ADMIN | `ProductAdminController` | `ProductService` | `ProductRepository`, `BrandRepository` |
| 좋아요 등록/취소/조회 | `ProductLikeController` | `ProductLikeService` | `ProductLikeRepository` |
| 주문 생성/조회 | `OrderController` | `OrderFacade`, `OrderService`, `StockService` | `OrderRepository`, `ProductRepository` |
| 주문 ADMIN 조회 | `OrderAdminController` | `OrderService` | `OrderRepository` |
| 결제 비동기 처리 | - | `PaymentWorker`, `PaymentService` | `PaymentRepository` |
| 주문 이벤트 전송 | - | `EventRelayWorker`, `OrderEventPublisher` | `OrderEventOutboxRepository` |

## 미확정 항목

- 없음
