package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    /** 저장 + 즉시 flush — unique 충돌을 호출 지점에서 DataIntegrityViolationException으로 감지 */
    UserModel saveAndFlush(UserModel user);
    Optional<UserModel> findByLoginId(String loginId);
}
