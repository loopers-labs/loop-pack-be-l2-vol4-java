# Plan: CPN-6 쿠폰 발급

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

`POST /api/v1/coupons/{couponId}/issue`로 로그인 회원이 템플릿에서 발급 쿠폰을 발급받는다. 발급 쿠폰(`UserCouponModel`)은 템플릿(`CouponModel`)과 별도 aggregate로 신규 도입하며, `OrderItemModel`처럼 `Long` ID 참조 + 원시 스냅샷으로 템플릿 정보를 복사 보유한다. 발급 흐름은 `UserCouponFacade.issueCoupon`이 회원 존재 → 템플릿 활성 조회 → 만료 검사 → 발급 이력 검사 → 스냅샷 생성·저장 순으로 처리한다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (기존 도메인·인증 토대 재사용)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] 회원 인증: `@LoginUser AuthenticatedUser`로 `userId` 주입 (resolver가 LoginId/Pw 검증·존재 확인 후 주입, 실패 시 UNAUTHENTICATED)
- [x] 회원 존재 재확인 `userRepository.getActiveById` (ORD `createOrder` 패턴 계승 — Facade 직접 호출 시에도 일관)
- [x] 템플릿 부재/삭제 → NOT_FOUND (`couponRepository.getActiveById` 재사용)
- [x] 만료·중복 발급 → CONFLICT (결정 7 어휘)
- [x] 스냅샷: 템플릿에서 VO로 검증된 값이라 발급 쿠폰은 원시 타입 보유·재검증 없음 (결정 3, `OrderItem` 상품 스냅샷과 동일 원리)
- [x] 만료 기준 시각은 표현 계층이 `DateTimeUtil.now()`로 확정해 주입 (요청 단위 시각 고정)
- [x] 1인 1매: 응용 계층 발급 이력 존재 검사 + DB UK `(user_id, coupon_id)`(ERD). 동시성 보장(UK 기반)은 단계 3~4 범위, 본 cycle은 응용 계층 검사로 기능 보장
- [x] 검증 단일화: 발급 쿠폰은 스냅샷이라 신규 VO 없음. `Coupon.isExpired`만 추가

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/CouponV1Controller.java` (신규, 회원) — `@RequestMapping("/api/v1/coupons")`. `POST /{couponId}/issue` → `issueCoupon(@PathVariable Long couponId, @LoginUser AuthenticatedUser loginUser)`, `@ResponseStatus(CREATED)`. `UserCouponFacade`·`DateTimeUtil` 주입, `userCouponFacade.issueCoupon(loginUser.userId(), couponId, dateTimeUtil.now())` 호출. 요청 바디 없음(식별자는 path, 회원은 인증).
- `interfaces/api/coupon/CouponV1Dto.java` (신규) — `IssueResponse(Long userCouponId)` + `from(UserCouponIssueInfo)`.
- `interfaces/api/coupon/CouponV1ApiSpec.java` (신규) — `@Tag`(Coupon 회원 API) + `@Operation`(쿠폰 발급).

### application
- `application/coupon/CouponFacade.java` (편집) — `issueCoupon(Long userId, Long couponId, ZonedDateTime now)` 추가(쿠폰 도메인 Facade에 편입):
  1. `UserModel user = userRepository.getActiveById(userId)` — 회원 존재(OrderFacade·LikeFacade 방식, 이후 `user.getId()` 사용)
  2. `CouponModel coupon = couponRepository.getActiveById(couponId)` — 부재/삭제 NOT_FOUND
  3. `if (coupon.isExpired(now)) throw CoreException(CONFLICT, ...)` — 만료
  4. `if (userCouponRepository.existsByUserIdAndCouponId(user.getId(), coupon.getId())) throw CoreException(CONFLICT, ...)` — 1인 1매
  5. `UserCouponModel issued = UserCouponModel.issue(user.getId(), coupon)` → `userCouponRepository.save(issued)`
  6. `return UserCouponIssueInfo.from(saved)`
  의존 추가: `UserRepository`·`UserCouponRepository` (기존 `CouponRepository`에 더함).
- `application/coupon/UserCouponIssueInfo.java` (신규) — `record UserCouponIssueInfo(Long userCouponId)` + `from(UserCouponModel)`.

### domain
- `domain/coupon/UserCouponModel.java` (신규 `@Entity`, `user_coupons`) — 필드: `Long userId`, `Long couponId`, `String name`, `@Enumerated(STRING) DiscountType discountType`, `int discountValue`, `int minOrderAmount`, `ZonedDateTime expiredAt`(NOT NULL), `ZonedDateTime usedAt`(nullable). `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}))`. `private @Builder` + 정적 팩토리 `static UserCouponModel issue(Long userId, CouponModel coupon)` — `coupon`의 `name.value()`·`type`·`discountValue`·`minOrderAmount.value()`·`expiredAt.value()`를 스냅샷 복사, `usedAt`은 미설정. (`use`·`calculateDiscount`·`getStatus`·`isOwnedBy`는 CPN-7·ORD-7 범위라 미도입)
- `domain/coupon/CouponModel.java` (편집) — `public boolean isExpired(ZonedDateTime now)` 추가, `expiredAt.isExpired(now)`에 위임.
- `domain/coupon/ExpiredAt.java` (편집) — `public boolean isExpired(ZonedDateTime now)` 추가: `value.isBefore(now)` (경계: `now`와 같으면 미만료). `of()`의 "현재 이후" 검증과 대칭.
- `domain/coupon/UserCouponRepository.java` (신규 interface) — `UserCouponModel save(UserCouponModel userCoupon)`, `boolean existsByUserIdAndCouponId(Long userId, Long couponId)`.

### infrastructure
- `infrastructure/coupon/UserCouponRepositoryImpl.java` (신규, `@Component`) — `UserCouponRepository` 구현, `UserCouponJpaRepository`에 위임.
- `infrastructure/coupon/UserCouponJpaRepository.java` (신규 `extends JpaRepository<UserCouponModel, Long>`) — `boolean existsByUserIdAndCouponId(Long userId, Long couponId)`.

### 설계 문서 정정
- `docs/volume-4/04-erd.md` — `user_coupons.min_order_amount`를 `NULL` → `NOT NULL`로 정정(erDiagram 주석 + DDL COMMENT). 스냅샷 원천(`Coupon.MinOrderAmount`)이 항상 non-null(0)이고 `03-class-diagram.md`(`int`)와 정합. (analyze에서 정식 합의 후 반영)

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `issueCoupon`을 기존 `CouponFacade`에 편입 | 쿠폰 도메인 Facade 하나로 응집, 별도 Facade 신설 잉여 (review 결정 — 당초 `UserCouponFacade` 분리안에서 선회) | 별도 `UserCouponFacade` — 같은 도메인에 Facade 둘이라 과분리 |
| `isExpired`를 `ExpiredAt` VO에 두고 `CouponModel`이 위임 | 시각 비교 의미를 시각 VO가 소유(`of`의 "현재 이후" 검증과 한 곳) | `CouponModel`에서 `expiredAt.value()` 직접 비교 — VO 내부 시각 비교 책임이 엔티티로 샘 |
| 발급 이력 검사를 응용 계층 `existsBy`로 | 본 cycle 동시성 미고려라 단건 검사로 기능 충족. UK는 단계 3~4 동시성 안전망 | UK 위반 예외 변환에 의존 — 동시성 미고려 cycle에서 과함 |
