# 01. Requirements Design Traceability

## 목적

이 문서는 루퍼스의 "우리가 함께 만들어갈 단 하나의 감성 이커머스" 프로젝트를 진행하는 동안 요구사항이 어떤 설계 결정과 책임 객체로 이어지는지 추적하기 위한 공통 기준이다.
요구사항, 시퀀스 다이어그램, 클래스 다이어그램, ERD, 구현 코드가 서로 다른 방향으로 흐르지 않도록 연결 근거를 남기는 것을 목표로 한다.

## 사용 원칙

- 요구사항은 사용자 시나리오, 정책, 제약, 예외 흐름 중 설계 결정이 필요한 단위로 작성한다.
- 설계 결정은 구현 구조나 데이터 구조에 영향을 주는 판단으로 작성한다.
- 책임 객체는 해당 결정을 실제로 수행하거나 보호해야 하는 객체, 계층, 저장소, 외부 경계로 작성한다.
- 하나의 요구사항이 여러 책임 객체로 나뉠 수 있다.
- 책임 객체를 정할 때 Controller에 도메인 규칙을 두지 않는다.
- 여러 도메인의 협력이 필요한 유스케이스는 Facade가 조율한다.
- 상태 변경과 도메인 규칙은 Domain Entity의 행위로 표현한다.
- Domain Service는 같은 도메인의 Repository 인터페이스를 통해 애그리거트를 조회하고 저장한다.
- 다른 도메인의 Repository를 직접 참조해야 한다면 Facade나 별도 조율 책임으로 경계를 다시 검토한다.

## 책임 객체 기준

| 책임 객체 | 역할 |
| --- | --- |
| `Controller` | 요청/응답 변환, 인증 사용자 전달, API 경계 처리 |
| `Facade` | 여러 도메인이 협력하는 유스케이스 조율 |
| `Domain Service` | 같은 도메인의 조회, 저장, 정책 단위 처리 |
| `Domain Entity` | 상태 변경, 불변식, 도메인 규칙 수행 |
| `Repository Interface` | 도메인 관점의 저장소 계약 |
| `External Gateway` | 외부 시스템과의 통신 경계 |
| `Scheduler` | 시간 기반 후속 처리 시작점 |
| `Compensation Service` | 실패나 지연 이후 보상 처리 조율 |
| `Database Constraint` | 유니크, FK, 인덱스 등 데이터 정합성의 마지막 방어선 |

## 요구사항-설계 결정-책임 객체 매핑

| 요구사항 | 설계 결정 | 책임 객체 |
| --- | --- | --- |
| 사용자는 브랜드를 조회할 수 있다. | 삭제되거나 비노출 상태인 브랜드는 사용자 조회에서 제외한다. | `Brand.isVisible`, `BrandService`, `BrandRepository` |
| 사용자는 상품 목록과 상세를 조회할 수 있다. | 상품 조회는 판매/노출 가능한 상품만 대상으로 한다. | `Product.isVisible`, `ProductService`, `ProductRepository` |
| 상품 목록은 브랜드 기준으로 필터링할 수 있다. | 상품은 브랜드를 객체 참조가 아니라 `brandId`로 참조한다. | `Product.brandId`, `ProductRepository`, `products.brand_id` |
| 상품 목록은 최신순, 가격 낮은순, 좋아요 많은순으로 정렬할 수 있다. | `likes_desc` 정렬을 위해 `Product.likeCount`를 조회용 파생 값으로 둔다. | `Product.likeCount`, `ProductLike`, `ProductRepository`, `product_like` |
| 같은 사용자는 같은 상품에 좋아요를 하나만 가질 수 있다. | 사용자-상품 조합을 유니크하게 관리하고, 중복 요청은 멱등 처리한다. | `ProductLikeService`, `ProductLikeRepository`, `UK(user_login_id, product_id)` |
| 좋아요 등록 시 좋아요 수가 증가해야 한다. | 좋아요 생성 결과가 실제 변경일 때만 상품 좋아요 수를 증가시킨다. | `ProductLikeFacade`, `ProductLikeService`, `Product.increaseLikeCount` |
| 좋아요 취소 시 좋아요 수가 감소해야 한다. | 좋아요 삭제 결과가 실제 변경일 때만 상품 좋아요 수를 감소시킨다. | `ProductLikeFacade`, `ProductLikeService`, `Product.decreaseLikeCount` |
| 사용자는 자신이 좋아요한 상품 목록을 조회할 수 있다. | 경로의 `userId`와 로그인 사용자가 일치해야 하며, 타 사용자 좋아요 목록 접근은 차단한다. | `Controller`, `ProductLikeFacade`, `ProductLikeService` |
| 사용자는 여러 상품을 한 번에 주문할 수 있다. | 주문 생성은 상품 검증, 재고 차감, 주문 항목 생성을 하나의 유스케이스로 조율한다. | `OrderFacade`, `ProductService`, `OrderService` |
| 주문 수량은 1 이상이어야 한다. | 요청 command 경계에서 기본 입력을 검증하고, 도메인 엔티티에서도 유효하지 않은 주문 항목을 만들지 않는다. | `OrderFacade`, `Order`, `OrderLine` |
| 재고가 충분한 상품은 주문 대상으로 확정한다. | 재고 차감 규칙은 상품 도메인 행위로 둔다. | `Product.deductStock`, `ProductService`, `ProductRepository` |
| 재고가 부족한 상품만 실패 처리하고 나머지는 주문한다. | 주문은 성공 항목과 실패 항목을 함께 표현한다. | `Order`, `OrderLine`, `OrderFailure` |
| 주문 응답은 성공 항목과 실패 항목을 구분해야 한다. | 부분 주문 결과를 주문 도메인 결과와 응답 객체에서 분리해 표현한다. | `OrderResult`, `OrderFacade`, `OrderResponse` |
| 주문 이력은 상품 정보 변경의 영향을 받으면 안 된다. | 주문 항목에 주문 당시 상품명과 가격을 스냅샷으로 저장한다. | `OrderLine`, `order_line` |
| 주문 생성과 결제 승인은 별도 단계로 분리한다. | 주문 생성은 내부 거래 준비, 결제 승인은 외부 시스템 연동으로 경계를 나눈다. | `OrderFacade`, `PaymentFacade`, `PaymentService`, `PaymentGateway` |
| 결제 성공 시 주문은 결제 완료 상태가 된다. | 외부 결제 결과는 결제 경계에서 받고, 주문 상태 전이는 주문 모델 행위로 수행한다. | `PaymentFacade`, `PaymentService`, `Order.markPaid` |
| 결제 실패 시 주문 상태와 재고가 어긋나면 안 된다. | 실패 상태 기록과 재고 해제는 보상 처리로 분리한다. | `Order.markPaymentFailed`, `CompensationService`, `ProductService.releaseStock` |
| 결제 기한이 지나면 주문을 취소할 수 있어야 한다. | 만료 주문 조회와 보상 처리는 시간 기반 후속 처리로 분리한다. | `PaymentTimeoutScheduler`, `OrderService`, `Order.markCanceledByPaymentTimeout`, `CompensationService` |
| 상품과 브랜드는 소프트 삭제한다. | 삭제 상태는 조회 조건과 도메인 노출 가능 여부 판단에 항상 반영한다. | `Brand.delete`, `Product.delete`, `Repository Query`, `deleted_at` |
| 좋아요 수는 조회 성능을 위해 별도 값으로 가진다. | `ProductLike`를 원본 데이터로 보고 `Product.likeCount`는 파생 값으로 관리한다. | `ProductLike`, `Product.likeCount`, `Database Constraint` |

## 산출물 작성 시 확인 질문

- 이 요구사항은 단순 API 흐름인가, 도메인 책임이 필요한 규칙인가?
- 이 규칙을 Controller나 Facade가 직접 처리하고 있지는 않은가?
- 상태 변경은 도메인 객체의 행위로 표현되어 있는가?
- 여러 도메인이 협력한다면 조율 책임이 명확한가?
- 다른 도메인의 Repository를 직접 참조하는 의존이 생기지는 않는가?
- 데이터 정합성을 도메인 로직과 DB 제약 중 어디까지 나눠 보장할 것인가?
- 예외 흐름이 상태, 응답, 저장 구조 중 어디에 남아야 하는가?
- 이 설계 결정이 시퀀스 다이어그램, 클래스 다이어그램, ERD 중 어디에 반영되어야 하는가?
