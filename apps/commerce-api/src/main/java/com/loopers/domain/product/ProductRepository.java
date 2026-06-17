package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;

import com.loopers.domain.product.projection.ProductAdminView;
import com.loopers.domain.product.projection.ProductDetail;
import com.loopers.domain.product.projection.ProductSummary;

public interface ProductRepository {

    ProductModel save(ProductModel product);

    ProductModel getActiveById(Long id);

    ProductModel getActiveByIdForUpdate(Long id);

    Optional<ProductModel> findActiveById(Long id);

    List<ProductModel> findActiveByBrandId(Long brandId);

    int incrementLikeCount(Long id);

    int decrementLikeCount(Long id);

    Page<ProductSummary> findActiveSummaries(Long brandId, ProductSortType sort, int page, int size);

    ProductDetail getActiveDetailById(Long id);

    Page<ProductAdminView> findActiveAdminViews(Long brandId, int page, int size);

    ProductAdminView getActiveAdminViewById(Long id);
}
