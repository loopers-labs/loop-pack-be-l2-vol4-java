package com.loopers.application.outbox;

import com.loopers.infrastructure.outbox.OutboxEntity;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.infrastructure.outbox.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커밋 직후(AFTER_COMMIT) outbox 메시지를 즉시 Kafka로 발행하는 <b>저지연 경로</b>(하이브리드 Outbox).
 *
 * <p><b>왜 하이브리드인가</b>: BEFORE_COMMIT append({@link OutboxAppender})로 내구성을 확보하되, 폴링
 * 릴레이({@link OutboxRelay})의 주기 지연을 없애기 위해 커밋 성공 직후 같은 스레드에서 바로 발행한다. 이 즉시
 * 발행은 <b>베스트 에포트</b>이며, 실패하거나(네트워크·브로커) 커밋~발행 사이에 앱이 죽으면 행이 PENDING으로 남아
 * 릴레이가 반드시 재발행한다 → At-Least-Once 안전망은 그대로 유지된다.
 *
 * <p><b>왜 AFTER_COMMIT + REQUIRES_NEW인가</b>: 커밋된 사실만 발행해야 유령 메시지가 없다(그래서
 * BEFORE_COMMIT에서 직접 send 하지 않는다 — 옛 at-most-once 방식을 되살리지 않는다). 커밋은 이미 끝났으므로
 * {@code markSent}는 신규 트랜잭션(REQUIRES_NEW)으로 남긴다(낙관적 락 없음 → markSent는 last-write-wins라
 * 충돌 없음).
 *
 * <p><b>중복 발행은 실제로 일어난다</b>: 아래 PENDING 확인과 릴레이의 조회는 같은 행을 잠그지 않는 read-then-act
 * 라, 커밋 직후 릴레이 폴링 주기가 겹치면 둘 다 PENDING을 보고 각자 발행한다. week9 E2E에서 13건 중 1건이 이렇게
 * 두 번 실렸다(같은 eventId 레코드 2개). 흡수는 <b>오직 소비자 멱등(event_handled)</b>이 한다 — 카프카의
 * {@code enable.idempotence}는 <b>한 프로듀서 세션 내 재시도</b>만 중복 제거하므로(PID+시퀀스 기준), 서로 다른
 * 트랜잭션에서 나간 별개의 send() 두 번은 서로 다른 레코드로 그대로 적재된다. 따라서 이 토픽을 구독하는 컨슈머는
 * 반드시 event_handled 로 멱등을 보장해야 한다(중복을 감수하겠다면 그 근거를 브로커 멱등에서 찾으면 안 된다).
 *
 * <p><b>왜 TransactionTemplate인가</b>: afterCommit 콜백에서 {@code @Transactional} 메서드를 자기호출하면
 * 프록시를 우회해 신규 트랜잭션이 생기지 않는다. 프로그래매틱 트랜잭션으로 이 함정을 피한다.
 *
 * <p>test 프로파일에서는 비활성({@code @Profile("!test")}) — 릴레이와 동일하게, 통합 테스트가 PENDING 상태를
 * 결정적으로 관찰하도록 둔다. {@code outbox.immediate.enabled=false}로도 끌 수 있다(순수 폴링으로 복귀).
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(name = "outbox.immediate.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxImmediatePublisher {

    private final OutboxJpaRepository outboxJpaRepository;
    private final OutboxMessagePublisher messagePublisher;
    private final TransactionTemplate requiresNew;

    public OutboxImmediatePublisher(OutboxJpaRepository outboxJpaRepository,
                                    OutboxMessagePublisher messagePublisher,
                                    PlatformTransactionManager transactionManager) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.messagePublisher = messagePublisher;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 현재 트랜잭션이 커밋되면 해당 outbox 행을 즉시 발행하도록 예약한다. 트랜잭션이 없으면(방어적) 조용히 무시한다
     * — append는 {@code MANDATORY}라 정상 경로에선 항상 트랜잭션 안이다. 등록된 콜백은 커밋 성공 시에만 실행된다.
     */
    public void scheduleAfterCommit(long outboxId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishNow(outboxId);
            }
        });
    }

    /**
     * 커밋 이후 호출된다. 신규 트랜잭션에서 행을 다시 읽어 아직 PENDING이면 발행하고 SENT로 마킹한다. 발행/트랜잭션
     * 실패 시 아무것도 바꾸지 않아 행이 PENDING으로 남고(릴레이가 재발행), 예외는 afterCommit 콜백 밖으로 전파하지
     * 않는다(이미 커밋된 주요 트랜잭션에 영향 없어야 한다).
     */
    void publishNow(long outboxId) {
        try {
            requiresNew.executeWithoutResult(status -> {
                OutboxEntity msg = outboxJpaRepository.findById(outboxId).orElse(null);
                if (msg == null || msg.getStatus() != OutboxStatus.PENDING) {
                    return; // 릴레이가 이미 보냈거나 행이 없음 — 재발행하지 않는다
                }
                try {
                    messagePublisher.publish(msg);
                    msg.markSent();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("outbox 즉시 발행 인터럽트(eventId={}) — PENDING 유지, 릴레이가 재발행", outboxId);
                } catch (Exception e) {
                    // 발행 실패 → markSent 생략(PENDING 유지). 변경이 없으므로 신규 트랜잭션은 그대로 커밋된다.
                    log.warn("outbox 즉시 발행 실패(eventId={}, topic={}): {} — PENDING 유지, 릴레이가 재발행",
                            outboxId, msg.getTopic(), e.getMessage());
                }
            });
        } catch (Exception e) {
            // 신규 트랜잭션 획득/커밋 자체의 실패 — afterCommit 밖으로 전파 금지
            log.warn("outbox 즉시 발행 트랜잭션 예외(eventId={}): {} — PENDING 유지, 릴레이가 재발행", outboxId, e.getMessage());
        }
    }
}
