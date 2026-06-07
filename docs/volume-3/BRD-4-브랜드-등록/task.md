# Task: BRD-4 브랜드 등록

**Plan**: ./plan.md

> 완료한 task는 `- [ ]` → `- [X]`. 구현은 CLAUDE.md·컨벤션 준수. 경로 prefix는 `apps/commerce-api/src/{main,test}/java/com/loopers/`.

## Phase F: Foundational (Brand aggregate 골격 + admin 인증 토대)

- [X] T001 `Name` VO 작성 + 단위 테스트 — `main/.../domain/brand/Name.java`, `test/.../domain/brand/NameTest.java` (1자·50자 통과 / 빈 문자열·51자 BAD_REQUEST, MIN=1·MAX=50 상수)
- [X] T002 설명(description)은 `BrandModel`의 `String @Column(columnDefinition="TEXT")` 필드로 직접 보유 — 검증·행위가 없어 VO 미도입(별도 파일·VO 단위 테스트 없음). 보존은 T003·T006에서 검증.
- [X] T003 `BrandModel` 작성 + 단위 테스트 — `main/.../domain/brand/BrandModel.java`, `test/.../domain/brand/BrandModelTest.java` (`@Entity @Table(name="brands")` extends BaseEntity, `@Embedded` VO, `private @Builder(rawName, rawDescription)`, 생성 시 필드 보유)
- [X] T004 `BrandRepository` 인터페이스 — `main/.../domain/brand/BrandRepository.java` (`save`, `existsActiveByName`)
- [X] T005 `BrandJpaRepository` + `BrandRepositoryImpl` — `main/.../infrastructure/brand/BrandJpaRepository.java`(`existsByNameValueAndDeletedAtIsNull`), `main/.../infrastructure/brand/BrandRepositoryImpl.java`(위임)
- [X] T006 `BrandRepository` 통합 테스트 — `test/.../infrastructure/brand/BrandRepositoryIntegrationTest.java` (저장·조회 / `existsActiveByName`이 삭제행 제외하는지)
- [X] T007 `ErrorType.FORBIDDEN(403)` 추가 — `main/.../support/error/ErrorType.java`
- [X] T008 `AdminAuthInterceptor` 작성 + 단위 테스트 — `main/.../interfaces/api/auth/AdminAuthInterceptor.java`(`preHandle`: `X-Loopers-Ldap`≠`loopers.admin` → FORBIDDEN), `test/.../interfaces/api/auth/AdminAuthInterceptorTest.java` (헤더 없음/오값 → 예외, 정값 → true)
- [X] T009 `WebMvcConfig`에 `AdminAuthInterceptor`를 `/api-admin/**` 경로로 등록 — `main/.../support/config/WebMvcConfig.java` (`addInterceptors` 추가, 기존 `addArgumentResolvers` 유지)

## Phase 1: 구현 (등록 유스케이스)

- [X] T010 `BrandCreateInfo` 작성 — `main/.../application/brand/BrandCreateInfo.java` (`record(Long brandId)` + `from(BrandModel)`)
- [X] T011 `BrandFacade.createBrand` 작성 + 단위 테스트 — `main/.../application/brand/BrandFacade.java`, `test/.../application/brand/BrandFacadeTest.java` (활성 동일 이름 → CONFLICT / 삭제된 동일 이름 → 통과 / 정상 → 저장 후 brandId 반환)
- [X] T012 `BrandAdminV1Dto` 작성 — `main/.../interfaces/api/brand/BrandAdminV1Dto.java` (`CreateRequest(name @NotBlank, description 무어노테이션)`, `CreateResponse(brandId) + from`)
- [X] T013 `BrandAdminV1ApiSpec` 작성 — `main/.../interfaces/api/brand/BrandAdminV1ApiSpec.java` (`@Tag`/`@Operation`)
- [X] T014 `BrandAdminV1Controller` 작성 — `main/.../interfaces/api/brand/BrandAdminV1Controller.java` (`POST /api-admin/v1/brands`, `@ResponseStatus(CREATED)`, `@Valid @RequestBody`, 인증 파라미터 없음)
- [X] T015 E2E 테스트 — `test/.../interfaces/api/BrandAdminV1ApiE2ETest.java` (정상 201+meta.result SUCCESS+brandId / admin 헤더 없음 403 / 이름 51자 400 / 활성 이름 중복 409. statusCode+meta.result+errorCode까지, 메시지 비단언. fixture는 BrandJpaRepository.save 직접)

## Phase 2: 마무리

- [X] T016 spec 테스트 계획 대비 누락 점검 (VO 경계 / Facade 분기 / Integration 삭제행 제외 / E2E 4분기 매핑) 및 `ApiControllerAdvice`가 Interceptor `preHandle` 예외를 403으로 매핑하는지 확인
- [X] T017 `.http` 파일 — `http/commerce-api/brand-admin-v1.http` (브랜드 등록 요청 샘플: 정상 / admin 헤더 누락 / 이름 범위 위반)
