package com.loopers.metrics.interfaces;

import com.loopers.metrics.application.MetricSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * product_metrics 일별 시드 — 배치 검증용 더미를 채운다. 실제 지표를 덮어쓰므로 local 에서만 뜬다.
 * 자동 검증은 이 엔드포인트에 의존하지 않고 테스트가 데이터를 직접 넣는다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/admin/metrics")
@RequiredArgsConstructor
public class MetricSeedController {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final MetricSeedService metricSeedService;

    @PostMapping("/seed")
    public SeedResult seed(@RequestBody SeedRequest request) {
        int rows = metricSeedService.seed(
                LocalDate.parse(request.from(), YYYYMMDD),
                LocalDate.parse(request.to(), YYYYMMDD),
                request.productIds());
        return new SeedResult(rows);
    }

    public record SeedRequest(String from, String to, List<Long> productIds) {
    }

    public record SeedResult(int seededRows) {
    }
}
