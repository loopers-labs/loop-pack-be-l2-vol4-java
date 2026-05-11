package com.loopers.domain.example;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "example")
public class ExampleModel extends BaseEntity {

    private String name;
    private String description;

    @Builder
    public ExampleModel(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if (description == null || description.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "설명은 비어있을 수 없습니다.");
        }

        this.name = name;
        this.description = description;
    }

    public void update(String newDescription) {
        if (newDescription == null || newDescription.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "설명은 비어있을 수 없습니다.");
        }
        this.description = newDescription;
    }
}
