# ADR-015: OrderStatus — 결제 로직 미구현, PENDING 단일 상태로 운영

- 날짜: 2026-05-28
- 상태: 승인됨

## 결정

결제 게이트웨이 연동이 없으므로, 주문 생성 시 `PENDING` 상태로 고정한다. 향후 결제 기능 추가 시 상태 전이 로직을 확장한다.

```java
public enum OrderStatus {
    PENDING,
    PAID,
    COMPLETED,
    CANCELLED
}
```

### 현재 구현 범위

| 상태 | 의미 | 현재 사용 여부 |
|---|---|---|
| `PENDING` | 주문 생성 직후 — 결제 대기 중 | ✅ 주문 생성 시 고정 |
| `PAID` | 결제 완료 — 처리 중 | ❌ 결제 연동 후 사용 예정 |
| `COMPLETED` | 주문 최종 완료 | ❌ 결제 연동 후 사용 예정 |
| `CANCELLED` | 주문 취소 | ❌ 취소 기능 추가 후 사용 예정 |

## 근거

결제 연동 없이 즉시 `COMPLETED`로 전이하면 실제 주문 완료 상태와 의미가 불일치한다. `PENDING`을 유지하면 향후 결제 흐름(`PENDING → PAID → COMPLETED`) 추가 시 기존 주문 데이터와 자연스럽게 연결된다.
