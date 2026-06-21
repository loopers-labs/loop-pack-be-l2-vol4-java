package com.loopers.support.domain;

import java.time.ZonedDateTime;

public abstract class DomainEntity {

    private Long id = 0L;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected DomainEntity() {}

    protected void assignMetadata(Long id, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        this.id = id == null ? 0L : id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public Long getId() {
        return id;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }

    public boolean isNew() {
        return id == null || id == 0L;
    }

    public void delete() {
        if (deletedAt == null) {
            deletedAt = ZonedDateTime.now();
        }
    }

    public void restore() {
        deletedAt = null;
    }
}
