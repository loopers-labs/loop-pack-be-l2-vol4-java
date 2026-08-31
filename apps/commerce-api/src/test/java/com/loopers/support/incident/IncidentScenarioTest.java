package com.loopers.support.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentIntent;
import com.loopers.utils.DatabaseCleanUp;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "management.server.port=0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.prometheus.access=read-only",
        "management.prometheus.metrics.export.enabled=true",
        "looppak.incident=checkout-latency-a"
    }
)
@Import(IncidentScenarioTest.Config.class)
class IncidentScenarioTest {
    @Autowired private PaymentFacade payments;
    @Autowired private CheckoutLatencyAIncidentFixture pg;
    @Autowired private DatabaseCleanUp cleanup;
    @LocalManagementPort private int managementPort;
    private final RestTemplate http = new RestTemplate();

    @AfterEach
    void clean() {
        cleanup.truncateAllTables();
        pg.reset();
    }

    @Test
    void baseline_is_10_of_10() throws Exception {
        Snapshot snapshot = run(false);
        assertThat(snapshot.healthUp()).isEqualTo(10);
        assertThat(snapshot.terminal()).isEqualTo(10);
        assertThat(snapshot.unknown()).isZero();
        assertThat(snapshot.providerEffects()).isEqualTo(10);
        writeRaw("baseline", snapshot);
    }

    @Test
    void fault_keeps_health_up_but_breaks_business_sli() throws Exception {
        Snapshot snapshot = run(true);
        assertThat(snapshot.healthUp()).isEqualTo(10);
        assertThat(snapshot.terminal()).isEqualTo(7);
        assertThat(snapshot.unknown()).isEqualTo(3);
        assertThat(snapshot.providerEffects()).isEqualTo(10);
        writeRaw("fault", snapshot);
    }

    @Test
    void recovery_reconciles_10_of_10() throws Exception {
        Snapshot fault = run(true);
        int dispatchesBeforeRecovery = pg.dispatches();
        List<PaymentIntent> recoveredIntents = new ArrayList<>(fault.intents());
        for (int index = 0; index < recoveredIntents.size(); index++) {
            PaymentIntent intent = recoveredIntents.get(index);
            if (intent.getStatus() == PaymentIntent.Status.UNKNOWN) {
                recoveredIntents.set(index, payments.reconcile(intent.getId()));
            }
        }
        Snapshot recovered = observe(recoveredIntents);
        assertThat(recovered.healthUp()).isEqualTo(10);
        assertThat(recovered.terminal()).isEqualTo(10);
        assertThat(recovered.unknown()).isZero();
        assertThat(recovered.providerEffects()).isEqualTo(10);
        assertThat(pg.dispatches()).isEqualTo(dispatchesBeforeRecovery);
        writeRaw("recovery", recovered);
    }

    private Snapshot run(boolean fault) {
        if (fault) {
            pg.enableFault();
        }
        List<PaymentIntent> intents = new ArrayList<>();
        int healthUp = 0;
        String health = "";
        for (int ordinal = 1; ordinal <= 10; ordinal++) {
            intents.add(payments.dispatch(ordinal, "attempt-%02d".formatted(ordinal), 1_000));
            ResponseEntity<String> response = get("/actuator/health");
            health = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && health != null && health.contains("\"status\":\"UP\"")) {
                healthUp++;
            }
        }
        String prometheus = get("/actuator/prometheus").getBody();
        return summarize(intents, healthUp, health, prometheus);
    }

    private Snapshot observe(List<PaymentIntent> intents) {
        int healthUp = 0;
        String health = "";
        for (int ordinal = 0; ordinal < 10; ordinal++) {
            ResponseEntity<String> response = get("/actuator/health");
            health = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && health != null && health.contains("\"status\":\"UP\"")) {
                healthUp++;
            }
        }
        return summarize(intents, healthUp, health, get("/actuator/prometheus").getBody());
    }

    private Snapshot summarize(List<PaymentIntent> intents, int healthUp, String health, String prometheus) {
        long unknown = intents.stream().filter(intent -> intent.getStatus() == PaymentIntent.Status.UNKNOWN).count();
        return new Snapshot(
            healthUp, (int) (intents.size() - unknown), (int) unknown, pg.providerEffects(),
            List.copyOf(intents), health == null ? "" : health, prometheus == null ? "" : prometheus
        );
    }

    private ResponseEntity<String> get(String path) {
        return http.getForEntity("http://localhost:" + managementPort + path, String.class);
    }

    private void writeRaw(String marker, Snapshot snapshot) throws Exception {
        Path evidence = repositoryRoot().resolve("evidence/week10");
        Files.createDirectories(evidence);
        Files.writeString(evidence.resolve(marker + "-health.json"), snapshot.rawHealth(), StandardCharsets.UTF_8);
        Files.writeString(evidence.resolve(marker + ".prom"), snapshot.rawPrometheus(), StandardCharsets.UTF_8);
        assertThat(snapshot.rawHealth()).isNotBlank();
        assertThat(snapshot.rawPrometheus()).containsPattern("(?m)^(# HELP|# TYPE|[a-zA-Z_:][a-zA-Z0-9_:]*)");
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    record Snapshot(
        int healthUp, int terminal, int unknown, int providerEffects,
        List<PaymentIntent> intents, String rawHealth, String rawPrometheus
    ) {}

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        PaymentGateway incidentPaymentGateway(CheckoutLatencyAIncidentFixture fixture) {
            return fixture;
        }

        @Bean
        CheckoutLatencyAIncidentFixture checkoutLatencyAIncidentFixture() {
            return new CheckoutLatencyAIncidentFixture();
        }
    }
}
