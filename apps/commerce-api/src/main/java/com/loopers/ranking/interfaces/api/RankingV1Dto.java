package com.loopers.ranking.interfaces.api;

import com.loopers.brand.application.BrandInfo;
import com.loopers.product.application.ProductListInfo;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.RankingItemInfo;
import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class RankingV1Dto {

    public record RankingRequest(
        RankingPeriod period,
        String date,
        int page,
        int size
    ) {
        private static final int MAX_PAGE_SIZE = 100;

        public RankingRequest {
            if (date == null || !date.matches("\\d{8}")) {
                throw invalidDate();
            }
            if (page < 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
            }
            if (size <= 0 || size > MAX_PAGE_SIZE) {
                throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상 100 이하여야 합니다.");
            }
        }

        public LocalDate rankingDate() {
            try {
                return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
            } catch (DateTimeParseException e) {
                throw invalidDate();
            }
        }

        private static CoreException invalidDate() {
            return new CoreException(ErrorType.BAD_REQUEST, "날짜는 yyyyMMdd 형식의 유효한 날짜여야 합니다.");
        }
    }

    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description()
            );
        }
    }

    public record ProductResponse(
        Long id,
        BrandResponse brand,
        String name,
        String description,
        long price,
        long likeCount
    ) {
        public static ProductResponse from(ProductListInfo info) {
            return new ProductResponse(
                info.id(),
                BrandResponse.from(info.brand()),
                info.name(),
                info.description(),
                info.price(),
                info.likeCount()
            );
        }
    }

    public record RankingItemResponse(
        long rank,
        ProductResponse product
    ) {
        public static RankingItemResponse from(RankingItemInfo info) {
            return new RankingItemResponse(
                info.rank(),
                ProductResponse.from(info.product())
            );
        }
    }
}
