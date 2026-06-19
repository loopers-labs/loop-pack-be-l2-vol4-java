# 05. 설계 의사결정 기록

> 설계 과정에서 고민하고 결정한 사항들을 기록합니다.  
> "왜 이렇게 했는가"와 "왜 다른 선택지를 버렸는가"를 함께 남깁니다.

---

## DD-009. Repository Interface — Domain Layer 유지

**고민**

현재 프로젝트는 도메인 객체가 JPA 엔티티와 동일하다 (`ProductModel extends BaseEntity`에 `@Entity` 직접 부착).
이 상태에서 Repository Interface를 Domain Layer에 두는 게 의미 있는지 의문이 생겼다.

```
// Domain Layer가 노출하는 타입
Optional<ProductModel> findActiveById(Long id);  // ProductModel은 이미 @Entity

// Infrastructure Layer 구현체
return productJpaRepository.findByIdAndDeletedAtIsNull(id);
```

도메인 객체가 이미 JPA에 결합되어 있다면, Repository Interface가 제공하는 추상화는 얼마나 실질적인가?

**제외한 선택지 1 — JpaRepository를 Domain Layer에 직접 배치**

도메인 객체가 이미 JPA 엔티티이므로, `spring-data-jpa`를 Domain Layer에 추가하는 게 큰 비약이 아니라는 논거가 있다.

제외 이유:

1. **메서드 노출 범위 통제 불가** — `JpaRepository`는 `deleteAll()`, `saveAll()`, `flush()`, `getOne()` 등 Application Layer에 노출해서는 안 될 메서드를 함께 올린다. 지금 interface는 비즈니스에 필요한 메서드만 명시적으로 선언한다.

2. **`@Query`가 Domain Layer를 오염** — 벌크 UPDATE 같은 JPQL 쿼리는 인프라 관심사다. Domain Layer에 `@Modifying @Query("UPDATE ProductModel p SET p.deletedAt = ...")` 가 존재하는 건 계층 책임을 위반한다.

3. **네이밍이 도메인 언어를 오염** — Spring Data JPA 파생 쿼리 이름(`findByIdAndDeletedAtIsNull`, `findAllByBrand_IdAndDeletedAtIsNull`)은 테이블 구조를 드러낸다. Application Layer는 `findActiveById`, `findAllActive` 같은 비즈니스 언어로 소통해야 한다.

**제외한 선택지 2 — POJO 도메인 객체 분리**

엔티티와 도메인 객체를 완전히 분리하면 진정한 DIP가 실현된다.

제외 이유:

도메인 7개 기준 POJO + JPA Entity + Mapper = 신규 파일 약 21개 추가, 30~40시간 공수.
현재 규모에서 DB 스키마와 도메인 모델이 실질적으로 달라지는 케이스가 없어 비용 대비 효과가 낮다.

추가로, `BaseEntity`의 `id` 필드가 `private final Long id = 0L`로 선언되어 JPA 컨텍스트 밖에서는 모든 인스턴스의 ID가 `0L`로 고정된다. 이 때문에 Fake Repository 기반 단위 테스트는 현재 구조에서 실용성이 낮다. "인터페이스를 Domain Layer에 두면 테스트 가능성이 높아진다"는 주장은 지금 엔티티 구조에서는 절반만 참이다.

**결정**

현재 구조(Domain Layer에 Repository Interface, Infrastructure Layer에 구현체)를 유지한다.

**근거**

POJO 분리 없이도 Interface 레이어가 주는 실질적 가치가 있다:

| 가치 | 설명 |
|---|---|
| 메서드 통제 | Application Layer가 접근 가능한 메서드를 비즈니스 필요 단위로 제한 |
| 시맨틱 네이밍 | JPA 파생 쿼리명이 아닌 도메인 언어로 메서드 노출 |
| 쿼리 격리 | `@Query`, `@Modifying` 등 인프라 구현 세부사항이 Domain Layer 밖으로 격리 |
| 마이그레이션 경로 | 향후 POJO 분리 시 Interface 계약은 그대로 유지되고 구현체만 교체하면 됨 |

DIP의 완전한 실현은 POJO 분리 이후이나, 현재 구조도 의존 방향(Application → Interface ← Impl)은 올바르게 유지한다.

**향후 고려**

도메인 로직이 복잡해져 DB 스키마와 도메인 모델이 실질적으로 달라지는 시점, 또는 JPA 없이 도메인 로직을 단위 테스트해야 할 필요가 생기는 시점에 POJO 분리를 재검토한다.

---

## DD-010. 단일 객체 검증 — Domain Service 아닌 Model에 위치

**고민**

`ProductDomainService.validateProductActive(product)`, `ProductDomainService.validateBrand(brand)` 처럼
단일 도메인 객체의 상태만 검사하는 로직이 Domain Service에 위치했다.

**결정**

자기 검증(self-validation)은 해당 Model 자신이 담당한다.

```java
// 변경 전: Domain Service 경유
productDomainService.validateProductActive(product);

// 변경 후: Model 자신이 담당
product.validateActive();
```

**근거**

Domain Service가 정당화되는 조건은 **여러 도메인 객체 간의 협력 로직**이다.
단일 객체의 상태(`isDeleted()`)만 보는 검증은 그 객체가 스스로 처리하는 것이 자연스럽고,
도메인 객체의 비즈니스 규칙 캡슐화 원칙에도 부합한다.

`ProductDomainService`에 남긴 `assembleDetail(product, stock)`은 Product + Brand + Stock
3개 도메인 객체를 조합하므로 Domain Service에 적합하다.

---

## DD-011. Brand 삭제 시 Product 연쇄 처리 — Application Service 간 횡방향 결합 제거

**고민**

`BrandService.delete()`가 `ProductService.deleteAllByBrandId()`를 호출하고 있었다.
Application Layer 서비스끼리의 횡방향 의존으로, Brand 유스케이스가 Product 유스케이스의 내부 구현을 알아야 하는 결합이 생겼다.

```java
// 변경 전: Application Layer 횡방향 결합
private final ProductService productService;
productService.deleteAllByBrandId(id);
```

**제외한 선택지 — BrandFacade 도입**

Brand 삭제가 여러 도메인을 조율하는 복잡한 흐름이라면 Facade가 맞다.
그러나 Brand 삭제의 Product 연쇄는 단방향 단순 소프트딜리트이고,
나머지 Brand 기능(`create`, `update`, `getById`, `getAll`)에는 Facade가 없어 `delete`만을 위한 Facade는 비대칭 구조를 만든다.

**결정**

`BrandService`가 `ProductService` 대신 `ProductRepository`를 직접 주입.
Application Layer → Infrastructure Layer는 올바른 의존 방향이고, 소프트딜리트 로직도 단순하다.

추가로 N건 루프 `save()` 대신 단일 벌크 UPDATE 쿼리로 교체해 DB 왕복을 최소화했다.

```java
// 변경 후: 단일 벌크 쿼리
productRepository.softDeleteAllByBrandId(id);

// Infrastructure: @Modifying @Query
// UPDATE ProductModel p SET p.deletedAt = :now WHERE p.brand.id = :brandId AND p.deletedAt IS NULL
```
