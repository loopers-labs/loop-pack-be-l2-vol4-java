package com.loopers.domain.order;
import com.loopers.application.order.*;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest class OrderDiscountPersistenceIntegrationTest {
 @Autowired OrderDiscountFacade facade; @Autowired OrderJpaRepository jpa; @Autowired EntityManager em; @Autowired DatabaseCleanUp cleanup;
 @AfterEach void clean(){cleanup.truncateAllTables();}
 @Test void preservesSnapshotAfterClearAndReload(){Order o=jpa.save(new Order(135135,10000));facade.apply(o.getId(),135135,10,Instant.parse("2026-08-31T23:59:59Z"));Order confirmed=jpa.findById(o.getId()).orElseThrow();confirmed.confirm();jpa.saveAndFlush(confirmed);em.clear();Order loaded=jpa.findById(o.getId()).orElseThrow();assertAll(()->assertThat(loaded.getDiscountAmount()).isEqualTo(1000),()->assertThat(loaded.getFinalAmount()).isEqualTo(9000),()->assertThat(loaded.isConfirmed()).isTrue());}
 @Test void rejectsOwnerAndExpiryBoundary(){Order o=jpa.save(new Order(135135,10000));assertThrows(RuntimeException.class,()->facade.apply(o.getId(),1,10,Instant.parse("2026-08-31T23:59:59Z")));assertThrows(RuntimeException.class,()->facade.apply(o.getId(),135135,10,Instant.parse("2026-09-01T00:00:00Z")));}
}
