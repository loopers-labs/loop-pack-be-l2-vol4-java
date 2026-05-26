# Plan: LIK-2 좋아요 취소

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

회원이 `DELETE /api/v1/products/{productId}/likes`로 좋아요를 멱등 취소(hard delete)한다. `LikeFacade.deleteLike`가 인증 회원 userId와 path productId로, 상품 활성 여부를 선검사(없으면 NOT_FOUND — 결정 6 C패턴: 부모 부재는 자원 부재)하고 회원·상품 조합 좋아요를 hard delete 한다(없으면 no-op — 결정 6: 삭제 대상 자신의 부재는 멱등). LIK-1에서 세운 Like aggregate와 `ProductRepository.existsActiveById`를 재사용하고, 회원·상품 조합 삭제 쿼리만 더한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure 준수
- [x] CRUD 네이밍: Facade·Controller 메서드 `deleteLike`
- [x] 회원 인증: `@LoginUser AuthenticatedUser`, 실패 시 `UNAUTHENTICATED`(401)
- [x] 결정 6(LIK-2 C패턴): 상품 부재/삭제 → 404(`existsActiveById` false) / 좋아요 행 부재 → 멱등 no-op
- [x] 결정 7(좋아요 hard delete): 취소는 `LikeRepository`가 회원·상품 조합 행을 실제 제거(soft delete 아님). 도메인 `delete()` 미사용
- [x] 응답 shape: `ApiResponse.success()`(Void, data null) — 200 OK
- [x] 본인 행만: 인증 회원 userId + productId 조합으로만 삭제

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `interfaces/api/like/LikeV1Controller.java` (편집) — `@DeleteMapping` `deleteLike(@PathVariable Long productId, @LoginUser AuthenticatedUser loginUser)`: 200 OK. `likeFacade.deleteLike(loginUser.userId(), productId)` 후 `ApiResponse.success()`(`ApiResponse<Void>`).
- `interfaces/api/like/LikeV1ApiSpec.java` (편집) — `ApiResponse<Void> deleteLike(Long productId, @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser)` `@Operation` 선언 추가.

### application (편집)
- `application/like/LikeFacade.java` (편집) — `deleteLike(Long userId, Long productId)` (void): `if (!productRepository.existsActiveById(productId)) throw NOT_FOUND;` → `likeRepository.deleteByUserIdAndProductId(userId, productId);` (없으면 no-op). `@Transactional`(클래스 레벨).

### domain (편집)
- `domain/like/LikeRepository.java` (편집) — `void deleteByUserIdAndProductId(Long userId, Long productId)` 추가(hard delete, 없으면 no-op).
- `LikeModel`: 변경 없음.

### infrastructure (편집)
- `infrastructure/like/LikeJpaRepository.java` (편집) — `void deleteByUserIdAndProductId(Long userId, Long productId)` 추가(Spring Data 파생 삭제 — Facade `@Transactional` 안에서 실행).
- `infrastructure/like/LikeRepositoryImpl.java` (편집) — `deleteByUserIdAndProductId`: JpaRepository에 위임.
- `infrastructure/product/*`: 변경 없음(`existsActiveById`는 LIK-1에서 추가됨).

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 취소를 `LikeRepository.deleteByUserIdAndProductId`(파생 삭제)로 직접 hard delete | 좋아요는 hard delete(결정 7)라 도메인 `delete()` 행위가 없음. 조합으로 바로 제거하는 게 단순하고 없으면 자연 no-op(멱등) | `findByUserIdAndProductId` 후 존재 시 `repository.delete(entity)` — 한 단계 더, 멱등 분기 수동 처리 |
| 상품 부재 시 404(완전 멱등 B 아님) | 결정 6의 LIK-2는 C패턴 — 부모(상품) 부재는 자원 부재, 좋아요 행 부재만 멱등. BRD-6·PRD-7의 완전 멱등과 의도된 차이(좋아요는 부수 자원) | 상품 부재도 200(완전 멱등) — 결정 6에서 좋아요만 C패턴으로 명시 분리 |
