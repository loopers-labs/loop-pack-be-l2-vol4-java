package com.loopers.domain.like;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "like_outbox")
public class LikeOutboxModel extends BaseEntity {

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LikeEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    protected LikeOutboxModel() {
    }

    public LikeOutboxModel(Long productId, LikeEventType eventType) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 필수입니다.");
        }
        this.productId = productId;
        this.eventType = eventType;
        this.status = OutboxStatus.PENDING;
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
    }
}
