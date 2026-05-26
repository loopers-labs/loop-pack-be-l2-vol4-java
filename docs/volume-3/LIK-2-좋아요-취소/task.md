# Task: LIK-2 좋아요 취소

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.
> 전제: LIK-1에서 Like aggregate 골격과 `ProductRepository.existsActiveById`가 세워져 있다(재사용).

## Phase 1: 도메인·인프라 (회원·상품 조합 hard delete)

- [X] T001 `LikeRepository.deleteByUserIdAndProductId(Long userId, Long productId)`(hard delete, 없으면 no-op) 추가 — `main/.../domain/like/LikeRepository.java`
- [X] T002 `LikeJpaRepository.deleteByUserIdAndProductId` + `LikeRepositoryImpl.deleteByUserIdAndProductId` 위임 — `main/.../infrastructure/like/LikeJpaRepository.java`, `main/.../infrastructure/like/LikeRepositoryImpl.java`
  - (analyze 결정: 파생 `deleteBy`는 앰비언트 트랜잭션을 요구해 repository 단독 통합 테스트는 제외. hard delete 행 제거·멱등 no-op·다른 회원 격리는 T005 E2E가 실제 Facade 트랜잭션으로 검증.)

## Phase 2: 취소 유스케이스 (`DELETE /api/v1/products/{productId}/likes`)

- [X] T003 `LikeFacade.deleteLike(userId, productId)` 추가 + 단위 테스트 — `main/.../application/like/LikeFacade.java`(`!existsActiveById` → NOT_FOUND / 그 외 `deleteByUserIdAndProductId` 호출(없으면 no-op)), `test/.../application/like/LikeFacadeTest.java`(DeleteLike: 상품 미존재 NOT_FOUND / 상품 활성 시 deleteByUserIdAndProductId 호출)
- [X] T004 `LikeV1ApiSpec.deleteLike` 선언 추가(`ApiResponse<Void> deleteLike(Long productId, @Parameter(hidden=true) @LoginUser AuthenticatedUser)`) + `LikeV1Controller` `@DeleteMapping deleteLike(@PathVariable Long productId, @LoginUser AuthenticatedUser loginUser)`(200, `ApiResponse.success()`) — `main/.../interfaces/api/like/LikeV1ApiSpec.java`, `main/.../interfaces/api/like/LikeV1Controller.java`
- [X] T005 E2E 테스트 — `test/.../interfaces/api/LikeV1ApiE2ETest.java`(DeleteLike: 좋아요 후 취소 200+meta.result SUCCESS+행 제거 / 좋아요 없이 취소 200(멱등) / 반복 취소 200 / 다른 회원의 같은 상품 좋아요 미영향 / 회원 인증 헤더 없음 401 / 상품 미존재 404. statusCode+meta.result+errorCode까지, 메시지 비단언)

## Phase 3: 마무리

- [X] T006 spec 테스트 계획 대비 누락 점검(Facade 상품 분기·삭제 호출 / E2E 6분기·다른 회원 격리 매핑)
- [X] T007 `.http` — `http/commerce-api/like-v1.http`(좋아요 취소 샘플 추가: 정상 / 멱등(없이 취소) / 반복 / 인증 누락 / 상품 미존재)
