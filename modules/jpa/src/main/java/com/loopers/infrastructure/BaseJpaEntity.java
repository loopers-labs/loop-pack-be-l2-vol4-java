package com.loopers.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import java.time.ZonedDateTime;

/**
 * 생성/수정/삭제 정보를 자동으로 관리해준다.
 * 재사용성을 위해 이 외의 컬럼이나 동작은 추가하지 않는다.
 */
@MappedSuperclass
@Getter
public abstract class BaseJpaEntity {

    @Id
    @Column(length = 60, nullable = false, updatable = false)
    private String id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    protected ZonedDateTime deletedAt;

    protected BaseJpaEntity() {}

    protected BaseJpaEntity(String id, ZonedDateTime deletedAt) {
        if (id != null) this.id = id;
        this.deletedAt = deletedAt;
    }

    protected void setId(String id) {
        this.id = id;
    }

    protected void guard() {}

    /** 엔티티별 3글자 도메인 코드를 제공한다. (USR, BRD, PRD, ...) */
    protected abstract String idCode();

    @PrePersist
    private void prePersist() {
        guard();

        if (this.id == null) {
            this.id = EntityId.generate(idCode());
        }
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        guard();

        this.updatedAt = ZonedDateTime.now();
    }
}
