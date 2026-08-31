package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql("classpath:week4/product-query-seed.sql")
class ProductQueryE2ETest {
    private static final String RESPONSE_SHA = "46c0bdfab8b815a0bef93fd4b09ebbe37e45ae79a61279e6ac7d2792d544ee02";
    @Autowired private ProductQueryService service;
    @Autowired private JdbcTemplate db;
    @Autowired private TestRestTemplate http;

    @Test
    void fixedSeedPlansAndOrderedResultAreStableBeforeAndAfterIndex() throws Exception {
        assertThat(db.queryForObject("select count(*) from products", Long.class)).isEqualTo(10_000);
        assertThat(db.queryForObject("select sum(price) from products", Long.class)).isEqualTo(472_685_000);
        assertThat(db.queryForObject("select count(*) from products where brand_id=7", Long.class)).isEqualTo(100);
        assertThat(db.queryForObject("select sum(price) from products where brand_id=7", Long.class)).isEqualTo(4_647_200);

        List<ProductQueryService.Result> before = service.find(7, 20);
        String baselinePlan = explain();
        assertThat(baselinePlan).contains("actual time").contains("rows=20");
        assertThat(checksum(before)).isEqualTo(RESPONSE_SHA);

        db.execute("create index idx_products_brand_price_id on products(brand_id,price,id)");
        db.execute("analyze table products");
        service.priceChanged(7);
        List<ProductQueryService.Result> after = service.find(7, 20);
        String indexedPlan = explain();
        assertThat(indexedPlan).contains("idx_products_brand_price_id").contains("actual time");
        assertThat(checksum(after)).isEqualTo(RESPONSE_SHA);
        assertThat(after).isEqualTo(before);

        assertThat(http.getForEntity(
            "/api/v1/products?brandId=7&sort=price_asc&limit=20", String.class
        ).getStatusCode().is2xxSuccessful()).isTrue();
        db.update("update products set price=0 where id=?", before.getFirst().id());
        service.priceChanged(7);
        assertThat(service.find(7, 20).getFirst().price()).isZero();
    }

    private String explain() {
        return String.join("\n", db.queryForList(
            "explain analyze select id,brand_id,name,price from products where brand_id=7 order by price,id limit 20",
            String.class
        ));
    }

    private String checksum(List<ProductQueryService.Result> rows) throws Exception {
        String json = rows.stream()
            .map(row -> "{\"id\":" + row.id() + ",\"price\":" + row.price() + "}")
            .reduce((left, right) -> left + "," + right)
            .map(body -> "[" + body + "]").orElse("[]");
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))
        );
    }
}
