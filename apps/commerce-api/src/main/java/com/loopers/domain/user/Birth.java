package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Embeddable
public class Birth {

    @Column(name = "birth", nullable = false)
    private String value;

    protected Birth() {}

    public Birth(String value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
