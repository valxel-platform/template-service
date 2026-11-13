/**
 * Domain events, published to Kafka first.
 *
 * <p>A request that changes state does not write to a database. It validates the command, publishes
 * one event that says what changed, and answers 202 Accepted once Kafka has acknowledged it. Event
 * workers (valxel-platform/template-event-worker) read the topic and keep the product's data stores up
 * to date. Kafka is the record; every database is a copy that can be rebuilt by replaying the topic.
 * A service never writes to its database and to Kafka in the same request (constitution VX-16):
 * one of the two writes can fail, and the copies drift apart for good.
 *
 * <pre>{@code
 * Published p = events.publish("patients", "patient.registered", patientId, data, idempotencyKey);
 * return ResponseEntity.accepted().body(Map.of("event_id", p.eventId()));
 * }</pre>
 *
 * <ul>
 *   <li>Each event is a CloudEvents 1.0 JSON document on topic {@code <prefix>.<entity>.v1}, keyed
 *       by the record's id, so every change to one record lands on one partition, in order.
 *   <li>The producer is idempotent and waits for every in-sync replica (acks=all). If Kafka doesn't
 *       acknowledge in time, {@link com.valxel.service.events.EventsUnavailableException} becomes a
 *       503: the change did not happen, and the caller retries.
 *   <li>A retry with the same {@code Idempotency-Key} gets the same event id, so event workers
 *       apply it once.
 *   <li>Events carry the trace (W3C {@code traceparent} header), so a change can be followed from
 *       the request through every worker that applies it.
 *   <li>Readers see a change once the event workers have applied it, usually within a second.
 *       Clients that must read their own write poll the read model for the event id.
 * </ul>
 */
package com.valxel.service.events;
