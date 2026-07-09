package com.loopers.application.outbox;

import com.loopers.infrastructure.outbox.OutboxEntity;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.infrastructure.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transactional Outbox 릴레이. {@code status=PENDING} 메시지를 폴링해 Kafka로 발행하고 {@code SENT}로 마킹한다.
 *
 * <p><b>At Least Once</b>: Kafka 전송이 ack된 메시지만 SENT로 바꾼다. 전송 실패/예외 시 PENDING으로 남아
 * 다음 주기에 재시도된다(유실 0, 중복 가능 → 소비자 멱등으로 흡수). 같은 파티션 키의 순서를 위해 id ASC로 보낸다.
 *
 * <p>여러 인스턴스가 동시에 폴링해 같은 메시지를 중복 발행하지 않도록 {@link SchedulerLock}으로 한 인스턴스만
 * 실행한다(week6 결제 reconcile과 동일 패턴, 락 상태는 shedlock 테이블 공유). 통합 테스트(profile {@code test})에서는
 * 스케줄러를 제외하고, 테스트가 직접 {@link #relayOnce(int)}를 호출해 결정적으로 검증한다.
 *
 * <p>하이브리드 발행에서 릴레이는 <b>안전망(fallback)</b> 역할을 겸한다. 커밋 직후 즉시 발행
 * ({@link OutboxImmediatePublisher})이 실패했거나 커밋~발행 사이에 앱이 죽어 PENDING으로 남은 행을
 * 다음 폴링에서 반드시 재발행한다. 실제 전송은 공통 {@link OutboxMessagePublisher}에 위임한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxJpaRepository outboxJpaRepository;
    private final OutboxMessagePublisher messagePublisher;

    @Value("${outbox.relay.batch-size:200}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${outbox.relay.fixed-delay:1000}",
            initialDelayString = "${outbox.relay.initial-delay:5000}")
    @SchedulerLock(
            name = "outboxRelay",
            lockAtMostFor = "${outbox.relay.lock-at-most-for:PT30S}",
            lockAtLeastFor = "${outbox.relay.lock-at-least-for:PT1S}")
    public void poll() {
        LockAssert.assertLocked();
        int sent = relayOnce(batchSize);
        if (sent > 0) {
            log.info("outbox 릴레이 발행: sent={}", sent);
        }
    }

    /**
     * PENDING 한 배치를 발행한다. 테스트에서 직접 호출 가능하도록 분리(스케줄/락과 독립).
     * 각 메시지는 send().get()으로 ack를 확인한 뒤에만 SENT로 마킹한다.
     *
     * @return 이번 호출에서 SENT로 마킹한 메시지 수
     */
    @Transactional
    public int relayOnce(int limit) {
        List<OutboxEntity> pending =
                outboxJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, PageRequest.of(0, limit));
        int sent = 0;
        for (OutboxEntity msg : pending) {
            try {
                messagePublisher.publish(msg);
                msg.markSent();
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("outbox 릴레이 인터럽트(eventId={}) — 중단", msg.getId());
                break;
            } catch (Exception e) {
                // 전송 실패 → PENDING 유지(다음 주기 재시도). 순서 보존을 위해 이번 배치는 여기서 중단한다.
                log.warn("outbox 발행 실패(eventId={}, topic={}): {} — PENDING 유지 후 재시도",
                        msg.getId(), msg.getTopic(), e.getMessage());
                break;
            }
        }
        return sent;
    }
}
