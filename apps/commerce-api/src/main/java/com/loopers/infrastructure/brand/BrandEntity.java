package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * brand 테이블 JPA 매핑 전용 엔티티. 순수 도메인(BrandModel)과 분리되어 영속 관심사만 담는다.
 * soft delete는 BaseEntity의 deletedAt/delete()/restore()를 그대로 사용한다.
 * 도메인 ↔ 엔티티 변환은 BrandEntityMapper가 담당.
 */
@Entity
@Table(name = "brand")
public class BrandEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    protected BrandEntity() {}

    public BrandEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 변경 가능한 상태만 갱신한다. managed 엔티티에 적용 → dirty checking이 UPDATE로 반영.
     * (soft delete 동기화는 BaseEntity.delete()/restore()로 별도 처리)
     */
    public void applyState(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
