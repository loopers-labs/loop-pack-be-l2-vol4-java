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
public class BirthDay {

    private static final Pattern BIRTHDAY_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Column(name = "birth_day", nullable = false, length = 10)
    private String value;

    public BirthDay(String value) {
        Guard.notBlank(value, "생년월일은 빈값이 들어올 수 없습니다.");
        Guard.matches(value, BIRTHDAY_PATTERN, "생년월일은 yyyy-MM-dd 형식에 맞춰 주세요.");
        this.value = value;
    }
}
