package com.valxel.service.events;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Publishes with an idempotent producer (acks=all) and waits for the broker's acknowledgement, so
 * a change is reported as done only once Kafka holds it on every in-sync replica.
 */
public class KafkaEventSink implements EventSink, AutoCloseable {

    private final Producer<String, byte[]> producer;
    private final Duration timeout;

    public KafkaEventSink(Producer<String, byte[]> producer, Duration timeout) {
        this.producer = producer;
        this.timeout = timeout;
    }

    public static KafkaEventSink create(String bootstrap, String clientId, Map<String, String> extra, Duration timeout) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        config.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (int) timeout.toMillis());
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) timeout.toMillis());
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) Math.min(timeout.toMillis(), 30_000));
        config.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        config.putAll(extra);
        return new KafkaEventSink(new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer()), timeout);
    }

    @Override
    public Position publish(String topic, String key, Map<String, String> headers, String json) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, json.getBytes(StandardCharsets.UTF_8));
        headers.forEach((name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
        try {
            RecordMetadata meta = producer.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return new Position(meta.topic(), meta.partition(), meta.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventsUnavailableException("interrupted while publishing an event", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new EventsUnavailableException("event not acknowledged by Kafka", e);
        }
    }

    @Override
    public void close() {
        producer.close(timeout);
    }
}
