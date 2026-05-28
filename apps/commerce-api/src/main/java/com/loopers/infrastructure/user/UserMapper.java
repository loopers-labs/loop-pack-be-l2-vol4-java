package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;

public class UserMapper {

    public static UserJpaEntity toJpaEntity(UserModel model) {
        return new UserJpaEntity(
                model.getId(),
                model.getUserId(),
                model.getName(),
                model.getEmail(),
                model.getPassword(),
                model.getBirthDate()
        );
    }

    public static UserModel toDomain(UserJpaEntity entity) {
        return UserModel.of(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getBirthDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
