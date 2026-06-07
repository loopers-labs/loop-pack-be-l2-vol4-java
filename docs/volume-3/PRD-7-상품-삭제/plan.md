# Plan: PRD-7 상품 삭제

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

`DELETE /api-admin/v1/products/{productId}`로 관리자가 상품을 멱등 soft delete 한다. `ProductFacade.deleteProduct`가 `ProductRepository.findActiveById`(Optional, 부재·삭제 시 빈 결과)로 조회해 활성 상품일 때만 `BaseEntity.delete()`를 호출하고, 부재·이미 삭제면 no-op으로 정상 응답한다(결정 6 멱등, 결정 7 soft delete). 도메인 신규 행위 없음. admin 인증은 `AdminAuthInterceptor`가 가드. PRD-6에서 추가한 JPA `findByIdAndDeletedAtIsNull`을 재사용한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] CRUD 네이밍: Facade·Controller 메서드 `deleteProduct`
- [x] admin 인증: `/api-admin/**` 인터셉터가 가드(컨트롤러 인증 파라미터 없음)
- [x] 결정 6(멱등 삭제): 부재·이미 삭제 모두 정상 응답. no-op 분기를 위해 예외 없는 `findActiveById`(Optional) 사용 — PRD-6의 `getActiveById`(throw)와 의미 구분
- [x] 결정 7(soft delete): `BaseEntity.delete()`(이미 멱등 — `deletedAt == null`일 때만 기록)
- [x] 응답 shape: `ApiResponse.success()`(Void, data null) — 200 OK, 별도 응답 record 불필요

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `interfaces/api/product/ProductAdminV1Controller.java` (편집) — `@DeleteMapping("/{productId}")` `deleteProduct(@PathVariable Long productId)` 추가. 200 OK. `productFacade.deleteProduct(productId)` 후 `ApiResponse.success()` 반환(`ApiResponse<Void>`).
- `interfaces/api/product/ProductAdminV1ApiSpec.java` (편집) — `ApiResponse<Void> deleteProduct(Long productId)` `@Operation` 선언 추가.
- `ProductAdminV1Dto`: 변경 없음(요청 본문·응답 데이터 없음).

### application (편집)
- `application/product/ProductFacade.java` (편집) — `deleteProduct(Long productId)` (신규, 반환 void): `productRepository.findActiveById(productId).ifPresent(ProductModel::delete)`. managed 엔티티 dirty checking으로 `deletedAt` 반영. `@Transactional`(클래스 레벨).

### domain (편집)
- `domain/product/ProductRepository.java` (편집) — `Optional<ProductModel> findActiveById(Long id)` 추가(부재·삭제 시 `Optional.empty`).
- `ProductModel`: 변경 없음(`BaseEntity.delete()` 사용).

### infrastructure (편집)
- `infrastructure/product/ProductRepositoryImpl.java` (편집) — `findActiveById`: `productJpaRepository.findByIdAndDeletedAtIsNull(id)` 그대로 위임(Optional 반환).
- `infrastructure/product/ProductJpaRepository.java` — 변경 없음(`findByIdAndDeletedAtIsNull`은 PRD-6에서 추가됨).

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 멱등 삭제용 `findActiveById`(Optional)를 `getActiveById`(throw)와 별도로 둠 | 같은 JPA 파생 쿼리(`findByIdAndDeletedAtIsNull`)를 공유하되, PRD-6 수정은 부재 시 404가 필요(throw)하고 PRD-7 삭제는 부재 시 no-op(멱등)이 필요해 null 처리 의미가 정반대 | `getActiveById`만으로 처리 후 Facade에서 예외 잡기 — 정상 흐름에 예외 제어, 멱등 의도 흐려짐 |
