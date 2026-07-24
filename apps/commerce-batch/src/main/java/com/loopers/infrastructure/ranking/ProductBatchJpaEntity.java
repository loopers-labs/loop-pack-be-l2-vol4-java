package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@Entity
@Immutable
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductBatchJpaEntity {

    @Id
    private Long id;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    ProductBatchJpaEntity(Long id, boolean deleted) {
        this.id = id;
        this.deleted = deleted;
    }
}
