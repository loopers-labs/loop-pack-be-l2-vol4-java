# ADR-029: 쿠폰 만료 상태 전환 — 배치 없이 Lazy 처리

- 날짜: 2026-06-08
- 상태: 승인됨

---

## Introduction & Goals

- **Context / Background**:
  `CouponEntity`는 `AVAILABLE / USED / EXPIRED` 세 가지 상태를 갖는다. 쿠폰 만료일(`expiredAt`)이 지난 쿠폰을 어떻게 `EXPIRED`로 전환할지 결정이 필요하다.

- **Goals**:
  4주차 스코프(트랜잭션, 락, 동시성, 쿠폰) 내에서, 배치 인프라 없이 만료 처리를 올바르게 동작시킨다.

---

## Detailed Design

### System Architecture

`CouponEntity`에 `status` 컬럼을 저장하되, 만료 전환은 명시적 배치 없이 Lazy하게 처리한다.

**조회 시 Lazy 변환:**
```
CouponEntity.resolveStatus(ZonedDateTime expiredAt):
  status == AVAILABLE && expiredAt < now() → EXPIRED 반환 (DB 미반영)
  그 외 → status 그대로 반환
```

**주문 시 유효성 검증:**
```
1. CouponEntity 조회 (비관적 락)
2. resolveStatus() 호출 → EXPIRED이면 CoreException
3. isOwnedBy() 확인 → 타 유저 쿠폰이면 CoreException
4. use() 호출 → AVAILABLE → USED
```

### Constraints

- DB의 status 컬럼은 실제 만료된 쿠폰이라도 `AVAILABLE`로 남아 있을 수 있다.
- 만료 여부의 최종 판단 기준은 항상 `CouponTemplateEntity.expiredAt`이다.
- 주문 처리 흐름에서 만료 쿠폰은 반드시 차단된다 (유효성 검증 필수).

---

## Alternatives Considered

| 옵션 | Pros | Cons |
|------|------|------|
| 동적 계산 (status 컬럼 없음) | 구조 단순. 배치/Lazy 불필요. | 상태별 필터 쿼리 복잡 (JOIN + 날짜 비교). |
| **선택: status 컬럼 + Lazy 전환** | 상태 필터 쿼리 단순. USED/EXPIRED 명확히 구분. API 응답에서 명확한 상태값 반환 가능. | DB status가 실제 상태와 일시적으로 불일치할 수 있음. |
| 배치로 EXPIRED 일괄 전환 | DB 상태가 항상 정확. 쿼리 신뢰도 높음. | 4주차 스코프 외. 배치 실행 전 시점에 여전히 Lazy 처리 필요. |

**선택 근거:**

4주차 요구사항에 배치 처리 언급이 없으며, 쿠폰 만료 배치는 `commerce-batch` 모듈에 별도로 추가할 수 있는 독립적 작업이다. 현재 스코프에서는 주문 시 유효성 검증에서 만료 쿠폰을 차단하는 것으로 정합성이 보장되므로 Lazy 방식으로 충분하다.
