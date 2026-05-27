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
