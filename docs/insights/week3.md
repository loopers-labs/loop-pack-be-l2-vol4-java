# week3 인사이트

> 날짜: 2026-05-27

---

## 개념 정리

### ApplicationService vs Facade — 역할이 다른가?

처음엔 프로젝트의 `MemberFacade`가 두 역할을 겸하고 있어서 같은 것처럼 보였지만, 개념상 구분이 있다.

- **ApplicationService** — 유스케이스 하나를 담당. 트랜잭션 경계 정의(`@Transactional`). DomainService + Repository를 조합해 유스케이스를 완성.
- **Facade** — 인터페이스 단순화. 여러 ApplicationService를 조합하거나, 도메인 객체를 Info로 변환해 Controller에 단순한 진입점을 제공.

두 레이어 모두 application 레이어에 위치하지만 책임이 다르다.

---

### Domain Service가 Repository 인터페이스에 의존해도 되는가?

된다. "도메인이 외부에 의존하면 안 된다"는 규칙은 정확히는 **구현체(infrastructure)에 의존하면 안 된다**는 의미다.

- `BrandRepository` (interface) → domain 레이어에 위치 → 의존 가능
- `BrandRepositoryImpl` (구현체) → infrastructure 레이어 → 의존 불가

이것이 **의존성 역전 원칙(DIP)**. DomainService는 인터페이스에만 의존하고, 구현체가 인터페이스를 구현한다.

```
DomainService → BrandRepository (interface, domain)
                        ↑
              BrandRepositoryImpl (infrastructure)
```

---

### ApplicationService가 Repository를 직접 호출해도 되는가?

된다. 판단 기준은 **"비즈니스 규칙이 있는가?"**

- 비즈니스 규칙 있음 → DomainService로 위임
- 단순 저장/조회 → ApplicationService에서 Repository 직접 호출

저장 자체엔 규칙이 없다. "저장하라"는 유스케이스 흐름의 일부이지 비즈니스 규칙이 아니다.

---

### Entity 내부 검증 vs DomainService 검증 — 어디에 놓아야 하는가?

판단 기준: **"Entity 혼자서 판단할 수 있는가?"**

| 검증 | 위치 | 이유 |
|---|---|---|
| null, 공백 | Entity | 자기 자신만 보면 됨 |
| 길이 제한 (1~20자) | Entity | 자기 자신만 보면 됨 |
| 이름 중복 | DomainService | DB 조회(Repository) 필요 |

---

### BrandInfo는 왜 application 레이어에 있는가?

`BrandInfo`는 HTTP 응답 DTO가 아니라, **application 레이어가 interface 레이어에 넘기는 내부 계약 객체**다.

흐름:
```
Domain (Brand)
    ↓  Facade가 포장
BrandInfo  ← application 레이어의 "상자"
    ↓  Controller가 HTTP 응답 형태로 변환
BrandResponse  ← 클라이언트에게 내려가는 DTO
```

이렇게 분리하면 `Brand` 필드가 바뀌어도 Controller가 직접 영향을 받지 않는다. `BrandInfo`만 수정하면 끝. 나아가 HTTP가 아닌 gRPC 같은 다른 전송 방식이 추가돼도 application 레이어 아래는 재사용 가능하다.

---

### 트랜잭션은 어느 레이어에서 시작하는가?

**ApplicationService**가 트랜잭션 경계를 정의한다 (`@Transactional`).

DomainService는 트랜잭션을 열지 않는다. ApplicationService가 시작한 트랜잭션 안에서 호출되어 같은 트랜잭션을 공유한다.

```
@Transactional  ← ApplicationService에서 시작
register()
    → domainService.validateDuplicateName()  ← 같은 트랜잭션
    → brandRepository.save()                 ← 같은 트랜잭션
```

---

### 브랜드 등록 후 응답을 내려줘야 하는가?

응답 바디 없이 201만 내려줄 경우 `BrandInfo`가 불필요하다. 반면 생성된 리소스를 바디에 담아 내려주면 클라이언트가 별도 조회 없이 `id`를 바로 알 수 있다는 장점이 있다.

**결정:** 생성된 브랜드 정보를 응답 바디에 포함한다.

---

## 설계 결정

### BrandInfo를 application 레이어에 위치시킨다

- **결정:** `BrandInfo`는 `application/brand/` 패키지에 둔다
- **이유:** Facade가 도메인 객체(`Brand`)를 직접 반환하지 않도록 하기 위해. Controller가 도메인 내부를 알 필요 없이 `BrandInfo`만 받아 Response로 변환한다.

---

### ApplicationService와 Facade를 분리한다

- **결정:** 기존 `MemberFacade`는 그대로 두고, 앞으로 Brand 이후 도메인부터 분리된 구조 적용
- **이유:** ApplicationService는 유스케이스 + 트랜잭션, Facade는 Info 변환 + 오케스트레이션으로 책임을 명확히 분리하기 위해

---

### 비즈니스 규칙 문서화 → 코드 작성 순서로 진행한다

- **결정:** 새 기능 작업 전에 `docs/domain/{domain}.md`에 비즈니스 규칙, 트랜잭션 경계, 접근 제어를 먼저 정리한다
- **이유:** 코드 작성 중 설계 방향이 흔들리는 것을 방지하고, 레이어별 책임을 사전에 확정하기 위해

---

## 오해 교정

### "도메인 레이어는 다른 곳에 의존하면 안 된다"

- **오해:** DomainService가 Repository를 주입받으면 안 된다
- **정정:** Repository **인터페이스**는 domain 레이어에 위치하므로 의존 가능하다. 금지되는 건 infrastructure 레이어의 **구현체**에 의존하는 것이다.

---

### "BrandInfo는 DTO니까 interface 레이어에 있어야 한다"

- **오해:** Info 객체 = HTTP 응답 DTO → interface 레이어 소속
- **정정:** `BrandInfo`는 HTTP와 무관한 application 레이어의 출력 계약 객체다. HTTP 응답 형태는 `BrandResponse`(interface 레이어)가 담당한다.

---

---

> 날짜: 2026-05-28

---

## 개념 정리

### `GenerationType.IDENTITY`에서 save() 후 getId()는 DB를 다시 조회하는가?

**아니다.** `IDENTITY` 전략은 `save()` 호출 시 즉시 INSERT를 실행하고, DB가 생성한 ID를 JPA가 곧바로 엔티티 객체 메모리에 채워준다.

```java
Product product = productDomainService.createProduct(...);  // 여기서 INSERT + ID 채워짐
stockDomainService.createStock(product.getId(), qty);        // 메모리 필드 읽기, DB 조회 없음
```

`SEQUENCE` 전략이었다면 Hibernate가 INSERT를 지연할 수 있어 다를 수 있지만, `IDENTITY`는 항상 즉시 실행이므로 안전하다.

---

### 상품 등록 시 트랜잭션을 어디서 묶는가?

상품 저장과 재고 생성은 반드시 같이 성공하거나 같이 실패해야 한다. 한쪽만 성공하면 시스템 불일치 상태가 된다.

```java
// ProductApplicationService
@Transactional
public Product createProduct(...) {
    brandDomainService.getBrand(brandId);       // 브랜드 존재 확인
    Product product = productDomainService.createProduct(...);  // 상품 저장
    stockDomainService.createStock(product.getId(), qty);       // 재고 생성
    return product;
}
```

`@Transactional`은 ApplicationService에. DomainService들은 이 트랜잭션 안에서 같이 실행된다.

---

## 설계 결정

### 브랜드 삭제 시 상품 cascade는 Product 재설계 이후에 추가한다

- **결정:** 브랜드 삭제 API 구현 시 브랜드 소프트딜리트만 처리. 상품 cascade 처리는 Product 도메인 재설계(Block 3) 완료 후 `BrandApplicationService.delete()`에 추가
- **이유:** 현재 Product 도메인이 재설계 중이라 cascade 코드를 지금 작성하면 이중 작업이 발생함. ProductService의 `softDeleteByBrandId()`가 완성된 후 연결하는 것이 안전함

---

### Product 도메인을 ProductModel에서 Product로 전면 재설계한다

- **결정:** 기존 `ProductModel`(stock 인라인, brandId 없음)을 삭제하고 `Product` 엔티티로 재작성
- **이유:** week2 설계 문서 기준과 불일치. `Stock`을 독립 도메인으로 분리하고 `brandId`, `likeCount`를 추가해 설계 의도대로 맞춤

---

> 날짜: 2026-05-29

---

## 개념 정리

### JPA Dirty Checking — @Transactional 안에서 save()를 생략해도 되는가?

**된다.** `@Transactional` 안에서 `findById()`로 가져온 엔티티는 **managed 상태**다. 트랜잭션이 커밋될 때 JPA가 변경사항을 자동 감지(Dirty Checking)해서 UPDATE 쿼리를 날린다.

```java
@Transactional
public void addLike(Long memberId, Long productId) {
    Product product = productRepository.findById(productId)...  // managed 상태
    if (added) {
        product.incrementLikeCount();  // 변경만 해도 트랜잭션 커밋 시 자동 UPDATE
        // productRepository.save(product) — 없어도 됨
    }
}
```

단, 단위 테스트에서 `verify(repository).save()`로 저장 여부를 검증하려면 명시적 save()가 필요하다. 이 경우 테스트 설계와 실제 코드의 트레이드오프가 생긴다.

---

### DomainService에 두어야 할 코드의 기준은 무엇인가?

**"단일 Entity 혼자서 판단할 수 없는 비즈니스 규칙"** 이 기준이다.

- `existsByName` → "이름이 겹치면 안 된다" 판단 → DomainService ✅
- 좋아요 멱등 처리 → "이미 있으면 저장 안 한다" 판단 → DomainService ✅
- `findById + entity메서드 + save` → 판단 없이 그냥 실행 → ApplicationService 직접 ✅

**DB에 접근한다**고 DomainService에 두는 게 아니다. **DB에 접근해서 비즈니스 규칙을 판단한다**가 기준이다.

---

### Facade가 Repository에 직접 의존해도 되는가?

**안 된다.** Facade는 DomainService / ApplicationService를 **조합**하는 계층이다. Repository에 직접 의존하면 레이어 경계가 흐려진다.

```java
// ❌ Facade에서 Repository 직접 사용
Product product = productRepository.findById(like.getProductId())...

// ✅ DomainService를 통해 접근
Product product = productDomainService.getProduct(like.getProductId());
```

Facade의 의존성은 DomainService / ApplicationService만으로 구성해야 한다.

---

## 설계 결정

### DomainService 기준을 재정의하고 ApplicationService의 Repository 주입을 허용한다

- **결정:** DomainService는 비즈니스 규칙 판단이 필요한 코드만. ApplicationService는 Repository를 직접 주입해 단순 find+save 처리 가능
- **이유:** "ApplicationService는 Repository 주입 금지"는 학습용 제약이었고, 실무에서는 비즈니스 규칙 없는 단순 CRUD는 ApplicationService에서 직접 처리하는 것이 더 자연스럽다. find+save를 억지로 DomainService에 넣으면 역할이 불명확해진다.

---

> 날짜: 2026-05-28 (계속)

---

## 개념 정리

### 좋아요 취소 — 존재하지 않는 경우에도 200을 반환해야 하는가?

**그렇다.** HTTP 스펙에서 DELETE는 멱등(idempotent)해야 한다. 이미 취소됐거나 애초에 좋아요가 없어도 "좋아요 없는 상태"라는 목적은 달성된다.

- 네트워크 재시도, 낙관적 UI 업데이트 시나리오에서 에러 없이 처리 가능
- GitHub, Twitter 등 실서비스 토글형 API가 이 방식을 따름
- 반면 "서버 상태와 클라이언트 불일치를 명시적으로 알려야 한다"는 관점에서 404를 반환하는 케이스도 있음

결론: **멱등 200이 실무 표준**이지만, 응답 바디에 `deleted: false` 를 포함해 처리 여부를 알려주는 방법도 있다. 클라이언트가 그 값을 실제로 쓰는지에 따라 필요성이 갈림.

---

### 고객 vs 어드민 — 같은 리소스에 다른 정보 범위를 제공하는 이유

API 소비자(고객 / 어드민)의 **목적이 다르기 때문**이다.

| 필드 | 고객 | 어드민 |
|---|---|---|
| `createdAt`, `updatedAt` | ❌ (운영 데이터) | ✅ |
| `stock` 수량 | ❌ (노출 불필요) | ✅ |
| `inStock` (boolean) | ✅ (구매 결정용) | ❌ |
| `brandName` | ✅ | ✅ |

고객은 탐색/구매 결정에 필요한 최소 정보만, 어드민은 운영 관리에 필요한 모든 정보를 받는다. 같은 도메인 모델에서 출발하더라도 응답 DTO를 분리해 각 소비자에게 맞는 뷰를 제공한다.

---

### 조합 없는 Facade — 레이어를 유지하는 게 의미 있는가?

Facade의 본래 가치는 **여러 서비스 조합 + 도메인 객체 → Info 변환**이다. 단순 위임만 하는 경우엔 레이어의 가치가 희미해진다.

```java
// 이런 Facade는 가치가 거의 없음
public Page<ProductInfo> getProducts(...) {
    return productApplicationService.getProducts(...);
}
```

실무 선택지:
1. **일관성 우선** — 모든 도메인이 Controller → Facade → ApplicationService 패턴을 따름. 나중에 조합 로직이 생길 때 자리가 있음
2. **실용성 우선** — 조합 없으면 Controller → ApplicationService 직접 호출. CQRS 패턴에서 Query는 Repository 직접 조회하기도 함

**판단 기준:** Facade가 아무것도 안 한다면 레이어로서의 존재 이유를 다시 물어야 한다. 단, 프로젝트 전체 아키텍처 일관성이 있다면 유지하는 것도 합리적이다.

---

## 설계 결정

### 상품/브랜드 목록 조회 시 N+1 방지를 위해 배치 조회를 사용한다

- **결정:** 상품 목록 조회 시 브랜드명과 재고를 상품별로 따로 조회하지 않고, `findAllByIdIn()` / `findAllByProductIdIn()`으로 한 번에 배치 조회 후 Map으로 조합
- **이유:** 상품 페이지(20개)마다 브랜드/재고 조회가 각각 20번씩 나가는 N+1을 방지하기 위해. JOIN 쿼리가 불가능한 구조(Product에 Brand `@ManyToOne` 없음)에서 실용적인 대안

---

> 날짜: 2026-05-28 (계속 2)

---

## 개념 정리

### 소프트딜리트는 데이터를 물리적으로 지우는가?

**아니다.** `deleted_at` 컬럼에 현재 시각을 세팅할 뿐, 나머지 필드는 원본 그대로 보존된다.

`@SQLRestriction("deleted_at IS NULL")`이 걸려 있으면 일반 조회에서는 안 보이지만, 직접 JOIN하거나 `@SQLRestriction` 없이 조회하면 여전히 접근 가능하다. 이 덕분에 `OrderItemSnapshot`처럼 이미 주문된 상품/브랜드가 삭제되어도 주문 이력의 FK 정합성이 유지된다.

---

### 브랜드 삭제 시 주문 중인 건은 어떻게 되는가?

**그냥 소프트딜리트해도 무방하다.** 주문 생성 시점에 상품명·가격·브랜드명이 `OrderItemSnapshot`에 복사되기 때문에, 이후 상품/브랜드가 삭제되어도 주문 이력에는 전혀 영향이 없다.

소프트딜리트를 채택한 핵심 이유가 바로 이것 — 물리 삭제를 하면 주문 이력이 FK 위반으로 깨지지만, 소프트딜리트는 데이터를 남기기 때문에 과거 주문이 안전하게 보존된다.

---

### 어드민 삭제 API에서 이미 삭제된 리소스를 다시 삭제 요청하면 멱등 200인가 404인가?

이 질문은 좋아요 취소 멱등(200)과 같은 철학처럼 보이지만, 호출 맥락이 다르다.

| 구분 | 좋아요 취소 | 어드민 브랜드 삭제 |
|---|---|---|
| 호출자 | 일반 사용자 (실수 재시도 흔함) | 어드민 (의도적 조작) |
| 목적 | UX 편의 — 이미 취소됐어도 OK | 리소스 관리 — 없는 걸 지우려 했다는 걸 알아야 함 |

**결정: 404.** 어드민 도구에서는 "이 리소스가 존재했나?"를 확인할 수 있게 오류로 알려주는 것이 운영 관점에서 더 유용하다. 2주차 설계 문서의 판단과 일치한다.

---

## 설계 결정

### 브랜드 수정 시 자기 자신 이름과 같아도 허용한다

- **결정:** `validateDuplicateNameExcluding(name, excludeId)` — 중복 체크 시 자기 자신(brandId)은 제외
- **이유:** "나이키"를 "나이키"로 수정하는 건 중복이 아니라 동일 상태 유지다. 현업에서 이름 변경이 없을 때도 다른 필드 수정과 함께 PUT 요청을 보내는 경우가 있어, 자기 자신 제외가 실용적이다.

---

### 어드민 상품 조회 — ApplicationService 재사용, DTO만 분리

- **결정:** `ProductApplicationService.getProduct()` / `getProducts()`를 어드민에서도 그대로 재사용. 어드민 전용 응답은 `ProductAdminResponse` DTO로만 분리
- **이유:** `ProductInfo`에 이미 `stockQuantity`, `brandId`, `createdAt`, `updatedAt` 등 어드민 필드가 모두 담겨 있어 로직 중복 없이 DTO 레이어 차이만으로 고객/어드민 응답을 분리할 수 있다. 레이어 중복 없이 뷰만 다르게 제공하는 것이 핵심.
