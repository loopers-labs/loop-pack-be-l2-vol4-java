# ADR-005: DB 레벨 FK 제약조건 미사용

- 날짜: 2026-05-20
- 상태: 승인됨 (수정 — 분리형 아키텍처 반영)

## 결정

모든 테이블 간 참조(`brand_id`, `product_id`, `user_id`, `order_id` 등)에서 DB 레벨 FK 제약조건을 생성하지 않는다. 도메인 Entity는 JPA 관계(`@ManyToOne`, `@OneToOne`, `@OneToMany`) 없이 ID 참조(Long)만 사용한다.

```java
// ProductEntity (domain) — JPA 관계 없음
public class ProductEntity extends BaseEntity {
    private Long brandId;   // @ManyToOne 없음, ID 참조만
    ...
}

// ProductJpaEntity (infrastructure) — DB 컬럼만 매핑
@Entity
@Table(name = "product")
public class ProductJpaEntity extends BaseJpaEntity {
    private Long brandId;   // FK 컬럼 존재, DB 레벨 FK 제약조건 없음
    ...
}
```

## 근거

이 프로젝트는 Soft Delete(`deletedAt` 컬럼) 방식을 사용하므로 DB에서 실제 행이 삭제되지 않는다. 브랜드 삭제 시 연관 상품 처리는 애플리케이션 레벨(`BrandFacade`)에서 일관되게 관리하며, DB FK 제약조건을 함께 두면 이 흐름과 중복된다.

### 고려한 대안

#### Option 1. DB 레벨 FK 제약조건 사용 (기각)

`brand_id` 컬럼에 DB FK 제약조건을 추가하는 방식이다.

- **장점**: DB 레벨에서 참조 무결성을 보장한다. 잘못된 `brand_id`가 저장되는 것을 DB가 차단한다.
- **단점**: Soft Delete 환경에서 FK 제약조건은 실질적으로 작동하지 않는다. 브랜드를 soft delete해도 행이 남아 있으므로 FK 위반이 발생하지 않는다. 반대로, 운영 실수로 브랜드 행이 실제 삭제되면 연관 상품 조회 시 FK 오류가 발생할 수 있다. 마이그레이션이 복잡해진다.

---

#### Option 2. FK 제약조건 없음 + ID 참조 (채택)

FK 컬럼은 존재하나 DB 제약조건은 생성하지 않는다. 참조 무결성은 애플리케이션 레벨에서 관리한다.

- **장점**: Soft Delete 방식과 자연스럽게 어울린다. 데이터 삭제·마이그레이션 시 FK 제약으로 인한 오류가 없다. 분리형 아키텍처(도메인 Entity가 JPA를 모름)와 일관성이 유지된다.
- **단점**: DB가 참조 무결성을 보장하지 않으므로, 애플리케이션 버그로 잘못된 `brand_id`가 저장될 경우 DB 레벨에서 차단되지 않는다. 애플리케이션 레벨 검증이 더 엄격해야 한다.
