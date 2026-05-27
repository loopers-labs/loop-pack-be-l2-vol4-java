# Task: ORD-2 다중 항목 주문

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`. ORD-1 골격 재사용 — 신규 파일 없음.

## Phase 1: 구현 (다중 항목 일반화)

- [X] T001 `OrderFacade.createOrder`에 중복 productId 거부 추가 — `main/.../application/order/OrderFacade.java` (항목의 productId distinct 수 < 항목 수 → `CoreException(BAD_REQUEST)`)
- [X] T002 `OrderFacade.createOrder`에 productId 오름차순 정렬 추가 — `main/.../application/order/OrderFacade.java` (차감 루프 전에 정렬 → 교착 회피)
- [X] T003 `OrderFacade` 다중 항목 단위 테스트 추가 — `test/.../application/order/OrderFacadeTest.java` (정상 다중 → 항목별 스냅샷·총액 합 / 중복 productId → BAD_REQUEST / 한 항목 상품 미존재 → NOT_FOUND·미저장 / 한 항목 재고 부족 → CONFLICT·미저장. 빈 목록은 DTO `@NotEmpty`로 막혀 E2E(T006)에서 검증)

## Phase 2: 마무리

- [X] T004 다중 항목 부분 실패 원복 검증 — E2E(`OrderV1ApiE2ETest`)의 `returnsConflict..._andRollsBack`·`returnsNotFound..._andRollsBack`로 검증(N개 중 1개 409/404 → 나머지 차감분 전부 원복·주문 미생성). 별도 Facade 통합 테스트는 두지 않음(E-1 결정 — E2E가 롤백 커버, 코드베이스 선례와 일관)
- [X] T005 다중 항목 교착 회피 — productId 오름차순 정렬(T002)로 잠금 획득 순서를 일관되게 유지하는 구조적 보장. 재고 음수 불가는 단일 상품 원자 차감 동시성 테스트(`ProductRepositoryIntegrationTest.DecreaseStock.neverGoesNegative_underConcurrentDecrease`)로 커버. 별도 다중 항목 deadlock 재현 테스트는 비결정적(flaky)이라 작성하지 않음.
- [X] T006 다중 항목 E2E 테스트 추가 — `test/.../interfaces/api/OrderV1ApiE2ETest.java` (다중 정상 201+항목 목록 / 빈 목록·수량0·중복 → 400 / 한 항목 상품 미존재 → 404 / 한 항목 재고 초과 → 409. statusCode+meta.result+errorCode)
- [X] T007 spec 테스트 계획 대비 누락 점검 (중복·정렬·부분 실패 원복·동시성 매핑)
- [X] T008 `.http` 파일에 다중 항목 샘플 추가 — `http/commerce-api/order-v1.http` (다중 정상 / 중복 productId / 한 항목 재고 초과)
