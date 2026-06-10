# Plan: LOCK-1 재고 차감 비관적 락

**Spec**: ./spec.md
**작성일**: 2026-06-10

## 요약

주문 흐름의 재고 차감에서 대상 상품을 비관적 쓰기 락(`SELECT ... FOR UPDATE`)으로 잠금 조회한 뒤 차감한다. `ProductJpaRepository`에 `@Lock(PESSIMISTIC_WRITE)` 잠금 조회 메서드를 추가하고, `ProductRepository.getByIdForUpdate`로 노출하며, `OrderFacade`가 차감 대상 상품을 이 메서드로 조회하도록 한 줄 교체한다. 차감 로직(`ProductModel.decreaseStock`)과 상품 식별자 정렬(데드락 방지)은 단계 3·ORD-7에서 이미 갖춰져 변경하지 않는다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock` (Spring Data JPA 기본 포함)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (OrderFacade → ProductRepository → ProductJpaRepository)
- [x] 검증은 VO `from()`에 단일화 — 재고 부족 검증은 `Stock.decrease`(단계 3), 본 시나리오는 검증 변경 없음
- [x] 결정 10 반영: 재고 차감 비관적 락. 데드락은 상품 식별자 순 잠금 획득(ORD-7 정렬 유지)
- [x] `get*` 접두사 = 없으면 NOT_FOUND (RepositoryImpl이 책임) — `getByIdForUpdate`도 동일
- [x] 조회 전용 `getActiveById`(락 없음)는 그대로 유지 — 잠금은 차감 경로에만 적용

## 레이어별 설계 결정 & 파일 맵

### interfaces
- 변경 없음 (`OrderV1Controller`·`OrderV1Dto` 그대로 — 동시성은 내부 구현)

### application
- `application/order/OrderFacade.java` — `createOrderItems` 루프에서 차감 대상 상품 조회를 `productRepository.getActiveById(...)` → `productRepository.getByIdForUpdate(...)`로 교체. 이 조회 하나로 잠금 + 단가·브랜드명 스냅샷 + 차감을 모두 처리(추가 조회 없음). 정렬·트랜잭션 경계는 유지.

### domain
- `domain/product/ProductRepository.java` — `ProductModel getByIdForUpdate(Long id)` 추가. 부재 시 NOT_FOUND(`get*` 규약). 기존 `getActiveById`는 유지.
- `domain/product/ProductModel.java` — 변경 없음 (`decreaseStock` 단계 3 기존).

### infrastructure
- `infrastructure/product/ProductJpaRepository.java` — `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.deletedAt IS NULL")` `Optional<ProductModel> findByIdAndDeletedAtIsNullForUpdate(Long id)` 추가. (derived query `findByIdAndDeletedAtIsNull`에 `@Lock`을 직접 붙이면 조회 전용 경로까지 잠기므로, 잠금 전용 메서드를 별도로 둔다.)
- `infrastructure/product/ProductRepositoryImpl.java` — `getByIdForUpdate(Long id)` 구현: `findByIdAndDeletedAtIsNullForUpdate(id).orElseThrow(() -> new CoreException(NOT_FOUND, "상품이 존재하지 않습니다."))`. 기존 `getActiveById`의 메시지·어휘 계승.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 잠금 전용 메서드(`findByIdAndDeletedAtIsNullForUpdate`)를 `@Query`로 별도 추가 | derived `findByIdAndDeletedAtIsNull`에 `@Lock`을 붙이면 `getActiveById`(조회 전용·읽기 경로)까지 비관적 락이 걸려 불필요한 락 비용 발생 | 기존 메서드에 `@Lock` 부착(조회 전용 경로 오염) — 기각 |
| 차감 경로의 상품 조회를 `getByIdForUpdate` 하나로 통합 | 단가·브랜드명 스냅샷도 같은 행에서 읽으므로 조회를 둘로 나눌 이유가 없음 | 스냅샷용 조회 + 잠금 조회 분리 — 불필요한 추가 쿼리, 기각 |
