# ADR-026: 좋아요 목록 조회 N+1 문제 — 현행 유지

- 날짜: 2026-05-29
- 상태: 현행 유지 (Known Issue)

## 컨텍스트

`LikeFacade.getLikedProducts()`는 좋아요 목록을 페이지 단위로 조회한 뒤, Like마다 `ProductService`와 `BrandService`를 각각 호출해 `LikeInfo`를 조합한다.

```
getLikedProducts(userId, pageable)
  └── likeService.getLikedProducts(userId, pageable)   # likes 쿼리 (1회)
        └── for each like:
              ├── productService.getProduct(productId)  # product 쿼리 (N회)
              └── brandService.getBrand(brandId)        # brand 쿼리 (N회)
```

페이지 크기 20 기준, 단일 요청에 최대 **41개 쿼리**가 발생한다. ADR-019의 `ProductFacade.getAllProducts()`와 동일한 N+1 패턴이다.

## 현재 결정

현 단계에서는 수정하지 않는다.

**허용 근거**: 페이지 크기 20으로 쿼리 수가 제한적이며, 현 트래픽 수준에서 허용 범위 내에 있다.

## 해결 방안 (추후 검토)

1. **IN 쿼리 일괄 조회**: `productIds`를 모아 `WHERE id IN (...)` 단일 쿼리로 대체 → 총 3쿼리로 감소.
2. **JOIN FETCH / QueryDSL 프로젝션**: `LikeEntity`, `ProductEntity`, `BrandEntity`를 단일 쿼리로 조인.
3. **ADR-025 Application Service 분리 조건 연계**: 중복 3회 이상 또는 비즈니스 로직 추가 시 `ProductQueryService` 도입과 함께 일괄 조회 최적화 적용.

## ADR-019와의 관계

ADR-019는 `ProductFacade.getAllProducts()`의 N+1을 다룬다. 이 ADR은 `LikeFacade.getLikedProducts()`의 동일 패턴을 별도로 기록한다. 두 이슈는 함께 해결하는 것이 효율적이며, ADR-025의 Application Service 분리 검토 시점에 함께 재검토한다.
