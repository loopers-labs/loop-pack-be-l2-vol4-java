# Plan: ORD-3 본인 주문 내역 (날짜 범위)

**Spec**: ./spec.md
**작성일**: 2026-05-27

## 요약

회원이 `GET /api/v1/orders?startAt=&endAt=&page=&size=`로 본인 주문 내역을 날짜 범위로 조회한다. 시작일·종료일은 `LocalDate`(ISO `yyyy-MM-dd`)로 받고, 누락 시 각각 기본값(시작일=오늘−1개월, 종료일=오늘)을 적용한다. `OrderFacade.readMyOrders`가 범위 검증(시작>종료·시작>오늘 → BAD_REQUEST)을 한 뒤, 일자 범위를 `[시작일 00:00, 종료일+1일 00:00)` 반열린 구간의 `ZonedDateTime`으로 변환해 `orderRepository.findActiveByUserIdAndOrderedAtBetween(userId, start, endExclusive, page, size)`로 주문 시각 내림차순 페이징 조회한다. 각 주문은 항목 요약을 포함하므로, 페이징된 주문마다 `findActiveItemsByOrderId`로 항목을 조회해 `OrderInfo.from(order, items)`로 매핑한다(주문 수만큼 항목 조회 — N+1, 본 라운드 규모에서 허용).

## 기술 컨텍스트

- 고정: Java 21 / Spring Boot 3.4.4 / JPA / MySQL / commerce-api
- 시나리오별 추가: 없음 (`@DateTimeFormat(iso=DATE)`로 LocalDate 파싱)

## 컨벤션·결정 점검

- [x] 호출 방향 준수 — Facade 조회 메서드 + Repository 조회 메서드 추가
- [x] 인증: `@LoginUser AuthenticatedUser` → 실패 401
- [x] 검증: **page/size는 검증하지 않음**(클라 신뢰 — `query-param-validation` 컨벤션, 기존 목록 cycle과 동일). **날짜 범위 검증만** Facade에서 수행
- [x] 결정 5(스냅샷): 항목 요약은 `OrderItemModel` 스냅샷 그대로 (ORD-1 `OrderInfo` 재사용)
- [x] 정렬: 주문 시각(orderedAt) 내림차순 고정
- [x] 조회 트랜잭션: `@Transactional(readOnly=true)`, 항목은 주문마다 `findActiveItemsByOrderId`로 조회(N+1 허용 — 복잡도 트래킹)

## 레이어별 설계 결정 & 파일 맵

### interfaces
- `interfaces/api/order/OrderV1Controller.java` (편집) — `@GetMapping` `readMyOrders(@RequestParam(required=false) @DateTimeFormat(iso=DATE) LocalDate startAt, @RequestParam(required=false) ... LocalDate endAt, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size, @LoginUser AuthenticatedUser loginUser)`. `orderFacade.readMyOrders(loginUser.userId(), startAt, endAt, page, size)` 후 `ApiResponse.success(OrderV1Dto.PageResponse.from(page))`.
- `interfaces/api/order/OrderV1Dto.java` (편집) — `PageResponse(List<OrderResponse> content, int page, int size, long totalElements, int totalPages)` + `from(Page<OrderInfo>)` 추가 (항목은 ORD-1 `OrderResponse` 재사용).
- `interfaces/api/order/OrderV1ApiSpec.java` (편집) — 내역 조회 `@Operation` 추가.

### application
- `application/order/OrderFacade.java` (편집) — `@Transactional(readOnly=true) Page<OrderInfo> readMyOrders(Long userId, LocalDate startAt, LocalDate endAt, int page, int size)`:
  - 기본값: `startAt == null → today.minusMonths(1)`, `endAt == null → today`. `today = LocalDate.now(ZONE)`.
  - 검증: `startAt.isAfter(endAt)` 또는 `startAt.isAfter(today)` → `CoreException(BAD_REQUEST)`. (page/size는 검증하지 않음 — 컨벤션)
  - 변환: `start = startAt.atStartOfDay(ZONE)`, `endExclusive = endAt.plusDays(1).atStartOfDay(ZONE)`. (ZONE = `ZoneId.systemDefault()`)
  - `orderRepository.findActiveByUserIdAndOrderedAtBetween(userId, start, endExclusive, page, size).map(order -> OrderInfo.from(order, orderRepository.findActiveItemsByOrderId(order.getId())))`.
- `OrderInfo`/`OrderItemInfo` (ORD-1) 재사용.

### domain
- `domain/order/OrderRepository.java` (ORD-1에서 정의) — `Page<OrderModel> findActiveByUserIdAndOrderedAtBetween(Long userId, ZonedDateTime start, ZonedDateTime endExclusive, int page, int size)`·`findActiveItemsByOrderId`.

### infrastructure
- `infrastructure/order/OrderJpaRepository.java` (ORD-1) — `Page<OrderModel> findByUserIdAndDeletedAtIsNullAndOrderedAtGreaterThanEqualAndOrderedAtLessThan(Long userId, ZonedDateTime start, ZonedDateTime endExclusive, Pageable pageable)` (Active + 반열린 구간 파생 쿼리).
- `infrastructure/order/OrderRepositoryImpl.java` (ORD-1) — `PageRequest.of(page, size, Sort.by(DESC, "orderedAt"))`로 위임.

## 복잡도 트래킹

| 결정 | 이유 | 기각한 더 단순한 대안 |
|------|------|----------------------|
| 일자 범위를 `[시작 00:00, 종료+1일 00:00)` 반열린 구간으로 변환 | "종료일 포함"을 시각 비교로 정확히 표현(종료일 23:59:59.999 경계 오차·DATETIME 정밀도 의존 회피) | `BETWEEN 시작 00:00 AND 종료 23:59:59`(밀리초 경계 누락 위험) |
| "오늘"·기준 타임존을 시스템 기본 zone(`LocalDate.now()`)으로 | BaseEntity가 `ZonedDateTime.now()`(시스템 zone)로 시각을 찍어 저장과 조회 기준이 일치. 테스트 프로파일은 Asia/Seoul | UTC 고정 기준(저장 시각의 시스템 zone과 어긋나 경계 흔들림) |
| 한쪽만 지정 시 나머지만 기본값 적용 | 소스 "누락된 경우 기본값"의 자연스러운 해석. 시작만 주면 종료=오늘, 종료만 주면 시작=오늘−1개월 | 한쪽이라도 있으면 기본값 미적용(명세 근거 없음) |
| 목록 항목 요약을 lazy 로딩(N+1 허용) | 본 라운드 규모·페이지 크기(≤100)에서 무시할 비용. 별도 fetch/배치 로딩은 조기 최적화 | 항목 배치 조회(`findItemsByOrderIds`) 신설(복잡도↑, 현 시점 불필요) |
