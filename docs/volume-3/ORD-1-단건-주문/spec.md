# Spec: ORD-1 단건 주문

**소스**: `docs/volume-2/01-requirements.md` — ORD-1 (시퀀스: `02-sequence-diagrams.md` ORD-1)
**작성일**: 2026-05-27
**상태**: Draft

## 시나리오 요약

로그인한 회원이 상품 식별자 하나와 수량을 담아 즉시 주문한다. 회원 인증을 통과한 요청만 허용하며, 대상 상품이 존재하고 삭제되지 않았는지 확인한 뒤 재고가 요청 수량 이상일 때만 그 수량만큼 차감한다. 차감 성공 시 주문 시점의 상품 정보(상품명·소속 브랜드명·단가)와 수량을 주문 항목 스냅샷으로 기록하고, 주문과 주문 항목을 같은 트랜잭션 안에서 저장한 뒤 생성된 주문의 식별자·상태·주문 시각·총 결제 금액·주문 항목 스냅샷을 반환한다. Order 도메인의 첫 시나리오라 Order aggregate 골격(`OrderModel`·`OrderItemModel`·`Quantity` VO·`OrderStatus` enum·`OrderRepository`)과 Product 재고 차감 능력(`ProductModel.decreaseStock`·`Stock.decrease`·원자적 조건부 차감 쿼리)을 새로 세운다. 회원 인증 토대(`@LoginUser AuthenticatedUser`)는 좋아요 cycle에서 만든 것을 재사용한다.

## 수용 시나리오 (Given/When/Then)

### Main Flow
1. **Given** 회원 인증 + 재고가 충분한 활성 상품, **When** 상품 식별자와 수량(재고 이하)으로 주문을 요청하면, **Then** 재고가 요청 수량만큼 차감되고 주문이 생성되며 주문 식별자·상태(CREATED)·주문 시각·총 결제 금액(단가×수량)·주문 항목 스냅샷(상품명·브랜드명·단가·수량)이 반환된다(201).
2. **Given** 재고 수량과 정확히 같은 수량으로 요청(경계), **When** 주문을 요청하면, **Then** 재고가 0으로 차감되고 주문이 정상 생성된다.

### Exception Flow
1. **Given** 회원 인증 정보가 없거나 잘못되었을 때, **When** 주문을 요청하면, **Then** 인증 실패로 응답한다(401 UNAUTHENTICATED).
2. **Given** 대상 상품이 존재하지 않거나 이미 삭제된 상태일 때, **When** 주문을 요청하면, **Then** 자원을 찾을 수 없다고 응답한다(404 NOT_FOUND).
3. **Given** 회원 인증을 통과했을 때, **When** 수량을 1 미만(0 또는 음수)으로 요청하면, **Then** 입력 검증 실패로 응답한다(400 BAD_REQUEST).
4. **Given** 상품의 재고가 요청 수량에 미치지 못할 때, **When** 주문을 요청하면, **Then** 자원 충돌로 응답하며(409 CONFLICT) 재고는 변하지 않는다.

### 비즈니스 규칙
- 주문 항목은 주문 시점의 상품명·소속 브랜드명·단가를 스냅샷으로 저장한다. 이후 상품 정보가 변경·삭제되어도 본 주문 표시는 유지된다. (결정 5)
- 주문 상태는 생성 상태(CREATED)만 진입한다.
- 재고는 주문 생성 시점에 즉시 차감한다. (결정 2)
- 총 결제 금액은 `단가 × 수량`으로 계산한다.
- 재고 차감·주문 저장·주문 항목 저장은 같은 트랜잭션으로 묶여 하나라도 실패하면 전체가 처음 상태로 되돌아간다.

## 엣지 케이스
- 수량 경계: 0(실패 400) / 음수(실패 400) / 1(통과) / 재고와 동일(통과, 재고 0) / 재고+1(실패 409).
- 상품 상태: 활성(통과) / 미존재(404) / 삭제됨(브랜드 cascade 삭제 포함, 404).
- 동시성: 같은 상품에 동시 주문이 몰려도 재고가 음수로 내려가지 않는다 — 합산 차감량이 재고를 초과하는 동시 요청 중 초과분은 409로 거절된다. (결정 4)
- 재고 부족(409) 시 재고·주문 모두 변화 없음(원복).

## 기능 요구사항

- **FR-001**: 시스템은 회원 인증(`X-Loopers-LoginId`/`X-Loopers-LoginPw`)을 통과한 요청만 주문을 허용해야 한다. 실패 시 401로 응답한다.
- **FR-002**: 시스템은 대상 상품이 존재하고 삭제되지 않았는지 검증해야 한다. 아니면 404로 응답한다.
- **FR-003**: 시스템은 수량이 1 이상의 정수인지 검증해야 한다. 미만이면 400으로 응답한다.
- **FR-004**: 시스템은 재고가 요청 수량 이상일 때만 요청 수량만큼 차감해야 한다. 부족하면 409로 응답하고 재고를 변경하지 않는다.
- **FR-005**: 시스템은 동시 주문에서도 재고가 음수가 되지 않도록 차감의 검사·갱신을 원자적으로 보장해야 한다. (결정 4)
- **FR-006**: 시스템은 주문 항목에 주문 시점의 상품명·브랜드명·단가·수량을 스냅샷으로 기록해야 한다.
- **FR-007**: 시스템은 주문과 주문 항목을 같은 트랜잭션으로 저장하고, 하나라도 실패하면 전체를 롤백해야 한다.
- **FR-008**: 시스템은 생성된 주문의 식별자·상태·주문 시각·총 결제 금액·주문 항목 스냅샷을 반환해야 한다.

## 관련 엔티티

- **OrderModel** (신규 aggregate root): 회원 식별자(userId)·상태(OrderStatus)·주문 시각(orderedAt)·총 결제 금액(totalPrice) 보유. 항목 컬렉션은 들지 않고, `OrderItemModel`이 `orderId`로 주문을 참조한다(`@OneToMany` 아님 — 코드베이스 ID 참조 패턴, plan 결정). 총액 합산·항목 정렬·트랜잭션·재고 차감 조율은 응용 계층 책임.
- **OrderItemModel** (신규, Order 종속): 주문 식별자(orderId)·주문 시점 상품 식별자·상품명·브랜드명·단가(평탄한 스냅샷 필드)·수량 보유. 영속 시점에 `assignOrder(orderId)`로 주문에 배선. Order 없이 독립 존재 의미 없음.
- **Quantity** (신규 VO): 주문 수량 1 이상 검증을 생성 시점에 단일화.
- **OrderStatus** (신규 enum): 본 라운드 `CREATED`만.
- **OrderRepository** (신규): 주문+항목 저장(`save(order, items)`)·활성 조회.
- **Product 재고 차감 능력** (신규, ORD-1이 도입): 동시성 보장용 원자적 조건부 차감 쿼리(결정 4 A안). in-memory `Stock.decrease`는 도입하지 않는다(plan 결정 — race 방지).
- **단가·총 결제 금액 스냅샷**: 이미 검증된 Product price의 스냅샷이라 재검증 불필요 → int 스냅샷으로 보관(PRD-5 `Price` 결정의 연장선). 공용 `Money` VO는 도입하지 않는다(YAGNI).
- **재사용**: `@LoginUser AuthenticatedUser`(회원 인증), `ProductRepository.getActiveById`/`findActiveById`(상품 활성 조회), `BrandRepository`(브랜드명 스냅샷 조회), `ErrorType`(BAD_REQUEST·NOT_FOUND·CONFLICT·UNAUTHENTICATED).

## 테스트 계획

| 레벨 | 대상 | 무엇을 단언하는가 |
|------|------|------------------|
| VO/Model 단위 | Quantity | 1 통과, 0·음수 예외(errorType BAD_REQUEST) |
| VO/Model 단위 | OrderItemModel | 스냅샷 필드 보유, totalPrice = 단가×수량 |
| VO/Model 단위 | OrderModel | 빌더 생성 시 상태 CREATED 기본·userId·orderedAt·totalPrice 보존 (총액 합산은 Facade 책임) |
| VO/Model 단위 | OrderItemModel | 스냅샷 필드 보유, totalPrice = 단가×수량, assignOrder로 orderId 배정 |
| Service/Facade 단위 | 주문 유스케이스 | 상품 미존재/삭제 시 NOT_FOUND, 재고 부족 시 CONFLICT, 정상 시 차감+스냅샷+총액 합산+저장 후 결과 반환 |
| Integration | OrderRepository / 재고 차감 쿼리 | save(order,items) 영속화·항목 orderId 배정·Active 조회·soft delete 필터. 원자적 조건부 차감(재고≥수량만 차감, 부족 시 0건·미변경 → 응용 계층이 CONFLICT 매핑). 재고 차감은 in-memory `Stock.decrease`가 아닌 원자 쿼리로 단일화 |
| Integration(동시성) | 재고 차감 쿼리 | 같은 상품 동시 차감에서 재고 음수 불가, 성공 건수 = floor(재고/수량) 류 검증 |
| E2E | `POST /api/v1/orders` | 201 + meta.result SUCCESS + 응답 키(식별자·상태·주문 시각·총 결제 금액·항목) / 인증 실패 401 / 상품 미존재 404 / 수량 0 → 400 / 재고 초과 → 409 (statusCode + meta.result + errorCode까지, 메시지 문구 단언 안 함) |

## 관련 결정

- **결정 2 (즉시 차감 A)**: 주문 생성 시점에 재고를 즉시 차감. 예약/확정 2단계는 도입하지 않는다.
- **결정 4 (동시성 메커니즘 A)**: 원자적 조건부 갱신(`UPDATE products SET stock = stock - :qty WHERE id = :id AND deleted_at IS NULL AND stock >= :qty`)으로 검사·차감 원자성을 DB가 한 쿼리에서 보장. 0건 갱신이면 재고 부족(409). in-memory `Stock.decrease`는 도입하지 않음(plan 확정 — race 방지, 단일 메커니즘).
- **결정 5 (스냅샷 범위 B)**: 주문 항목에 상품명·브랜드명·단가·수량 기록. 설명·이미지는 제외.
- **결정 7 (soft delete)**: 주문·주문 항목은 soft delete 대상(본 라운드에 삭제 API 없음). BaseEntity 상속 + 조회는 전부 Active(`...AndDeletedAtIsNull`)로 일관(plan 결정 B-1).
- **연관 구조 (plan 확정 A-1)**: `OrderItemModel`은 `@OneToMany` 내장 컬렉션이 아니라 `orderId` 참조 + 명시적 조회(`findActiveItemsByOrderId`)로 둔다 — 코드베이스 ID 참조 패턴과 일관. 총액은 Facade가 합산, `orderedAt`은 Facade가 1회 선언해 주입.
- **API 요청 형태 (plan 확정)**: ORD-1·ORD-2가 `POST /api/v1/orders`를 공유. 요청 본문은 처음부터 항목 리스트(`items: [{productId, quantity}]`). 본 spec의 수용 시나리오·테스트는 단건 케이스에 집중한다.

## 성공 기준 / 범위 밖

- **성공**: 위 수용 시나리오·테스트가 green. `POST /api/v1/orders`가 단건 주문에 대해 인증·상품 검증·수량 검증·원자적 차감·스냅샷·트랜잭션 저장·응답을 명세대로 처리.
- **범위 밖**: 다중 항목·중복 거부·부분 실패 원복(ORD-2), 주문 조회/내역/상세(ORD-3·4·5·6), 결제·예약/확정 2단계, 공용 `Money` VO, 주문 취소/삭제 API.
