package com.loopers.infrastructure.useractionlog;

import com.loopers.domain.useractionlog.UserActionLogModel;
import com.loopers.domain.useractionlog.UserActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserActionLogRepositoryImpl implements UserActionLogRepository {

    private final UserActionLogJpaRepository userActionLogJpaRepository;

    @Override
    public UserActionLogModel save(UserActionLogModel userActionLog) {
        return userActionLogJpaRepository.save(userActionLog);
    }
}
