package com.loopers.tddstudy.interfaces.api.queue;

import com.loopers.tddstudy.application.queue.QueueStatus;
import com.loopers.tddstudy.application.queue.WaitingQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/queue")
public class QueueV1Controller {

    private final WaitingQueueService waitingQueueService;

    public QueueV1Controller(WaitingQueueService waitingQueueService) {
        this.waitingQueueService = waitingQueueService;
    }

    @PostMapping("/enter")
    public ResponseEntity<QueueV1Dto.StatusResponse> enter(@RequestHeader("X-USER-ID") Long userId) {
        QueueStatus s = waitingQueueService.enter(userId);
        return ResponseEntity.ok(QueueV1Dto.StatusResponse.from(s));
    }

    @GetMapping("/position")
    public ResponseEntity<QueueV1Dto.StatusResponse> position(@RequestHeader("X-USER-ID") Long userId) {
        QueueStatus s = waitingQueueService.position(userId);
        return ResponseEntity.ok(QueueV1Dto.StatusResponse.from(s));
    }
}
