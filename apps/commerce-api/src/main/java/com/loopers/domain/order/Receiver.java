package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Receiver {

    @Column(name = "receiver_name", nullable = false)
    private String name;

    @Column(name = "receiver_phone", nullable = false)
    private String phone;

    protected Receiver() {}

    public Receiver(String name, String phone) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        if (phone == null || phone.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.name = name;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
