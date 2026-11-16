package com.valxel.service.events;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * The approved data contracts of the topics this service publishes (the design "Datasets, contracts
 * and reporting"). The SDLC tool writes {@code contracts/<dataset>.json} (the JSON Schema generated from
 * each ODCS contract) and {@code contracts/lock.yaml} (dataset: version, sha, topic) on the feature
 * branch; the build puts them on the classpath under {@code contracts/}, so nothing reads GitHub at run
 * time.
 *
 * <p>{@link Events#publish} checks an event's data against its topic's contract before it reaches
 * Kafka: a property the contract doesn't have (so has no data class), a missing required one, or a
 * value of the wrong type is a bug in this service, and nothing is published. This checks the subset
 * of JSON Schema the tool generates: type, format (date, date-time, time, uuid), enum, required,
 * minLength, maxLength, pattern and additionalProperties.
 */
public final class EventContracts {

    /** One dataset: its contract's schema and where it is published. */
    public record Contract(String dataset, String topic, String version, String schemaId, Map<String, Object> schema) {}

    /** Data that breaks its contract. The message names the field and the rule, never the value. */
    public static class ContractViolationException extends IllegalStateException {
        private final String dataset;
        private final String field;
        private final String rule;

        ContractViolationException(String dataset, String field, String rule) {
            super(dataset + ": " + field + " breaks the data contract (" + rule + ")");
            this.dataset = dataset;
            this.field = field;
            this.rule = rule;
        }

        public String dataset() {
            return dataset;
        }

        public String field() {
            return field;
        }

        public String rule() {
            return rule;
        }
    }

    private static final EventContracts NONE = new EventContracts(Map.of());
    private final Map<String, Contract> byTopic;

    public EventContracts(Map<String, Contract> byTopic) {
        this.byTopic = Map.copyOf(byTopic);
    }

    /** No contracts: a service that publishes no contracted topic. */
    public static EventContracts none() {
        return NONE;
    }

    /** The contracts under {@code dir} on the classpath; none when there is no lock.yaml. */
    @SuppressWarnings("unchecked")
    public static EventContracts load(ClassLoader loader, String dir) {
        Map<String, Object> lock = (Map<String, Object>) read(loader, dir + "/lock.yaml");
        if (lock == null) {
            return NONE;
        }
        Map<String, Contract> out = new LinkedHashMap<>();
        lock.forEach((dataset, row) -> {
            Map<String, Object> r = (Map<String, Object>) row;
            Map<String, Object> schema = (Map<String, Object>) read(loader, dir + "/" + dataset + ".json");
            if (schema == null) {
                throw new IllegalStateException(dir + "/lock.yaml lists " + dataset + " but " + dataset + ".json is missing");
            }
            String topic = String.valueOf(r.get("topic"));
            out.put(topic, new Contract(dataset, topic, String.valueOf(r.get("version")), String.valueOf(schema.get("$id")), schema));
        });
        return new EventContracts(out);
    }

    private static Object read(ClassLoader loader, String path) {
        try (InputStream in = loader.getResourceAsStream(path)) {
            return in == null ? null : new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("can't read " + path, e);
        }
    }

    public Optional<Contract> forTopic(String topic) {
        return Optional.ofNullable(byTopic.get(topic));
    }

    /** Throws {@link ContractViolationException} when {@code data} breaks the contract. */
    @SuppressWarnings("unchecked")
    public static void check(Contract c, Map<String, ?> data) {
        Map<String, Object> props = (Map<String, Object>) c.schema().getOrDefault("properties", Map.of());
        for (String key : data.keySet()) {
            if (!props.containsKey(key)) {
                throw new ContractViolationException(c.dataset(), key, "not in the contract");
            }
        }
        for (Object req : (List<Object>) c.schema().getOrDefault("required", List.of())) {
            if (data.get(String.valueOf(req)) == null) {
                throw new ContractViolationException(c.dataset(), String.valueOf(req), "required");
            }
        }
        data.forEach((key, value) -> {
            String rule = value == null ? null : violation((Map<String, Object>) props.get(key), value);
            if (rule != null) {
                throw new ContractViolationException(c.dataset(), key, rule);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static String violation(Map<String, Object> s, Object v) {
        String type = String.valueOf(s.get("type"));
        boolean ok = switch (type) {
            case "string" -> v instanceof CharSequence || (s.containsKey("format") && v instanceof TemporalAccessor) || v instanceof UUID;
            case "integer" -> v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof java.math.BigInteger;
            case "number" -> v instanceof Number;
            case "boolean" -> v instanceof Boolean;
            case "object" -> v instanceof Map;
            case "array" -> v instanceof Collection || v.getClass().isArray();
            default -> true;
        };
        if (!ok) {
            return "type " + type;
        }
        if (v instanceof CharSequence cs) {
            String text = cs.toString();
            Object format = s.get("format");
            if (format != null && !matchesFormat(String.valueOf(format), text)) {
                return "format " + format;
            }
            if (s.get("minLength") instanceof Number n && text.length() < n.intValue()) {
                return "minLength";
            }
            if (s.get("maxLength") instanceof Number n && text.length() > n.intValue()) {
                return "maxLength";
            }
            if (s.get("pattern") instanceof String p && !Pattern.compile(p).matcher(text).find()) {
                return "pattern";
            }
        }
        List<Object> allowed = (List<Object>) s.get("enum");
        if (allowed != null && allowed.stream().noneMatch(a -> String.valueOf(a).equals(String.valueOf(v)))) {
            return "enum";
        }
        return null;
    }

    private static boolean matchesFormat(String format, String text) {
        try {
            switch (format) {
                case "date" -> LocalDate.parse(text);
                case "date-time" -> OffsetDateTime.parse(text);
                case "time" -> java.time.OffsetTime.parse(text);
                case "uuid" -> UUID.fromString(text);
                default -> {
                    // Other formats (email, uri...) aren't checked here.
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
