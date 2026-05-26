# Plan: LIK-1 좋아요 등록

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

회원이 `POST /api/v1/products/{productId}/likes`로 좋아요를 등록한다. Like 도메인의 첫 시나리오라 Like aggregate(`LikeModel` + `LikeRepository` + impl + JpaRepository)를 신설한다. `LikeFacade`가 회원 인증(`@LoginUser AuthenticatedUser`)으로 받은 userId와 path의 productId로, 상품 활성 여부를 선검사(없으면 NOT_FOUND)하고 회원·상품 조합 존재 검사 후 없을 때만 저장한다(멱등). 상품 활성 검증을 위해 `ProductRepository.existsActiveById`를 신설(BrandRepository 선례)하며, 이는 LIK-2도 공유한다. 회원 인증은 `AuthenticatedUserArgumentResolver`가 헤더로 처리한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검
- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (Facade가 Repository 주입, 도메인 서비스 없음 — Brand/Product 패턴)
- [x] 검증 단일화: userId·productId는 다른 aggregate 식별자 참조라 VO 없이 `Long` 직접 보유(`ProductModel.brandId` 선례). 좋아요엔 형식·길이 규칙이 없음
- [x] CRUD 네이밍: Facade·Controller 메서드 `createLike` (create*/delete* 컨벤션)
- [x] 회원 인증: `@LoginUser AuthenticatedUser`(헤더 `X-Loopers-LoginId/LoginPw`), 실패 시 `UNAUTHENTICATED`(401) — Controller는 `@LoginUser` 파라미터, 표현 계층은 엔티티 미수신(경량 record)
- [x] 결정 7(soft delete C — 좋아요 hard delete): `LikeModel`은 `BaseEntity` 상속(deleted_at 컬럼 존재하나 미사용). 등록은 일반 insert, 취소(LIK-2)는 행 제거
- [x] 멱등 등록: 존재 검사(`existsByUserIdAndProductId`) 후 없을 때만 저장
- [x] 동시성: `(user_id, product_id)` UNIQUE는 ERD 자연키로 스키마에 선언. 동시 경합 graceful 처리·테스트는 범위 밖(spec 결정)
- [x] 정팩 네이밍: 매개변수 2개 → `LikeModel.of(userId, productId)`

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/like/LikeV1Controller.java` (신규) — `@RestController @RequestMapping("/api/v1/products/{productId}/likes")`. `@PostMapping` `createLike(@PathVariable Long productId, @LoginUser AuthenticatedUser loginUser)`: 200 OK(`@ResponseStatus` 미지정 — 기본 200). `likeFacade.createLike(loginUser.userId(), productId)` 후 `ApiResponse.success()`(`ApiResponse<Void>`). 요청 본문·응답 데이터 없음 → DTO 없음.
- `interfaces/api/like/LikeV1ApiSpec.java` (신규) — `@Tag`/`@Operation`. `ApiResponse<Void> createLike(Long productId, @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser)`.

### application
- `application/like/LikeFacade.java` (신규) — `@Service @Transactional`. 주입: `ProductRepository`, `LikeRepository`. `createLike(Long userId, Long productId)` (void): `if (!productRepository.existsActiveById(productId)) throw NOT_FOUND;` → `if (!likeRepository.existsByUserIdAndProductId(userId, productId)) likeRepository.save(LikeModel.of(userId, productId));`. 반환 없음(200 + 바디 없음).

### domain
- `domain/like/LikeModel.java` (신규) — `@Entity @Table(name="likes", uniqueConstraints=@UniqueConstraint(name="uk_likes_user_id_product_id", columnNames={"user_id","product_id"}))` extends `BaseEntity`. 필드: `@Column(name="user_id", nullable=false) Long userId`, `@Column(name="product_id", nullable=false) Long productId`. `@NoArgsConstructor(PROTECTED)` + `private LikeModel(Long, Long)` + `public static LikeModel of(Long userId, Long productId)`. VO·`@Builder` 없음(필드 2개, 검증 규칙 없음).
- `domain/like/LikeRepository.java` (신규) — `LikeModel save(LikeModel like)`, `boolean existsByUserIdAndProductId(Long userId, Long productId)`. (삭제는 LIK-2.)
- `domain/product/ProductRepository.java` (편집) — `boolean existsActiveById(Long id)` 추가(상품 활성 존재 검사 — BrandRepository 선례. LIK-1·LIK-2 공유).
- 도메인 서비스: **없음** (상품 검증은 `ProductRepository` 재사용, Facade 책임).

### infrastructure
- `infrastructure/like/LikeJpaRepository.java` (신규) — `extends JpaRepository<LikeModel, Long>`. `boolean existsByUserIdAndProductId(Long userId, Long productId)`.
- `infrastructure/like/LikeRepositoryImpl.java` (신규) — `@Component`, `LikeJpaRepository` 위임(save·existsByUserIdAndProductId).
- `infrastructure/product/ProductJpaRepository.java` (편집) — `boolean existsByIdAndDeletedAtIsNull(Long id)` 추가.
- `infrastructure/product/ProductRepositoryImpl.java` (편집) — `existsActiveById`: `productJpaRepository.existsByIdAndDeletedAtIsNull(id)` 위임.

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| `LikeModel`에 VO 없이 `Long userId`·`productId` 직접 보유 | 식별자 참조라 형식·길이 검증 규칙이 없음. `ProductModel.brandId` 선례와 동형 | `UserId`/`ProductId` VO 도입 — 검증할 불변식이 없어 빈 래퍼(과설계) |
| 멱등 등록을 존재 검사로 처리하고 동시성 graceful 핸들링은 보류 | 단일 스레드 멱등은 존재 검사로 충분. 동시 경합 처리는 별도 동시성 과제 주제(spec 사용자 결정). UNIQUE 자연키는 스키마에 유지해 최후 방어 | 등록 시 `try/catch DataIntegrityViolationException`으로 경합 흡수 — 현 cycle 범위 밖 동시성 처리를 미리 끌어옴 |
| `LikeFacade`가 `ProductRepository` 직접 주입 | cross-domain 검증을 application이 타 도메인 Repository로 수행 — `ProductFacade`→`BrandRepository` 선례와 동형 | `LikeFacade`→`ProductFacade` 호출(facade 간 호출, 선례 없음) |
