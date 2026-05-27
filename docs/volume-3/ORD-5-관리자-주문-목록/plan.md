# Plan: ORD-5 관리자 주문 목록

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

관리자가 `GET /api-admin/v1/orders?page=&size=`로 전체 회원의 주문 목록을 조회한다. `/api-admin/**`는 `AdminAuthInterceptor`가 자동 가드(실패 403)하므로 컨트롤러에 인증 파라미터가 없다. `OrderFacade.readOrders(page, size)`가 `orderRepository.findActiveByPage(page, size)`로 삭제되지 않은 전체 주문을 주문 시각 내림차순 페이징 조회하고, 항목을 제외한 헤더 레벨 정보(주문 식별자·회원 식별자·상태·주문 시각·총액)만 `OrderAdminSummaryInfo`로 매핑한다(항목 조회 자체를 하지 않음).

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음

## 컨벤션·결정 점검

- [x] 호출 방향 준수 — admin Facade·Repository 조회 메서드 추가
- [x] 인증: admin은 인가 게이트 — `AdminAuthInterceptor`가 `/api-admin/**` 가드(403). 컨트롤러에 인증 파라미터 없음 (PRD-3 admin 패턴)
- [x] 검증: **page/size 검증하지 않음**(클라 신뢰 — `query-param-validation` 컨벤션, 기존 목록 cycle과 동일)
- [x] 정렬: 주문 시각 내림차순 고정
- [x] soft delete: `findActiveByPage` → `findByDeletedAtIsNull`로 삭제 주문 제외 (B-1)
- [x] 응답 범위: 헤더 레벨만(항목 미포함) — 상세는 ORD-6 (PRD-3 목록 vs PRD-4 상세 패턴)

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/order/OrderAdminV1Controller.java` (신규) — `@RequestMapping("/api-admin/v1/orders")`. `@GetMapping` `readOrders(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size)`, 200. `orderFacade.readOrders(page, size)` 후 `ApiResponse.success(OrderAdminV1Dto.PageResponse.from(page))`. (인증 파라미터 없음 — Interceptor 가드)
- `interfaces/api/order/OrderAdminV1Dto.java` (신규) — `SummaryResponse(Long orderId, Long userId, String status, ZonedDateTime orderedAt, Integer totalPrice)` + `from(OrderAdminSummaryInfo)`, `PageResponse(content, page, size, totalElements, totalPages)` + `from(Page<OrderAdminSummaryInfo>)`.
- `interfaces/api/order/OrderAdminV1ApiSpec.java` (신규) — `@Tag`/`@Operation`.

### application
- `application/order/OrderFacade.java` (편집) — `@Transactional(readOnly=true) Page<OrderAdminSummaryInfo> readOrders(int page, int size)`: `orderRepository.findActiveByPage(page, size).map(OrderAdminSummaryInfo::from)` (page/size 검증 없음).
- `application/order/OrderAdminSummaryInfo.java` (신규) — `record(Long orderId, Long userId, OrderStatus status, ZonedDateTime orderedAt, Integer totalPrice)` + `from(OrderModel)` (항목 미조회).

### domain
- `domain/order/OrderRepository.java` (ORD-1에서 정의) — `Page<OrderModel> findActiveByPage(int page, int size)`.

### infrastructure
- `infrastructure/order/OrderJpaRepository.java` (ORD-1) — `Page<OrderModel> findByDeletedAtIsNull(Pageable pageable)`.
- `infrastructure/order/OrderRepositoryImpl.java` (ORD-1) — `PageRequest.of(page, size, Sort.by(DESC, "orderedAt"))` 위임.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 헤더 전용 `OrderAdminSummaryInfo`(항목 미조회) | 목록은 헤더 레벨만(명세). 연관을 끊어 항목은 별도 조회 대상이므로, 목록에선 항목 조회를 아예 호출하지 않음 | 항목 포함 Info 재사용(불필요 항목 조회·응답 오염) |
| admin 컨트롤러 `OrderAdminV1Controller` 분리 | `/api-admin/v1/*`는 경로·인증·응답 shape가 public과 다름(BRD/PRD admin 선례) | public `OrderV1Controller`에 혼재(경계 흐려짐) |
