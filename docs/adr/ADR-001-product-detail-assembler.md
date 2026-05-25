# ADR-001: 상품 상세 조회의 Product + Brand 조합 위치 — ProductDetailAssembler(도메인 서비스) 채택

## 상태
Accepted

## 배경

상품 상세/목록 조회 API는 `ProductModel`(상품)과 `BrandModel`(브랜드) 두 도메인의 정보를 조합해 응답해야 한다.
초기 구현(`3e1ef9e`)에서는 `application` 레이어의 `ProductInfo`가 두 도메인 모델을 직접 받아 조합했다.

```java
// 초기 구현 (3e1ef9e)
public static ProductInfo from(BrandModel brand, ProductModel product) { ... }

// ProductFacade 호출부
BrandModel brand = brandFinder.getById(product.getBrandId());
return ProductInfo.from(brand, product);
```

이 조합을 **어느 레이어에 둘 것인가**를 결정하기 위해 작성한다.

## 판단 기준

조합 로직의 위치는 **조합 행위가 도메인 규칙을 담는가**로 가른다.

- **도메인 규칙이 없는 조합** — 여러 출처의 필드를 골라 응답/조회 모델로 형 변환하는 것뿐이라면, 이는 응용 레이어의 책임이다. 여러 도메인을 조립해 유스케이스를 제공하는 것이 응용 레이어의 정의이기 때문이다.
- **도메인 규칙이 있는 조합** — 두 도메인을 합칠 때 지켜야 할 불변식·정합성·파생값 계산이 끼어든다면(예: 비활성 브랜드 상품 제외, 브랜드 등급별 유효가 산정), 그 규칙은 응용 레이어에 흩어두지 말고 도메인에 모은다. 이때 결과 타입은 행동·불변식을 가진 진짜 도메인 VO가 된다.

이 기준만 적용하면, **현재의 조합은 다섯 필드 복사가 전부이고 어떤 규칙도 없으므로 응용 레이어 조합(Option A)이 자연스러운 기본값이다.**

## 제약

과제 체크리스트에 **"상품 상세 조회 시 Product + Brand 정보 조합은 도메인 서비스에서 처리한다"** 는 요구사항이 명시되어 있다. 이는 본 결정의 구속 조건이다.

## 결정

위 제약에 따라 **도메인 서비스 방식(Option B)을 채택한다.** `domain/product`에 `ProductDetailAssembler`를 두고, `ProductFacade`는 조회한 `ProductModel`과 `BrandModel`을 Assembler에 넘겨 `ProductDetail`(도메인 VO)을 만든 뒤 `ProductInfo`로 변환한다.

```java
// 변경 후 (20c4893)
// domain/product — 도메인 서비스
public static ProductDetail assemble(ProductModel product, BrandModel brand) { ... }
public static List<ProductDetail> assembleAll(List<ProductModel> products, Map<Long, BrandModel> brandMap) { ... }

// application/product
public static ProductInfo from(ProductDetail detail) { ... }

// ProductFacade 호출부
BrandModel brand = brandFinder.getById(product.getBrandId());
return ProductInfo.from(ProductDetailAssembler.assemble(product, brand));
```

이 채택은 "현재의 조합에 도메인 규칙이 있어서"가 아니라 **요구사항 제약 + 향후 규칙 유입 시의 자리 확보** 때문임을 분명히 한다.

## 선택지

### Option A: `ProductInfo.from(BrandModel, ProductModel)` — 응용 레이어에서 직접 조합

- **장점**:
  - 판단 기준상 규칙 없는 조합의 자연스러운 자리. 중간 타입 불필요.
  - 변환이 단일 메서드로 끝나 추적이 쉽다.
- **단점**:
  - 조합에 향후 도메인 규칙이 생기면 응용 레이어에 흩어질 위험.
  - 과제 요구사항(도메인 서비스 처리)을 충족하지 못한다.

### Option B: `ProductDetailAssembler` + `ProductDetail` — 도메인 서비스에서 조합 (채택)

- **장점**:
  - 과제 요구사항을 충족한다.
  - 조합에 도메인 규칙(브랜드 상태/등급 등)이 추가될 때 응용 레이어를 건드리지 않고 한곳에서 흡수할 수 있는 자리를 미리 확보한다.
  - `ProductDetailAssembler`, `ProductInfo`를 각각 독립적으로 단위 테스트할 수 있다.
- **단점**:
  - 현재는 규칙이 없어 `ProductDetail`이 `ProductInfo`와 구조적으로 거의 동일한 사본이 된다.
  - 변환 경로가 `ProductModel + BrandModel → ProductDetail → ProductInfo` 두 단계가 된다.

## 결과

- **긍정**: 요구사항을 충족하고, 두 도메인의 조합 규칙이 들어올 단일 진입점을 확보했다. 향후 비활성 브랜드 처리·등급 할인·집계 규칙 같은 *진짜 도메인 로직*이 생기면 이 구조의 이점이 비로소 실질화된다.
- **부정/솔직한 비용**: 현재 시점에서는 조합에 규칙이 없으므로 이 구조가 곧바로 주는 이득은 없다. `ProductDetail`은 `ProductInfo`의 사본에 가깝다. 즉 **지금은 요구사항을 위한 선반영이며, 규칙이 들어오기 전까지는 보일러플레이트로 보일 수 있다.**
- **재검토 신호**: 일정 기간이 지나도 `assemble`에 어떤 규칙도 붙지 않는다면, Option A(응용 레이어 조합)로 되돌리는 것을 재검토한다. 위치 판단의 기준은 규칙의 유무다.

## 참고

- 관련 커밋: `3e1ef9e`(초기 구현 — 응용 레이어 조합), `20c4893`(ProductDetailAssembler 도입)
- 관련 파일:
  - `domain/product/ProductDetailAssembler.java`
  - `domain/product/ProductDetail.java`
  - `application/product/ProductInfo.java`
  - `application/product/ProductFacade.java`
