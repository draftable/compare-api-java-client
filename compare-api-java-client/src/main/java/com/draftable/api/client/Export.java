package com.draftable.api.client;

import javax.annotation.Nonnull;

/*
    Represents a single export of a given comparison
 */
public final class Export{

    /** Identifier of the comparison used for running this export */
    @Nonnull private final String identifier;

    /** Identifier of the export itself (note that it is different from the comparison ID). */
    @Nonnull private final String comparison;

    /** Url of the export */
    @Nonnull private final String url;

    /** Export kind. Supported values: single_page, combined, left, right. */
    @Nonnull private final String kind;

    /** Indicates if the export is Ready. */
    @Nonnull private final boolean ready;

    /** Indicates if the export has failed. */
    @Nonnull private final boolean failed;

    public Export(String identifier, String comparison, String url, String kind, boolean ready, boolean failed) {
        this.identifier = identifier;
        this.comparison = comparison;
        this.url = url;
        this.kind = kind;
        this.ready = ready;
        this.failed = failed;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getComparison() {
        return comparison;
    }

    public String getUrl() {
        return url;
    }

    public String getKind() {
        return kind;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean getFailed() {
        return failed;
    }
}
