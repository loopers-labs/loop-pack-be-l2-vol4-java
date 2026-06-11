# ADR-001: 상품 상세 조회의 Product + Brand 조합 위치 — 응용 레이어 직접 조합 채택

## 상태
Accepted

## 배경

상품 상세/목록 조회 API는 `ProductModel`(상품)과 `BrandModel`(브랜드) 두 도메인의 정보를 조합해 응답해야 한다.
이 조합을 **어느 레이어에 둘 것인가**를 결정하기 위해 작성한다.

## 판단 기준

조합 로직의 위치는 **조합 행위가 도메인 규칙을 담는가**로 가른다.

- **도메인 규칙이 없는 조합** — 여러 출처의 필드를 골라 응답/조회 모델로 형 변환하는 것뿐이라면, 이는 응용 레이어의 책임이다. 여러 도메인을 조립해 유스케이스를 제공하는 것이 응용 레이어의 정의이기 때문이다.
- **도메인 규칙이 있는 조합** — 두 도메인을 합칠 때 지켜야 할 불변식·정합성·파생값 계산이 끼어든다면(예: 비활성 브랜드 상품 제외, 브랜드 등급별 유효가 산정), 그 규칙은 응용 레이어에 흩어두지 말고 도메인에 모은다. 이때 결과 타입은 행동·불변식을 가진 진짜 도메인 VO가 된다.

**현재의 조합은 다섯 필드 복사가 전부이고 어떤 규칙도 없으므로 응용 레이어 조합(Option A)이 자연스러운 기본값이다.**

## 결정

**Option A를 채택한다.** `ProductInfo.from(ProductModel, BrandModel)`이 두 도메인을 직접 조합한다. `ProductFacade`는 조회한 `ProductModel`과 `BrandModel`을 바로 `ProductInfo.from`에 전달한다.

```java
// application/product
public static ProductInfo from(ProductModel product, BrandModel brand) {
    return new ProductInfo(
        product.getId(),
        brand.getName(),
        product.getName(),
        product.getPrice(),
        product.getLikeCount()
    );
}

// ProductFacade 호출부
BrandModel brand = brandFinder.getById(product.getBrandId());
return ProductInfo.from(product, brand);
```

## 선택지

### Option A: `ProductInfo.from(ProductModel, BrandModel)` — 응용 레이어에서 직접 조합 (채택)

- **장점**:
  - 판단 기준상 규칙 없는 조합의 자연스러운 자리. 중간 타입 불필요.
  - 변환이 단일 메서드로 끝나 추적이 쉽다.
- **단점**:
  - 조합에 향후 도메인 규칙이 생기면 응용 레이어에 흩어질 위험. 그 시점에 재이전이 필요하다.

### Option B: `ProductDetailAssembler` + `ProductDetail` — 도메인 서비스에서 조합

- **장점**:
  - 조합에 도메인 규칙이 추가될 때 응용 레이어를 건드리지 않고 한곳에서 흡수할 수 있는 자리를 미리 확보한다.
- **단점**:
  - 규칙이 없는 현재 시점에서 `ProductDetail`은 `ProductInfo`의 사본에 가깝다.
  - 변환 경로가 `ProductModel + BrandModel → ProductDetail → ProductInfo` 두 단계가 돼 불필요한 간접 계층이 생긴다.

## 결과

- **긍정**: 중간 타입 없이 단일 메서드로 변환이 끝나 코드가 단순하다. 응용 레이어의 책임 범위(도메인 조립)에 자연스럽게 속한다.
- **부정/솔직한 비용**: 향후 조합에 도메인 규칙이 생기면 그 시점에 도메인 레이어로 이전해야 한다.
- **재검토 신호**: 조합 로직에 필드 복사 이상의 비즈니스 규칙(브랜드 상태 필터, 등급 할인 등)이 추가되면 Option B(도메인 서비스 방식)로 이전한다.