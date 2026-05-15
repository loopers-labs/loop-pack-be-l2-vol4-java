package com.loopers.auth;

import com.loopers.util.PasswordRotateUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordRotateUtilTest {
    @Test @DisplayName("각 숫자 d → d+1 % 10") void rotateDigits_basic() { assertThat(PasswordRotateUtil.rotateDigits("Agent1234!")).isEqualTo("Agent2345!"); }
    @Test @DisplayName("9는 0으로 변환") void rotateDigits_nineBecomesZero() { assertThat(PasswordRotateUtil.rotateDigits("Pass9!")).isEqualTo("Pass0!"); }
    @Test @DisplayName("숫자 없으면 그대로") void rotateDigits_noDigits() { assertThat(PasswordRotateUtil.rotateDigits("AgentPass!")).isEqualTo("AgentPass!"); }
}
