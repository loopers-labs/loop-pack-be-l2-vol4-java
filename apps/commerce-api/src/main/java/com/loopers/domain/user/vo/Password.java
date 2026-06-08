package com.loopers.domain.user.vo;

import com.loopers.support.Guard;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Password {

    @Column(name = "password", nullable = false)
    private String value;

    public Password(String encodedValue) {
        Guard.notBlank(encodedValue, "비밀번호는 빈값이 들어올 수 없습니다.");
        this.value = encodedValue;
    }
}
