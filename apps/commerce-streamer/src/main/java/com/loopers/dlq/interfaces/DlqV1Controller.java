package com.loopers.dlq.interfaces;

import com.loopers.dlq.application.DlqService;
import com.loopers.dlq.domain.DlqMessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DLQ 운영 API. 담당자가 격리 메시지를 조회하고(어떤 토픽·에러) 재처리/폐기한다.
 */
@RestController
@RequestMapping("/api/v1/dlq")
@RequiredArgsConstructor
public class DlqV1Controller {

    private final DlqService dlqService;

    @GetMapping
    public Page<DlqV1Dto.Summary> list(
            @RequestParam(name = "topic", required = false) String topic,
            @RequestParam(name = "status", required = false) DlqMessageStatus status,
            Pageable pageable
    ) {
        return dlqService.list(topic, status, pageable).map(DlqV1Dto.Summary::from);
    }

    @GetMapping("/{id}")
    public DlqV1Dto.Detail get(@PathVariable("id") Long id) {
        return DlqV1Dto.Detail.from(dlqService.get(id));
    }

    @PostMapping("/{id}/retry")
    public void retry(@PathVariable("id") Long id) {
        dlqService.retry(id);
    }

    @PostMapping("/{id}/discard")
    public void discard(@PathVariable("id") Long id) {
        dlqService.discard(id);
    }
}
