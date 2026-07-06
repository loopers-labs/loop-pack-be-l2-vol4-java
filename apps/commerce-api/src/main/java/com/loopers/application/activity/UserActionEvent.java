package com.loopers.application.activity;

/**
 * 유저 행동 이벤트 — 서버 레벨 행동 로깅(횡단 관심사)용.
 *
 * <p>좋아요/취소/주문 등 <strong>상태를 바꾸는</strong> 유저 행동을 각 유스케이스가 발행하고,
 * {@link UserActionLogListener}가 커밋 이후({@code AFTER_COMMIT}) 로깅한다 —
 * 성공적으로 커밋된 행동만 기록되며(롤백 시 미기록), 로깅 실패가 본 흐름에 영향을 주지 않는다.
 *
 * <p>조회(VIEW)는 트랜잭션이 없는 읽기라 별도 {@link ProductViewedEvent} + 즉시 리스너로 처리한다.
 */
public record UserActionEvent(Long userId, ActionType actionType, Long targetId) {

    public enum ActionType { LIKE, UNLIKE, ORDER }

    public static UserActionEvent like(Long userId, Long productId) {
        return new UserActionEvent(userId, ActionType.LIKE, productId);
    }

    public static UserActionEvent unlike(Long userId, Long productId) {
        return new UserActionEvent(userId, ActionType.UNLIKE, productId);
    }

    public static UserActionEvent order(Long userId, Long orderId) {
        return new UserActionEvent(userId, ActionType.ORDER, orderId);
    }
}
