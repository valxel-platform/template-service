package com.valxel.service.observability;

import java.util.regex.Pattern;

/** URL paths fit for logs, audit details and error reports: identifiers masked. */
public final class Paths {

    private static final Pattern ID_LIKE = Pattern.compile(".*\\d.*");

    private Paths() {}

    /**
     * The path with every segment that holds a digit (an id, an MRN, a date) replaced by
     * {@code {id}}: /api/v1/patients/MRN-55821/notes becomes /api/v1/patients/{id}/notes. A full
     * URL keeps its scheme and host.
     */
    public static String withoutIds(String url) {
        if (url == null) {
            return null;
        }
        int start = 0;
        int scheme = url.indexOf("://");
        if (scheme >= 0) {
            int slash = url.indexOf('/', scheme + 3);
            if (slash < 0) {
                return url;
            }
            start = slash;
        }
        String[] parts = url.substring(start).split("/", -1);
        StringBuilder out = new StringBuilder(url.substring(0, start));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(ID_LIKE.matcher(parts[i]).matches() ? "{id}" : parts[i]);
        }
        return out.toString();
    }
}
