# ADR-006: 재고 별도 테이블 분리 (product_stock)

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

상품 재고(`quantity`)를 `product` 테이블의 컬럼으로 관리하지 않고, 별도 `product_stock` 테이블로 분리한다.

```
product_stock
  id          (PK)
  product_id  (bigint)  ← product ID 참조 (1:1)
  quantity    (int)
  created_at / updated_at / deleted_at
```

## 근거

**락 격리**: 주문 시 재고 차감은 `product_stock` row에만 락을 건다. `product` 테이블은 락 대상에서 제외되므로, 동시 주문이 많더라도 상품 목록·상세 조회 성능에 영향을 주지 않는다.

**변경 빈도 분리**: 상품명·가격 등 메타데이터는 드물게 변경되는 반면, 재고는 주문마다 변경된다. 변경 빈도가 다른 데이터를 같은 row에 두면 불필요한 row 경합이 발생한다.

**확장성**: 향후 예약 재고(`reserved_quantity`), 창고별 재고(`warehouse_id`) 등 재고 관련 기능 추가 시 `product_stock` 테이블에만 변경이 집중되어 product 도메인 모델을 오염시키지 않는다.

## 트레이드오프

- 상품 생성 시 `product_stock` row도 함께 생성해야 하는 책임이 추가됨
- 재고 포함 상품 조회 시 JOIN 필요 (ProductService 내부에서 처리)

## 관계

- `product` : `product_stock` = 1 : 1
- `product_id`는 ID만 저장하며 JPA `@OneToOne` 관계는 사용하지 않는다 (ADR-005와 동일한 이유)

## 추가 결정: UNIQUE 제약 및 deduct() 설계

**`UNIQUE(product_id)`**: `product_stock` 테이블에 `product_id` UNIQUE 제약을 추가한다. 상품당 재고 행이 2개 이상 생성되는 것을 DB 레벨에서 원천 차단한다.

**`deduct(amount)` 단일 메서드**: 재고 차감 메서드(`deduct`)는 내부에서 잔여 재고 검증(부족 시 예외)과 차감을 모두 수행한다. 별도의 `validate()` 메서드를 두지 않는다.

근거: `validate()` → `deduct()` 를 순서대로 호출하면 두 호출 사이에 다른 트랜잭션이 재고를 변경할 수 있는 TOCTOU(Time-of-Check to Time-of-Use) 경쟁 조건이 발생한다. `FOR UPDATE` 락이 걸린 상태에서 `deduct()` 하나로 검증과 차감을 원자적으로 처리하면 이 문제가 사라진다.
