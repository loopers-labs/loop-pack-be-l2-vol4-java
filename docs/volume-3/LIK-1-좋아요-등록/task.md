# Task: LIK-1 좋아요 등록

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase F: Foundational (Like aggregate 골격)

- [X] T001 `LikeModel` 작성 + 단위 테스트 — `main/.../domain/like/LikeModel.java`(`@Entity @Table(name="likes", uniqueConstraints=uk_likes_user_id_product_id(user_id,product_id))` extends BaseEntity, `@Column(name="user_id"/"product_id", nullable=false) Long`, `@Builder` + `@NoArgsConstructor(PROTECTED)` + `@AllArgsConstructor(PROTECTED)`), `test/.../domain/like/LikeModelTest.java`(생성 시 userId·productId 보유)
- [X] T002 `LikeRepository` 인터페이스 — `main/.../domain/like/LikeRepository.java`(`LikeModel save(LikeModel)`, `boolean existsByUserIdAndProductId(Long, Long)`)
- [X] T003 `LikeJpaRepository` + `LikeRepositoryImpl` — `main/.../infrastructure/like/LikeJpaRepository.java`(`extends JpaRepository<LikeModel, Long>`, `existsByUserIdAndProductId`), `main/.../infrastructure/like/LikeRepositoryImpl.java`(`@Component`, save·exists 위임)
- [X] T004 `LikeRepository` 통합 테스트 — `test/.../infrastructure/like/LikeRepositoryIntegrationTest.java`(저장 후 식별자 부여·필드 보존 / `existsByUserIdAndProductId` true·false / 동일 (user_id, product_id) 직접 중복 저장 시 UNIQUE 제약 위반(DataIntegrityViolationException, 단일 스레드))

## Phase 1: 상품 활성 검증 토대 + 등록 유스케이스 (`POST /api/v1/products/{productId}/likes`)

- [X] T005 (review 결정 반영) `ProductRepository.existsActiveById` 미도입 — Facade를 `userRepository.getActiveById(userId)` + `productRepository.getActiveById(productId)` 패턴으로 전환(기존 메서드 재사용, `UserRepository` 주입 추가). 초기 작성한 `existsActiveById` + JPA/impl/통합 테스트는 제거.
- [X] T006 `LikeFacade.createLike(userId, productId)` 작성 + 단위 테스트 — `main/.../application/like/LikeFacade.java`(`UserRepository`·`ProductRepository`·`LikeRepository` 주입; `getActiveById(userId)` 회원 존재 검증 / `getActiveById(productId)` 상품 활성 검증 / 미등록 → save / 이미 등록 → no-op), `test/.../application/like/LikeFacadeTest.java`(CreateLike: 회원 미존재 NOT_FOUND / 상품 미존재 NOT_FOUND / 미등록 시 save 호출 / 이미 등록 시 save 미호출)
- [X] T007 `LikeV1ApiSpec` 작성 — `main/.../interfaces/api/like/LikeV1ApiSpec.java`(`@Tag`/`@Operation`, `ApiResponse<Void> createLike(Long productId, @Parameter(hidden=true) @LoginUser AuthenticatedUser)`)
- [X] T008 `LikeV1Controller` 작성 — `main/.../interfaces/api/like/LikeV1Controller.java`(`@RequestMapping("/api/v1/products/{productId}/likes")`, `@PostMapping createLike(@PathVariable Long productId, @LoginUser AuthenticatedUser loginUser)`, 200, `ApiResponse.success()`)
- [X] T009 E2E 테스트 — `test/.../interfaces/api/LikeV1ApiE2ETest.java`(CreateLike: 정상 200+meta.result SUCCESS+좋아요 저장 / 반복 등록 200(행 1건) / 회원 인증 헤더 없음 401 / 상품 미존재 404. statusCode+meta.result+errorCode까지, 메시지 비단언. fixture는 User·Product JpaRepository.save 직접, 인증 헤더 X-Loopers-LoginId/LoginPw)

## Phase 2: 마무리

- [X] T010 spec 테스트 계획 대비 누락 점검(Like 모델 / Facade 멱등 분기 / Integration 저장·조회·UNIQUE / E2E 4분기 매핑)
- [X] T011 `.http` — `http/commerce-api/like-v1.http`(좋아요 등록 샘플: 정상 / 반복 멱등 / 인증 누락 / 상품 미존재)
