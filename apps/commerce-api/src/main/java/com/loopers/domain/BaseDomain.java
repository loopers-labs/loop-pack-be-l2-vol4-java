package com.loopers.domain;

import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public abstract class BaseDomain {

    protected Long id;
    protected ZonedDateTime createdAt;
    protected ZonedDateTime updatedAt;
    protected ZonedDateTime deletedAt;

    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
    }
}
