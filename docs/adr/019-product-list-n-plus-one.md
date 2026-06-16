# ADR-019: 상품 목록 조회 N+1 문제 — 미해결

- 날짜: 2026-05-28
- 상태: 미해결 (Known Issue)

## 컨텍스트

`ProductFacade.getAllProducts()`는 상품 목록을 페이지 단위로 조회한 뒤, 상품마다 `BrandService`와 `InventoryService`를 각각 호출해 `ProductInfo`를 조합한다.

```
getAllProducts(page)
  └── productService.getAllProducts(page)        # 상품 목록 쿼리 (1회)
        └── for each product:
              ├── brandService.getBrand(brandId) # 브랜드 쿼리 (N회)
              └── inventoryService.get(productId)# 재고 쿼리 (N회)
```

페이지 크기 20 기준, 단일 요청에 최대 **41개 쿼리**가 발생한다.

## 현재 결정

현 단계에서는 수정하지 않는다.

**허용 근거**: 페이지 크기가 20으로 고정되어 있어 쿼리 수가 제한적이며, 현 트래픽 수준에서 허용 범위 내에 있다.

## 해결 방안 (추후 검토)

트래픽 증가 시 아래 중 하나를 선택해 적용한다.

1. **IN 쿼리 일괄 조회**: `brandIds`와 `productIds`를 모아 `WHERE id IN (...)` 단일 쿼리로 대체 → 총 3쿼리로 감소.
2. **JOIN FETCH / QueryDSL 프로젝션**: `ProductEntity`와 `BrandEntity`, `InventoryEntity`를 단일 쿼리로 조인해 DTO로 직접 매핑.
3. **캐싱**: 브랜드 정보처럼 변경이 드문 데이터는 Redis에 캐시해 쿼리 자체를 제거.

## 트레이드오프

- 방안 1·2 모두 `ProductFacade` 또는 `ProductService`가 `BrandRepository`, `InventoryRepository`에 직접 의존하거나, 전용 조회용 쿼리 서비스를 별도로 두어야 한다.
- 도메인 경계(ADR-018)와의 충돌을 고려해 구조 변경 시 사전 설계 검토가 필요하다.
