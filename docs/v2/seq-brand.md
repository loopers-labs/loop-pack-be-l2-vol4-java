# Brand Sequence Diagrams

## GET /api/v1/brands/{brandId}

```mermaid
sequenceDiagram
    actor User
    participant Controller as BrandV1Controller
    participant Facade as BrandFacade
    participant BrandService
    participant DB

    User->>Controller: GET /api/v1/brands/{brandId}
    Controller->>Facade: getBrand(brandId)
    Facade->>BrandService: getBrand(brandId)
    BrandService->>DB: SELECT * FROM brands WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 브랜드
        BrandService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>User: 404 Not Found
    end
    BrandService-->>Facade: BrandModel
    Facade-->>Controller: BrandInfo
    Controller-->>User: 200 OK ApiResponse(BrandResponse)
```

---

## GET /api-admin/v1/brands

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandService
    participant DB

    Admin->>Controller: GET /api-admin/v1/brands?page=0&size=20
    Controller->>Facade: getBrands(page, size)
    Facade->>BrandService: getBrands(page, size)
    BrandService->>DB: SELECT * FROM brands WHERE deleted_at IS NULL LIMIT ? OFFSET ?
    BrandService-->>Facade: Page~BrandModel~
    Facade-->>Controller: Page~BrandInfo~
    Controller-->>Admin: 200 OK ApiResponse(Page~BrandResponse~)
```

---

## GET /api-admin/v1/brands/{brandId}

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandService
    participant DB

    Admin->>Controller: GET /api-admin/v1/brands/{brandId}
    Controller->>Facade: getBrand(brandId)
    Facade->>BrandService: getBrand(brandId)
    BrandService->>DB: SELECT * FROM brands WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 브랜드
        BrandService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    BrandService-->>Facade: BrandModel
    Facade-->>Controller: BrandInfo
    Controller-->>Admin: 200 OK ApiResponse(BrandResponse)
```

---

## POST /api-admin/v1/brands

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandService
    participant DB

    Admin->>Controller: POST /api-admin/v1/brands {name}
    Controller->>Facade: createBrand(name)
    Facade->>BrandService: createBrand(name)
    BrandService->>DB: INSERT INTO brands (name)
    BrandService-->>Facade: BrandModel
    Facade-->>Controller: BrandInfo
    Controller-->>Admin: 201 Created ApiResponse(BrandResponse)
```

---

## PUT /api-admin/v1/brands/{brandId}

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandService
    participant DB

    Admin->>Controller: PUT /api-admin/v1/brands/{brandId} {name}
    Controller->>Facade: updateBrand(brandId, name)
    Facade->>BrandService: updateBrand(brandId, name)
    BrandService->>DB: SELECT * FROM brands WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 브랜드
        BrandService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    BrandService->>DB: UPDATE brands SET name = ? WHERE id = ?
    BrandService-->>Facade: BrandModel
    Facade-->>Controller: BrandInfo
    Controller-->>Admin: 200 OK ApiResponse(BrandResponse)
```

---

## DELETE /api-admin/v1/brands/{brandId}

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as BrandAdminV1Controller
    participant Facade as BrandFacade
    participant BrandService
    participant ProductService
    participant DB

    Admin->>Controller: DELETE /api-admin/v1/brands/{brandId}
    Controller->>Facade: deleteBrand(brandId)
    Facade->>BrandService: getBrand(brandId)
    BrandService->>DB: SELECT * FROM brands WHERE id = ? AND deleted_at IS NULL
    alt 존재하지 않는 브랜드
        BrandService-->>Facade: CoreException(NOT_FOUND)
        Facade-->>Controller: CoreException
        Controller-->>Admin: 404 Not Found
    end
    note over Facade, DB: @Transactional 시작
    Facade->>BrandService: delete(brandId)
    BrandService->>DB: UPDATE brands SET deleted_at = now() WHERE id = ?
    Facade->>ProductService: deleteAllByBrandId(brandId)
    ProductService->>DB: UPDATE products SET deleted_at = now() WHERE brand_id = ? AND deleted_at IS NULL
    note over Facade, DB: @Transactional 종료
    Controller-->>Admin: 200 OK ApiResponse(null)
```
