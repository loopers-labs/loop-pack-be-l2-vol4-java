package com.loopers.application.like;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.LikeEventType;
import com.loopers.domain.outbox.OutboxEventHandler;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

// 릴레이(③)가 like 이벤트 outbox를 위임받아 처리하는 핸들러.
// payload를 역직렬화해 원본 이벤트를 복원한 뒤 공유 반영 로직(LikeCountReflector)에 위임한다.
@RequiredArgsConstructor
@Component
public class LikeEventHandler implements OutboxEventHandler {

    private static final Set<String> SUPPORTED = Set.of(
            LikeEventType.LIKED_EVENT.name(),
            LikeEventType.UNLIKED_EVENT.name());

    private final LikeCountReflector likeCountReflector;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED.contains(eventType);
    }

    @Override
    public void handle(OutboxModel outbox) {
        LikeEventPayload payload = deserialize(outbox.getPayload());
        likeCountReflector.reflect(outbox.getEventId(), payload.productId(), LikeEventType.valueOf(outbox.getEventType()));
    }

    private LikeEventPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, LikeEventPayload.class);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "like 이벤트 payload 역직렬화에 실패했습니다.");
        }
    }

    // LikedEvent / UnlikedEvent 가 공통으로 갖는 필드 구조.
    private record LikeEventPayload(String eventId, Long productId) {
    }
}
