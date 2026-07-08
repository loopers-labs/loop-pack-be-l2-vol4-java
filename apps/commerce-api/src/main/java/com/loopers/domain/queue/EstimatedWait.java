package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 예상 대기시간 값객체. {@code 순번 / 초당 처리량} 을 분 단위로 환산해 표현한다. (결정 #9)
 * 분모·분자 모두 시변(moving target)이라 정확한 초는 false precision → **올림하여 under-promise**("약 N분").
 * 순번 0 은 곧 입장 차례이므로 "곧 주문 가능"으로 표현한다.
 */
public record EstimatedWait(long minutes) {

    private static final int SECONDS_PER_MINUTE = 60;

    public EstimatedWait {
        if (minutes < 0) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "예상 대기 시간은 음수일 수 없습니다: " + minutes);
        }
    }

    /**
     * 순번과 초당 처리량으로 예상 대기(분)를 산정한다.
     * 1분 미만의 대기는 올림해 최소 "약 1분"이 되도록 하여 "약 0분" 같은 신뢰 훼손을 피한다(순번 0 은 예외 = 곧 입장).
     */
    public static EstimatedWait of(long position, double throughputPerSecond) {
        if (throughputPerSecond <= 0) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "처리량은 0보다 커야 합니다: " + throughputPerSecond);
        }
        if (position < 0) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "순번은 음수일 수 없습니다: " + position);
        }
        double seconds = position / throughputPerSecond;
        long minutes = (long) Math.ceil(seconds / SECONDS_PER_MINUTE);
        return new EstimatedWait(minutes);
    }

    /** 곧 입장 차례(순번 0). */
    public boolean imminent() {
        return minutes == 0;
    }

    public String description() {
        return imminent() ? "곧 주문 가능" : "약 " + minutes + "분";
    }
}
