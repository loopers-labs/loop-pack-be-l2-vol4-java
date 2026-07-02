package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;

public record EventEnvelope(String eventId, String eventType, Long aggregateId, JsonNode payload) {
}