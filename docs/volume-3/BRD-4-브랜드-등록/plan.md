# Plan: BRD-4 브랜드 등록

**Spec**: ./spec.md
**작성일**: 2026-05-25

## 요약

관리자가 `POST /api-admin/v1/brands`로 브랜드를 등록한다. User 도메인 참조 구현을 그대로 본떠 Brand aggregate(`BrandModel` + `Name` VO + `BrandRepository`, 설명은 검증 없어 `String` 직접 보유)를 신설하고, Facade가 활성 이름 중복을 선검사한다. 추가로 모든 admin 시나리오가 재사용할 **관리자 인증 토대**(`AdminAuthInterceptor` + `ErrorType.FORBIDDEN`)를 함께 도입한다. admin API는 표현 계층(Controller/Dto/ApiSpec)에서 public API와 분리하되, application `BrandFacade`는 단일로 둔다.

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (기존 의존성으로 충족)

## 컨벤션·결정 점검

- [x] 호출 방향 interfaces → application → domain → infrastructure 준수 (Facade가 Repository 주입, 도메인 서비스 없음 — User 패턴)
- [x] 검증은 VO `from()`에 단일화. DTO는 `@NotBlank`로 null/blank만 1차 방어, 길이(1~50)는 `Name.from()`이 단일 원천
- [x] 인증: admin은 주입할 데이터 없는 순수 인가 게이트라 `AdminAuthInterceptor`가 `/api-admin/**` 경로를 가드 (값 주입이 필요한 회원 인증의 ArgumentResolver와 도구 분업). admin 컨트롤러에 인증 파라미터 없음
- [x] 결정 7(soft delete): `BaseEntity` 상속으로 `deleted_at` 확보. 중복 검사는 `deletedAt IS NULL` 행만 대상
- [x] 결정 B(설명): `description`은 검증·행위가 없어 VO 없이 `String` 컬럼으로 직접 보유, 값 그대로 저장(null 허용)
- [x] 결정 C(동시성): 애플리케이션 선검사만. DB UNIQUE·락 미도입
- [x] 신규 결정(admin 403): `ErrorType.FORBIDDEN(403)` 추가, 인증 실패 시 매핑

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/brand/BrandAdminV1Controller.java` (신규) — `POST /api-admin/v1/brands`, `@ResponseStatus(CREATED)`. 시그니처: `createBrand(@Valid @RequestBody BrandAdminV1Dto.CreateRequest request)` (인증 파라미터 없음 — Interceptor가 경로 가드). `BrandFacade.createBrand(name, description)` 호출 후 `ApiResponse.success(CreateResponse.from(info))`.
- `interfaces/api/brand/BrandAdminV1Dto.java` (신규)
  - `CreateRequest(String name, String description)`: `name`에 `@NotBlank`(message 명시). `description`은 선택 필드라 무어노테이션.
  - `CreateResponse(Long brandId)`: `from(BrandCreateInfo)`.
- `interfaces/api/brand/BrandAdminV1ApiSpec.java` (신규) — SpringDoc `@Tag`/`@Operation`.

**admin 인증 토대 (신규 횡단, interfaces/api/auth)**
- `AdminAuthInterceptor.java` (신규) — `HandlerInterceptor` 구현 `@Component`. `preHandle`: `X-Loopers-Ldap` 헤더가 `loopers.admin`과 일치하지 않으면(null 포함) `CoreException(ErrorType.FORBIDDEN)`, 일치하면 `true` 반환. 헤더명·기대값은 `private static final` 상수.

### application
- `application/brand/BrandFacade.java` (신규) — `@Service @Transactional`. `createBrand(String name, String description)`: `brandRepository.existsActiveByName(name)`이면 `CoreException(CONFLICT)`; 아니면 `BrandModel.builder()...build()` 후 `save`, `BrandCreateInfo.from(saved)` 반환.
- `application/brand/BrandCreateInfo.java` (신규) — `record BrandCreateInfo(Long brandId)` + `from(BrandModel)`.

### domain
- `domain/brand/BrandModel.java` (신규) — `@Entity @Table(name="brands")` extends `BaseEntity`. `@Embedded Name name`, `@Column(columnDefinition="TEXT") String description`(검증·행위가 없어 VO 없이 원시 타입 직접 보유 — 결정 B). `@NoArgsConstructor(PROTECTED)`+`@AllArgsConstructor(PROTECTED)`. `private @Builder BrandModel(String rawName, String rawDescription)` → `this.name = Name.from(rawName); this.description = rawDescription`. (update/delete는 BRD-5/BRD-6 cycle에서 추가 — 이번 범위 밖. delete()는 BaseEntity 상속분 존재.)
- VO:
  - `domain/brand/Name.java` (신규) — `record @Embeddable`, `@Column(name="name", nullable=false, length=50)`. `from(value)`: null/blank → BAD_REQUEST; 길이 `MIN_LENGTH(1)`~`MAX_LENGTH(50)` 위반 → BAD_REQUEST(String.format).
- `domain/brand/BrandRepository.java` (신규) — `BrandModel save(BrandModel)`, `boolean existsActiveByName(String name)`.
- 도메인 서비스: **없음** (중복 검사는 Facade 책임 — User 패턴).

### infrastructure
- `infrastructure/brand/BrandRepositoryImpl.java` (신규) — `@Component`, `BrandJpaRepository` 위임. `existsActiveByName` → `existsByNameValueAndDeletedAtIsNull`.
- `infrastructure/brand/BrandJpaRepository.java` (신규) — `extends JpaRepository<BrandModel, Long>`. 파생 쿼리 `boolean existsByNameValueAndDeletedAtIsNull(String value)` (`name.value` + 상속된 `deletedAt`).

### support (편집)
- `support/error/ErrorType.java` (편집) — `FORBIDDEN(HttpStatus.FORBIDDEN, ..., "접근 권한이 없습니다.")` 추가.
- `support/config/WebMvcConfig.java` (편집) — `addInterceptors`로 `AdminAuthInterceptor`를 `/api-admin/**` 경로 패턴에 등록 (기존 `addArgumentResolvers`는 유지).
- `ApiControllerAdvice`는 `CoreException` → `ErrorType.status` 일반 매핑이라 FORBIDDEN 추가만으로 403 응답됨. Interceptor `preHandle`에서 던진 예외도 ControllerAdvice가 처리하는지 implement에서 확인.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| admin 컨트롤러를 `<Domain>AdminV1Controller`로 분리 명명 (Controller/Dto/ApiSpec만, Facade는 단일) | `/api-admin/v1/*`(admin)와 `/api/v1/*`(public)는 경로·인증·응답 shape가 전부 다름. BRD-1 public 조회는 `BrandV1Controller`로 별도 추가 예정. 모든 admin cycle의 명명 선례. 도메인 조작은 호출자 무관이라 Facade는 단일 유지 | public/admin 한 컨트롤러 혼재(경계 흐려짐) / `BrandAdminFacade` 분리(현 시점 YAGNI) |
| admin 인가를 `AdminAuthInterceptor`(경로 `/api-admin/**`)로 구현 | admin은 핸들러에 주입할 데이터가 없는 순수 인가 게이트라, 값 주입용 도구인 ArgumentResolver와 맞지 않음. 경로 패턴 가드는 admin prefix가 확정적이라 추가 admin 엔드포인트에 자동 적용되고 사용 안 하는 파라미터를 남기지 않음 | `@AdminUser`+`AuthenticatedAdmin` ArgumentResolver(읽지 않는 파라미터·도구 오용) / `@AdminOnly` 마커+Interceptor(코드 약간 더, URL 의존 제거 이점은 prefix 확정 상황에선 미미) / Servlet Filter(ControllerAdvice 예외 처리에서 벗어남) |
