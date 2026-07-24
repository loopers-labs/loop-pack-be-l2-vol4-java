package com.loopers.metrics.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ProductMetricEventHandler {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricHourlyRepository productMetricHourlyRepository;
    private final CatalogMetricsMetrics catalogMetricsMetrics;
    private final Clock clock;

    @Transactional
    public void handle(CatalogEventEnvelope event, EventHandlingMetadata metadata) {
        handleCommands(List.of(new ProductMetricEventCommand(event, metadata)));
    }

    @Transactional
    public void handleBatch(List<ProductMetricEventCommand> commands) {
        handleCommands(commands);
    }

    private void handleCommands(List<ProductMetricEventCommand> commands) {
        Instant handledAt = clock.instant();
        ZonedDateTime handledAtUtc = handledAt.atZone(ZoneOffset.UTC);
        Map<ProductMetricGroup, ProductMetricDelta> metricDeltas = new LinkedHashMap<>();
        Map<ProductMetricHourlyGroup, ProductMetricHourlyDelta> hourlyDeltas = new LinkedHashMap<>();
        int newEventCount = 0;

        for (ProductMetricEventCommand command : commands) {
            boolean saved = eventHandledRepository.saveIfAbsent(
                command.event(),
                command.metadata(),
                handledAtUtc
            );
            if (!saved) {
                continue;
            }
            newEventCount++;

            ProductMetricDelta metricDelta = command.metricDelta();
            ProductMetricGroup metricGroup = new ProductMetricGroup(
                metricDelta.metricDate(),
                metricDelta.productId()
            );
            metricDeltas.merge(metricGroup, metricDelta, ProductMetricDelta::plus);

            ProductMetricHourlyDelta hourlyDelta = command.hourlyDelta();
            ProductMetricHourlyGroup hourlyGroup = new ProductMetricHourlyGroup(
                hourlyDelta.windowStart(),
                hourlyDelta.productId()
            );
            hourlyDeltas.merge(hourlyGroup, hourlyDelta, ProductMetricHourlyDelta::plus);
        }

        if (!metricDeltas.isEmpty()) {
            productMetricsRepository.addAll(List.copyOf(metricDeltas.values()), handledAt);
        }
        if (!hourlyDeltas.isEmpty()) {
            productMetricHourlyRepository.addAll(List.copyOf(hourlyDeltas.values()), handledAtUtc);
        }
        catalogMetricsMetrics.recordAggregation(
            commands.size(),
            newEventCount,
            metricDeltas.size(),
            hourlyDeltas.size()
        );
    }

    private record ProductMetricGroup(
        LocalDate metricDate,
        long productId
    ) {
    }

    private record ProductMetricHourlyGroup(
        LocalDateTime windowStart,
        long productId
    ) {
    }
}
