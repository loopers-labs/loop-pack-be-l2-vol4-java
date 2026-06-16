# Plan: LOCK-2 쿠폰 사용 낙관적 락

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

발급 쿠폰(`UserCouponModel`)에 `@Version` 필드를 추가해, 사용 완료(`usedAt`) 전이를 커밋할 때 버전이 변경됐으면 `OptimisticLockingFailureException`이 발생하도록 한다. 동일 쿠폰 동시 사용 시 두 번째 트랜잭션이 이 예외로 실패하며, `ApiControllerAdvice`에 전용 핸들러를 추가해 자원 충돌(409)로 매핑한다(미매핑 시 `handle(Throwable)`로 빠져 500). `apply` 로직·조회 흐름은 변경하지 않는다 — 버전이 동시성 충돌을 자동 검출한다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: `jakarta.persistence.Version`, `org.springframework.dao.OptimisticLockingFailureException`

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (변경은 domain·interfaces 두 곳)
- [x] 검증은 VO `from()`에 단일화 — 본 시나리오는 검증 추가 없음(낙관 락은 JPA 인프라 메커니즘)
- [x] 결정 9 반영: 쿠폰 사용 낙관적 락, 재시도 없음, 충돌 → 409
- [x] 에러 단언 범위: 핸들러는 `ErrorType.CONFLICT`로 매핑(메시지 문구 단언 금지 — E2E는 statusCode+meta.result+errorCode까지)
- [x] `@Version`은 `UserCouponModel`에만 둔다 — `BaseEntity`에 두면 전 엔티티가 낙관 락이 되어 과함(결정 9는 쿠폰만)

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `OrderV1Controller`·`OrderV1Dto`·`ApiControllerAdvice` — 변경 없음. 충돌은 응용 계층에서 이미 `CoreException(CONFLICT)`로 번역되어, 기존 `handle(CoreException)` 핸들러가 409로 응답한다(인프라 예외 전용 핸들러를 두지 않는다).

### application
- `application/order/OrderFacade.java` — `applyCoupon`에서 `userCoupon.apply(...)` 직후 `userCouponRepository.saveAndFlush(userCoupon)`로 명시 flush. `try/catch (OptimisticLockingFailureException)` → `throw new CoreException(ErrorType.CONFLICT, ...)`. 버전 충돌이 flush 시점에 나는데 트랜잭션 커밋은 메서드 밖이라, 명시 flush로 충돌을 메서드 안에서 감지해 도메인 예외로 번역한다. import `org.springframework.dao.OptimisticLockingFailureException`.

### domain
- `domain/coupon/UserCouponModel.java` — `@Version private Long version;` 필드 추가. 클래스 레벨 `@Builder`/`@AllArgsConstructor`와 공존(빌더에 `version` 노출되나 `issue`·테스트 fixture가 호출하지 않으면 null → 신규 insert 시 Hibernate가 0으로 관리). `apply`·`getStatus`·`issue` 로직 변경 없음.
- `domain/coupon/UserCouponRepository.java` — `saveAndFlush(UserCouponModel)` 추가(`save`와 별개, 즉시 영속화로 충돌 즉시 감지).

### infrastructure
- `infrastructure/coupon/UserCouponRepositoryImpl.java` — `saveAndFlush`를 `userCouponJpaRepository.saveAndFlush(...)`로 위임. `@Version`은 JPA가 자동 처리(UPDATE 시 `WHERE version = ?` + 증가). `ddl-auto: create`(local/test)가 `version` 컬럼을 생성하므로 마이그레이션 불필요.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `@Version`을 `UserCouponModel`에만 부착 | 결정 9는 쿠폰 사용만 낙관 락 대상. `BaseEntity`에 두면 전 도메인이 낙관 락이 되어 불필요한 충돌·과보수 | `BaseEntity.version` 공통화 — 범위 초과, 기각 |
| `OptimisticLockingFailureException`을 응용 계층(`OrderFacade`)에서 `CoreException(CONFLICT)`로 번역 + 명시 flush(`saveAndFlush`) | 인프라 예외를 도메인 언어로 번역하는 것이 레이어 책임에 맞고, Facade/통합 테스트로 검증 가능. 버전 충돌은 flush 시점이라 명시 flush 없이는 메서드 안에서 못 잡음 | advice 전용 핸들러로 인프라 예외 직접 처리 — 레이어 책임 모호·통합 테스트 검증 불가, 기각 |
| 충돌 시 재시도 루프 없음 | 1회용 쿠폰이라 충돌 = 이미 사용됨 → 재시도해도 실패가 정답(결정 9) | `@Retryable` 등 재시도 — 무의미·복잡도 증가, 기각 |
