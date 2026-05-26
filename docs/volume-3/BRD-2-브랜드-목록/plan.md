# Plan: BRD-2 브랜드 목록 (admin)

**Spec**: ./spec.md
**작성일**: 2026-05-25

## 요약

`GET /api-admin/v1/brands?page&size`로 관리자가 브랜드를 페이징 조회한다. 도메인 엔티티가 이미 JPA 엔티티(`@Entity`)라 페이징만 framework-free로 분리하는 건 비대칭적 비용이므로, **Spring Data `Pageable`/`Page<BrandModel>`를 그대로 사용**한다(도메인 순수화는 추후 도메인↔JPA 엔티티 분리 시점에 `PageResult`까지 함께 도입 — 결정). 단, 응답 본문은 Spring `Page`/`PageImpl`를 직렬화하지 않고 자체 `PageResponse` DTO로 매핑한다.

## 기술 컨텍스트
- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 추가: Spring Data 페이징(`Pageable`/`Page`/`PageRequest`/`Sort`) — 직접 사용

## 컨벤션·결정 점검
- [x] 호출 방향 준수
- [x] CRUD 네이밍: `readBrands`(복수)
- [x] 페이지네이션: Spring Data `Pageable`/`Page` 직접 사용. 도메인 엔티티가 이미 JPA라 부분 순수성(PageResult) 회피, 추후 엔티티 분리 시 일괄 도입
- [x] page/size 검증: `BrandFacade.readBrands`의 가드(page<0 또는 size 범위 밖 → `CoreException(BAD_REQUEST)`). "미지정 시 기본"은 `@RequestParam(defaultValue)`. ⚠️ 당초 컨트롤러 `@Validated`+Bean Validation을 시도했으나, 컨트롤러가 `BrandAdminV1ApiSpec` 인터페이스를 구현해 `@Validated`가 JDK 동적 프록시를 만들면서 `@RequestBody`/`@PathVariable`/`@RequestParam` 바인딩이 깨져(admin 엔드포인트 500) Facade 가드로 전환
- [x] 응답: `Page`/`PageImpl` 직접 직렬화 금지 → `PageResponse` DTO로 매핑
- [x] admin 인증: `/api-admin/**` 인터셉터 가드
- [x] 결정 7: 삭제 행 제외, 등록 시각 내림차순 정렬

## 레이어별 설계 결정 & 파일 맵

### interfaces (편집)
- `BrandAdminV1Controller`에 클래스 레벨 `@Validated` 추가. `@GetMapping` `readBrands(@RequestParam(defaultValue="0") @PositiveOrZero int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size)` → `brandFacade.readBrands(page, size)` → `ApiResponse.success(PageResponse.from(page))`.
- `BrandAdminV1Dto`에 `PageResponse(List<Item> content, int page, int size, long totalElements, int totalPages)` + `Item(Long brandId, String name, String description, ZonedDateTime createdAt, ZonedDateTime updatedAt)` 추가. `from(Page<BrandInfo>)`: `page.getContent().stream().map(Item::from)`, `page.getNumber()`, `getSize()`, `getTotalElements()`, `getTotalPages()`.
- `BrandAdminV1ApiSpec`에 `readBrands` 선언.

### application
- `BrandFacade.readBrands(int page, int size)` (신규, `@Transactional(readOnly = true)`): page/size 가드(page<0·size 1~100 밖 → `CoreException(BAD_REQUEST)`) → `brandRepository.findActiveByPage(page, size)` → `.map(BrandInfo::from)` → `Page<BrandInfo>` 반환. (정렬은 쿼리 메서드명에 고정)

### domain (편집)
- `BrandRepository.findActiveByPage(int page, int size)` → `Page<BrandModel>` (신규). 도메인 인터페이스에는 `Pageable` 미노출(int 전달), `Page` 반환만 허용.

### infrastructure (편집)
- `BrandJpaRepository.findByDeletedAtIsNull(Pageable pageable)` → `Page<BrandModel>` (신규).
- `BrandRepositoryImpl.findActiveByPage(int,int)`: `PageRequest.of(page, size)` → `findByDeletedAtIsNullOrderByCreatedAtDesc(pageable)` 위임 (정렬을 쿼리 메서드명에 고정).

### support
- 변경 없음 (page/size 검증을 Facade 가드로 옮겨 `CoreException`→기존 advice 매핑 재사용. `@Validated`+`ConstraintViolationException` 핸들러 불필요).

## 복잡도 트래킹
| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| Spring Data `Pageable`/`Page` 직접 사용 | 도메인 엔티티가 이미 JPA(`@Entity`)라 페이징만 순수화하는 건 비대칭. 진짜 분리는 엔티티 분리 시점에 `PageResult`까지 일괄 | 도메인 `PageResult`/`PageQuery` 선도입 — 현 시점 과한 부분 순수성(논의 후 기각) |
| page/size 검증을 Facade 가드로 | 컨트롤러가 ApiSpec 인터페이스를 구현해 `@Validated`가 JDK 프록시로 바인딩을 깸(500). CoreException→400 기존 매핑 재사용 | 컨트롤러 `@Validated`+Bean Validation — 인터페이스 구현 컨벤션과 충돌(시도 후 기각) |
| 응답은 `PageResponse`로 매핑(Page 직렬화 안 함) | Spring `PageImpl` JSON 계약 불안정(Spring도 경고). 요구된 메타 4필드만 안정 노출 | `Page` 그대로 응답 — 계약 불안정 |
