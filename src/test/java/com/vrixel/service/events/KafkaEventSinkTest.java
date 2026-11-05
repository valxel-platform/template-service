package com.vrixel.service.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

class KafkaEventSinkTest {

    @Test
    void eventsAreKeyedCarryTheirHeadersAndReportWhereTheyLanded() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
        try (KafkaEventSink sink = new KafkaEventSink(producer, Duration.ofSeconds(1))) {
            EventSink.Position at = sink.publish("emr.intake-api.patients.v1", "p-1", Map.of("ce_type", "t"), "{\"id\":\"e1\"}");
            assertThat(at.topic()).isEqualTo("emr.intake-api.patients.v1");
            assertThat(at.offset()).isGreaterThanOrEqualTo(0);
        }
        ProducerRecord<String, byte[]> sent = producer.history().getFirst();
        assertThat(sent.key()).isEqualTo("p-1");
        assertThat(new String(sent.value(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"e1\"}");
        assertThat(new String(sent.headers().lastHeader("ce_type").value(), StandardCharsets.UTF_8)).isEqualTo("t");
    }

    @Test
    void anEventKafkaDoesntAcknowledgeFails() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(false, null, new StringSerializer(), new ByteArraySerializer());
        KafkaEventSink sink = new KafkaEventSink(producer, Duration.ofMillis(100));
        assertThatThrownBy(() -> sink.publish("t", "k", Map.of(), "{}")).isInstanceOf(EventsUnavailableException.class);
    }

    @Test
    void aRefusedEventFails() {
        MockProducer<String, byte[]> producer =
                new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
        producer.sendException = new IllegalStateException("broker down");
        KafkaEventSink sink = new KafkaEventSink(producer, Duration.ofMillis(100));
        assertThatThrownBy(() -> sink.publish("t", "k", Map.of(), "{}")).isInstanceOf(EventsUnavailableException.class);
    }
}
