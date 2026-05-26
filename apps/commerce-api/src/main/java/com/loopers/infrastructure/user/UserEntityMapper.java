package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;

/**
 * UserModel(순수 도메인) ↔ UserEntity(JPA) 변환기. 도메인과 영속 경계 사이의 번역만 담당한다.
 * 게이트 VO(LoginId)는 엔티티에서 primitive(String) 컬럼으로 풀어 저장한다.
 */
public final class UserEntityMapper {

    private UserEntityMapper() {}

    public static UserEntity toEntity(UserModel user) {
        return new UserEntity(
                user.getLoginId().getValue(),
                user.getPassword(),
                user.getName(),
                user.getBirthday(),
                user.getEmail()
        );
    }

    public static UserModel toDomain(UserEntity entity) {
        return UserModel.reconstitute(
                entity.getId(),
                entity.getLoginId(),
                entity.getPassword(),
                entity.getName(),
                entity.getBirthday(),
                entity.getEmail()
        );
    }
}
