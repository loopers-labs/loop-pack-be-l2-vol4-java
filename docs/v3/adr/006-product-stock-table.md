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
