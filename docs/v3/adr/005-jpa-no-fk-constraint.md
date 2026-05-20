# ADR-005: @ManyToOne FK 제약조건 제거

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

`ProductModel → BrandModel` `@ManyToOne` 관계에서 DB 레벨 FK 제약조건을 생성하지 않는다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "brand_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
private BrandModel brand;
```

## 근거

이 프로젝트는 soft delete(`deletedAt` 컬럼) 방식을 사용하므로 DB에서 실제 row가 삭제되지 않는다. 브랜드 삭제 시 연관 상품 처리는 애플리케이션 레벨(`BrandService`)에서 일관되게 관리하며, DB FK 제약조건은 이 흐름과 중복이다.

프로젝트 전반이 애플리케이션 레벨 제어를 채택하고 있으므로, FK 제약조건 없이 가는 것이 일관성 측면에서 적절하다.
