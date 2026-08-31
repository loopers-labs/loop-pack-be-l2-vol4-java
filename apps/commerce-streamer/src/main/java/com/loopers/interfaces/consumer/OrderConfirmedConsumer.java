package com.loopers.interfaces.consumer;
import lombok.RequiredArgsConstructor;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;
// Hides: durable event-effect deduplication behind one order-confirmed operation.
@Component @RequiredArgsConstructor public class OrderConfirmedConsumer{private final JdbcTemplate db;@Transactional public boolean handle(String eventId,String orderId){db.execute("create table if not exists kafka_effect_ledger(event_id varchar(80) primary key,order_id varchar(80) not null)");return db.update("insert ignore into kafka_effect_ledger(event_id,order_id) values (?,?)",eventId,orderId)==1;}}
