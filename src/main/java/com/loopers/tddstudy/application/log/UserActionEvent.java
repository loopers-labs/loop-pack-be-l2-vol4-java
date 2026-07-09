package com.loopers.tddstudy.application.log;

public record UserActionEvent(
        Long userId,
        String actionType,   // VIEW / CLICK / LIKE / ORDER ...
        Long targetId
) {}
