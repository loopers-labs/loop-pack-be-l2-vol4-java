package com.loopers.domain.brand;

import com.loopers.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE brand SET is_deleted = true WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(name = "brand")
public class BrandModel extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    public BrandModel(String name) {
        this.name = name;
    }

    public void update(String name) {
        this.name = name;
    }
}
