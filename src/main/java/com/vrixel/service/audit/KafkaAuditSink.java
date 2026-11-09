package com.vrixel.service.audit;

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
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Publishes to Kafka with an idempotent producer (acks=all) and waits for the broker's
 * acknowledgement, so a failure reaches the caller instead of being lost.
 */
public class KafkaAuditSink implements AuditSink, AutoCloseable {

    private static final int LINGER_MS = 5;

    private final Producer<String, byte[]> producer;
    private final String topic;
    private final Duration timeout;

    public KafkaAuditSink(Producer<String, byte[]> producer, String topic, Duration timeout) {
        this.producer = producer;
        this.topic = topic;
        this.timeout = timeout;
    }

    public static KafkaAuditSink create(String bootstrap, String topic, Map<String, String> extra, Duration timeout) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "audit");
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (int) timeout.toMillis());
        // Kafka requires delivery.timeout.ms >= linger.ms + request.timeout.ms.
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) timeout.toMillis() + LINGER_MS);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) Math.min(timeout.toMillis(), 30_000));
        // Waited-for records go out at once; records sent without waiting batch for a few ms.
        config.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);
        config.putAll(extra);
        return new KafkaAuditSink(
                new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer()), topic, timeout);
    }

    @Override
    public void publish(String key, String json) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, json.getBytes(StandardCharsets.UTF_8));
        record.headers().add("ce_type", Auditor.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        try {
            producer.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuditUnavailableException("interrupted while publishing an audit record", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new AuditUnavailableException("audit record not acknowledged by Kafka", e);
        }
    }

    @Override
    public void publishAsync(String key, String json) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, json.getBytes(StandardCharsets.UTF_8));
        record.headers().add("ce_type", Auditor.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        try {
            producer.send(record, (meta, error) -> {
                if (error != null) {
                    LOG.error("audit record dropped ({}): {}", error.getClass().getSimpleName(), key);
                }
            });
        } catch (RuntimeException e) { // the buffer is full or the producer is closed
            LOG.error("audit record dropped ({}): {}", e.getClass().getSimpleName(), key);
        }
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("audit");

    @Override
    public void close() {
        producer.close(timeout);
    }
}
