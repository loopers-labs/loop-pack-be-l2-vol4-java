# Spec: LOCK-2 쿠폰 사용 낙관적 락

**소스**: `docs/volume-4/01-requirements.md` — 결정 9 / ORD-7 비기능 요구사항
**작성일**: 2026-06-10
**상태**: Draft

## 시나리오 요약

같은 발급 쿠폰으로 여러 주문이 동시에 요청될 때 쿠폰이 단 한 번만 사용되도록, 발급 쿠폰의 사용 완료(`USED`) 전이를 낙관적 락으로 보호한다. 주문 흐름은 발급 쿠폰을 조회해 `UserCouponModel.apply`에서 가용성 검증(미사용·미만료) 후 `usedAt`을 기록하는데, 같은 쿠폰을 동시에 조회한 두 트랜잭션이 모두 미사용 상태를 읽으면 검증이 함께 통과한 뒤 서로의 사용 전이를 덮어써(Lost Update), 1회용 쿠폰이 여러 주문에 중복 적용된다(단계 3 재현: 쿠폰 1장에 8건 주문 성공). 발급 쿠폰에 버전을 두어 사용 전이 커밋 시 버전이 바뀌었으면 충돌로 실패시킨다. 1인 1매라 동시 사용은 한 회원이 여러 기기로 동시 주문하는 극히 드문 경우뿐이고, 쿠폰은 1회용이라 충돌 = "이미 사용됨"이므로 **재시도하지 않고** 두 번째 요청을 자원 충돌(409)로 응답한다. 충돌 시 발생하는 `OptimisticLockingFailureException`을 409로 매핑해, "이미 사용된 쿠폰" 요청과 동일한 응답 계약을 유지한다(매핑이 없으면 500으로 샌다). 단건 주문의 쿠폰 적용 동작은 ORD-7과 동일하다(회귀 없음).

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 회원이 보유한 사용 가능 발급 쿠폰 1장, **When** 같은 `userCouponId`로 10건을 동시에 주문, **Then** 정확히 1건만 성공(쿠폰 `USED` 전이·할인 적용)하고 나머지는 실패하며, 쿠폰은 단 한 번만 사용된다.
2. **Given** 사용 가능 발급 쿠폰, **When** 단건 주문(비동시)에 쿠폰 적용, **Then** ORD-7과 동일하게 할인이 적용되고 쿠폰이 `USED`로 전이된다(기능 회귀 없음).

### Exception Flow
1. **Given** 같은 쿠폰의 동시 주문 중 한 건이 먼저 사용 전이를 커밋, **When** 다른 건이 사용 전이를 커밋, **Then** 버전 충돌(`OptimisticLockingFailureException`)이 발생하고 시스템은 409 CONFLICT로 응답하며 해당 주문은 저장되지 않는다(재시도 없음).

### 비즈니스 규칙
- 동일 발급 쿠폰의 동시 사용은 낙관적 락(버전)으로 차단되어, 쿠폰은 최대 한 번만 사용된다.
- 충돌한 두 번째 요청은 재시도하지 않고 자원 충돌(409)로 응답한다(1회용이라 재시도 무의미).
- 버전 충돌 예외는 자원 충돌(409)로 매핑되어 "이미 사용된 쿠폰"과 동일한 응답 계약을 가진다.

## 엣지 케이스
- 쿠폰 1장 + 동시 N(N≫1): 성공 1, 나머지 409.
- 비동시(순차) 주문: 두 번째는 이미 `USED`라 도메인 가드(`apply`의 가용성 검증)에서 먼저 409 — 버전 충돌 경로에 도달하지 않음.
- 단건 쿠폰 적용: 락이 동작에 영향을 주지 않음(회귀 없음).

## 기능 요구사항
- **FR-001**: 시스템은 발급 쿠폰에 버전을 두어, 사용 완료 전이를 커밋할 때 버전이 변경됐으면 충돌로 실패시켜야 한다.
- **FR-002**: 시스템은 동일 쿠폰 동시 사용 시 정확히 한 건만 사용 전이에 성공하고 쿠폰이 한 번만 사용되도록 보장해야 한다.
- **FR-003**: 시스템은 버전 충돌 시 재시도하지 않고 자원 충돌(409)로 응답해야 한다.
- **FR-004**: 시스템은 사용 전이 충돌 예외(`OptimisticLockingFailureException`)를 자원 충돌(409)로 매핑해, 저수준 예외가 서버 오류(500)로 새지 않도록 해야 한다.

> **설계 출처**: 본 흐름은 `02-sequence-diagrams.md`의 ORD-7 다이어그램(저장 시 쿠폰 version 검증 → 충돌 시 409)과 `01-requirements.md` 결정 9, `04-erd.md`의 `user_coupons.version` 컬럼을 따른다.

## 관련 엔티티
- **UserCouponModel** (기존, 변경): 낙관적 락 버전 필드를 추가한다(`@Version` 매핑, `user_coupons.version` 컬럼). `apply(orderAmount, now)`(가용성 검증·할인 계산·`usedAt` 전이) 로직 자체는 ORD-7·단계 3과 동일하며, 사용 전이 flush 시 버전이 동시성 충돌을 검출한다.
- **OrderFacade** (기존, 변경): `applyCoupon`에서 `apply` 직후 `UserCouponRepository.saveAndFlush`로 명시 flush해 충돌을 메서드 안에서 감지하고, `OptimisticLockingFailureException`을 잡아 `CoreException(CONFLICT)`로 번역한다(인프라 예외를 도메인 언어로). 버전 충돌은 flush 시점에 나는데 트랜잭션 커밋은 메서드 밖이라 명시 flush가 필요하다.
- **UserCouponRepository** (기존, 변경): `saveAndFlush(UserCouponModel)` 추가 — 즉시 영속화로 버전 충돌을 호출 지점에서 감지하게 한다(`save`는 그대로 유지).
- **재사용**: `@Transactional` 트랜잭션 경계(ORD-7), `ErrorType.CONFLICT`, `DateTimeUtil`(쿠폰 만료 기준·`usedAt`).

## 테스트 계획
| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| Integration (동시성) | `OrderConcurrencyIntegrationTest.ConcurrentCouponUse` | 쿠폰 1장 + 동시 10 주문 → 성공 1건 (단계 3 재현 테스트가 실패→통과로 전환) |
| Integration (회귀) | OrderFacade 쿠폰 적용 경로 | 비동시 쿠폰 적용이 ORD-7과 동일하게 할인·`USED` 전이 (기존 단위/통합/E2E 그린 유지) |
| Integration (동시성, 409 계약) | `OrderConcurrencyIntegrationTest.ConcurrentCouponUse` | 실패한 주문이 모두 `CoreException(CONFLICT)`임을 단언(B). 버전 충돌이 응용 계층에서 도메인 충돌로 번역돼 500으로 새지 않음을 통합 레벨에서 검증한다(advice 단위 테스트 불필요). 쿠폰 사용 충돌의 409 응답 계약은 기존 `OrderV1ApiE2ETest`의 "이미 사용한 쿠폰 → 409"도 커버. |

## 관련 결정
- **결정 9 (쿠폰 사용 동시성 — 낙관적 락)**: 충돌 빈도 매우 낮음(1인 1매·다중 기기) + 재시도 의미 없음(1회용)이라 낙관적 락 채택. 재시도 루프를 두지 않고 충돌 시 빠르게 실패. `OptimisticLockingFailureException`을 409로 매핑해 "이미 사용된 쿠폰"과 동일 계약 유지.

## 성공 기준 / 범위 밖
- **성공**: `ConcurrentCouponUse` 통과(성공 1건) + 버전 충돌이 409로 응답(500 아님) + 기존 단일스레드 단위/통합/E2E 전부 그린(회귀 없음).
- **범위 밖**: 재고 사용 동시성(LOCK-1), 쿠폰 발급 동시성(UNIQUE 제약으로 이미 보호 — 별도 트랙), 충돌 시 자동 재시도, 분산 락.
