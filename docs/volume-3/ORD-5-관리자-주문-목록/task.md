# Task: ORD-5 관리자 주문 목록

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase 1: 구현 (관리자 주문 목록 조회)

- [X] T001 `OrderRepository.findActiveByPage` 추가 — `main/.../domain/order/OrderRepository.java` (`Page<OrderModel> findActiveByPage(int page, int size)`)
- [X] T002 `OrderJpaRepository` + `OrderRepositoryImpl` 구현 — `main/.../infrastructure/order/OrderJpaRepository.java`(`findByDeletedAtIsNull(Pageable)`), `main/.../infrastructure/order/OrderRepositoryImpl.java`(`PageRequest.of(page,size,Sort.by(DESC,"orderedAt"))` 위임)
- [X] T003 `OrderRepository` 전체 목록 통합 테스트 — `test/.../infrastructure/order/OrderRepositoryIntegrationTest.java` (전체 회원 활성 주문·삭제 주문 제외·페이징·총 개수)
- [X] T004 `OrderAdminSummaryInfo` 작성 — `main/.../application/order/OrderAdminSummaryInfo.java` (`record(orderId, userId, OrderStatus status, orderedAt, totalPrice)` + `from(OrderModel)`)
- [X] T005 `OrderFacade.readOrders` 작성 + 단위 테스트 — `main/.../application/order/OrderFacade.java`(`@Transactional(readOnly=true)`, page/size 그대로 전달 — 컨벤션상 미검증), `test/.../application/order/OrderFacadeTest.java` (정상 페이징 → 헤더 레벨 Info 변환)
- [X] T006 `OrderAdminV1Dto` 작성 — `main/.../interfaces/api/order/OrderAdminV1Dto.java` (`SummaryResponse(orderId, userId, status(String), orderedAt, totalPrice) + from`, `PageResponse + from(Page<OrderAdminSummaryInfo>)`)
- [X] T007 `OrderAdminV1ApiSpec` 작성 — `main/.../interfaces/api/order/OrderAdminV1ApiSpec.java` (`@Tag`/`@Operation`)
- [X] T008 `OrderAdminV1Controller` 작성 — `main/.../interfaces/api/order/OrderAdminV1Controller.java` (`@RequestMapping("/api-admin/v1/orders")`, `@GetMapping`, 인증 파라미터 없음)
- [X] T009 E2E 테스트 — `test/.../interfaces/api/OrderAdminV1ApiE2ETest.java` (정상 200+메타 키+항목 키(orderId·userId·status·orderedAt·totalPrice) / 빈 결과 200 / admin 헤더 없음 403. statusCode+meta.result+errorCode. fixture는 OrderJpaRepository.save 직접). size·page 위반 400은 미검증(컨벤션상 page/size 미검증).

## Phase 2: 마무리

- [X] T010 spec 테스트 계획 대비 누락 점검 (전체 노출·정렬·페이징·헤더 레벨 필드·403 매핑) 및 `AdminAuthInterceptor`가 `/api-admin/v1/orders` 가드하는지 확인
- [X] T011 `.http` 파일 — `http/commerce-api/order-admin-v1.http` (관리자 목록: 정상 / admin 헤더 누락(403))
