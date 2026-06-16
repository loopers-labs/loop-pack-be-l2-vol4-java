package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserEntity;

public class UserMapper {

    public static UserJpaEntity toJpaEntity(UserEntity model) {
        return new UserJpaEntity(
                model.getId(),
                model.getUserId(),
                model.getName(),
                model.getEmail(),
                model.getPassword(),
                model.getBirthDate()
        );
    }

    public static UserEntity toDomain(UserJpaEntity entity) {
        return UserEntity.of(
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
