# Task: LIK-3 좋아요한 상품 목록

**Plan**: ./plan.md

> 완료 시 `- [ ]` → `- [X]`. 경로 prefix `apps/commerce-api/src/{main,test}/java/com/loopers/`. PRD-1 토대(`ProductSummary`·`ProductSummaryInfo`·`ProductV1Dto.PageResponse`) 재사용.

## Phase 1: 좋아요한 상품 목록 (`GET /api/v1/users/{userId}/likes`)

- [X] T001 `LikeRepository.findLikedProductSummaries(Long userId, int page, int size): Page<ProductSummary>` 선언 — `domain/like/LikeRepository.java`
- [X] T002 `LikeJpaRepository.findLikedProductSummaries`(JPQL: LikeModel→Product→Brand JOIN ON + 좋아요 서브쿼리 + `ORDER BY myLike.createdAt DESC, myLike.id DESC` + countQuery) — `infrastructure/like/LikeJpaRepository.java`
- [X] T003 `LikeRepositoryImpl.findLikedProductSummaries`(PageRequest.of 위임) — `infrastructure/like/LikeRepositoryImpl.java`
- [X] T004 통합 테스트 — `test/.../infrastructure/like/LikeRepositoryIntegrationTest.java`(본인 좋아요한 활성 상품만 / 최신 좋아요 순 / 삭제 상품·브랜드 제외 / 페이징·총 개수 / 좋아요 수 집계 / 타 회원 좋아요 미포함)
- [X] T005 `LikeFacade.readLikedProducts(authUserId, pathUserId, page, size)` `@Transactional(readOnly=true)`: 가드 → 불일치 시 빈 페이지 → 일치 시 조회+map + 단위 테스트 — `application/like/LikeFacade.java`, `test/.../application/like/LikeFacadeTest.java`(ReadLikedProducts: 본인 조회·불일치 빈 페이지·page/size 가드)
- [X] T006 `UserLikeV1ApiSpec` + `UserLikeV1Controller` GET(`@LoginUser`, `ProductV1Dto.PageResponse` 재사용) — `interfaces/api/like/`
- [X] T007 E2E — `test/.../interfaces/api/UserLikeV1ApiE2ETest.java`(ReadLikedProducts: 200 메타·항목 키 / 최신 좋아요 순 / 삭제 상품 제외 / 빈 결과 200 / 타인·미존재 userId 200 빈 목록 / 인증 실패 401 / size·page 위반 400)

## Phase 2: 마무리
- [X] T050 spec 테스트 계획 대비 누락 점검
- [X] T051 `.http` — `http/commerce-api/like-v1.http`(좋아요한 상품 목록 추가)
