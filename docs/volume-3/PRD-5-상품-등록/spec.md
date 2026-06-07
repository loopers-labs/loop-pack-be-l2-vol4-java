# Spec: PRD-5 상품 등록

**소스**: `docs/volume-2/01-requirements.md` — PRD-5
**작성일**: 2026-05-26
**상태**: Draft

## 시나리오 요약

관리자가 소속 브랜드 식별자·상품 이름·설명·가격·초기 재고를 포함해 새 상품을 등록한다. 관리자 인증을 통과한 요청만 허용하며, 지정한 브랜드가 존재하고 삭제되지 않았는지 확인한 뒤, 이름(1~100자)·가격(0 이상 정수)·초기 재고(0 이상 정수) 검증을 통과하면 새 상품을 생성하고 생성된 식별자를 반환한다. Product 도메인의 첫 시나리오라 Product aggregate 골격(`Product` + `Name`·`Price`·`Stock` VO + `ProductRepository`, 설명은 검증·행위가 없어 `String` 직접 보유)을 새로 세운다. 관리자 인증 토대는 BRD-4에서 만든 것을 재사용한다.

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 관리자 인증 정보를 보유하고 지정한 브랜드가 활성 상태일 때, **When** 유효한 브랜드 식별자·이름·설명·가격·재고로 등록을 요청하면, **Then** 새 상품이 생성되고 생성된 상품 식별자가 반환된다(201).
2. **Given** 관리자 인증 정보를 보유하고 설명을 생략했을 때, **When** 이름·가격·재고만으로 등록을 요청하면, **Then** 설명 없이(null) 상품이 정상 생성된다.
3. **Given** 가격 0·초기 재고 0(경계 최소값)으로 요청할 때, **When** 등록을 요청하면, **Then** 상품이 정상 생성된다.

### Exception Flow
1. **Given** 관리자 인증 정보가 없거나 `X-Loopers-Ldap` 값이 올바르지 않을 때, **When** 등록을 요청하면, **Then** 인증 실패로 응답한다(403 FORBIDDEN).
2. **Given** 지정한 브랜드가 존재하지 않거나 이미 삭제된 상태일 때, **When** 등록을 요청하면, **Then** 자원을 찾을 수 없다고 응답한다(404 NOT_FOUND).
3. **Given** 관리자 인증을 통과했을 때, **When** 이름이 빈 문자열이거나 100자를 초과해 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).
4. **Given** 관리자 인증을 통과했을 때, **When** 가격이 0 미만이거나 초기 재고가 0 미만으로 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).

### 비즈니스 규칙
- 상품 이름은 1자 이상 100자 이하 문자열이다.
- 가격은 0 이상의 정수이다.
- 초기 재고는 0 이상의 정수이다.
- 설명은 선택 입력이다. 미입력 시 null로 저장하고, 입력된 값은 정규화 없이 그대로 저장한다. 선택 입력 근거는 ERD `products.description TEXT NULL`(nullable)이며, 소스 Main Flow의 "설명을 포함해" 표현은 필수성을 규정하지 않는다. (BRD-4 결정 B 선례)
- 소속 브랜드는 등록 시점에 지정하며, 등록 시 브랜드가 활성 상태인지 확인한다. (이후 브랜드 변경 불가는 PRD-6 규칙)

## 엣지 케이스

- 이름 경계값: 0자(빈 문자열, 실패) / 1자(통과) / 100자(통과) / 101자(실패).
- 가격 경계값: -1(실패) / 0(통과).
- 재고 경계값: -1(실패) / 0(통과).
- 설명: 미입력(null 저장) / 빈 문자열(그대로 저장) / 일반 문자열(그대로 저장).
- 브랜드 상태: 활성(통과) / 미존재(404) / 삭제됨(404).
- 권한 경계: 회원 인증 헤더(`X-Loopers-LoginId/Pw`)만 있고 admin 헤더가 없으면 인증 실패(403).

## 기능 요구사항

- **FR-001**: 시스템은 관리자 인증(`X-Loopers-Ldap: loopers.admin`)을 통과한 요청만 상품 등록을 허용해야 한다. 실패 시 403 FORBIDDEN으로 응답한다.
- **FR-002**: 시스템은 지정한 브랜드가 존재하고 삭제되지 않았는지 검증해야 한다. 아니면 404 NOT_FOUND로 응답한다.
- **FR-003**: 시스템은 상품 이름이 1~100자 범위인지 검증해야 한다. 벗어나면 400으로 응답한다.
- **FR-004**: 시스템은 가격이 0 이상의 정수인지 검증해야 한다. 미만이면 400으로 응답한다.
- **FR-005**: 시스템은 초기 재고가 0 이상의 정수인지 검증해야 한다. 미만이면 400으로 응답한다.
- **FR-006**: 시스템은 설명을 선택 입력으로 받아 미입력 시 null, 입력 시 값 그대로 저장해야 한다.
- **FR-007**: 시스템은 생성된 상품의 식별자를 반환해야 한다.

## 관련 엔티티

- **Product** (신규 aggregate): 브랜드 식별자(brandId)·이름·설명·가격·재고 보유. 자기 생성 책임. 브랜드 존재 검증은 응용 계층 책임. `update`/`delete`/`decreaseStock`/`isStockAvailable`은 후속 cycle(PRD-6·PRD-7·ORD-1·PRD-1) 범위라 이번엔 만들지 않는다.
- **Name** (신규 VO, 상품 이름): 1~100자 검증을 생성 시점에 단일화.
- **Price** (신규 VO): 0 이상 정수 검증을 생성 시점에 단일화. (클래스 다이어그램의 공용 `Money` 대신 Product 전용 `Price`로 도입 — 아래 관련 결정 참조)
- **Stock** (신규 VO): 0 이상 정수 검증을 생성 시점에 단일화. `decrease()`/`isAvailable()` 동작 메서드는 이번에 도입하지 않는다(후속 cycle).
- **설명(description)**: 검증·행위가 없어 VO 없이 `String` 필드로 직접 보유(선택). (BRD-4 선례)
- **ProductRepository** (신규): 저장.
- **재사용**: `BrandRepository.existsActiveById`(브랜드 활성 존재 검증; 없으면 응용 계층이 NOT_FOUND), `AdminAuthInterceptor`(admin 인증), `ErrorType`(BAD_REQUEST·NOT_FOUND·FORBIDDEN).

## 테스트 계획

| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| VO/Model 단위 | Name | 1자·100자 통과, 빈 문자열·101자 예외(errorType BAD_REQUEST) |
| VO/Model 단위 | Price | 0 통과, -1 예외(errorType BAD_REQUEST) |
| VO/Model 단위 | Stock | 0 통과, -1 예외(errorType BAD_REQUEST) |
| VO/Model 단위 | Product | 생성 시 brandId·이름·설명·가격·재고 보유, null 설명 허용 |
| Service/Facade 단위 | 상품 등록 유스케이스 | 브랜드 미존재/삭제 시 NOT_FOUND, 정상 시 저장 후 식별자 반환 |
| Integration | ProductRepository | 저장·조회 |
| E2E | `POST /api-admin/v1/products` | 201 + meta.result SUCCESS + 응답에 식별자 / admin 인증 실패 403 / 브랜드 미존재 404 / 이름 101자 400 / 가격 -1 400 (statusCode + meta.result + errorCode까지, 메시지 문구는 단언 안 함) |

## 관련 결정

- **결정 3 (재고 보관 위치 A)**: 재고는 `products` 테이블의 `stock` 컬럼으로 보관(별도 Stock 테이블 미사용).
- **BRD-4 결정 B 선례 (설명 입력)**: `description`은 검증·행위가 없어 VO 없이 `String`으로 직접 보유, 값 정규화 없이 그대로 저장(null 허용).
- **본 cycle 신규 결정 (가격 VO)**: 클래스 다이어그램의 공용 `Money` 대신 Product 전용 `Price` VO로 도입. 가격 검증(0 이상)은 값이 시스템에 처음 들어오는 상품 등록 시점에 단일화한다. Order·OrderItem의 단가는 이미 검증된 Product price의 스냅샷이라 재검증이 불필요하므로 int 스냅샷으로 두는 방향(최종 확정은 Order cycle). 공용 `Money` 추상화는 현 시점 불필요(YAGNI).
- **본 cycle 신규 결정 (stock 동작 범위)**: `Stock` VO는 생성·검증만 갖는다. `decrease(Quantity)`/`isAvailable()`은 실제 사용처(ORD-1 재고 차감·PRD-1 가용 여부 노출)에서 추가한다.
- **재사용**: 관리자 인증(BRD-4 `AdminAuthInterceptor`), 브랜드 활성 검증(`BrandRepository.existsActiveById`).
- **본 cycle review 결정 (가격·재고 DTO 검증)**: 가격·재고에 DTO `@PositiveOrZero`를 둔다. dto.md가 `@Positive`류 수치 가드를 DTO 1차 방어로 허용하므로 컨벤션과 정합(가격·재고는 0 허용이라 OrZero). VO(`Price`/`Stock.from()`)도 `≥0`을 가지므로 이중 가드이며, 음수는 API 계약에 명시된다.
- **본 cycle review 결정 (상품 이름 VO명)**: 상품 이름 VO는 `Name`(도메인 패키지 `domain.product` 내라 접두 생략). (사용자 결정)

## 성공 기준 / 범위 밖

- **성공**: 위 모든 수용 시나리오·테스트 계획이 green. `POST /api-admin/v1/products`가 인증·브랜드 검증·입력 검증·생성 분기를 명세대로 처리.
- **범위 밖**: 상품 조회·목록·수정·삭제(다른 cycle), 재고 차감·가용 여부, 공용 `Money` VO, 가격 상한, 등록 후 브랜드 변경, 상품 이름 중복 검사(요구사항에 없음).
