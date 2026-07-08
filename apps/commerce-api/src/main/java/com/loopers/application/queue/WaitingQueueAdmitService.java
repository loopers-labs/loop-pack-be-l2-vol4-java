package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class WaitingQueueAdmitService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final WaitingQueueProperties properties;

    public int admit() {
        List<String> userLoginIds = waitingQueueRepository.popWaitingUsers(properties.batchSize());
        for (String userLoginId : userLoginIds) {
            waitingQueueRepository.issueEntryToken(userLoginId, UUID.randomUUID().toString(), properties.ttl());
        }
        return userLoginIds.size();
    }
}
