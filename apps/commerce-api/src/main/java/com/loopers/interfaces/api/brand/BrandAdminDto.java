package com.loopers.interfaces.api.brand;

public class BrandAdminDto {
    public record RegisterBrandRequest(String name) {}
    public record UpdateBrandRequest(String name) {}
    public record BrandResponse(Long id, String name) {}
}
