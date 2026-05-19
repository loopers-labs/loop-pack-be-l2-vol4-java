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
public class UserName {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z가-힣]{1,20}$");

    @Column(name = "name", nullable = false, length = 20)
    private String value;

    protected UserName() {}

    public UserName(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글/영문으로 구성된 1~20자여야 합니다.");
        }
        this.value = value;
    }

    public String masked() {
        if (value.length() == 1) {
            return "*";
        }
        return value.substring(0, value.length() - 1) + "*";
    }
}
