package com.loopers.domain.product.vo;

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
public class ProductName {

    @Column(name = "name", nullable = false, length = 200)
    private String value;

    public ProductName(String value) {
        Guard.notBlank(value, "상품명은 비어있을 수 없습니다.");
        Guard.minLength(value, 2, "상품명은 2글자 이상이어야 합니다.");
        this.value = value;
    }
}