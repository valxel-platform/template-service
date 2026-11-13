package com.valxel.service.audit;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;

/** An in-memory audit sink for tests; {@code fail} makes it refuse records. */
public class RecordingSink implements AuditSink {

    public final List<String> keys = new ArrayList<>();
    public final List<DocumentContext> events = new ArrayList<>();
    public boolean fail;

    @Override
    public void publish(String key, String json) {
        if (fail) {
            throw new IllegalStateException("audit sink down");
        }
        keys.add(key);
        events.add(JsonPath.parse(json));
    }

    public static Auditor auditor(RecordingSink sink, boolean required) {
        AuditProperties props = new AuditProperties(null, null, null, required, null, "emr", "test", "phi", null);
        return new Auditor(sink, props, "template-service", java.time.Clock.systemUTC(), () -> new String[] {"t1", "s1"});
    }
}
