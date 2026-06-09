# Spec: CPN-1 쿠폰 템플릿 등록

**소스**: `docs/volume-4/01-requirements.md` — CPN-1
**작성일**: 2026-06-09
**상태**: Draft

## 시나리오 요약

관리자가 새로운 쿠폰 템플릿을 등록한다. 관리자 인증을 통과한 요청만 허용하며, 쿠폰 이름(1~100자)·할인 타입(정액 `FIXED`/정률 `RATE`)·할인 값·최소 주문 금액(선택)·만료 시각을 받아 입력값이 허용 범위 안에 있으면 새 쿠폰 템플릿을 생성하고 생성된 식별자를 반환한다. Coupon 도메인의 첫 시나리오라 Coupon aggregate 골격(`Coupon` Model + `Name`·`DiscountValue`·`MinOrderAmount` VO + `DiscountType` enum + `CouponRepository`)을 새로 세운다. 관리자 인증 토대는 BRD-4에서 만든 것을 재사용한다.

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 관리자 인증 정보를 보유했을 때, **When** 이름·정액 타입·할인 값·최소 주문 금액·미래 만료 시각으로 등록을 요청하면, **Then** 새 쿠폰 템플릿이 생성되고 생성된 식별자가 반환된다(201).
2. **Given** 관리자 인증 정보를 보유했을 때, **When** 정률 타입·할인 값(1~100)으로 등록을 요청하면, **Then** 쿠폰 템플릿이 정상 생성된다.
3. **Given** 관리자 인증 정보를 보유하고 최소 주문 금액을 생략했을 때, **When** 이름·타입·할인 값·만료 시각만으로 등록을 요청하면, **Then** 최소 주문 금액 없이(null) 쿠폰 템플릿이 정상 생성된다.
4. **Given** 같은 이름의 쿠폰 템플릿이 이미 존재할 때, **When** 같은 이름으로 등록을 요청하면, **Then** 새 쿠폰 템플릿이 정상 생성된다(이름 중복 허용).

### Exception Flow
1. **Given** 관리자 인증 정보가 없거나 `X-Loopers-Ldap` 값이 올바르지 않을 때, **When** 등록을 요청하면, **Then** 인증 실패로 응답한다(403 FORBIDDEN).
2. **Given** 관리자 인증을 통과했을 때, **When** 이름이 빈 문자열이거나 100자를 초과해 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).
3. **Given** 관리자 인증을 통과했을 때, **When** 할인 타입이 정액·정률 어느 쪽도 아닌 값으로 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).
4. **Given** 관리자 인증을 통과했을 때, **When** 정률 쿠폰의 할인 값이 1 미만이거나 100을 초과해 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).
5. **Given** 관리자 인증을 통과했을 때, **When** 정액 쿠폰의 할인 값이 1 미만으로 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).
6. **Given** 관리자 인증을 통과했을 때, **When** 최소 주문 금액을 지정했는데 1 미만으로 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).
7. **Given** 관리자 인증을 통과했을 때, **When** 만료 시각이 현재 시각 이전으로 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).

### 비즈니스 규칙
- 할인 타입은 정액(`FIXED`)과 정률(`RATE`) 두 가지다.
- 정액 쿠폰의 할인 값은 원 단위 할인 금액으로 1 이상의 정수다.
- 정률 쿠폰의 할인 값은 퍼센트로 1 이상 100 이하의 정수다.
- 최소 주문 금액은 선택 입력이다. 지정하면 1 이상의 정수이며, 미지정 시 null로 저장하고 주문 금액 제약 없이 적용할 수 있다.
- 만료 시각은 이 템플릿의 발급 가능 기한이며, 현재 시각 이후여야 한다. (결정 2)
- 쿠폰 이름은 중복될 수 있다. 템플릿의 식별은 식별자로만 한다(브랜드 이름 중복 금지와 의도된 차이). DB UNIQUE 제약·중복 검사 없음.
- 관리자 인증은 `X-Loopers-Ldap: loopers.admin` 헤더로 식별한다.

## 엣지 케이스

- 이름 경계값: 0자(빈 문자열, 실패) / 1자(통과) / 100자(통과) / 101자(실패).
- 정률 할인 값 경계: 0(실패) / 1(통과) / 100(통과) / 101(실패).
- 정액 할인 값 경계: 0(실패) / 1(통과).
- 최소 주문 금액: 미입력(null 저장) / 0(실패) / 1(통과).
- 만료 시각: 과거(실패) / 현재 시각 이전(실패) / 미래(통과).
- 할인 타입: `FIXED`(통과) / `RATE`(통과) / 알 수 없는 값(실패).
- 권한 경계: 회원 인증 헤더(`X-Loopers-LoginId/Pw`)만 있고 admin 헤더가 없으면 인증 실패(403).
- 이름 중복: 동일 이름 활성 템플릿이 이미 있어도 새 템플릿 정상 생성.

## 기능 요구사항

- **FR-001**: 시스템은 관리자 인증(`X-Loopers-Ldap: loopers.admin`)을 통과한 요청만 쿠폰 템플릿 등록을 허용해야 한다. 실패 시 403 FORBIDDEN으로 응답한다.
- **FR-002**: 시스템은 쿠폰 이름이 1~100자 범위인지 검증해야 한다. 벗어나면 400으로 응답한다.
- **FR-003**: 시스템은 할인 타입이 정액(`FIXED`)·정률(`RATE`) 중 하나인지 검증해야 한다. 아니면 400으로 응답한다.
- **FR-004**: 시스템은 정률 쿠폰의 할인 값이 1~100인지, 정액 쿠폰의 할인 값이 1 이상인지 검증해야 한다. 벗어나면 400으로 응답한다.
- **FR-005**: 시스템은 최소 주문 금액을 선택 입력으로 받아, 지정 시 1 이상인지 검증하고 미지정 시 null로 저장해야 한다. 1 미만이면 400으로 응답한다.
- **FR-006**: 시스템은 만료 시각이 현재 시각 이후인지 검증해야 한다. 이전이면 400으로 응답한다.
- **FR-007**: 시스템은 생성된 쿠폰 템플릿의 식별자를 반환해야 한다.

## 관련 엔티티

- **Coupon** (신규 aggregate): 이름·할인 타입·할인 값·최소 주문 금액·만료 시각 보유. 값이 최초로 들어오는 검증 원천 지점이라 VO로 입력을 검증한다. 자기 생성(`create`) 책임. `update`/`delete`/`isExpired`는 후속 cycle(CPN-2·3·6) 범위라 이번엔 만들지 않는다.
- **Name** (신규 VO, 쿠폰 이름): 1~100자 검증을 생성 시점에 단일화. (패키지 `domain.coupon` 내라 접두 생략)
- **DiscountType** (신규 enum): `FIXED`·`RATE`. **할인 값의 전체 검증(null·타입별 범위 — 정액 ≥1, 정률 1~100)을 `validate(Integer)`가 단일 소유**한다(review 결정 B). `discountValue`는 타입 없이는 유효성을 확정할 수 없어 별도 VO 없이 원시 `int`로 보유. `calculate`(할인 계산)는 사용처(ORD-7) 범위라 이번엔 도입하지 않는다.
- **MinOrderAmount** (신규 VO): 1 이상 검증을 생성 시점에 단일화. 미지정(null) 허용.
- **ExpiredAt** (신규 VO): 만료 시각. null·과거 시각 금지를 `from()`에 단일화 (review 결정 — 사용자 입력값이라 VO로 캡슐화, `BirthDate`의 미래 날짜 금지와 대칭).
- **CouponRepository** (신규): 저장.
- **재사용**: `AdminAuthInterceptor`(admin 인증, BRD-4), `ErrorType`(BAD_REQUEST·FORBIDDEN).

## 테스트 계획

| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| VO/Model 단위 | Name | 1자·100자 통과, 빈 문자열·101자 예외(errorType BAD_REQUEST) |
| VO/Model 단위 | MinOrderAmount | 1 통과, 0 예외(errorType BAD_REQUEST) |
| VO/Model 단위 | ExpiredAt | 미래 통과, null·과거 예외(errorType BAD_REQUEST) |
| VO/Model 단위 | DiscountType | null·정액 <1·정률 범위 밖(0·101) 예외, 정액 ≥1·정률 1~100 통과 (errorType BAD_REQUEST) |
| VO/Model 단위 | Coupon | 생성 시 이름·타입·할인 값·최소 주문 금액(null 허용)·만료 시각 보유, 정률 값 101·만료 시각 과거면 예외(errorType BAD_REQUEST) |
| Service/Facade 단위 | 쿠폰 템플릿 등록 유스케이스 | 정상 시 저장 후 식별자 반환, 할인 값 범위 밖이면 BAD_REQUEST·미저장 |
| Integration | CouponRepository | 저장·조회, 최소 주문 금액 null 보존 |
| E2E | `POST /api-admin/v1/coupons` | 201 + meta.result SUCCESS + 응답에 식별자(+최소금액 생략 201) / admin 인증 실패 403 / 알 수 없는 할인 타입 400 / 이름 101자 400 / 정률 값 101 400 / 만료 시각 과거 400 (statusCode + meta.result + errorCode까지, 메시지 문구는 단언 안 함) |

## 관련 결정

- **결정 1 (템플릿·발급 쿠폰 분리)**: `Coupon`은 관리자가 정의하는 템플릿. 발급 쿠폰(`UserCoupon`)은 CPN-6 범위로, 이번 cycle은 템플릿 aggregate만 세운다.
- **결정 2 (만료 시각 의미)**: 템플릿의 만료 시각은 발급 가능 기한이며 현재 시각 이후여야 한다. 발급 시 쿠폰에 복사되는 동작은 CPN-6 범위.
- **본 cycle 신규 결정 (이름 중복 허용)**: 쿠폰 이름은 중복 허용. 브랜드(BRD-4)와 달리 중복 검사·DB UNIQUE 없음 — 같은 이름의 할인 행사를 기간을 달리해 반복 발행하는 운영을 위함.
- **review 결정 B (할인 값 검증 일원화)**: 할인 값의 유효성은 타입과 분리 불가능(예: RATE 5000은 하한만으론 못 거름)하므로, `DiscountValue` VO를 두지 않고 `DiscountType.validate(Integer)`가 null·타입별 전체 범위(정액 ≥1, 정률 1~100)를 단일 소유한다. `validate`(public, null 가드) + `validateRange`(abstract, 타입별 범위) template method로 분기 없이 다형 디스패치. `discountValue`는 원시 `int`로 보유. (당초 plan의 `DiscountValue` VO + 검증 분할을 대체)
- **review 결정 (discountType enum 수신)**: DTO가 `discountType`을 `String`이 아니라 `DiscountType` enum으로 수신한다. 허용 외 값은 Jackson 역직렬화 실패 → `ApiControllerAdvice`의 enum `InvalidFormatException` 처리로 `BAD_REQUEST` + 허용 값 목록 응답. `DiscountType.from(String)` 수동 파싱은 제거(잉여). 매칭은 대문자 `FIXED`/`RATE` 엄격.
- **review 결정 (ExpiredAt VO)**: 만료 시각은 사용자 입력값이라 `ExpiredAt` VO로 캡슐화해 null·과거 금지를 `from()`에 단일화(`BirthDate`의 미래 날짜 금지와 대칭). `CouponModel`의 외톨이 검증 메서드 제거.
- **재사용**: 관리자 인증(BRD-4 `AdminAuthInterceptor`).

## 성공 기준 / 범위 밖

- **성공**: 위 모든 수용 시나리오·테스트 계획이 green. `POST /api-admin/v1/coupons`가 인증·입력 검증·생성 분기를 명세대로 처리.
- **범위 밖**: 쿠폰 템플릿 수정·삭제·조회(CPN-2~5), 발급(CPN-6), `Coupon.update`/`delete`/`isExpired`, `DiscountType.calculate`(할인 계산), 발급 쿠폰(`UserCoupon`), 이름 중복 검사, 동시 등록 race.
