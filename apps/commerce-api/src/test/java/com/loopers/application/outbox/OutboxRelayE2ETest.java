package com.loopers.application.outbox;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.application.like.LikeFacade;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class OutboxRelayE2ETest {

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainersConfig::getBootstrapServers);
    }

    @Autowired private LikeFacade likeFacade;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        userJpaRepository.save(new User("tester01", "Password1!", "홍길동", "1990-05-14", "t@e.com", Gender.M));
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        productId = productJpaRepository.save(new Product(brand.getId(), "에어맥스", "운동화", 1000L, 10)).getId();
    }

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("like() 후 relay 가 catalog-events 토픽으로 발행한다.")
    @Test
    void likePublishesToCatalogEvents() {
        likeFacade.like("tester01", productId);

        AtomicReference<String> value = new AtomicReference<>();
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of("catalog-events"));
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofMillis(500));
                records.forEach(r -> value.set(r.value()));
                assertThat(value.get()).isNotNull();
            });
        }
        assertThat(value.get()).contains("\"type\":\"LikeAdded\"").contains("\"productId\":" + productId);
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainersConfig.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "relay-e2e-verify");
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(p);
    }
}
