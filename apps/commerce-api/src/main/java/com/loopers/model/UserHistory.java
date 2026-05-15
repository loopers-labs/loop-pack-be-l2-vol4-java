package com.loopers.model;

import lombok.Data;

@Data
public class UserHistory {
    private int id;
    private int userId;
    private String changedField;
    private String oldValue;
    private String newValue;
    private String changedAt;
}
