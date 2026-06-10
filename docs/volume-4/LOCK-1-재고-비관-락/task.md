# Task: LOCK-1 재고 차감 비관적 락

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수.

## Phase 1: 구현

- [X] T001 `ProductJpaRepository`에 잠금 조회 메서드 추가 `apps/commerce-api/src/main/java/com/loopers/infrastructure/product/ProductJpaRepository.java` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.deletedAt IS NULL")` `Optional<ProductModel> findByIdAndDeletedAtIsNullForUpdate(Long id)`. import `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock`.
- [X] T002 `ProductRepository` 인터페이스에 `ProductModel getByIdForUpdate(Long id)` 추가 `apps/commerce-api/src/main/java/com/loopers/domain/product/ProductRepository.java`
- [X] T003 `ProductRepositoryImpl`에 `getByIdForUpdate` 구현 `apps/commerce-api/src/main/java/com/loopers/infrastructure/product/ProductRepositoryImpl.java` — `findByIdAndDeletedAtIsNullForUpdate(id).orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."))`
- [X] T004 `OrderFacade.createOrderItems` 루프에서 차감 대상 상품 조회를 `productRepository.getActiveById(itemCommand.productId())` → `productRepository.getByIdForUpdate(itemCommand.productId())`로 교체 `apps/commerce-api/src/main/java/com/loopers/application/order/OrderFacade.java`

## Phase 2: 마무리

- [X] T005 검증: `ConcurrentStockDecrease`(성공 1·재고 0) 통과 확인 — 전체 빌드 그린에 포함.
- [X] T006 회귀 점검: `OrderFacadeTest`(mock)가 `getActiveById`→`getByIdForUpdate` 교체로 깨져 스텁·검증을 `getByIdForUpdate`로 동반 수정 후 그린. 통합/E2E도 그린.
