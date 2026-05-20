# Product Sequence Diagrams

## GET /api/v1/products

```mermaid
sequenceDiagram
    actor User
    participant Controller as ProductV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant DB

    User->>Controller: GET /api/v1/products?brandId=&sort=latest&page=0&size=20
    Controller->>Facade: getProducts(brandId, sort, page, size)
    Facade->>ProductService: getProducts(brandId, sort, page, size)
    note over ProductService: sort 기본값 = latest
    ProductService->>DB: SELECT * FROM products WHERE deleted_at IS NULL [AND brand_id = ?] ORDER BY created_at DESC LIMIT ? OFFSET ?
    ProductService-->>Facade: Page~ProductModel~
    Facade-->>Controller: Page~ProductInfo~
    Controller-->>User: 200 OK ApiResponse(Page~ProductResponse~)
```

---

## GET /api/v1/products/{productId}

```mermaid
sequenceDiagram
    actor User
    participant Controller as ProductV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant DB

    User->>Controller: GET /api/v1/products/{productId}
    Controller->>Facade: getProduct(productId)
    Facade->>ProductService: getProduct(productId)
    ProductService->>DB: SELECT * FROM products WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 상품
        ProductService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>User: 404 Not Found
    end
    ProductService-->>Facade: ProductModel
    Facade-->>Controller: ProductInfo
    Controller-->>User: 200 OK ApiResponse(ProductResponse)
```

---

## GET /api-admin/v1/products

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as ProductAdminV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant DB

    Admin->>Controller: GET /api-admin/v1/products?page=0&size=20&brandId={brandId}
    Controller->>Facade: getProducts(brandId, page, size)
    Facade->>ProductService: getProducts(brandId, page, size)
    ProductService->>DB: SELECT * FROM products WHERE deleted_at IS NULL [AND brand_id = ?] LIMIT ? OFFSET ?
    ProductService-->>Facade: Page~ProductModel~
    Facade-->>Controller: Page~ProductInfo~
    Controller-->>Admin: 200 OK ApiResponse(Page~ProductResponse~)
```

---

## GET /api-admin/v1/products/{productId}

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as ProductAdminV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant DB

    Admin->>Controller: GET /api-admin/v1/products/{productId}
    Controller->>Facade: getProduct(productId)
    Facade->>ProductService: getProduct(productId)
    ProductService->>DB: SELECT * FROM products WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 상품
        ProductService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    ProductService-->>Facade: ProductModel
    Facade-->>Controller: ProductInfo
    Controller-->>Admin: 200 OK ApiResponse(ProductResponse)
```

---

## POST /api-admin/v1/products

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as ProductAdminV1Controller
    participant Facade as ProductFacade
    participant BrandService
    participant ProductService
    participant DB

    Admin->>Controller: POST /api-admin/v1/products {brandId, name, price, stock, description}
    Controller->>Facade: createProduct(request)
    Facade->>BrandService: getBrand(brandId)
    BrandService->>DB: SELECT * FROM brands WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 브랜드
        BrandService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    Facade->>ProductService: createProduct(brandId, name, price, stock, description)
    ProductService->>DB: INSERT INTO products (brand_id, name, price, description)
    ProductService->>DB: INSERT INTO product_stocks (product_id, quantity)
    ProductService-->>Facade: ProductModel
    Facade-->>Controller: ProductInfo
    Controller-->>Admin: 201 Created ApiResponse(ProductResponse)
```

---

## PUT /api-admin/v1/products/{productId}

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as ProductAdminV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant DB

    Admin->>Controller: PUT /api-admin/v1/products/{productId} {name, price, description}
    note over Controller: brandId 수정 불가 — 요청 필드에서 제외
    Controller->>Facade: updateProduct(productId, name, price, description)
    Facade->>ProductService: updateProduct(productId, name, price, description)
    ProductService->>DB: SELECT * FROM products WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 상품
        ProductService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    ProductService->>DB: UPDATE products SET name = ?, price = ?, description = ? WHERE id = ?
    ProductService-->>Facade: ProductModel
    Facade-->>Controller: ProductInfo
    Controller-->>Admin: 200 OK ApiResponse(ProductResponse)
```

---

## DELETE /api-admin/v1/products/{productId}

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as ProductAdminV1Controller
    participant Facade as ProductFacade
    participant ProductService
    participant DB

    Admin->>Controller: DELETE /api-admin/v1/products/{productId}
    Controller->>Facade: deleteProduct(productId)
    Facade->>ProductService: deleteProduct(productId)
    ProductService->>DB: SELECT * FROM products WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 상품
        ProductService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    ProductService->>DB: UPDATE products SET deleted_at = now() WHERE id = ?
    Controller-->>Admin: 200 OK ApiResponse(null)
```
