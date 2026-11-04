/**
 * The audit trail: who did what to which records, and how it turned out.
 *
 * <p>Every record is one CloudEvents 1.0 event of type {@code vrixel.audit.v1}, published to the
 * product's audit topic on Kafka. The platform's audit sink (vrixel-shared-services/ss-audit-sink)
 * copies the topic into an append-only, hash-chained Postgres table and archives it to object
 * storage. Records name people and records by id only; never put field values (names, notes, dates
 * of birth) in them.
 *
 * <pre>{@code
 * auditor.record("patient.read", Actor.user(jwt.getSubject()), "patient", List.of(patientId));
 * }</pre>
 *
 * <p>With {@code app.audit.required} (the default) a record that can't be published throws
 * {@link com.vrixel.service.audit.AuditUnavailableException}, which the API answers with 503: the
 * caller gets no data and no success unless the access is on record.
 */
package com.vrixel.service.audit;
