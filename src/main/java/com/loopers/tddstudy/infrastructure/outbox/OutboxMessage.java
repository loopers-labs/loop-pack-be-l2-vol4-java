package com.loopers.tddstudy.infrastructure.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;
    private String topic;
    private String partitionKey;

    @Lob @Column(columnDefinition = "CLOB")
    private String payload;                       // CatalogEvent JSON

    @Column(columnDefinition = "varchar(20)")
    private String status;                        // INIT / SENT

    private LocalDateTime createdAt;

    protected OutboxMessage() {}

    public OutboxMessage(String eventId, String topic, String partitionKey, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = "INIT";
        this.createdAt = LocalDateTime.now();
    }

    public void markSent() { this.status = "SENT"; }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getPartitionKey() { return partitionKey; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
}
