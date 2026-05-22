package com.loopers.auth;

import com.loopers.exception.ApiException;
import com.loopers.vo.UserName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserNameVoTest {
    @Test @DisplayName("한글 2자 → 정상") void korean() { assertThat(new UserName("홍길").getValue()).isEqualTo("홍길"); }
    @Test @DisplayName("영문 2자 → 정상") void english() { assertThat(new UserName("ab").getValue()).isEqualTo("ab"); }
    @Test @DisplayName("1자 → 에러") void tooShort() { assertThatThrownBy(() -> new UserName("홍")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("숫자 포함 → 에러") void containsNumber() { assertThatThrownBy(() -> new UserName("홍길1")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("특수문자 포함 → 에러") void containsSpecial() { assertThatThrownBy(() -> new UserName("홍길!")).isInstanceOf(ApiException.class); }
    @Test @DisplayName("빈 값 → 에러") void blank() { assertThatThrownBy(() -> new UserName("")).isInstanceOf(ApiException.class); }
}
