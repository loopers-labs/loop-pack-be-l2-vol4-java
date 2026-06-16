# ADR-015: orderStatus

- 날짜: 2026-05-22
- 상태: 보류

> 현재 요구사항에 결제 흐름이 없으므로 OrderStatus 확정을 차후 주차로 미룬다. 임시로 `COMPLETED` 단일 값을 사용한다.

## 결정

`OrderStatus`는 `COMPLETED` 단일 값만 가진다.

```java
public enum OrderStatus {
    COMPLETED
}
```

## 근거

일반적인 커머스 시스템은 아래와 같은 상태 흐름을 가진다.

```
PENDING → PAID → SHIPPING → DELIVERED → COMPLETED
                                       ↘ CANCELLED
```

그러나 현재 시스템은 결제 게이트웨이, 배송 연동이 없는 단순 주문 생성 흐름이다. 주문 생성 시점에 재고 차감과 주문 확정이 동시에 이루어지므로, 생성 즉시 완료 상태가 된다.

### 고려한 대안

#### Option 1. 다중 상태 (PENDING / PAID / SHIPPING / COMPLETED / CANCELLED 등) (기각)

- **장점**: 실제 커머스 흐름을 충실히 표현한다.
- **단점**: 결제·배송 연동이 없는 현재 범위에서는 상태 전이 로직만 추가되고 실제 동작은 없다. 불필요한 복잡도 증가다.

---

#### Option 2. COMPLETED 단일 상태 (채택)

- **장점**: 현재 비즈니스 흐름(주문 생성 = 즉시 완료)을 정확히 표현한다. 구현이 단순하다.
- **단점**: 향후 결제·취소·배송 흐름 추가 시 상태값 확장이 필요하다.

## 향후 고려사항

결제 게이트웨이 또는 배송 연동이 추가될 경우 `PENDING`, `CANCELLED` 등의 상태를 확장한다. `OrderStatus`는 enum이므로 값 추가만으로 확장 가능하며, 기존 `COMPLETED` 데이터에는 영향이 없다.
