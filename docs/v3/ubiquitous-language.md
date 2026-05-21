# 유비쿼터스 언어 (Ubiquitous Language)

> 이 문서는 비즈니스 요구사항 · 설계 문서 · 코드에서 **동일하게 사용해야 하는 용어**를 정의합니다.
> 새로운 도메인 개념이 추가되거나 기존 정의가 변경될 경우 반드시 이 문서를 먼저 갱신합니다.

---

## 도메인 개념

### 유저 (User)
- **정의**: 서비스에 회원가입한 일반 사용자.
- **식별**: `loginId` (영문·숫자 조합) + `password` (Bcrypt 암호화) 조합으로 인증.
- **코드 표현**: `UserModel`, `UserService`, `UserFacade`
- **주의**: DB 테이블명은 `USERS` (MySQL 예약어 충돌 회피). 코드에서는 `User` 접두사 사용.

### 어드민 (Admin)
- **정의**: 브랜드·상품·주문을 관리하는 운영자. 현재는 단일 백오피스 운영 주체로 간주한다.
- **식별**: HTTP 헤더 `X-Loopers-Ldap: loopers.admin` 고정값 검증. DB 레코드 없음.
- **코드 표현**: Controller 계층의 `AdminV1Controller` 접두사, Interceptor의 LDAP 검증 로직.
- **주의**: `AdminModel` · `AdminRepository` 는 존재하지 않는다. "어드민"은 인가 개념이지 엔티티가 아니다.

---

### 브랜드 (Brand)
- **정의**: 상품을 묶는 판매 주체 단위. (예: Nike, Adidas)
- **코드 표현**: `BrandModel`, `BrandService`, `BrandFacade`
- **생명주기**: 등록 → 수정 → 삭제(Soft Delete). 삭제 시 연관 상품도 함께 Soft Delete.

### 상품 (Product)
- **정의**: 브랜드가 판매하는 개별 판매 단위. 반드시 하나의 브랜드에 속한다.
- **코드 표현**: `ProductModel`, `ProductService`, `ProductFacade`
- **포함 정보**: 브랜드, 이름, 설명, 가격, 재고 수량, 좋아요 수.
- **주의**: 브랜드는 상품 생성 이후 변경 불가.

### 재고 (Inventory)
- **정의**: 특정 상품의 수량 상태 및 그 변동을 관리하는 개념. 현재는 판매 가능 수량 관리에 집중하며, 향후 재입고·입출고 이력·재고 조정으로 확장된다.
- **코드 표현**: `ProductInventoryModel`, `ProductInventoryRepository`
- **주의**: `Stock` · `Quantity` 가 아닌 `Inventory` 로 통일한다. 별도 테이블(`product_inventory`)로 관리하며, 주문 시 `FOR UPDATE` 락을 통해 동시성을 보장한다.
- **향후 확장**: 재입고(restocking), 입출고 이력(movement history), 재고 조정(adjustment) 기능 추가 시 이 모델을 확장한다.

### 좋아요 (Like)
- **정의**: 유저가 특정 상품에 관심을 표시하는 행위 및 그 기록.
- **코드 표현**: `LikeModel`, `LikeService`, `LikeFacade`
- **상태**: 한 유저가 동일 상품에 좋아요는 1개만 존재 (UNIQUE 제약).

#### 좋아요 수 (likeCount)
- **정의**: 특정 상품에 등록된 좋아요의 총 개수.
- **코드 표현**: `ProductModel.likeCount` (DB 비정규화 컬럼)
- **관리 방식**: 좋아요 등록/취소 시 SQL 원자적 증감으로 갱신. COUNT 쿼리 사용 금지.

---

### 주문 (Order)
- **정의**: 유저가 하나 이상의 상품을 구매 요청한 단위. 생성 시점에 재고가 차감되고 즉시 완료 상태가 된다.
- **코드 표현**: `OrderModel`, `OrderService`, `OrderFacade`
- **상태**: 현재는 `COMPLETED` 단일 상태 (결제 기능 추가 이전 단계, ADR-012).

### 주문 항목 (OrderItem)
- **정의**: 주문에 포함된 개별 상품 라인. 주문 시점의 상품 정보를 스냅샷으로 보유한다.
- **코드 표현**: `OrderItemModel`
- **주의**: `LineItem` · `OrderLine` 이 아닌 `OrderItem` 으로 통일한다.

---

## 핵심 동작 용어

### 좋아요 등록 / 좋아요 취소
- **등록**: `addLike` — 유저가 상품에 좋아요를 누르는 행위.
  - soft-deleted 레코드가 존재하면 `restore()` (deleted_at = null), 없으면 신규 INSERT.
- **취소**: `removeLike` — 유저가 등록한 좋아요를 철회하는 행위. Soft Delete 적용.
- **주의**: `createLike` / `deleteLike` / `cancelLike` 사용 금지. `add` / `remove` 로 통일.

### 재고 차감 (Deduct Inventory)
- **정의**: 주문 생성 시 요청 수량만큼 재고를 줄이는 행위.
- **코드 표현**: `ProductInventoryModel.deduct(amount)`, `ProductService.deductInventory(productId, quantity)`
- **주의**: `reduce` · `decrease` · `subtract` 가 아닌 `deduct` 로 통일한다.

### 재고 확인 (Inventory Check)
- **정의**: 주문 요청 수량이 현재 재고 이하인지 검증하는 행위. 락 없이 수행하는 fast fail 용도.
- **코드 표현**: Facade 레이어에서 `ProductInventoryModel.quantity >= requestedQuantity` 조건 확인.

### 스냅샷 (Snapshot)
- **정의**: 주문 시점의 상품 이름·가격을 `OrderItem` 에 복사해 보존하는 패턴. 이후 상품 정보가 변경되어도 주문 이력은 당시 정보를 그대로 유지한다.
- **코드 표현**: `OrderItemModel.productName`, `OrderItemModel.productPrice`
- **주의**: 별도 스냅샷 엔티티를 만들지 않고, `OrderItem` 의 컬럼으로 표현한다.

---

## 시스템 개념

### 유저 인증 (User Authentication)
- **정의**: 요청 헤더의 `X-Loopers-LoginId` + `X-Loopers-LoginPw` 로 유저를 식별하는 절차.
- **코드 표현**: Interceptor (또는 ArgumentResolver) → `UserService` 조회 → `UserModel` 반환.
- **실패 시**: `401 Unauthorized`.

### 어드민 인가 (Admin Authorization)
- **정의**: 요청 헤더의 `X-Loopers-Ldap` 값이 `loopers.admin` 과 일치하는지 검증하는 절차.
- **코드 표현**: Interceptor → 문자열 비교.
- **실패 시**: `403 Forbidden`.

### 소프트 딜리트 (Soft Delete)
- **정의**: DB에서 행을 물리적으로 제거하지 않고 `deleted_at` 컬럼에 삭제 시각을 기록해 논리적으로 삭제하는 방식.
- **코드 표현**: `BaseEntity.delete()` 호출 → `deletedAt = now()`.
- **조회 필터**: 모든 조회 쿼리는 `deleted_at IS NULL` 조건을 적용한다.
- **적용 대상**: `Brand`, `Product`, `ProductInventory`, `Like`, `Order`, `OrderItem`.
- **좋아요**: `Like` 는 Soft Delete + Restore 패턴 적용.
  취소 시 `deleted_at` 설정, 재등록 시 기존 레코드를 `restore()` 하여 UNIQUE 제약 유지 (ADR-008).

### 연쇄 삭제 (Cascade Soft Delete)
- **정의**: 브랜드 삭제 시 해당 브랜드의 모든 상품 및 연관 Like, ProductInventory도 함께 Soft Delete 처리.
- **코드 표현**: `BrandFacade.deleteBrand()` → bulk 처리 (`productIds` 일괄 조회 → `ProductService.deleteAll()` + `ProductInventoryService.deleteAllByProducts()` + `LikeService.deleteAllByProducts()`).
- **주의**: JPA `CascadeType` 미사용. Facade 레이어가 오케스트레이션 담당 (ADR-013).
- **좋아요**: Brand/Product 삭제 시 연관 Like도 연쇄 Soft Delete한다. 좋아요 취소 시에는 `like_count`를 차감하지만, 연쇄 삭제 시에는 `like_count` 차감을 수행하지 않는다.
