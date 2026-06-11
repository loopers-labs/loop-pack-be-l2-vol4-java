# 3주차 구현 순서

## 의존 관계 기준 진행 순서

### 1단계 — Brand 도메인 (신규, 기반)
다른 도메인이 Brand를 참조하므로 먼저 구현.
- `BrandModel` (Entity, `name` 필드)
- `BrandRepository` (Domain 인터페이스)
- `BrandService` (findById, 없으면 NOT_FOUND)
- 단위 테스트: BrandModel 생성 / 예외

### 2단계 — Product 도메인 확장
기존 골격에 Brand 연관 및 재고 차감 로직 추가.
- `ProductModel`에 `brandId(Long)` 참조, `decreaseStock(int)` 메서드
- `ProductRepository`에 정렬 조건(`latest`, `price_asc`, `likes_desc`) 쿼리 추가
- 단위 테스트: 재고 차감 정상 / 음수 방지 예외

### 3단계 — Product + Brand 도메인 서비스
상품 상세 조회 시 Product + Brand 정보 조합.
- `ProductDetailAssembler` (`domain/product`): `ProductModel` + `brandName(String)` → `ProductDetail` 조합. `BrandModel` 의존 없이 이름 값만 수신해 도메인 간 결합 제거
- `BrandFinder` 에 `getNameById(Long)`, `getNamesByIds(Collection<Long>)` 추가 — `ProductFacade` 가 `BrandModel` 전체 대신 이름만 요청
- `ProductFacade` 가 `ProductFinder`/`BrandFinder` 로 각 도메인 조회 후 `ProductDetailAssembler` 로 조합하는 흐름 완성
- 단위 테스트: `ProductDetailAssemblerTest` (순수 도메인 로직), `ProductFacadeTest` (협력 객체 mock, `Set.of(brandId)` 등 실 값으로 검증), `BrandFinderTest` 에 `getNameById`/`getNamesByIds` 케이스 추가

### 4단계 — Like 도메인
User + Product 간 관계.
- `LikeModel`, `LikeRepository`, `LikeService`
- 단위 테스트: 좋아요 등록 / 중복 방지 / 취소 흐름

### 5단계 — Order 도메인 (가장 복잡)
재고 차감 + 다중 도메인 예외 처리.
- `OrderModel`, `OrderItemModel`, `OrderRepository`, `OrderService`
- 단위 테스트: 정상 주문 / 재고 부족 / 유저·상품 부재 예외

### 6단계 — Application Layer (Facade) 통합
각 도메인 준비 후 Facade 완성.
- `ProductFacade`: Product + Brand + Like 조합
- `LikeFacade`: 좋아요 등록/취소
- `OrderFacade`: 주문 생성 (재고 차감 포함)

---

## TDD 사이클

각 단계는 **Red → Green → Refactor** 순으로 진행.
- Red: 실패하는 테스트 먼저 작성
- Green: 테스트를 통과시키는 최소 구현
- Refactor: 리팩토링

## 진행 현황

- [x] 1단계 — Brand 도메인
- [x] 2단계 — Product 도메인 확장
- [x] 3단계 — Product + Brand 도메인 서비스
- [x] 4단계 — Like 도메인
- [ ] 5단계 — Order 도메인
- [ ] 6단계 — Application Layer Facade
