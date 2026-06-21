# 요구사항 분석 이력 및 3주차 반영 기준

## TL;DR

이번 설계의 핵심은 상품 탐색부터 주문, 결제, 재고 차감까지 이어지는 흐름에서 내부 데이터 정합성과 외부 시스템 장애를 분리하는 것이다.

| 구분 | 기본 방향 |
| --- | --- |
| 주문 생성 | 상품 판매 상태와 재고를 검증하고 재고를 차감한 뒤 `PAYMENT_PENDING` 주문과 `REQUESTED` 결제 row를 같은 DB 트랜잭션에서 생성한다. |
| 결제 요청 | 주문 생성 API는 `PAYMENT_PENDING`을 반환하고, 결제 요청은 내부 비동기로 처리한다. 외부 결제 시스템은 `auth/capture/void`를 지원한다고 가정한다. |
| 결제 대기 | 결제 대기 시간은 주문 생성 시각 기준 최대 1분으로 제한한다. |
| 결제 대기 만료 | 소스 레벨에서 1분 초과를 판정하고 `PAYMENT_FAILED`와 실패 사유 `TIMEOUT`으로 처리한다. |
| 결제 실패/취소/타임아웃 | 결제 결과 기록, 주문 상태 전이, 재고 복구를 하나의 DB 트랜잭션으로 처리한다. |
| 결제 결과 확인 | 사용자는 주문 상태 조회 API로 결제 결과를 확인한다. |
| 중복 결제 처리 | 주문 생성 시 `payment` row를 먼저 만들고, 결제 worker는 `REQUESTED` 상태와 `orderId` 기반 idempotency key로 중복 외부 결제를 막는다. |
| 외부 데이터 플랫폼 | 주문 성공 이벤트를 outbox에 저장한 뒤 `EventRelayWorker`가 별도로 전송하고 재시도한다. |
| 좋아요 | 판매 가능한 상품에만 새 좋아요를 등록하고, `product_like`와 `product.like_count`를 같은 트랜잭션에서 갱신한다. |
| 상품 조회 | 판매 중지/품절 상품은 목록과 상세 조회에 노출하지 않는다. |
| 내 좋아요 목록 | 예전에 좋아요한 판매 중지/품절 상품도 노출한다. |

## 읽는 포인트

- 확정된 요구사항보다 아직 결정해야 할 정책과 경계를 드러내는 데 초점을 둔다.
- 주문/결제/외부 연동은 하나의 성공 흐름처럼 보이지만, 실제로는 서로 다른 트랜잭션 경계와 장애 경계를 가진다.
- 이후 다이어그램 문서는 이 요구사항에서 나온 책임 분리와 정합성 전략을 시각적으로 검증한다.

## 아키텍처 원칙

이번 설계는 장기적으로 큰 서비스를 만든다는 전제로 도메인 우선 모듈러 모놀리스 구조를 기준으로 한다. 최상위 모듈은 `catalog`, `ordering`, `payment`, `event`로 나누고, 각 모듈 내부에서 `interfaces`, `application`, `domain`, `infrastructure` 계층을 둔다.

## 문제 상황 재해석

이번 설계의 핵심 문제는 상품 탐색부터 주문, 결제, 재고 차감까지 이어지는 이커머스 흐름에서 데이터 정합성을 유지하는 것이다.

사용자 관점에서는 상품을 확인하고 좋아요를 누르며, 재고 범위 안에서 주문을 접수하고 결제 결과를 조회할 수 있어야 한다. 같은 좋아요 요청을 여러 번 보내거나 결제 중 외부 시스템이 실패해도 사용자는 예측 가능한 결과를 받아야 한다.

비즈니스 관점에서는 품절 상품 판매, 결제 성공 후 주문 실패 같은 불일치를 막아야 한다. 좋아요 수, 상품 상태, 주문/결제 상태는 운영자가 신뢰할 수 있는 기준 데이터가 되어야 한다.

시스템 관점에서는 내부 트랜잭션과 외부 결제 시스템 호출의 경계를 분리해야 한다. DB 트랜잭션 안에 외부 호출을 오래 붙잡으면 장애 전파와 락 경합이 커질 수 있으므로, 주문 상태와 결제 상태를 명시적으로 관리하고 내부 비동기 결제 처리를 상태 기반으로 운영하는 설계가 필요하다.

## 설계 범위

포함 범위:

- 상품 목록 조회
- 상품 목록 필터, 정렬, 페이징
- 상품 상세 조회
- 브랜드 조회
- 상품 좋아요 등록/취소
- 내 좋아요 목록 조회
- 좋아요 멱등 처리
- 주문 생성
- 주문 목록 조회
- 주문 시 재고 차감
- 결제 요청 및 결과 반영
- 주문/결제 흐름에서 필요한 외부 시스템 연동
- 브랜드/상품/주문 ADMIN API
- 랭킹/추천 확장을 위한 사용자 행동 기록 기준

제외 범위:

- 회원가입, 내 정보 조회, 비밀번호 변경은 `service.md`의 전체 서비스 API에 존재하지만 1주차 `identity` 기능으로 보고 volume-2 신규 도메인 설계에서는 제외한다.
- 쿠폰 발급/사용은 `service.md`의 서비스 흐름에는 등장하지만 구체 API와 정책이 없으므로 이번 설계에서는 주문 도메인의 향후 확장 포인트로만 둔다.

## 기본 설계 가정

상세 시나리오 문서가 현재 저장소에 충분히 포함되어 있지 않아, 아래 내용은 설계 초안의 기본 가정이다. 실제 구현 전 정책 확정이 필요하다.

- 사용자는 기존 회원 시스템의 `userId`로 식별한다.
- `User`는 volume-2의 새 도메인이 아니며, 기존 `identity` 모듈의 `userId` 참조로만 다룬다.
- volume-2 ERD에서는 `User` 내부 테이블과 필드를 상세 설계하지 않는다.
- 유저 로그인이 필요한 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더를 받는다.
- volume-2 설계에서는 `X-Loopers-LoginId`를 `userId`로 참조하고, `X-Loopers-LoginPw`는 기존 `identity` 인증 경계의 입력으로만 본다.
- ADMIN API는 `/api-admin/v1` prefix와 `X-Loopers-Ldap: loopers.admin` 헤더를 사용한다.
- 상품은 하나의 브랜드에 속한다.
- 상품 상태는 `ON_SALE`, `SOLD_OUT`, `STOPPED`로 구분한다.
- 상품 목록과 상세 조회에는 판매 가능한 상품만 노출하며, `SOLD_OUT`/`STOPPED` 상품은 제외한다.
- 좋아요 등록은 판매 가능한 상품에만 허용한다.
- `SOLD_OUT`/`STOPPED` 상품에 대한 좋아요 등록 요청은 허용하지 않는다.
- 내 좋아요 목록에는 사용자가 예전에 좋아요한 `SOLD_OUT`/`STOPPED` 상품도 노출한다.
- 좋아요 취소는 기존 이력 정리 목적이므로 상품 상태와 무관하게 허용한다.
- 좋아요 등록과 취소는 허용된 정책 범위 안에서 멱등하게 처리한다.
- 좋아요 수는 강한 정합성으로 관리한다.
- `product_like`를 정합성 기준 데이터로 두고, `product.like_count`는 조회용 카운터로 보관한다.
- 좋아요 등록/취소와 `product.like_count` 증감은 같은 DB 트랜잭션에서 처리한다.
- `product_like(user_id, product_id)` 유니크 제약으로 중복 등록을 방지한다.
- 주문 생성 시 상품 판매 상태와 재고를 검증하고, 재고는 내부 DB 트랜잭션에서 먼저 차감한다.
- 주문 생성 API는 주문을 `PAYMENT_PENDING` 상태로 저장하고, 같은 DB 트랜잭션에서 `payment(order_id, status=REQUESTED)` row를 생성한 뒤 사용자에게 먼저 응답한다.
- 주문 목록/상세 응답의 `paymentStatus`는 항상 존재하며, 주문 생성 직후에는 `REQUESTED`로 반환한다.
- 결제 요청은 주문 생성 트랜잭션 이후 서버 내부 비동기 흐름에서 외부 결제 시스템에 요청한다.
- 외부 결제 시스템은 `auth`, `capture`, `void`를 지원한다고 가정한다.
- 결제 대기 시간은 주문 생성 시각 기준 최대 1분으로 제한한다.
- 1분을 초과한 `PAYMENT_PENDING` 주문은 정상 지연이 아니라 결제 처리 실패로 본다.
- 1분 초과 판정은 주문 생성 시각과 주문/결제 상태를 기준으로 소스 레벨에서 처리한다.
- `TIMEOUT`은 별도 컬럼이 아니라 실패 사유 값이다.
- 결제 대기 만료 시 주문은 `PAYMENT_FAILED` 상태가 되고, 실패 사유 값은 `TIMEOUT`이 된다.
- 외부 결제 실패, 취소, 타임아웃 시 주문은 실패/취소 상태가 되고, 차감된 재고는 즉시 복구한다.
- 외부 결제 실패, 취소, 타임아웃 처리에서는 결제 결과 기록, 주문 상태 전이, 재고 복구를 하나의 DB 트랜잭션으로 처리한다.
- 사용자는 주문 상태 조회 API로 결제 성공/실패/취소 결과를 확인한다.
- `payment.order_id` unique 제약으로 같은 주문의 결제 row는 하나만 생성한다.
- 결제 worker는 `REQUESTED` 상태의 `payment` row를 외부 결제 요청 대상으로 처리한다.
- 외부 결제 요청에는 `orderId` 기반 idempotency key를 사용한다.
- worker는 `PAYMENT_PENDING` 주문과 `REQUESTED` 결제를 스캔해 1분 초과 건을 `PAYMENT_FAILED/TIMEOUT`으로 전이한다.
- 외부 결제 요청 후 응답이 지연되거나 worker가 중단되어도 주문 생성 시각 기준 1분을 넘기면 만료 처리한다.
- 이미 성공/실패/취소로 확정된 주문과 결제는 만료 스캔에서 재처리하지 않는다.
- 1분 이후 도착한 결제 성공 응답은 주문을 다시 `PAID`로 되돌리지 않는다.
- 결제 성공 처리 시 `OrderEventPublisher`가 주문 완료 이벤트를 `order_event_outbox`에 저장한다.
- 주문 `PAID` 상태 전이와 outbox 저장은 같은 DB 트랜잭션 경계에서 처리한다.
- 실제 외부 데이터 플랫폼 전송은 `EventRelayWorker`가 outbox를 읽어 수행한다.
- `PaymentService`는 `DataPlatformClient`를 직접 호출하지 않는다.
- 결제 성공 후 외부 데이터 플랫폼 전송 실패는 주문 성공과 분리하고 재시도 대상으로 둔다.

## 결정 상태 요약

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| 회원 경계 | 확정 | `User`는 기존 `identity` 모듈의 `userId` 참조로만 보고, volume-2에서는 내부 테이블/필드를 설계하지 않는다. |
| 상품과 브랜드 관계 | 기본안 확정 | 상품은 하나의 브랜드에 속한다고 본다. |
| 좋아요 멱등성 | 기본안 확정 | `userId + productId` 유니크 제약을 기준으로 한다. |
| 좋아요 수 정합성 수준 | 확정 | 강한 정합성으로 관리한다. |
| 좋아요 카운터 기준 | 확정 | `product_like`를 기준 데이터로 두고 `product.like_count`를 조회용 카운터로 둔다. |
| 좋아요 등록 허용 범위 | 확정 | 판매 가능한 상품에만 새 좋아요를 등록할 수 있다. |
| 내 좋아요 목록 노출 | 확정 | 예전에 좋아요한 판매 중지/품절 상품도 내 좋아요 목록에는 노출한다. |
| 내 좋아요 목록 API 경로 | 확정 | `service.md` 원문을 따라 `GET /api/v1/users/{userId}/likes`를 사용한다. 단, `X-Loopers-LoginId`와 path `userId`가 같아야 한다. |
| 상품 목록 페이징/정렬 | 확정 | `service.md` 기준으로 `page=0`, `size=20`, `sort=latest/price_asc/likes_desc`를 사용한다. |
| ADMIN API | 확정 | `service.md` 기준으로 브랜드/상품/주문 ADMIN API를 `/api-admin/v1` 하위에 둔다. |
| ADMIN 브랜드 삭제 | 확정 | 브랜드 row는 보존하고 `deletedAt`을 표시하며, 해당 브랜드 상품은 `ProductStatus.STOPPED`으로 전환한다. |
| ADMIN 상품 삭제 | 확정 | 과거 주문 이력 보호를 위해 물리 삭제하지 않고 `ProductStatus.STOPPED`으로 전환한다. |
| 인증 헤더 | 확정 | 유저 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw`, ADMIN API는 `X-Loopers-Ldap`를 사용한다. |
| 쿠폰 | 확정 | `service.md`에 구체 API가 없으므로 이번 설계에서는 주문 확장 포인트로만 기록하고 요청 DTO에는 포함하지 않는다. |
| 주문 생성 시 재고 차감 | 비관적 락 확정 | 내부 정합성이 필요하므로 재고 행을 잠근 뒤 DB 트랜잭션 안에서 처리한다. |
| 주문 생성 시 결제 row 생성 | 확정 | 주문 생성 트랜잭션에서 `payment(order_id, status=REQUESTED)`를 함께 저장해 주문 조회 응답의 `paymentStatus`를 항상 제공한다. |
| 주문/결제 처리 방식 | 확정 | 단일 주문 API로 `PAYMENT_PENDING`을 반환하고 내부 비동기로 결제를 처리한다. |
| 외부 결제 호출 위치 | 확정 | 주문 생성 트랜잭션 밖에서 호출하고, PG는 `auth/capture/void`를 지원한다고 가정한다. |
| 결제 대기 시간 | 확정 | 주문 생성 시각 기준 최대 1분으로 제한한다. |
| 결제 대기 만료 처리 | 확정 | 외부 요청 전/후와 worker 재시작 여부와 관계없이 1분 초과 건을 `PAYMENT_FAILED` 상태와 실패 사유 값 `TIMEOUT`으로 처리한다. |
| 결제 실패/취소/타임아웃 보상 방식 | 확정 | 결제 결과 기록, 주문 상태 전이, 재고 복구를 하나의 DB 트랜잭션으로 처리한다. |
| 결제 결과 확인 | 확정 | 주문 상태 조회 API로 확인한다. |
| 중복 결제 요청 처리 | 확정 | 주문 생성 트랜잭션의 `payment` row 선생성, `payment.order_id` unique 제약, 외부 결제 `orderId` idempotency key를 함께 사용한다. |
| 품절/판매 중지 상품 조회 | 확정 | 목록과 상세 조회 모두 노출하지 않는다. |

## 상태 전이 정책

### OrderStatus

| 현재 상태 | 이벤트 | 다음 상태 | 처리 |
| --- | --- | --- | --- |
| - | 주문 생성 성공 | `PAYMENT_PENDING` | 재고 차감, 주문 저장, `Payment(REQUESTED)` 저장을 완료하고 사용자에게 먼저 응답한다. |
| `PAYMENT_PENDING` | 결제 성공 | `PAID` | 결제 성공 기록과 주문 완료 이벤트 저장을 같은 DB 트랜잭션에 묶는다. |
| `PAYMENT_PENDING` | 결제 실패 | `PAYMENT_FAILED` | 결제 실패 기록, 주문 상태 전이, 재고 복구를 같은 DB 트랜잭션에 묶는다. |
| `PAYMENT_PENDING` | 결제 대기 1분 초과 | `PAYMENT_FAILED` | 실패 사유 값은 `TIMEOUT`으로 기록하고 재고를 복구한다. |
| `PAYMENT_PENDING` | 결제 취소 | `CANCELED` | 결제 취소 기록, 주문 상태 전이, 재고 복구를 같은 DB 트랜잭션에 묶는다. |
| `PAID` | 결제 성공 이벤트 중복 수신 | `PAID` | 멱등하게 무시한다. |
| `PAYMENT_FAILED` | 1분 이후 결제 성공 응답 도착 | `PAYMENT_FAILED` | 이미 실패 확정된 주문은 `PAID`로 되돌리지 않는다. |
| `CANCELED` | 결제 성공 응답 도착 | `CANCELED` | 이미 취소 확정된 주문은 `PAID`로 되돌리지 않는다. |

### PaymentStatus

| 현재 상태 | 이벤트 | 다음 상태 | 처리 |
| --- | --- | --- | --- |
| - | 주문 생성 성공 | `REQUESTED` | 주문 생성 트랜잭션에서 `payment(order_id, REQUESTED)`를 함께 저장한다. |
| `REQUESTED` | 외부 승인/매입 성공 | `SUCCESS` | `transactionKey`를 저장하고 주문을 `PAID`로 전이한다. |
| `REQUESTED` | 외부 결제 실패 | `FAILED` | 실패 사유를 저장하고 주문 실패 및 재고 복구를 수행한다. |
| `REQUESTED` | 외부 결제 취소 | `CANCELED` | 취소 사유를 저장하고 주문 취소 및 재고 복구를 수행한다. |
| `REQUESTED` | 결제 대기 1분 초과 | `FAILED` | 실패 사유 값은 `TIMEOUT`으로 기록한다. |
| `SUCCESS`/`FAILED`/`CANCELED` | 만료 스캔 또는 중복 결과 수신 | 현재 상태 유지 | 확정 상태는 다시 처리하지 않는다. |

### OutboxStatus

| 현재 상태 | 이벤트 | 다음 상태 | 처리 |
| --- | --- | --- | --- |
| - | 주문 결제 성공 이벤트 저장 | `PENDING` | 주문 `PAID` 전이와 같은 DB 트랜잭션에서 저장한다. |
| `PENDING` | 외부 데이터 플랫폼 전송 성공 | `SENT` | 전송 완료 상태로 갱신한다. |
| `PENDING` | 외부 데이터 플랫폼 전송 실패, 재시도 가능 | `PENDING` | `retry_count`를 증가시키고 다음 재시도 대상으로 남긴다. |
| `PENDING` | 외부 데이터 플랫폼 전송 실패, 최대 재시도 초과 | `FAILED` | 수동 확인 대상으로 남긴다. |
| `SENT`/`FAILED` | worker 재스캔 | 현재 상태 유지 | 전송 대상에서 제외한다. |

## 기능 요구사항

### 상품

- 사용자는 판매 가능한 상품 목록을 조회할 수 있다.
- 상품 목록에는 상품 ID, 상품명, 가격, 상품 상태, 브랜드명, 좋아요 수를 포함한다.
- 상품 목록은 `brandId`로 특정 브랜드 상품만 필터링할 수 있다.
- 상품 목록은 `sort`로 정렬하며 허용 값은 `latest`, `price_asc`, `likes_desc`다.
- 상품 목록의 기본 페이지 조건은 `page=0`, `size=20`이다.
- 판매 중지/품절 상품은 상품 목록과 상세 조회에 노출하지 않는다.
- 내 좋아요 목록은 일반 상품 목록과 별도로 보며, 예전에 좋아요한 판매 중지/품절 상품도 포함한다.
- 사용자는 상품 상세를 조회할 수 있다.
- 상품 상세에는 목록 정보와 함께 설명, 재고 상태, 브랜드 정보를 포함한다.

### 브랜드

- 브랜드는 상품의 소유 또는 제조 주체를 표현한다.
- 브랜드 조회 시 브랜드 ID, 브랜드명, 설명을 반환한다.
- 상품은 반드시 하나의 브랜드를 가진다는 기본안을 둔다.
- 삭제된 브랜드는 대고객 브랜드 조회와 신규 상품 등록 대상에서 제외한다.

### ADMIN

- ADMIN API는 `/api-admin/v1` prefix를 사용한다.
- ADMIN API는 `X-Loopers-Ldap: loopers.admin` 헤더를 통해 어드민을 식별한다.
- 어드민은 등록된 브랜드 목록과 브랜드 상세를 조회할 수 있다.
- 어드민은 브랜드를 등록, 수정, 삭제할 수 있다.
- 브랜드 삭제 API는 브랜드 row를 물리 삭제하지 않고 `deletedAt`을 표시한다.
- 브랜드 제거 시 해당 브랜드의 상품들도 물리 삭제하지 않고 `STOPPED` 상태로 전환한다.
- 삭제된 브랜드는 대고객 브랜드 조회, ADMIN 기본 목록, 신규 상품 등록 대상에서 제외한다.
- 어드민은 등록된 상품 목록과 상품 상세를 조회할 수 있다.
- 상품 ADMIN 목록은 `page`, `size`, `brandId` 조건을 받을 수 있다.
- 어드민은 상품을 등록, 수정, 삭제할 수 있다.
- 상품 등록 시 상품의 브랜드는 이미 등록된 브랜드여야 한다.
- 상품 정보 수정 시 상품의 브랜드는 수정할 수 없다.
- 상품 삭제 API는 상품 row를 물리 삭제하지 않고 `STOPPED` 상태로 전환한다.
- `STOPPED` 상품은 대고객 상품 목록/상세, 주문, 신규 좋아요 등록 대상에서 제외한다.
- 어드민은 주문 목록과 단일 주문 상세를 조회할 수 있다.

### 좋아요

- 사용자는 판매 가능한 상품에 좋아요를 등록할 수 있다.
- 판매 중지/품절 상품에는 새 좋아요를 등록할 수 없다.
- 이미 좋아요한 판매 가능 상품에 다시 등록 요청을 보내도 성공으로 처리한다.
- 사용자는 상품 좋아요를 취소할 수 있다.
- 좋아요하지 않은 상품에 취소 요청을 보내도 성공으로 처리한다.
- 좋아요 취소는 상품 상태와 무관하게 허용한다.
- 사용자는 내 좋아요 목록에서 예전에 좋아요한 판매 중지/품절 상품도 확인할 수 있다.
- 내 좋아요 목록 응답에는 현재 상품 상태를 포함한다.
- 좋아요 수는 음수가 될 수 없다.
- 좋아요 등록/취소는 사용자와 상품 기준으로 유일해야 한다.
- 좋아요 신규 등록 시에만 `product.like_count`를 1 증가시킨다.
- 실제 취소된 좋아요가 있을 때만 `product.like_count`를 1 감소시킨다.
- 좋아요 이력 변경과 카운터 증감은 같은 DB 트랜잭션에서 처리한다.
- 카운터 증감은 DB 원자적 업데이트로 처리한다.
- 운영 복구가 필요하면 `product_like` 기준으로 `product.like_count`를 재집계한다.

### 주문

- 사용자는 상품과 수량을 지정해 주문을 생성할 수 있다.
- 주문 생성 시 상품 판매 가능 여부와 재고를 검증한다.
- 주문 생성 성공 시 주문 상태는 `PAYMENT_PENDING`, 결제 상태는 `REQUESTED`가 된다.
- 주문 생성 API는 `orderId`와 `PAYMENT_PENDING` 상태를 응답한다.
- 주문 목록/상세 조회의 `paymentStatus`는 주문 생성 직후에도 `REQUESTED`로 채워진다.
- 재고가 부족하면 주문은 생성하지 않는다.
- 주문 항목에는 주문 당시 상품명과 가격을 저장한다.
- 사용자는 `startAt`, `endAt` 기간 조건으로 본인의 주문 목록을 조회할 수 있다.
- 주문 목록 조회는 `X-Loopers-LoginId` 기준 본인 주문만 반환한다.
- 주문 목록 응답에는 주문 ID, 주문 상태, 결제 상태, 총액, 주문 생성 시각을 포함한다.

### 재고

- 주문 생성 시 주문 수량만큼 재고를 차감한다.
- 재고가 부족하면 주문을 실패시킨다.
- 동시 주문에서 재고가 음수가 되지 않도록 비관적 락 전략이 필요하다.

### 결제

- 주문 생성 후 내부 비동기 흐름에서 외부 결제 시스템에 결제를 요청한다.
- 결제 대기 시간은 주문 생성 시각 기준 최대 1분이다.
- 1분을 초과한 `PAYMENT_PENDING` 주문은 결제 처리 실패로 간주한다.
- 1분 초과 여부는 소스 레벨에서 판정하며, 타임아웃 전용 컬럼은 두지 않는다.
- 결제 성공 시 주문 상태를 `PAID`로 변경하고 결제 상태를 `SUCCESS`로 기록한다.
- 결제 실패 시 주문 상태를 `PAYMENT_FAILED`로 변경하고 결제 상태를 `FAILED`로 기록한다.
- 결제 대기 만료 시 주문 상태를 `PAYMENT_FAILED`로 변경하고 실패 사유 값은 `TIMEOUT`으로 처리한다.
- 결제 취소 시 주문을 취소 상태로 전이한다.
- 결제 실패, 취소, 타임아웃 시 차감한 재고를 즉시 복구한다.
- 결제 실패, 취소, 타임아웃 처리 시 결제 결과 기록, 주문 상태 전이, 재고 복구는 하나의 DB 트랜잭션으로 처리한다.
- 사용자는 주문 상태 조회 API로 결제 결과를 확인한다.
- 주문 생성 트랜잭션에서 `payment(order_id, status=REQUESTED)` row를 먼저 생성한다.
- 결제 worker는 `REQUESTED` 상태의 결제 row를 외부 결제 요청 대상으로 처리한다.
- 외부 결제 요청에는 `orderId` 기반 idempotency key를 사용한다.
- 결제 worker는 `PAYMENT_PENDING` 주문과 `REQUESTED` 결제를 스캔해 1분 초과 건을 만료 처리한다.
- 외부 결제 요청 후 응답이 지연되거나 worker가 중단되어도 주문 생성 시각 기준 1분을 넘기면 `PAYMENT_FAILED/TIMEOUT`으로 전이한다.
- 결제 상태가 이미 성공/실패/취소로 확정된 건은 만료 스캔에서 재처리하지 않는다.
- 1분 이후 도착한 결제 성공 응답은 이미 실패 처리된 주문을 다시 `PAID`로 되돌리지 않는다.

### 외부 시스템 연동

- 결제 시스템은 주문 결제 요청과 결과 응답을 담당한다.
- 데이터 플랫폼은 주문 완료 이벤트를 수신한다.
- 외부 시스템 실패는 내부 주문 정합성과 분리해 재시도 또는 보상 대상으로 관리한다.
- PG는 `auth/capture/void` 계약을 제공한다고 가정한다.
- 주문 완료 이벤트는 outbox에 먼저 저장하고, 실제 전송은 별도 worker가 처리한다.
- 사용자 행동 데이터는 이후 랭킹/추천으로 확장될 수 있게 기록 기준을 둔다.
- 좋아요 행동은 `product_like`, 주문 행동은 `orders`/`order_line`과 `order_event_outbox`를 기준으로 남긴다.
- 상품 조회 행동 이벤트는 `event` 모듈의 향후 확장 포인트로 두며, 이번 제출 설계에서는 별도 조회 이벤트 테이블을 만들지 않는다.

## API 계약 초안

### 공통 규칙

- 응답은 현재 코드 패턴과 맞춰 `ApiResponse<T>` 형태로 감싼다.
- 성공 응답은 `meta.result=SUCCESS`, 실패 응답은 `meta.result=FAIL`을 사용한다.
- 대고객 API는 `/api/v1` prefix를 사용한다.
- ADMIN API는 `/api-admin/v1` prefix를 사용한다.
- 사용자 식별이 필요한 API는 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더를 필수로 받는다.
- `X-Loopers-LoginId`는 기존 `identity` 모듈의 `userId` 참조이며, volume-2에서는 회원 내부 필드를 검증하지 않는다.
- `X-Loopers-LoginPw`는 기존 `identity` 인증 경계의 입력으로 보고, volume-2 신규 도메인 모델에는 저장하지 않는다.
- ADMIN API는 `X-Loopers-Ldap` 헤더 값이 `loopers.admin`이어야 한다.
- path에 `userId`가 있는 사용자 전용 API는 `X-Loopers-LoginId`와 path `userId`가 같아야 한다. 다르면 타 유저 직접 접근으로 보고 거부한다.
- 상품 목록과 상품 상세 조회는 공개 API로 두되, 로그인 사용자의 좋아요 여부가 필요한 경우 `X-Loopers-LoginId`를 선택적으로 받을 수 있다.

### 기존 identity API 참조

아래 API는 `service.md`의 전체 서비스 API에 포함되지만, 1주차 `identity` 기능으로 보고 volume-2 신규 설계 대상에서는 제외한다.

| 기능 | Method | Path | Header | 처리 |
| --- | --- | --- | --- | --- |
| 회원가입 | POST | `/api/v1/users` | 없음 | 기존 `identity` 모듈 책임 |
| 내 정보 조회 | GET | `/api/v1/users/me` | `X-Loopers-LoginId`, `X-Loopers-LoginPw` | 기존 `identity` 모듈 책임 |
| 비밀번호 변경 | PUT | `/api/v1/users/password` | `X-Loopers-LoginId`, `X-Loopers-LoginPw` | 기존 `identity` 모듈 책임 |

### 대고객 API

| 기능 | Method | Path | Header | Request | Response |
| --- | --- | --- | --- | --- | --- |
| 상품 목록 조회 | GET | `/api/v1/products` | 선택: `X-Loopers-LoginId` | query: `brandId`, `sort=latest`, `page=0`, `size=20` | `products[].{productId,name,price,status,brandName,likeCount}`, `page` |
| 상품 상세 조회 | GET | `/api/v1/products/{productId}` | 선택: `X-Loopers-LoginId` | path: `productId` | `productId`, `name`, `description`, `price`, `status`, `stockQuantity`, `brand`, `likeCount`, `liked` |
| 브랜드 조회 | GET | `/api/v1/brands/{brandId}` | 없음 | path: `brandId` | `brandId`, `name`, `description` |
| 좋아요 등록 | POST | `/api/v1/products/{productId}/likes` | 필수: `X-Loopers-LoginId`, `X-Loopers-LoginPw` | path: `productId` | `productId`, `liked=true`, `likeCount` |
| 좋아요 취소 | DELETE | `/api/v1/products/{productId}/likes` | 필수: `X-Loopers-LoginId`, `X-Loopers-LoginPw` | path: `productId` | `productId`, `liked=false`, `likeCount` |
| 내 좋아요 목록 조회 | GET | `/api/v1/users/{userId}/likes` | 필수: `X-Loopers-LoginId`, `X-Loopers-LoginPw` | path: `userId`, query: `page=0`, `size=20` | `products[].{productId,name,price,status,brandName,likeCount}`, `page` |
| 주문 생성 | POST | `/api/v1/orders` | 필수: `X-Loopers-LoginId`, `X-Loopers-LoginPw` | body: `items[].{productId,quantity}` | `orderId`, `orderStatus=PAYMENT_PENDING`, `totalAmount` |
| 주문 목록 조회 | GET | `/api/v1/orders` | 필수: `X-Loopers-LoginId`, `X-Loopers-LoginPw` | query: `startAt`, `endAt` | `orders[].{orderId,orderStatus,paymentStatus,totalAmount,createdAt}` |
| 주문 상태 조회 | GET | `/api/v1/orders/{orderId}` | 필수: `X-Loopers-LoginId`, `X-Loopers-LoginPw` | path: `orderId` | `orderId`, `orderStatus`, `paymentStatus`, `failureReason`, `totalAmount`, `items[]` |

### ADMIN API

| 기능 | Method | Path | Header | Request | Response |
| --- | --- | --- | --- | --- | --- |
| 브랜드 목록 조회 | GET | `/api-admin/v1/brands` | 필수: `X-Loopers-Ldap` | query: `page=0`, `size=20` | `brands[].{brandId,name,description}`, `page` |
| 브랜드 상세 조회 | GET | `/api-admin/v1/brands/{brandId}` | 필수: `X-Loopers-Ldap` | path: `brandId` | `brandId`, `name`, `description` |
| 브랜드 등록 | POST | `/api-admin/v1/brands` | 필수: `X-Loopers-Ldap` | body: `name`, `description` | `brandId`, `name`, `description` |
| 브랜드 정보 수정 | PUT | `/api-admin/v1/brands/{brandId}` | 필수: `X-Loopers-Ldap` | path: `brandId`, body: `name`, `description` | `brandId`, `name`, `description` |
| 브랜드 삭제 | DELETE | `/api-admin/v1/brands/{brandId}` | 필수: `X-Loopers-Ldap` | path: `brandId` | 없음. 내부적으로 `Brand.deletedAt` 표시와 관련 상품 `STOPPED` 전환 |
| 상품 목록 조회 | GET | `/api-admin/v1/products` | 필수: `X-Loopers-Ldap` | query: `page=0`, `size=20`, `brandId` | `products[].{productId,name,price,status,brandId,stockQuantity,likeCount}`, `page` |
| 상품 상세 조회 | GET | `/api-admin/v1/products/{productId}` | 필수: `X-Loopers-Ldap` | path: `productId` | `productId`, `name`, `description`, `price`, `status`, `brandId`, `stockQuantity`, `likeCount` |
| 상품 등록 | POST | `/api-admin/v1/products` | 필수: `X-Loopers-Ldap` | body: `brandId`, `name`, `description`, `price`, `stockQuantity`, `status` | `productId` |
| 상품 정보 수정 | PUT | `/api-admin/v1/products/{productId}` | 필수: `X-Loopers-Ldap` | path: `productId`, body: `name`, `description`, `price`, `stockQuantity`, `status` | `productId` |
| 상품 삭제 | DELETE | `/api-admin/v1/products/{productId}` | 필수: `X-Loopers-Ldap` | path: `productId` | 없음. 내부적으로 `Product.status=STOPPED` 전환 |
| 주문 목록 조회 | GET | `/api-admin/v1/orders` | 필수: `X-Loopers-Ldap` | query: `page=0`, `size=20` | `orders[].{orderId,userId,orderStatus,paymentStatus,totalAmount,createdAt}`, `page` |
| 주문 상세 조회 | GET | `/api-admin/v1/orders/{orderId}` | 필수: `X-Loopers-Ldap` | path: `orderId` | `orderId`, `userId`, `orderStatus`, `paymentStatus`, `failureReason`, `totalAmount`, `items[]` |

현재 구현 반영 상태:

- 대고객 상품 API는 `/api/v1/products` 목록/상세 조회를 담당한다.
- 브랜드/상품 mutation API는 `/api-admin/v1/brands`, `/api-admin/v1/products` 하위 ADMIN 경계로 분리했다.
- 도메인 엔티티는 JPA/Spring 의존을 갖지 않고, infrastructure `*JpaEntity`가 영속성 매핑을 담당한다.
- 브랜드 삭제는 `product.brand_id` FK와 과거 주문 이력을 보호하기 위해 브랜드 row를 보존하고 `deletedAt`만 표시한다.
- 상품 삭제는 과거 주문의 `order_line.product_id` 참조와 상품 스냅샷을 보호하기 위해 물리 삭제가 아니라 `STOPPED` 상태 전환으로 처리한다.

### 내부/외부 계약

| 대상 | 호출 | 요청 | 응답 | 비고 |
| --- | --- | --- | --- | --- |
| `PaymentGateway` | `authorize` | `orderId`, `amount`, `idempotencyKey=orderId` | `transactionKey`, `result` | 외부 결제 승인 |
| `PaymentGateway` | `capture` | `transactionKey` | `result` | 승인된 결제 매입 |
| `PaymentGateway` | `voidAuthorization` | `transactionKey` | `result` | 매입 실패 또는 취소 시 승인 취소 |
| `DataPlatformClient` | `sendOrderPaid` | `OrderPaidEvent` | `result` | 실패해도 주문 성공을 되돌리지 않는다. |

### 주요 에러 매핑

| 상황 | HTTP | `errorCode` | 처리 기준 |
| --- | --- | --- | --- |
| 필수 헤더 누락, 요청 값 누락, 수량 0 이하, 잘못된 기간 조건, 잘못된 정렬 기준 | 400 | `Bad Request` | `BAD_REQUEST` |
| `X-Loopers-Ldap` 누락 또는 `loopers.admin` 불일치 | 400 | `Bad Request` | 현재 공통 에러 타입 기준으로 `BAD_REQUEST`로 매핑한다. |
| 판매 중지/품절 상품 상세 조회, 삭제된 브랜드 조회 | 404 | `Not Found` | 공개 조회에서는 노출하지 않는 리소스로 본다. |
| 상품/브랜드/주문이 존재하지 않음 | 404 | `Not Found` | `NOT_FOUND` |
| path `userId`와 로그인 사용자 불일치 | 400 | `Bad Request` | 타 유저 직접 접근 요청으로 본다. |
| 판매 중지/품절 상품 좋아요 등록 | 400 | `Bad Request` | 정책 위반으로 본다. |
| 재고 부족 주문 | 400 | `Bad Request` | 주문은 생성하지 않는다. |
| 중복 좋아요 등록/취소 | 200 | - | 멱등 성공으로 응답한다. |
| 외부 결제 실패/취소/타임아웃 | 200 | - | 주문 상태 조회에서 `PAYMENT_FAILED` 또는 `CANCELED`로 확인한다. |
| 서버 내부 오류 | 500 | `Internal Server Error` | `INTERNAL_ERROR` |

## 확인 필요 질문

### 정책 질문

- 현재 확정되지 않은 정책 질문은 없다.

### 경계 질문

1. 주문 생성 책임은 주문 도메인에 둘 것인가, 주문 애플리케이션 서비스가 재고/결제를 조합할 것인가?
2. 재고 차감 실패 예외를 주문 실패와 어떻게 매핑할 것인가?

### 확장 질문

1. 여러 상품을 한 주문에 담는 장바구니형 주문을 지원할 예정인가?
2. 카드/간편결제 같은 복수 결제 수단을 지원할 예정인가?
3. 상품 조회 행동까지 외부 데이터 플랫폼으로 전송할 예정인가?

## 선택지와 영향

| 주제 | 선택지 | 영향 |
| --- | --- | --- |
| 주문/결제 흐름 | 주문 생성 후 즉시 동기 결제 요청 | 구현 흐름은 단순하지만 외부 장애가 사용자 응답에 직접 영향 |
| 주문/결제 흐름 | 단일 주문 API + 내부 비동기 결제 처리 | 기본안. 사용자가 결제를 기다리지 않아도 되지만 상태 조회와 대기 만료 처리가 필요 |
| 결제 대기 만료 | 소스 레벨에서 1분 초과 판정 후 `PAYMENT_FAILED` + `TIMEOUT` | 대기 주문이 오래 남지 않고 결제 처리 장애를 명시적으로 드러냄 |
| 결제 응답 지연 | 주문 생성 시각 기준 1분 초과 건 스캔 | 외부 응답 지연이나 worker 중단 후에도 pending 상태가 남지 않음 |
| 재고 정합성 | 비관적 락 | 구현이 명확하지만 경합 시 성능 저하 가능 |
| 재고 정합성 | 조건부 업데이트 | 성능에 유리하지만 영향 row 검증과 실패 처리가 필요 |
| 좋아요 수 | 상품 카운터 즉시 갱신 | 기본안. 조회가 빠르고 강한 정합성을 유지할 수 있지만 상품 row 경합 가능 |
| 좋아요 수 | 이력 테이블 실시간 집계 | 정합성은 단순하지만 조회 비용 증가 |
| 좋아요 등록 정책 | 판매 가능한 상품만 허용 | 탐색에서 숨긴 상품에 새 좋아요가 생기지 않음 |
| 내 좋아요 목록 | 과거 좋아요 이력 기준 조회 | 판매 중지/품절 상품도 사용자가 남긴 이력으로 확인 가능 |
| 상품 목록 정렬 | `latest`, `price_asc`, `likes_desc` | `service.md` 기준. 기본 정렬은 `latest`로 둔다. |

## 개념 모델

액터:

- 사용자
- 외부 결제 시스템
- 외부 데이터 플랫폼
- 운영자

핵심 도메인:

- Product
- Brand
- ProductLike
- Order
- OrderLine
- Payment
- Stock

보조 도메인 또는 외부 시스템:

- PaymentGateway
- DataPlatformClient
- OrderEventPublisher
- OrderEventOutbox
- EventRelayWorker

## 유저 시나리오

1. 사용자는 상품 목록을 조회한다.
2. 사용자는 상품 상세에서 브랜드, 가격, 재고 상태, 좋아요 수를 확인한다.
3. 사용자는 상품에 좋아요를 등록하거나 취소한다.
4. 사용자는 내 좋아요 목록에서 예전에 좋아요한 판매 중지/품절 상품도 확인할 수 있다.
5. 사용자는 상품 수량을 선택해 주문을 요청한다.
6. 시스템은 상품 판매 상태와 재고를 검증한다.
7. 시스템은 재고를 차감하고 주문을 생성한다.
8. 시스템은 사용자에게 `orderId`와 `PAYMENT_PENDING` 상태를 응답한다.
9. 내부 비동기 흐름은 외부 결제 시스템에 결제를 요청한다.
10. 결제 성공 시 주문을 결제 완료 상태로 변경한다.
11. 결제 실패, 취소, 타임아웃 시 결제 결과 기록, 주문 상태 전이, 재고 복구를 하나의 DB 트랜잭션으로 처리한다.
12. 사용자는 주문 상태 조회 API로 결제 결과를 확인한다.
13. 사용자는 기간 조건으로 본인의 주문 목록을 조회한다.
14. 주문 완료 이벤트는 outbox에 저장되고, 별도 worker가 외부 데이터 플랫폼으로 전송한다.

## 설계 리스크

- 주문 생성, 재고 차감, 결제 요청을 하나의 트랜잭션으로 묶으면 트랜잭션이 과도하게 커진다.
- 주문과 결제 상태가 분리되면 상태 전이 누락이나 중복 처리 문제가 생길 수 있다.
- 내부 비동기 결제 처리에서는 `PAYMENT_PENDING` 1분 초과 처리와 중복 결제 요청 처리가 누락되면 상태가 오래 남거나 중복 반영될 수 있다.
- 1분 이후 외부 결제 성공 응답이 도착하면 현재 주문 상태와 충돌할 수 있으므로, 이미 실패 처리된 주문을 다시 성공으로 전이하지 않는 방어가 필요하다.
- 좋아요 수를 강한 정합성 카운터로 관리하면 인기 상품에서 카운터 row 경합이 생길 수 있다.
- 좋아요 이력과 카운터가 어긋나는 경우 `product_like` 기준 재집계가 필요하다.
- 내 좋아요 목록이 판매 중지/품절 상품을 포함하므로, 일반 상품 목록과 같은 조회 조건을 재사용하면 과거 좋아요 이력이 누락될 수 있다.
- 재고 차감을 애플리케이션 검증만으로 처리하면 동시 주문에서 초과 판매가 발생할 수 있다.
- 외부 시스템 실패를 사용자 요청 실패로 직접 연결하면 장애 전파 범위가 커진다.

## PR 리뷰 포인트 초안

- 단일 주문 API + 내부 비동기 결제 처리 방식이 사용자 경험과 운영 흐름에 적절한지
- `PAYMENT_PENDING` 1분 초과 시 `PAYMENT_FAILED/TIMEOUT`으로 전이되는지
- 외부 결제 요청 후 응답 지연 또는 worker 중단 상황에서도 1분 초과 건이 스캔되어 만료 처리되는지
- 결제 실패/취소/타임아웃 시 결제 결과 기록, 주문 상태 전이, 재고 복구가 하나의 DB 트랜잭션으로 묶였는지
- 주문 생성 트랜잭션에서 `payment(order_id, status=REQUESTED)` row 선생성이 수행되는지
- 외부 결제 요청에 `orderId` 기반 idempotency key가 포함되는지
- 결제 성공 시 `PaymentService`가 `DataPlatformClient`를 직접 호출하지 않고 outbox 저장만 요청하는지
- 좋아요 등록/취소와 `like_count` 증감이 같은 트랜잭션으로 묶였는지
- `product_like(user_id, product_id)` 유니크 제약과 `like_count >= 0` 방어가 있는지
- 판매 중지/품절 상품에는 새 좋아요 등록이 불가능한지
- 내 좋아요 목록에는 예전에 좋아요한 판매 중지/품절 상품이 포함되는지
- 상품 목록이 `page=0`, `size=20`, `sort=latest/price_asc/likes_desc` 기준을 따르는지
- 브랜드/상품/주문 ADMIN API가 `/api-admin/v1`과 `X-Loopers-Ldap` 경계를 따르는지
- 상품 등록/수정/삭제가 public API가 아니라 ADMIN API에만 노출되는지
- 상품 삭제가 물리 삭제가 아니라 `STOPPED` 상태 전환으로 처리되는지
- 유저 API가 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 계약을 따르는지
