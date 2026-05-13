package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginId {

    private static final Pattern ALPHANUMERIC = Pattern.compile("^[a-zA-Z0-9]+$");

    @Column(name = "login_id", nullable = false)
    private String value;

    public LoginId(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.");
        }
        if (!ALPHANUMERIC.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용할 수 있습니다.");
        }
        this.value = value;
    }
}
