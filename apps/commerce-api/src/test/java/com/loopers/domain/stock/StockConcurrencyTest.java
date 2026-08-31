package com.loopers.domain.stock;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest class StockConcurrencyTest {
 @Autowired StockService service; @Autowired StockJpaRepository jpa; @Autowired DatabaseCleanUp cleanup;
 @AfterEach void clean(){cleanup.truncateAllTables();}
 @Test void reconcilesEightRequests(){reconcile(8);}
 @Test void reconcilesTwelveRequests(){reconcile(12);}
 private void reconcile(int requests){StockModel stock=jpa.save(new StockModel(77,5));CountDownLatch ready=new CountDownLatch(requests),start=new CountDownLatch(1),done=new CountDownLatch(requests);AtomicInteger success=new AtomicInteger(),failure=new AtomicInteger();try(ExecutorService pool=Executors.newFixedThreadPool(requests)){for(int i=0;i<requests;i++)pool.submit(()->{ready.countDown();try{start.await();service.decrease(stock.getId(),1);success.incrementAndGet();}catch(Exception e){failure.incrementAndGet();}finally{done.countDown();}});try{assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();assertThat(done.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new AssertionError(e);}}int remaining=jpa.findById(stock.getId()).orElseThrow().getQuantity();assertThat(success.get()+remaining).isEqualTo(5);assertThat(success.get()+failure.get()).isEqualTo(requests);}
}
