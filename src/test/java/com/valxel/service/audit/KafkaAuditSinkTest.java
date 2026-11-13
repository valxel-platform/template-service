package com.valxel.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class KafkaAuditSinkTest {

    @Test
    void recordsAreKeyedBySubjectAndAcknowledged() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
        try (KafkaAuditSink sink = new KafkaAuditSink(producer, "emr.audit.v1", Duration.ofSeconds(1))) {
            sink.publish("patient/p-1", "{\"id\":\"e1\"}");
        }
        ProducerRecord<String, byte[]> sent = producer.history().getFirst();
        assertThat(sent.topic()).isEqualTo("emr.audit.v1");
        assertThat(sent.key()).isEqualTo("patient/p-1");
        assertThat(new String(sent.value(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"e1\"}");
        assertThat(new String(sent.headers().lastHeader("ce_type").value(), StandardCharsets.UTF_8))
                .isEqualTo("valxel.audit.v1");
    }

    @Test
    void aRecordKafkaDoesntAcknowledgeFails() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(false, null, new StringSerializer(), new ByteArraySerializer());
        KafkaAuditSink sink = new KafkaAuditSink(producer, "t", Duration.ofMillis(100));
        assertThatThrownBy(() -> sink.publish("k", "{}")).isInstanceOf(AuditUnavailableException.class);
    }

    @Test
    void aRefusedRecordFails() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
        producer.sendException = new IllegalStateException("broker down");
        KafkaAuditSink sink = new KafkaAuditSink(producer, "t", Duration.ofMillis(100));
        assertThatThrownBy(() -> sink.publish("k", "{}")).isInstanceOf(AuditUnavailableException.class);
    }
}
