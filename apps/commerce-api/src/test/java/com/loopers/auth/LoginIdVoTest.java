package com.loopers.auth;

import com.loopers.exception.ApiException;
import com.loopers.vo.LoginId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LoginIdVoTest {
    @Test @DisplayName("정상") void loginId_valid() { assertThat(new LoginId("testuser").getValue()).isEqualTo("testuser"); }
    @Test @DisplayName("빈 값 → 에러") void loginId_blank() { assertThatThrownBy(() -> new LoginId("")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("공백만 → 에러") void loginId_whitespaceOnly() { assertThatThrownBy(() -> new LoginId("   ")).isInstanceOf(ApiException.class); }
}
