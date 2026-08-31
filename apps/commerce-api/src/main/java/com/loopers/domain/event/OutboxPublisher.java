package com.loopers.domain.event;public interface OutboxPublisher{void publish(String eventId,String payload);}
