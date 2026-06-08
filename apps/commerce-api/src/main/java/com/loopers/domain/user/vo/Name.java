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
public class Name {

    @Column(name = "name", nullable = false, length = 50)
    private String value;

    public Name(String value) {
        Guard.notBlank(value, "이름은 비어있을 수 없습니다.");
        Guard.minLength(value, 2, "이름은 2글자 이상으로 작성해 주세요.");
        Guard.maxLength(value, 50, "이름은 50자를 초과할 수 없습니다.");
        this.value = value;
    }
}
