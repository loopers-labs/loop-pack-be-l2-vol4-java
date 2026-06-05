package com.loopers.application.product;

import java.math.BigDecimal;

public class ProductCommand {
    public record Create(Long brandId, String name, BigDecimal price, long stock) {}
    public record Update(Long brandId, String name, BigDecimal price, long stock) {}
}
