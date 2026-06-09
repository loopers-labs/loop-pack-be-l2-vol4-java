# Plan: CPN-1 쿠폰 템플릿 등록

**Spec**: ./spec.md
**작성일**: 2026-06-09

## 요약

`POST /api-admin/v1/coupons`로 쿠폰 템플릿을 등록한다. Coupon 도메인의 첫 시나리오라 aggregate 골격(`CouponModel` + `Name`·`MinOrderAmount`·`ExpiredAt` VO + `DiscountType` enum + `CouponRepository`/`Impl`/`JpaRepository`)을 세우고, Brand 도메인의 등록 흐름(Controller → Facade → Model.builder → Repository.save)을 그대로 본뜬다. 관리자 인증은 기존 `AdminAuthInterceptor`가 `/api-admin/**`에 자동 적용되므로 신규 작업이 없다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음
- 시간 타입: 도메인은 `ZonedDateTime`(`OrderModel.orderedAt` 선례)을 쓴다. 클래스 다이어그램의 `LocalDateTime expiredAt`은 설계 표기 단순화이며, 코드 컨벤션(`ZonedDateTime`)으로 정렬한다.

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (Brand 패턴 동일)
- [x] 검증은 VO `from()`에 단일화 — `Name`/`MinOrderAmount`/`ExpiredAt`. DTO는 null/blank 1차 방어만(`@NotBlank`/`@NotNull` + message)
- [x] 할인 값 전체 검증(null·정액 ≥1·정률 1~100)은 `DiscountType.validate`가 단일 소유 (review 결정 B — VO 미사용, 원시 int)
- [x] discountType은 enum 수신, 허용 외 값은 `ApiControllerAdvice` enum 처리로 BAD_REQUEST (`DiscountType.from` 제거)
- [x] 만료 시각 "현재 이후" 불변식은 `ExpiredAt` VO `from()`에서 검증 (review 결정 — 사용자 입력값)
- [x] 관리자 인증: `AdminAuthInterceptor`가 `/api-admin/**` 자동 적용 — 재사용, 신규 없음
- [x] 인증된 회원 시그니처(`@LoginUser`)는 이 시나리오와 무관 (admin 전용)
- [x] 별도 도메인 Service 없음 — 교차 애그리거트 로직이 없어 Facade만 둔다 (Brand 선례)
- [x] 이름 중복 검사·DB UNIQUE 없음 (spec 결정, 브랜드와 의도적 차이)

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/coupon/CouponAdminV1Controller.java`
  - `POST /api-admin/v1/coupons` → `createCoupon(@Valid @RequestBody CreateRequest)`, `@ResponseStatus(CREATED)`
  - Facade 호출: `createCoupon(name, discountType, discountValue, minOrderAmount, expiredAt)`
- `interfaces/api/coupon/CouponAdminV1Dto.java`
  - `CreateRequest(String name, DiscountType discountType, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt)`
    - `@NotBlank` name, `@NotNull` discountType(enum), `@NotNull` discountValue, `@NotNull` expiredAt. `minOrderAmount`는 선택이라 검증 없음. 허용 외 discountType 문자열은 Jackson → `ApiControllerAdvice` enum 처리로 400.
  - `CreateResponse(Long couponId)` + `from(CouponCreateInfo)`
- `interfaces/api/coupon/CouponAdminV1ApiSpec.java` — `@Tag` + `@Operation`(쿠폰 등록)

### application
- `application/coupon/CouponFacade.java` — `@Service @Transactional @RequiredArgsConstructor`
  - `createCoupon(name, DiscountType discountType, discountValue, minOrderAmount, expiredAt)`: `CouponModel.builder()...build()` → `couponRepository.save` → `CouponCreateInfo.from`
- `application/coupon/CouponCreateInfo.java` — `record CouponCreateInfo(Long couponId)` + `from(CouponModel)`

### domain
- `domain/coupon/CouponModel.java` — `@Entity @Table(name="coupons")`, `BaseEntity` 상속
  - 필드: `@Embedded Name name`, `@Enumerated(STRING) @Column(discount_type) DiscountType type`, `@Column(discount_value) int discountValue`, `@Embedded MinOrderAmount minOrderAmount`(NOT NULL, 0=제약 없음), `@Embedded ExpiredAt expiredAt`
  - `@Builder private CouponModel(String rawName, DiscountType type, Integer rawValue, Integer rawMinOrderAmount, ZonedDateTime rawExpiredAt, ZonedDateTime now)`:
    - `Name.from` / `type.validate(rawValue)`(null·타입별 전체 범위) / `discountValue = rawValue` / `minOrderAmount = MinOrderAmount.from(rawMinOrderAmount)`(null→0) / `ExpiredAt.of(rawExpiredAt, now)`
  - `update`/`delete`/`isExpired`는 후속 cycle 범위 — 도입하지 않음
- VO (`@Embeddable record`, Brand `Name`/Product `Price` 패턴):
  - `domain/coupon/Name.java` — `from(String)`, 1~100자, BAD_REQUEST
  - `domain/coupon/MinOrderAmount.java` — `from(Integer)`, ≥0, BAD_REQUEST. null→0(제약 없음). (`@Column min_order_amount` NOT NULL)
  - `domain/coupon/ExpiredAt.java` — `from(value, now)`, null·`now` 이전 금지, BAD_REQUEST (`@Column expired_at`). `now`는 `DateTimeUtil`로 주입
- `domain/coupon/DiscountType.java` — `enum { FIXED, RATE }`
  - `public final void validate(Integer value)`: null 가드 후 `validateRange`에 위임 (template method)
  - `abstract void validateRange(int value)`: FIXED는 `value < 1` 시 BAD_REQUEST, RATE는 `value < 1 || value > 100` 시 BAD_REQUEST
  - `from(String)`·`calculate`(할인 계산)는 미도입 — 파싱은 Jackson+advice, calculate는 ORD-7 범위
- `domain/coupon/CouponRepository.java` — `CouponModel save(CouponModel)` (CPN-1 범위; 조회·삭제는 후속 cycle에서 확장)

### infrastructure
- `infrastructure/coupon/CouponRepositoryImpl.java` — `@Component`, `save` 위임
- `infrastructure/coupon/CouponJpaRepository.java` — `extends JpaRepository<CouponModel, Long>`

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `DiscountType`를 template method enum으로 (`validate` + `validateRange`) | review 결정 B — 할인 값 유효성이 타입과 분리 불가하므로 null·범위 전체를 `DiscountType`이 단일 소유. 분기 없이 다형 디스패치, ORD-7 `calculate`도 같은 구조로 확장 | `if (type == RATE)` 분기를 모델에 두기 / `DiscountValue` VO로 하한만 검증(타입 의존 상한을 못 담아 단일 원천 실패) |
| `discountValue`를 VO 없이 원시 `int` | 검증이 타입 의존이라 단독 VO가 불가능 — 행위·독립 검증 없는 값은 원시 타입(model.md) | `DiscountValue` VO 유지 — 하한만 검증해 거짓 안전감 |
| 만료 시각 검증을 `ExpiredAt` VO `from(value, now)`에서 비교, `now`는 `DateTimeUtil`로 표현 계층에서 주입 | 사용자 입력값 + 생성 시점 불변식이라 VO로 단일화(`BirthDate` 미래 금지 대칭). 기준 시각을 주입받아 요청 단위로 고정하고 테스트를 결정적으로 만든다(review 결정 — 만료 템플릿 조회 검증 가능) | `ExpiredAt`이 내부에서 `ZonedDateTime.now()` 직접 호출 — 만료 상태를 테스트로 구성 불가 |
| discountType enum 수신(`from` 제거) | `ApiControllerAdvice`가 enum `InvalidFormatException`을 이미 BAD_REQUEST + 허용목록으로 처리 — 수동 파싱 잉여 | `String` 수신 + `DiscountType.from` 수동 파싱 |

> **설계 산출물 정합**: 본 cycle의 review 결정(B·ExpiredAt VO·enum 수신·MinOrderAmount 0 기본·DateTimeUtil 주입)은 `docs/volume-4/03-class-diagram.md`·`04-erd.md`·`01-requirements.md`에 이미 동기화 반영했다(사용자 승인).
