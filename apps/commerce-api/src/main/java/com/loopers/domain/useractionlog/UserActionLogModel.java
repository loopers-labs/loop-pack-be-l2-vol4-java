package com.loopers.domain.useractionlog;

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
@Table(name = "user_action_logs")
public class UserActionLogModel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 30)
    private UserActionType actionType;

    // 행동의 대상 엔티티 ID (예: orderId, paymentId)
    @Column(name = "reference_id", nullable = false, updatable = false)
    private Long referenceId;

    @Column(name = "detail", updatable = false, length = 200)
    private String detail;

    protected UserActionLogModel() {}

    public UserActionLogModel(Long userId, UserActionType actionType, Long referenceId, String detail) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (actionType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "행동 유형은 필수입니다.");
        }
        if (referenceId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "참조 ID는 필수입니다.");
        }
        this.userId = userId;
        this.actionType = actionType;
        this.referenceId = referenceId;
        this.detail = detail;
    }
}
