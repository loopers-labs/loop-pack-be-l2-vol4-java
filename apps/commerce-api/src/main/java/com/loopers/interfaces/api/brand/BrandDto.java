package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;

public final class BrandDto {

    private BrandDto() {}

    public static final class Get {

        private Get() {}

        public static final class V1 {

            private V1() {}

            public record Response(
                Long id,
                String name,
                String description
            ) {
                public static Response from(BrandInfo info) {
                    return new Response(
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
                String name,
                String description
            ) {
                public static Response from(BrandInfo info) {
                    return new Response(
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
                @NotBlank
                String name,
                @NotBlank
                String description
            ) {}

            public record Response(
                Long id,
                String name,
                String description
            ) {
                public static Response from(BrandInfo info) {
                    return new Response(
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
                String description
            ) {}

            public record Response(
                Long id,
                String name,
                String description
            ) {
                public static Response from(BrandInfo info) {
                    return new Response(
                        info.id(),
                        info.name(),
                        info.description()
                    );
                }
            }
        }
    }
}
