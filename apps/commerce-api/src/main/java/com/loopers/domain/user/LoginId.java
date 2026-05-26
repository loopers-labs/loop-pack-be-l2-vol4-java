package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

@Embeddable
public class LoginId {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    @Column(name = "login_id", nullable = false)
    private String value;

    protected LoginId() {}

    public LoginId(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
