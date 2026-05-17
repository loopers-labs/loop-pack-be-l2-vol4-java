package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@Embeddable
@EqualsAndHashCode
public class LoginId {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9]{8,16}$");

    @Column(name = "login_id", nullable = false, unique = true, length = 16)
    private String value;

    protected LoginId() {}

    public LoginId(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자로 구성된 8~16자여야 합니다.");
        }
        this.value = value;
    }
}
