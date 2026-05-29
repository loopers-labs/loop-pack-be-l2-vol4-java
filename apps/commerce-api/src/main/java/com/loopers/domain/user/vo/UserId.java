package com.loopers.domain.user.vo;

import com.loopers.support.Guard;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserId {

    private static final Pattern USERID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9]+$");

    @Column(name = "userid", nullable = false, length = 16)
    private String value;

    public UserId(String value) {
        Guard.notBlank(value, "아이디는 빈값이 들어올 수 없습니다.");
        Guard.maxLength(value, 16, "아이디는 16자를 초과할 수 없습니다.");
        Guard.matches(value, USERID_PATTERN, "아이디는 영문과 숫자만 허용됩니다.");
        this.value = value;
    }
}
