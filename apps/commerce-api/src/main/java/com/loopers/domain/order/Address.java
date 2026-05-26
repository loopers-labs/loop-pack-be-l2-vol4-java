package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Address {

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "detail_address")
    private String detailAddress;

    protected Address() {}

    public Address(String zipCode, String address, String detailAddress) {
        if (zipCode == null || zipCode.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        if (address == null || address.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.zipCode = zipCode;
        this.address = address;
        this.detailAddress = detailAddress;
    }

    public String getZipCode() {
        return zipCode;
    }

    public String getAddress() {
        return address;
    }

    public String getDetailAddress() {
        return detailAddress;
    }
}
