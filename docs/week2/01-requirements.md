# 감성 이커머스 서비스 요구사항 명세 (Week 4)

## 1. 개요
본 문서는 '감성 이커머스' 서비스의 핵심 기능인 유저 관리, 브랜드 및 상품 관리, 그리고 주문 시스템에 대한 요구사항을 정의합니다. 
단순한 기능 구현을 넘어, 데이터의 일관성과 이력 보존을 고려한 설계를 지향합니다.

---

## 2. 핵심 비즈니스 정책

### 2.1 재고 관리 및 결제 정책
*   **재고 차감 시점 (가선점):** 주문 생성 시점에 재고를 차감(가선점)하고 주문 상태를 결제 대기(`PENDING`)로 설정한다. 주문 생성 후 30분 이내에 결제가 완료되지 않으면 주문은 자동 취소(`CANCELED`) 처리되고 가선점된 재고는 복구된다.
*   **재고 부족 처리:** 주문 요청 수량이 잔여 재고보다 많을 경우, 해당 주문 건 전체를 실패(Rollback) 처리하고 적절한 에러 메시지를 반환한다.
*   **동시성 제어 (재고):** 다수의 유저가 동일 상품을 동시에 주문할 경우, 재고 수량의 정합성이 깨지지 않도록 **비관적 락(Pessimistic Lock)** 메커니즘을 적용하여 순차 처리를 보장해야 한다.
*   **데드락 방지:** 한 주문에서 여러 상품을 함께 결제할 때 발생할 수 있는 데드락(Deadlock)을 방지하기 위해, 반드시 **상품 ID(PK) 오름차순으로 정렬한 후 순서대로 비관적 락(FOR UPDATE)을 획득**해야 한다.

### 2.2 주문 데이터 불변성 (Snapshot)
*   **스냅샷 전략:** 주문이 완료된 시점의 상품 정보를 `ORDER_ITEM` 테이블에 직접 복제하여 저장한다.
*   **보관 필드:** 상품명(`name`), 가격(`price`), 브랜드명(`brand_name`) 등 핵심 정보를 컬럼 단위로 저장하여, 원본 상품 정보가 변경되거나 삭제되어도 주문 당시의 정보를 보존한다.
*   **금액 스냅샷:** 주문 전체 수준에서 **쿠폰 적용 전 총액(원가), 할인 금액, 최종 결제 금액**을 모두 영속화하여 이력을 추적한다.

### 2.3 데이터 삭제 정책 (Soft Delete)
*   **논리 삭제 적용:** 브랜드 및 상품 삭제 시 DB에서 레코드를 물리적으로 제거하지 않고, `is_deleted`와 같은 플래그를 사용하여 논리적으로 삭제 처리한다.
*   **연쇄 삭제:** 브랜드 삭제 시, 해당 브랜드에 속한 모든 상품도 함께 논리 삭제 처리되어야 한다.

### 2.4 사용자 식별 및 보안
*   **인증/인가:** 별도의 인증 프레임워크를 사용하지 않으며, 아래의 HTTP 헤더를 통해 사용자를 식별한다.
    *   **일반 유저:** `X-Loopers-LoginId` (아이디), `X-Loopers-LoginPw` (비밀번호)
    *   **어드민 유저:** `X-Loopers-Ldap` (값: `loopers.admin`)
*   **접근 제어:** 유저는 본인의 정보 및 주문 내역만 조회/수정할 수 있으며, 타인의 데이터에 직접 접근할 수 없다.

### 2.5 좋아요(Likes) 및 정렬 정책
*   **좋아요 수 관리 (Dynamic Count):** 상품 목록의 '좋아요 많은 순' 정렬 시 반정규화(`like_count` 컬럼)를 사용하지 않고, 필요 시 `PRODUCT_LIKES` 테이블의 COUNT 쿼리를 통해 동적으로 계산하여 제공한다. (초기 트래픽 수준을 고려한 아키텍처 단순화)
*   **좋아요 동시성 제어 불필요:** `PRODUCT` 테이블의 `like_count`를 수정하는 로직이 제거되었으므로, 좋아요 등록/취소 시 상품에 대한 **비관적 락(Pessimistic Lock)이 불필요**하다.
*   **멱등성 및 롤백 방지:** 이미 좋아요를 누른 상품에 대해 중복 요청이 올 경우, DB의 Unique Key 제약조건 위반 예외가 발생하여 트랜잭션이 강제로 `rollback-only` 상태가 되는 것을 차단하기 위해 **좋아요 이력을 선조회(Exist Check)하여 분기(멱등성 리턴)**해야 한다.
*   **삭제 데이터 처리:**
    *   상품이나 브랜드가 논리 삭제된 경우, '나의 좋아요 목록'에서 자동으로 제외한다.
    *   이미 삭제된 상품에 대해 좋아요를 등록하려고 시도할 경우 에러를 반환한다.

### 2.6 쿠폰 및 할인 정책
*   **쿠폰 발급 및 선착순 정책:** 쿠폰 발급 시 선착순 수량 제한(재고 제한)은 없으며, 유효기간 내에 1인 1매에 한해 발급 여부(중복 발급 방지)만 체크한다.
*   **쿠폰 종류 및 할인 계산:**
    *   **정액 쿠폰(`FIXED`):** 주문 금액에서 고정된 할인 금액(원)을 차감한다. (주문 금액이 할인액보다 작을 경우 최종 금액은 0원 미만이 될 수 없다)
    *   **정률 쿠폰(`RATE`):** 주문 금액의 일정 비율(%)을 할인한다. 단, 쿠폰에 **최대 할인 금액 한도(`maxDiscountAmount`)**가 설정된 경우 해당 금액 한도까지만 할인한다.
    *   **할인 계산 책임:** 할인 금액 계산 및 유효 조건(최소 주문금액 대조 등) 검증 로직은 **Coupon 도메인(Entity)이 직접 스스로 수행**하도록 책임을 분리한다. 주문 도메인은 쿠폰 객체에 금액만 전달하여 최종 차감 금액을 반환받는다.
*   **쿠폰 유효 조건:**
    *   쿠폰 템플릿에 등록된 최소 주문 금액(`minOrderAmount`) 조건보다 주문 상품의 총액이 크거나 같을 때만 사용할 수 있다.
    *   쿠폰 만료일(`expiredAt`)이 지나지 않았고, 상태가 사용 가능(`AVAILABLE`)인 본인 소유의 쿠폰이어야 한다.
    *   **만료 판단 방식:** 내 쿠폰 목록 조회 시 만료 상태는 별도의 동기화 배치 없이, **조회 시점에 서버 현재 시간과 만료일을 동적으로 대조하여 실시간으로 가공 및 반환(WAS 가공)**한다.
*   **쿠폰 일회성:** 사용 완료된 쿠폰은 즉시 `USED` 상태로 변경되며 재사용이 불가합니다.
* **쿠폰 동시 사용 방지:** 동일한 발급 쿠폰을 여러 기기나 브라우저에서 동시에 주문에 사용하더라도, **낙관적 락(Optimistic Lock)**을 통해 단 한 번만 사용되도록 방어하고 중복 요청은 에러를 반환한다.

---

## 3. 기능 요구사항 (API List)

### 3.1 유저 (Users)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

### 3.2 브랜드 & 상품 (Brands / Products)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 (필터 및 정렬 지원) |
| GET | `/api/v1/products/{productId}` | X | 상품 상세 정보 조회 |

*   **상품 목록 조회 파라미터:**
    *   `brandId`: 특정 브랜드 필터링 (다중 선택은 제외하고 단일 ID 매칭 원칙 유지)
    *   `sort`: `latest` (기본), `likes_desc`
        *   `likes_desc` 정렬 시, 좋아요 개수가 0개인 상품도 포함(Left Join)하여 노출한다.
        *   `likes_desc` 정렬 시, 좋아요 개수가 동일한 상품은 최신 등록 순(`latest`)으로 2차 정렬한다.
    *   `page`, `size`: 페이징 지원 (기본 0, 20)

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/brands` | O | 등록된 브랜드 목록 조회 |
| POST | `/api-admin/v1/brands` | O | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | O | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | O | 브랜드 삭제 (연관 상품 논리 삭제 포함) |
| GET | `/api-admin/v1/products` | O | 등록된 상품 목록 조회 (brandId 필터 포함) |
| POST | `/api-admin/v1/products` | O | 상품 등록 (유효한 브랜드 ID 필수) |
| PUT | `/api-admin/v1/products/{productId}` | O | 상품 정보 수정 (브랜드 변경 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | O | 상품 삭제 (논리 삭제) |

### 3.3 좋아요 (Likes)
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 (중복 요청 시 성공 반환) |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/me/likes` | O | 내가 좋아요 한 상품 목록 조회 (삭제된 상품 제외) |

### 3.4 주문 (Orders)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders/checkout` | O | 주문 요청 (단일 API, 내부적으로 단일 트랜잭션 기반 PG사 연동 처리) |
| GET | `/api/v1/orders` | O | 유저의 주문 목록 조회 (`startAt`, `endAt` 기간 필터) |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/orders` | O | 전체 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

### 3.5 쿠폰 (Coupons)
#### 고객용 기능
| METHOD | URI | user_required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/coupons/{couponId}/issue` | O | 쿠폰 발급 요청 |
| GET | `/api/v1/users/me/coupons` | O | 내 쿠폰 목록 조회 (AVAILABLE / USED / EXPIRED 상태 반환) |

#### 어드민 기능
| METHOD | URI | ldap_required | 설명 |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/coupons?page=0&size=20` | O | 쿠폰 템플릿 목록 조회 |
| GET | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 상세 조회 |
| POST | `/api-admin/v1/coupons` | O | 쿠폰 템플릿 등록 (정액/정률 타입 및 할인값, 최대 할인 한도 등 입력) |
| PUT | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 수정 |
| DELETE | `/api-admin/v1/coupons/{couponId}` | O | 쿠폰 템플릿 삭제 |
| GET | `/api-admin/v1/coupons/{couponId}/issues?page=0&size=20` | O | 특정 쿠폰의 발급 내역 조회 |



## 4. 기술적 제약 사항 및 고려 사항
- **데이터 정합성:** 재고 확인 및 차감은 원자적(Atomic)으로 수행되어야 한다.
- **단일 트랜잭션 기반 주문/결제 처리:** 데이터 정합성을 가장 우선시하여 클라이언트의 결제/주문 단일 요청 API(`/api/v1/orders/checkout`) 내부의 주문 검증, 재고 차감 비관적 락, 외부 PG사 승인 API, 주문 완료 처리를 모두 하나의 트랜잭션으로 묶어 처리한다. 이를 통해 복잡한 보상 트랜잭션 구현 없이, 외부 통신 실패 시 스프링 프레임워크 롤백으로 데이터 일관성을 쉽게 유지한다.
- **예외 처리 규칙:** 비즈니스 예외는 모두 `CoreException(ErrorType, customMessage?)`로 통일한다. HTTP 상태/에러 코드는 `ErrorType` enum에서 통합 관리하여 응답한다. 새로운 예외가 필요한 경우 enum 내에서 정의하여 사용한다.
- **확장성 (DIP):** 외부 결제사 연동 등 인프라스트럭처 확장에 대처할 수 있도록 결제 처리는 `PaymentGateway` 인터페이스에 의존하며, 테스트 및 로컬 환경을 위해 가짜 승인을 처리하는 `MockPaymentGateway`를 제공한다.
- **삭제 정책:** 모든 삭제는 `Logical Delete`를 원칙으로 하여 주문 이력과의 참조 무결성을 유지한다.
