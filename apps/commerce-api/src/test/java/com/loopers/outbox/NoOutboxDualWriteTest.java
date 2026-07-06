package com.loopers.outbox;

import com.loopers.outbox.application.OutboxMessagePublisher;
import com.loopers.outbox.domain.OutboxEvent;
import com.loopers.outbox.infrastructure.OutboxEventJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Outbox 없이 Kafka 만 직접 발행하면?" 의 실패 모드 실증.
 *
 * DB write 와 메시지 발행은 서로 다른 자원이라 한 트랜잭션으로 묶이지 않는다(dual-write).
 * 발행 시점을 커밋 전/후 어디에 두든 한쪽 불일치가 남는다 — 이를 두 케이스로 재현한다.
 * (여기서 OutboxEvent 행은 "주문 같은 도메인 row" 의 대역일 뿐, outbox 메커니즘을 쓰는 게 아니다)
 */
@SpringBootTest
class NoOutboxDualWriteTest {

    /** 발행 사실만 기록하는 fake. 실제 Kafka 없이 "메시지가 나갔는지" 만 본다. */
    static class RecordingPublisher implements OutboxMessagePublisher {
        final List<String> published = new ArrayList<>();

        @Override
        public void publish(String topic, String key, String payload) {
            published.add(payload);
        }
    }

    /** 항상 실패하는 fake. 브로커 다운 상황을 흉내낸다. */
    static class FailingPublisher implements OutboxMessagePublisher {
        final List<String> published = new ArrayList<>();

        @Override
        public void publish(String topic, String key, String payload) {
            throw new IllegalStateException("kafka down");
        }
    }

    private final PlatformTransactionManager transactionManager;
    private final OutboxEventJpaRepository rowRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    NoOutboxDualWriteTest(PlatformTransactionManager transactionManager,
                          OutboxEventJpaRepository rowRepository,
                          DatabaseCleanUp databaseCleanUp) {
        this.transactionManager = transactionManager;
        this.rowRepository = rowRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OutboxEvent row() {
        return OutboxEvent.pending("order-events", "1", "OrderCreated", "{\"orderId\":1}");
    }

    @Test
    @DisplayName("트랜잭션 안에서 발행 후 롤백되면 — DB엔 주문이 없는데 메시지는 이미 나간다(유령 이벤트)")
    void givenPublishInsideTx_whenRollback_thenGhostEvent() {
        RecordingPublisher publisher = new RecordingPublisher();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            rowRepository.save(row());                                 // 주문 저장(아직 미커밋)
            publisher.publish("order-events", "1", "{\"orderId\":1}"); // 같은 트랜잭션 안에서 발행
            throw new IllegalStateException("결제 검증 실패 → 롤백");      // 이후 단계 실패로 롤백
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowRepository.count()).isZero();        // DB 는 롤백되어 주문이 없다
        assertThat(publisher.published).hasSize(1);        // 그러나 메시지는 이미 브로커로 나갔다 → 유령 이벤트
    }

    @Test
    @DisplayName("커밋 성공 후 발행이 실패하면 — 주문은 저장됐는데 이벤트는 영영 유실된다(되돌릴 수 없음)")
    void givenPublishAfterCommit_whenPublishFails_thenEventLost() {
        FailingPublisher publisher = new FailingPublisher();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> rowRepository.save(row()));  // 주문 커밋 성공

        // 커밋 이후 발행 시도가 실패한다. 커밋은 이미 끝나 되돌릴 수 없다.
        assertThatThrownBy(() -> publisher.publish("order-events", "1", "{\"orderId\":1}"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rowRepository.count()).isEqualTo(1);    // 주문은 남아있다
        assertThat(publisher.published).isEmpty();         // 이벤트는 유실 — 재시도 장치가 없으면 영구 불일치
    }
}
