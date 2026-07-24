package com.loopers.ranking.perf;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.Consumer;

final class ProductMetricPerfDataGenerator {

    private static final DatasetDefinition DEFAULT_DATASET = new DatasetDefinition(
        "product-ranking",
        "ranking-perf-v1",
        1,
        42L,
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        List.of(
            new ProductGroup(10_000, 31, 10_000, 100_000),
            new ProductGroup(90_000, 15, 100, 10_000),
            new ProductGroup(300_000, 2, 10, 500),
            new ProductGroup(600_000, 0, 0, 0)
        )
    );

    private final DatasetDefinition dataset;

    static ProductMetricPerfDataGenerator defaultGenerator() {
        return new ProductMetricPerfDataGenerator(DEFAULT_DATASET);
    }

    ProductMetricPerfDataGenerator(DatasetDefinition dataset) {
        this.dataset = Objects.requireNonNull(dataset);
    }

    DatasetDefinition dataset() {
        return dataset;
    }

    long generate(Consumer<ProductMetricSeedRow> consumer) {
        Objects.requireNonNull(consumer);

        SplittableRandom random = new SplittableRandom(dataset.randomSeed());
        LocalDateTime generatedAt = dataset.metricEndDate()
            .plusDays(1)
            .atStartOfDay();
        long generatedCount = 0;

        for (int dayIndex = 0; dayIndex < dataset.metricDayCount(); dayIndex++) {
            LocalDate metricDate = dataset.metricStartDate().plusDays(dayIndex);
            long firstProductId = 1;

            for (ProductGroup group : dataset.productGroups()) {
                long lastProductId = firstProductId + group.productCount() - 1L;
                if (group.activityDays() > 0) {
                    for (long productId = firstProductId;
                         productId <= lastProductId;
                         productId++) {
                        if (!isActive(productId, dayIndex, group.activityDays())) {
                            continue;
                        }
                        consumer.accept(metric(
                            metricDate,
                            dayIndex,
                            productId,
                            group,
                            random,
                            generatedAt
                        ));
                        generatedCount++;
                    }
                }
                firstProductId = lastProductId + 1;
            }
        }
        return generatedCount;
    }

    private boolean isActive(long productId, int dayIndex, int activityDays) {
        int dayCount = dataset.metricDayCount();
        int offset = Math.floorMod(mix(productId ^ dataset.randomSeed()), dayCount);
        int position = Math.floorMod(dayIndex * coprimeStep(dayCount) + offset, dayCount);
        return position < activityDays;
    }

    private ProductMetricSeedRow metric(
        LocalDate metricDate,
        int dayIndex,
        long productId,
        ProductGroup group,
        SplittableRandom random,
        LocalDateTime generatedAt
    ) {
        long viewCount = random.nextLong(group.minViewCount(), group.maxViewCount() + 1);
        long likeDelta = Math.round(viewCount * random.nextDouble(0.01, 0.05));
        if (isLikeCancellation(productId, dayIndex) && likeDelta > 0) {
            likeDelta = -likeDelta;
        }
        long orderQuantity = Math.round(viewCount * random.nextDouble(0.0005, 0.01));
        long orderAmount = Math.multiplyExact(orderQuantity, unitPrice(productId));

        return new ProductMetricSeedRow(
            metricDate,
            productId,
            viewCount,
            likeDelta,
            orderQuantity,
            orderAmount,
            generatedAt
        );
    }

    private boolean isLikeCancellation(long productId, int dayIndex) {
        return Math.floorMod(productId + dayIndex + dataset.randomSeed(), 20) == 0;
    }

    private long unitPrice(long productId) {
        long priceUnit = Math.floorMod(mix(productId + dataset.randomSeed()), 99_901);
        return 1_000 + priceUnit * 10;
    }

    private static int coprimeStep(int dayCount) {
        int step = Math.max(1, dayCount / 2);
        while (greatestCommonDivisor(step, dayCount) != 1) {
            step++;
        }
        return step;
    }

    private static int greatestCommonDivisor(int left, int right) {
        int first = left;
        int second = right;
        while (second != 0) {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    private static long mix(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    record ProductMetricSeedRow(
        LocalDate metricDate,
        long productId,
        long viewCount,
        long likeDelta,
        long orderQuantity,
        long orderAmount,
        LocalDateTime updatedAt
    ) {
    }

    record ProductGroup(
        int productCount,
        int activityDays,
        long minViewCount,
        long maxViewCount
    ) {

        ProductGroup {
            if (productCount <= 0) {
                throw new IllegalArgumentException("productCount는 양수여야 합니다.");
            }
            if (activityDays < 0) {
                throw new IllegalArgumentException("activityDays는 음수일 수 없습니다.");
            }
            if (activityDays == 0 && (minViewCount != 0 || maxViewCount != 0)) {
                throw new IllegalArgumentException(
                    "비활성 상품군의 조회 수 범위는 0이어야 합니다."
                );
            }
            if (activityDays > 0
                && (minViewCount <= 0 || maxViewCount < minViewCount)) {
                throw new IllegalArgumentException(
                    "활성 상품군의 조회 수 범위가 올바르지 않습니다."
                );
            }
        }
    }

    record DatasetDefinition(
        String datasetName,
        String datasetVersion,
        int generatorVersion,
        long randomSeed,
        LocalDate metricStartDate,
        LocalDate metricEndDate,
        List<ProductGroup> productGroups
    ) {

        DatasetDefinition {
            if (datasetName == null || datasetName.isBlank()) {
                throw new IllegalArgumentException("datasetName은 비어 있을 수 없습니다.");
            }
            if (datasetVersion == null || datasetVersion.isBlank()) {
                throw new IllegalArgumentException(
                    "datasetVersion은 비어 있을 수 없습니다."
                );
            }
            if (generatorVersion <= 0) {
                throw new IllegalArgumentException("generatorVersion은 양수여야 합니다.");
            }
            Objects.requireNonNull(metricStartDate);
            Objects.requireNonNull(metricEndDate);
            if (metricEndDate.isBefore(metricStartDate)) {
                throw new IllegalArgumentException(
                    "metricEndDate는 metricStartDate보다 빠를 수 없습니다."
                );
            }
            productGroups = List.copyOf(productGroups);
            if (productGroups.isEmpty()) {
                throw new IllegalArgumentException("productGroups는 비어 있을 수 없습니다.");
            }

            int metricDayCount = Math.toIntExact(
                ChronoUnit.DAYS.between(metricStartDate, metricEndDate) + 1
            );
            boolean invalidActivityDays = productGroups.stream()
                .anyMatch(group -> group.activityDays() > metricDayCount);
            if (invalidActivityDays) {
                throw new IllegalArgumentException(
                    "activityDays는 Metric 기간의 날짜 수를 넘을 수 없습니다."
                );
            }
        }

        int metricDayCount() {
            return Math.toIntExact(
                ChronoUnit.DAYS.between(metricStartDate, metricEndDate) + 1
            );
        }

        long productCount() {
            return productGroups.stream()
                .mapToLong(ProductGroup::productCount)
                .sum();
        }

        long activeProductCount() {
            return productGroups.stream()
                .filter(group -> group.activityDays() > 0)
                .mapToLong(ProductGroup::productCount)
                .sum();
        }

        long metricCount() {
            return productGroups.stream()
                .mapToLong(group -> Math.multiplyExact(
                    (long) group.productCount(),
                    group.activityDays()
                ))
                .sum();
        }
    }
}
