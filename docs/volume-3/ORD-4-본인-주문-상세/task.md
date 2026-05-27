# Task: ORD-4 본인 주문 상세

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`. ORD-1 `OrderInfo`·`OrderItemInfo` 재사용.

## Phase 1: 구현 (본인 주문 상세 조회)

- [X] T001 `OrderRepository.getActiveByIdAndUserId` 추가 — `main/.../domain/order/OrderRepository.java` (`OrderModel getActiveByIdAndUserId(Long orderId, Long userId)` — 없으면 Impl NOT_FOUND)
- [X] T002 `OrderJpaRepository` + `OrderRepositoryImpl` 구현 — `main/.../infrastructure/order/OrderJpaRepository.java`(파생 쿼리 `findByIdAndUserIdAndDeletedAtIsNull`), `main/.../infrastructure/order/OrderRepositoryImpl.java`(`orElseThrow(NOT_FOUND)` 위임)
- [X] T003 `OrderRepository` 조회 통합 테스트 추가 — `test/.../infrastructure/order/OrderRepositoryIntegrationTest.java` (본인 주문 → 반환 / 타인 주문 → NOT_FOUND / 미존재 → NOT_FOUND)
- [X] T004 `OrderFacade.readMyOrder` 작성 + 단위 테스트 — `main/.../application/order/OrderFacade.java`(`@Transactional(readOnly=true)`, `getActiveByIdAndUserId` + `findActiveItemsByOrderId`로 `OrderInfo.from(order, items)`), `test/.../application/order/OrderFacadeTest.java` (미존재/타인 → NOT_FOUND / 본인 → 항목 포함 OrderInfo)
- [X] T005 `OrderV1Controller`·`OrderV1ApiSpec`에 상세 조회 추가 — `main/.../interfaces/api/order/OrderV1Controller.java`(`@GetMapping("/{orderId}")`, `@LoginUser`), `main/.../interfaces/api/order/OrderV1ApiSpec.java`(`@Operation`)
- [X] T006 E2E 테스트 추가 — `test/.../interfaces/api/OrderV1ApiE2ETest.java` (본인 주문 200+응답 키(orderId·status·orderedAt·totalPrice·items 전체) / 인증 실패 401 / 미존재 404 / 타인 주문 404. statusCode+meta.result+errorCode. fixture는 OrderJpaRepository.save 직접으로 주문 준비)

## Phase 2: 마무리

- [X] T007 spec 테스트 계획 대비 누락 점검 (Facade 404·본인 / Integration 소유 필터(타인·미존재 empty) / E2E 4분기, 타인=404 매핑)
- [X] T008 `.http` 파일에 본인 상세 샘플 추가 — `http/commerce-api/order-v1.http` (본인 주문 상세 / 타인 주문(404) / 미존재(404))
