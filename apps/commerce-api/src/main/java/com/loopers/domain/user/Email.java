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
public class Email {

    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final int MAX_LENGTH = 254;

    @Column(name = "email", nullable = false, length = MAX_LENGTH)
    private String value;

    protected Email() {}

    public Email(String value) {
        if (value == null || value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
        this.value = value;
    }
}
