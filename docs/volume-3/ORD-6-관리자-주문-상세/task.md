# Task: ORD-6 관리자 주문 상세

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`. ORD-1 `OrderItemInfo` 재사용.

## Phase 1: 구현 (관리자 주문 상세 조회)

- [X] T001 `OrderRepository.getActiveById` 추가 — `main/.../domain/order/OrderRepository.java` (`OrderModel getActiveById(Long orderId)`)
- [X] T002 `OrderRepositoryImpl.getActiveById` 구현 — `main/.../infrastructure/order/OrderRepositoryImpl.java` (`findByIdAndDeletedAtIsNull` → empty 시 `CoreException(NOT_FOUND)`)
- [X] T003 `OrderRepository.getActiveById` 통합 테스트 — `test/.../infrastructure/order/OrderRepositoryIntegrationTest.java` (존재 주문 → 반환 / 삭제·미존재 → NOT_FOUND)
- [X] T004 `OrderAdminInfo` 작성 — `main/.../application/order/OrderAdminInfo.java` (`record(orderId, userId, OrderStatus status, orderedAt, totalPrice, List<OrderItemInfo> items)` + `from(OrderModel order, List<OrderItemModel> items)`)
- [X] T005 `OrderFacade.readOrder` 작성 + 단위 테스트 — `main/.../application/order/OrderFacade.java`(`@Transactional(readOnly=true)`, `getActiveById` + `findActiveItemsByOrderId`), `test/.../application/order/OrderFacadeTest.java` (미존재 → NOT_FOUND / 존재 → userId 포함 OrderAdminInfo)
- [X] T006 `OrderAdminV1Dto`에 상세 응답 추가 — `main/.../interfaces/api/order/OrderAdminV1Dto.java` (`DetailResponse(orderId, userId, status, orderedAt, totalPrice, List<ItemResponse>) + from(OrderAdminInfo)`, `ItemResponse(productId, productName, brandName, unitPrice, quantity) + from(OrderItemInfo)`)
- [X] T007 `OrderAdminV1Controller`·`OrderAdminV1ApiSpec`에 상세 조회 추가 — `main/.../interfaces/api/order/OrderAdminV1Controller.java`(`@GetMapping("/{orderId}")`), `main/.../interfaces/api/order/OrderAdminV1ApiSpec.java`(`@Operation`)
- [X] T008 E2E 테스트 추가 — `test/.../interfaces/api/OrderAdminV1ApiE2ETest.java` (정상 200+응답 키(orderId·userId·status·orderedAt·totalPrice·items 전체) / admin 헤더 없음 403 / 미존재 404. statusCode+meta.result+errorCode)

## Phase 2: 마무리

- [X] T009 spec 테스트 계획 대비 누락 점검 (미존재 404·존재 상세·userId 포함·항목 전체·403 매핑)
- [X] T010 `.http` 파일에 관리자 상세 샘플 추가 — `http/commerce-api/order-admin-v1.http` (관리자 상세 / 미존재(404) / admin 헤더 누락(403))
