# ADR-028: 주문 전체 정보를 JSON 스냅샷으로 저장

- 날짜: 2026-06-08
- 상태: 승인됨

---

## Introduction & Goals

- **Context / Background**:
  기존 주문 구조는 `orders` 테이블과 별도 `order_items` 테이블로 분리되어 있었다. 4주차에서 쿠폰 적용 기능이 추가됨에 따라, 주문 스냅샷에 쿠폰 적용 전 금액, 할인 금액, 최종 결제 금액이 모두 포함되어야 한다는 요구사항이 추가되었다.

  이를 반영하기 위한 구조 변경 방향으로 (1) 컬럼 추가, (2) 별도 VO 분리, (3) JSON 전체 스냅샷 세 가지를 검토했다.

- **Goals**:
  주문 시점의 상품 정보 + 쿠폰 적용 금액 정보를 하나의 불변 스냅샷으로 저장하여, 이후 상품/쿠폰 정보 변경에 영향을 받지 않도록 한다.

---

## Detailed Design

### System Architecture

`order_items` 테이블을 제거하고, `orders` 테이블에 `snapshot TEXT` 컬럼 하나를 추가한다.

```
orders 테이블
  - id, user_id, status
  - snapshot TEXT  ← 신규 추가
```

스냅샷 JSON 구조:
```json
{
  "items": [
    { "productId": 1, "productName": "상품A", "productPrice": 10000, "quantity": 2, "subtotal": 20000 }
  ],
  "originalAmount": 20000,
  "discountAmount": 2000,
  "finalAmount": 18000,
  "couponId": 42
}
```

JPA `AttributeConverter`를 통해 `OrderSnapshot` ↔ JSON String 자동 변환한다.

### Data Models

```
OrderSnapshot (record)
  - items: List<OrderSnapshotItem>
  - originalAmount: Long
  - discountAmount: Long
  - finalAmount: Long
  - couponId: Long (nullable)

OrderSnapshotItem (record)
  - productId, productName, productPrice, quantity, subtotal
```

### Constraints

- `snapshot` 컬럼은 주문 생성 시 한 번만 기록되며, 이후 변경하지 않는다.
- 스냅샷 내 items 정보를 기준으로 DB 쿼리 필터링은 하지 않는다.
- `order_items` 테이블 및 `OrderItemVO`, `OrderItemJpaVO`, `OrderItemJpaRepository`는 제거한다.

---

## Alternatives Considered

| 옵션 | Pros | Cons |
|------|------|------|
| 기존 구조 유지 + 금액 컬럼 추가 | 변경 최소화. 금액 필드 직접 쿼리 가능. | order_items와 orders 두 테이블에 스냅샷이 분산. 컬럼이 늘어날수록 스키마 변경 잦아짐. |
| 금액 VO 분리 (OrderAmountVO) | 금액 개념이 명확히 묶임. | items는 여전히 별도 테이블. 스냅샷이 여전히 분리됨. |
| **선택: JSON 전체 스냅샷** | 주문 시점 정보 전체가 단일 컬럼에 불변 저장. 스키마 변경 없이 스냅샷 구조 확장 가능. 이커머스 도메인에서 검증된 패턴. | 스냅샷 내부 값으로 DB 필터링 불가. Jackson 역직렬화 의존. |

**선택 근거:**

요구사항의 핵심은 "주문 정보에는 당시의 상품 정보가 스냅샷으로 저장되어야 한다"이다 (ADR-001 참고). 쿠폰 금액까지 추가되면서 스냅샷 범위가 확장되었고, 이를 여러 컬럼/테이블에 분산 저장하는 것보다 하나의 JSON으로 통합하는 것이 스냅샷의 의미에 더 부합한다. 주문 이력 데이터는 조회 위주이며 스냅샷 내부 값으로 필터링하는 요구사항이 없으므로 JSON 방식의 단점이 실질적으로 발생하지 않는다.
