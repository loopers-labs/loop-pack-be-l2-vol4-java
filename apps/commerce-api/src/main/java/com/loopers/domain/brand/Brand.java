package com.loopers.domain.brand;


import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Brand {

    private final Long id;

    private String name;

    private String description;

    private boolean deleted;


    private Brand(Long id, String name, String description, boolean deleted) {
        validateName(name);
        this.id = id;
        this.name = name;
        this.description = description;
        this.deleted = deleted;
    }

    public static Brand create(String name, String description) {
        return new Brand(null, name, description, false);
    }

    public static Brand restore(Long id, String name, String description) {
        if (id == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID가 비어있을 수 없습니다.");
        }
        return new Brand(id, name, description, false);
    }

    public void modify(String name, String description) {
        validateName(name);
        this.name = name;
        this.description = description;
    }

    public void delete() {
        this.deleted = true;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 명은 비어있을 수 없습니다.");
        }
    }
}
