package com.loopers.auth;

import com.loopers.exception.ApiException;
import com.loopers.vo.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmailVoTest {
    @Test @DisplayName("정상 이메일") void email_valid() { assertThat(new Email("test@test.com").getValue()).isEqualTo("test@test.com"); }
    @Test @DisplayName("@ 없음 → 에러") void email_missingAt() { assertThatThrownBy(() -> new Email("testtest.com")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("도메인 없음 → 에러") void email_missingDomain() { assertThatThrownBy(() -> new Email("test@")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("빈 값 → 에러") void email_blank() { assertThatThrownBy(() -> new Email("")).isInstanceOf(ApiException.class); }
}
