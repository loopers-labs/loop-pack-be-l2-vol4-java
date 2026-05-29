# ADR-025: 상품+브랜드 조합 위치 — Facade 유지

- 날짜: 2026-05-29
- 상태: 승인됨

## 결정

`ProductEntity`와 `BrandEntity`의 조합(`ProductInfo` 생성)은 현재 상태인 **Facade 레이어에서 유지**한다.

단, 아래 조건 중 하나라도 충족될 경우 `ProductQueryService`(Application Service)로 분리를 진행한다.

- 상품+브랜드 조합이 필요한 Facade가 **3곳 이상**으로 늘어나는 경우
- 두 조합 사이에 **비즈니스 로직**이 추가로 필요한 경우 (단순 조합 이상의 판단 로직)

> **다음 주차 진행 시, 리팩토링 판단 여부 검토**

## 배경

현재 `ProductEntity`는 `brandId(Long)` 참조만 보유하며, 브랜드명(`brandName`)은 포함하지 않는다.
고객 응답에 브랜드명이 필요한 경우, Facade에서 `ProductService`와 `BrandService`를 각각 호출해 조합한다.

```java
// ProductFacade
ProductEntity product = productService.getProduct(id);
BrandEntity brand = brandService.getBrand(product.getBrandId());
return ProductInfo.from(product, brand);

// LikeFacade
ProductEntity product = productService.getProduct(like.getProductId());
BrandEntity brand = brandService.getBrand(product.getBrandId());
return LikeInfo.from(product, brand);
```

이 패턴이 `ProductFacade`, `LikeFacade` 두 곳에 중복되어 있어 분리를 검토했다.

## 검토한 대안

### 1. 현재 유지 (Facade 직접 조합)

- 도메인 순수성 유지 (ProductService, BrandService 각자 독립)
- 중복이 2회로 분리 효과 제한적
- 새로운 Facade 추가 시 패턴 반복 필요

### 2. Domain Service 이동

- `ProductService`가 `BrandRepository`에 의존 → Product/Brand Aggregate 경계 침범
- 미채택

### 3. Application Service 추가 (`ProductQueryService`)

```java
@Component
public class ProductQueryService {
    public ProductInfo getProductWithBrand(Long productId) {
        ProductEntity product = productService.getProduct(productId);
        BrandEntity brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand);
    }
}
```

- 중복 제거, 도메인 순수성 유지
- 현재 중복 2회 → 분리 대비 레이어 추가 비용이 더 큼
- 중복 3회 이상이거나 조합 간 비즈니스 로직이 생길 때 효과가 명확해짐

## 미채택 근거

현재 중복 발생 지점이 2곳(ProductFacade, LikeFacade)으로, Application Service 도입 비용 대비 효과가 크지 않다.

또한 "상품+브랜드 조합"은 현재 단순 데이터 조합으로, 두 Facade 간 별도의 비즈니스 판단이 없다. 이 상태에서 분리하면 레이어만 늘어나고 실질적 이점이 적다.

## 트레이드오프

| 항목 | Facade 유지 (현재) | Application Service 분리 |
|---|---|---|
| 중복 | 2곳 반복 | 제거 |
| 도메인 순수성 | ✅ | ✅ |
| 레이어 복잡도 | 낮음 | 증가 |
| 분리 효과 | 중복 2회 → 제한적 | 중복 3회↑ → 효과 명확 |
