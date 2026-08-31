package com.loopers.interfaces.api;
import com.loopers.domain.order.Order;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) class OrderDiscountE2ETest {
 @Autowired TestRestTemplate http; @Autowired OrderJpaRepository jpa; @Autowired DatabaseCleanUp cleanup;
 @AfterEach void clean(){cleanup.truncateAllTables();}
 @Test void appliesAndRetriesThroughRealEntryPoint(){Order o=jpa.save(new Order(135135,10000));HttpHeaders h=new HttpHeaders();h.set("X-USER-ID","135135");h.setContentType(MediaType.APPLICATION_JSON);HttpEntity<Map<String,Long>> req=new HttpEntity<>(Map.of("couponId",10L),h);String url="/api/v1/orders/"+o.getId()+"/discount";ResponseEntity<String> first=http.postForEntity(url,req,String.class);ResponseEntity<String> retry=http.postForEntity(url,req,String.class);assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);assertThat(retry.getBody()).isEqualTo(first.getBody());assertThat(first.getBody()).contains("\"discountAmount\":1000","\"finalAmount\":9000");}
}
