package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class ShippingInfo {

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false)
    private String receiverPhone;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "detail_address")
    private String detailAddress;

    protected ShippingInfo() {}

    public ShippingInfo(String receiverName, String receiverPhone, String zipCode, String address, String detailAddress) {
        if (receiverName == null || receiverName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수령인 이름은 비어있을 수 없습니다.");
        }
        if (receiverPhone == null || receiverPhone.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수령인 연락처는 비어있을 수 없습니다.");
        }
        if (zipCode == null || zipCode.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "우편번호는 비어있을 수 없습니다.");
        }
        if (address == null || address.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기본 주소는 비어있을 수 없습니다.");
        }
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipCode = zipCode;
        this.address = address;
        this.detailAddress = detailAddress;
    }
}
