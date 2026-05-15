package com.loopers.dto;

import com.loopers.model.UserHistory;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserHistoryResponse {
    private int id;
    private String changedField;
    private String oldValue;
    private String newValue;
    private String changedAt;

    public static UserHistoryResponse from(UserHistory h) {
        return new UserHistoryResponse(h.getId(), h.getChangedField(), h.getOldValue(), h.getNewValue(), h.getChangedAt());
    }
}
