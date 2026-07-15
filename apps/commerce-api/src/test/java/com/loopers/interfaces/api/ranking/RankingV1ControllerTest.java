package com.loopers.interfaces.api.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 날짜 파라미터 파싱 검증 — 잘못된 형식은 500 이 아닌 400(BAD_REQUEST) 이어야 한다.
 * 파싱은 서비스 호출 전에 실패하므로 서비스 없이(null) 순수 단위로 검증한다.
 */
class RankingV1ControllerTest {

    private final RankingV1Controller controller = new RankingV1Controller(null);

    @DisplayName("잘못된 date 형식은 BAD_REQUEST CoreException 을 던진다")
    @Test
    void invalidDateThrowsBadRequest() {
        assertThatThrownBy(() -> controller.getRankings("2026-07-15", 20, 1))
            .isInstanceOf(CoreException.class)
            .extracting(e -> ((CoreException) e).getErrorType())
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("잘못된 dateHour 형식은 BAD_REQUEST CoreException 을 던진다")
    @Test
    void invalidDateHourThrowsBadRequest() {
        assertThatThrownBy(() -> controller.getHourlyRankings("20260715", 20, 1))   // 시(HH) 누락
            .isInstanceOf(CoreException.class)
            .extracting(e -> ((CoreException) e).getErrorType())
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
