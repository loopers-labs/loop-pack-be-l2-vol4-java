package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class ProductDto {

    private ProductDto() {}

    public static final class Get {

        private Get() {}

        public static final class V1 {

            private V1() {}

            public record Response(
                Long id,
                BrandResponse brand,
                String name,
                String description,
                Long price,
                Integer stock,
                Integer likeCount
            ) {
                public static Response from(ProductInfo info) {
                    return new Response(
                        info.id(),
                        BrandResponse.from(info.brand()),
                        info.name(),
                        info.description(),
                        info.price(),
                        info.stock(),
                        info.likeCount()
                    );
                }
            }

            public record BrandResponse(
                Long id,
                String name,
                String description
            ) {
                public static BrandResponse from(ProductInfo.BrandInfo info) {
                    return new BrandResponse(
                        info.id(),
                        info.name(),
                        info.description()
                    );
                }
            }
        }
    }

    public static final class List {

        private List() {}

        public static final class V1 {

            private V1() {}

            public record Response(
                Long id,
                BrandResponse brand,
                String name,
                String description,
                Long price,
                Integer stock,
                Integer likeCount
            ) {
                public static Response from(ProductInfo info) {
                    return new Response(
                        info.id(),
                        BrandResponse.from(info.brand()),
                        info.name(),
                        info.description(),
                        info.price(),
                        info.stock(),
                        info.likeCount()
                    );
                }
            }

            public record BrandResponse(
                Long id,
                String name,
                String description
            ) {
                public static BrandResponse from(ProductInfo.BrandInfo info) {
                    return new BrandResponse(
                        info.id(),
                        info.name(),
                        info.description()
                    );
                }
            }
        }
    }

    public static final class Create {

        private Create() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotNull
                Long brandId,
                @NotBlank
                String name,
                @NotBlank
                String description,
                @NotNull
                @PositiveOrZero
                Long price,
                @NotNull
                @PositiveOrZero
                Integer stock
            ) {}

            public record Response(
                Long id,
                BrandResponse brand,
                String name,
                String description,
                Long price,
                Integer stock,
                Integer likeCount
            ) {
                public static Response from(ProductInfo info) {
                    return new Response(
                        info.id(),
                        BrandResponse.from(info.brand()),
                        info.name(),
                        info.description(),
                        info.price(),
                        info.stock(),
                        info.likeCount()
                    );
                }
            }

            public record BrandResponse(
                Long id,
                String name,
                String description
            ) {
                public static BrandResponse from(ProductInfo.BrandInfo info) {
                    return new BrandResponse(
                        info.id(),
                        info.name(),
                        info.description()
                    );
                }
            }
        }
    }

    public static final class Update {

        private Update() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotBlank
                String name,
                @NotBlank
                String description,
                @NotNull
                @PositiveOrZero
                Long price,
                @NotNull
                @PositiveOrZero
                Integer stock
            ) {}

            public record Response(
                Long id,
                BrandResponse brand,
                String name,
                String description,
                Long price,
                Integer stock,
                Integer likeCount
            ) {
                public static Response from(ProductInfo info) {
                    return new Response(
                        info.id(),
                        BrandResponse.from(info.brand()),
                        info.name(),
                        info.description(),
                        info.price(),
                        info.stock(),
                        info.likeCount()
                    );
                }
            }

            public record BrandResponse(
                Long id,
                String name,
                String description
            ) {
                public static BrandResponse from(ProductInfo.BrandInfo info) {
                    return new BrandResponse(
                        info.id(),
                        info.name(),
                        info.description()
                    );
                }
            }
        }
    }
}
