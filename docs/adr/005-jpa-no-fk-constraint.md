# ADR-005: @ManyToOne FK 제약조건 제거

- 날짜: 2026-05-20
- 상태: 승인됨

## 결정

`ProductModel → BrandModel` `@ManyToOne` 관계 및 모든 JPA 연관 관계에서 DB 레벨 FK 제약조건을 생성하지 않는다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "brand_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
private BrandModel brand;
```

## 근거

이 프로젝트는 Soft Delete(`deletedAt` 컬럼) 방식을 사용하므로 DB에서 실제 행이 삭제되지 않는다. 브랜드 삭제 시 연관 상품 처리는 애플리케이션 레벨(`BrandFacade`)에서 일관되게 관리하며, DB FK 제약조건을 함께 두면 이 흐름과 중복된다.

### 고려한 대안

#### Option 1. DB 레벨 FK 제약조건 사용

`@JoinColumn`에 별도 설정 없이 Hibernate 기본값을 사용하여 DB에 FK 제약조건을 생성하는 방식이다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "brand_id")  // FK 제약조건 자동 생성
private BrandModel brand;
```

- **장점**: DB 레벨에서 참조 무결성을 보장한다. 잘못된 `brand_id`가 저장되는 것을 DB가 차단한다.
- **단점**: Soft Delete 환경에서 FK 제약조건은 실질적으로 작동하지 않는다. 브랜드를 soft delete해도 행이 남아 있으므로 FK 위반이 발생하지 않는다. 반대로, 운영 실수로 브랜드 행이 실제 삭제되면 연관 상품 조회 시 FK 오류가 발생할 수 있다. 연관 관계가 많아질수록 DDL과 마이그레이션이 복잡해진다.

---

#### Option 2. FK 제약조건 없음 (채택)

`@ForeignKey(ConstraintMode.NO_CONSTRAINT)`로 FK 제약조건 생성을 명시적으로 억제하는 방식이다. 참조 무결성은 애플리케이션 레벨에서 관리한다.

```java
@JoinColumn(name = "brand_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
```

- **장점**: Soft Delete 방식과 자연스럽게 어울린다. 데이터 삭제·마이그레이션 시 FK 제약으로 인한 오류가 없다. 프로젝트 전반의 애플리케이션 레벨 제어 원칙과 일관성이 유지된다.
- **단점**: DB가 참조 무결성을 보장하지 않으므로, 애플리케이션 버그로 잘못된 `brand_id`가 저장될 경우 DB 레벨에서 차단되지 않는다. 애플리케이션 레벨 검증이 더 엄격해야 한다.
