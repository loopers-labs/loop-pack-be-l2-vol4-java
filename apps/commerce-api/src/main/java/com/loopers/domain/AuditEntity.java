package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * 생성/수정/삭제 시각만 관리하는 감사 전용 매핑 superclass. {@link BaseEntity}와 달리 <b>id를 보유하지 않아</b>,
 * 식별자 생성 전략을 엔티티가 직접 정할 수 있다(예: 앱 생성 TSID를 @Id로 직접 부여).
 * <p>
 * BaseEntity는 그대로 두고(다른 도메인은 auto-increment 유지), 식별자 전략이 다른 엔티티만 이쪽을 상속한다.
 */
@MappedSuperclass
@Getter
public abstract class AuditEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "deleted_at")
    private ZonedDateTime deletedAt;

    /** 엔티티 유효성 검증 훅. PrePersist/PreUpdate 시점에 호출된다. */
    protected void guard() {}

    @PrePersist
    private void prePersist() {
        guard();
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        guard();
        this.updatedAt = ZonedDateTime.now();
    }

    /** delete는 멱등 — 이미 삭제된 엔티티를 다시 삭제해도 동일 결과. */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /** restore도 멱등 — 삭제되지 않은 엔티티를 복원해도 동일 결과. */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
    }
}
