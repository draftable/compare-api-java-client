package com.draftable.api.client;

import javax.annotation.Nonnull;

/*
    Represents a single export of a given comparison
 */
public final class Export{

    /** Identifier of the export itself (note that it is different from the comparison ID). */
    @Nonnull private final String identifier;

    /** Identifier of the comparison used for running this export. */
    @Nonnull private final String comparison;

    /** Url of the export. Note that this is set to null if the export is not ready yet. */
    private final String url;

    /** Export kind. Supported values: SINGLE_PAGE, COMBINED, LEFT, RIGHT. */
    private final ExportKind kind;

    /** Indicates if the export is ready. */
    private final boolean ready;

    /** Indicates if the export has failed. Note that it is set to null when the export is not ready. */
    private final Boolean failed;

    /** Error message for failed exports. This is set to null for successful exports. */
    private final String errorMessage;

    public Export(String identifier, String comparison, String url, ExportKind kind, boolean ready, Boolean failed, String errorMessage) {
        this.identifier = identifier;
        this.comparison = comparison;
        this.url = url;
        this.kind = kind;
        this.ready = ready;
        this.failed = failed;
        this.errorMessage = errorMessage;
    }

    /** Gets identifier of the export */
    public String getIdentifier() {
        return identifier;
    }

    /** Gets identifier of the comparison used for running this export */
    public String getComparison() {
        return comparison;
    }

    /** Gets export url */
    public String getUrl() {
        return url;
    }

    /** Gets export kind. Supported values: SINGLE_PAGE, COMBINED, LEFT, RIGHT. . */
    public ExportKind getKind() {
        return kind;
    }

    /** Gets indicator if the export is ready. */
    public boolean isReady() {
        return ready;
    }

    /** Gets indicator if the export has failed. */
    public Boolean getFailed() {
        return failed;
    }

    /** Gets error message for failed exports. Returns null for successful exports. */
    public String getErrorMessage(){
        return errorMessage;
    }
}
