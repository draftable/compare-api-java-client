package com.draftable.api.client;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;

/**
 * Represents a comparison that has been created.
 */
@SuppressWarnings("ConstantConditions")
public final class Comparison {

    // region Public: Side class (represents one of the sides)

    /**
     * Represents a file passed as the left or right side of a comparison.
     */
    public static final class Side {
        /** The file's extension as lowercase. */
        @Nonnull
        private final String fileType;
        /** The file's source URL, if it was given by URL. */
        @Nullable
        private final String sourceURL;
        /** The file's display name, if one was given. */
        @Nullable
        private final String displayName;

        /**
         * Creates a representation of a file passed as the left or right side of a
         * comparison.
         *
         * @param fileType    The file's extension, as lowercase.
         * @param sourceURL   The file's source URL, if it was given by URL.
         * @param displayName The file's display name, if one was given.
         */
        Side(@Nonnull final String fileType, @Nullable final String sourceURL, @Nullable final String displayName) {
            if (fileType == null) {
                throw new IllegalArgumentException("`fileType` must not be null");
            }
            this.fileType = fileType;
            this.sourceURL = sourceURL;
            this.displayName = displayName;
        }

        /**
         * Gets the file's extension, as lowercase.
         *
         * @return The file's extension, as lowercase.
         */
        @Nonnull
        public final String getFileType() {
            return fileType;
        }

        /**
         * Gets the file's source URL, if it was given by URL.
         *
         * @return The source URL, if the file was given by URL, otherwise null.
         */
        @Nullable
        public final String getSourceURL() {
            return sourceURL;
        }

        /**
         * Gets the file's display name, if one was given.
         *
         * @return The display name, if one was given, otherwise null.
         */
        @Nullable
        public final String getDisplayName() {
            return displayName;
        }

        @Override
        public final String toString() {
            return String.format("Side(%s, %s, %s)", '"' + fileType + '"',
                    sourceURL == null ? "displayName: null" : '"' + sourceURL + '"',
                    displayName == null ? "displayName: null" : '"' + displayName + '"');
        }
    }

    // endregion Public: Side class (represents one of the sides)

    // region Private fields

    /** The comparison's identifier. */
    @Nonnull
    private final String identifier;

    /** Metadata for the left file. */
    @Nonnull
    private final Side left;

    /** Metadata for the right file. */
    @Nonnull
    private final Side right;

    /** Whether the comparison is public or private. */
    private final boolean isPublic;

    /** When the comparison was created. */
    @Nonnull
    private final Instant creationTime;

    /** When the comparison expires, or null if it doesn't expire. */
    @Nullable
    private final Instant expiryTime;

    /** Whether the comparison has been processed. */
    private final boolean ready;

    /** If the comparison is ready, gives the time at which processing finished. */
    @Nullable
    private final Instant readyTime;

    /**
     * Whether the comparison failed. Is null if the comparison hasn't been
     * processed.
     */
    @Nullable
    private final Boolean failed;

    /**
     * If the comparison failed, gives an error message describing the failure. Null
     * otherwise.
     */
    @Nullable
    private final String errorMessage;

    // endregion Private fields

    // region Public constructor

    /**
     * Creates a representation of a comparison that has been created.
     *
     * @param identifier   The comparison's identifier.
     * @param left         Representation of the left file.
     * @param right        Representation of the right file.
     * @param isPublic     Whether the comparison is public or private.
     * @param creationTime When the comparison was created.
     * @param expiryTime   When the comparison expires, or null if it doesn't
     *                     expire.
     * @param ready        Whether the comparison has been processed.
     * @param readyTime    If the comparison is ready, the time at which it became
     *                     ready.
     * @param failed       Whether the comparison failed. Is null if the comparison
     *                     hasn't been processed.
     * @param errorMessage If the comparison failed, gives an error message
     *                     describing the failure. Null otherwise.
     */
    public Comparison(@Nonnull final String identifier, @Nonnull final Side left, @Nonnull final Side right,
            boolean isPublic, @Nonnull final Instant creationTime, @Nullable final Instant expiryTime,
            final boolean ready, @Nullable Instant readyTime, @Nullable final Boolean failed,
            @Nullable final String errorMessage) {

        if (identifier == null) {
            throw new IllegalArgumentException("`identifier` must not be null");
        }
        if (left == null) {
            throw new IllegalArgumentException("`left` must not be null");
        }
        if (right == null) {
            throw new IllegalArgumentException("`right` must not be null");
        }
        if (creationTime == null) {
            throw new IllegalArgumentException("`creationTime` must not be null");
        }
        if (ready) {
            if (readyTime == null) {
                throw new IllegalArgumentException("`readyTime` must not be null if `ready` is true");
            }
            if (failed == null) {
                throw new IllegalArgumentException("`failed` must not be null if `ready` is true");
            }
            if (failed) {
                if (errorMessage == null) {
                    throw new IllegalArgumentException("`errorMessage` must not be null if `failed` is true");
                }
            } else {
                if (errorMessage != null) {
                    throw new IllegalArgumentException("`errorMessage` must be null if `failed` is false");
                }
            }
        } else {
            if (readyTime != null) {
                throw new IllegalArgumentException("`readyTime` must be null if `ready` is false");
            }
            if (failed != null) {
                throw new IllegalArgumentException("`failed` must be null if `ready` is false");
            }
            if (errorMessage != null) {
                throw new IllegalArgumentException("`errorMessage` must be null if `ready` is false");
            }
        }

        this.identifier = identifier;
        this.left = left;
        this.right = right;
        this.isPublic = isPublic;
        this.creationTime =  creationTime.plusSeconds(0);
        this.expiryTime = expiryTime != null ? expiryTime.plusSeconds(0) : null;
        this.ready = ready;
        this.readyTime = readyTime != null ? readyTime.plusSeconds(0) : null;
        this.failed = failed;
        this.errorMessage = errorMessage;
    }

    // endregion Public constructor

    // region Public getter methods: getIdentifier(), getLeft(), getRight(),
    // getIsPublic(), getCreationTime(), getExpiryTime(), getReady(), getFailed(),
    // getErrorMessage()

    /**
     * Gets the comparison's identifier.
     *
     * @return The comparison's identifier.
     */
    @Nonnull
    public final String getIdentifier() {
        return identifier;
    }

    /**
     * Gets metadata for the left file.
     *
     * @return Metadata for the left file.
     */
    @Nonnull
    public final Side getLeft() {
        return left;
    }

    /**
     * Gets metadata for the right file.
     *
     * @return Metadata for the right file.
     */
    @Nonnull
    public final Side getRight() {
        return right;
    }

    /**
     * Gets whether the comparison is public or private.
     *
     * @return true if the comparison is public, false otherwise.
     */
    public final boolean getIsPublic() {
        return isPublic;
    }

    /**
     * Gets when the comparison was created.
     *
     * @return When the comparison was created.
     */
    @Nonnull
    public final Instant getCreationTime() {
        return creationTime.plusSeconds(0);
    }

    /**
     * Gets when the comparison expires, or null if it doesn't expire.
     *
     * @return When the comparison expires, or null if it doesn't expire.
     */
    @Nullable
    public final Instant getExpiryTime() {
        return expiryTime != null ? expiryTime.plusSeconds(0) : null;
    }

    /**
     * Gets whether the comparison has been processed.
     *
     * @return true if the comparison has been processed, false otherwise.
     */
    public final boolean getReady() {
        return ready;
    }

    /**
     * If the comparison is ready, gives the time at which processing finished.
     *
     * @return When the comparison became ready, or null if it's not ready yet.
     */
    @Nullable
    public final Instant getReadyTime() {
        return readyTime != null ? readyTime.plusSeconds(0) : null;
    }

    /**
     * Gets whether the comparison failed. Is null if the comparison hasn't been
     * processed (i.e. isn't ready).
     *
     * @return true if the comparison failed, false if the comparison is ready and
     *         succeeded, and null if the comparison isn't ready.
     */
    @Nullable
    public final Boolean getFailed() {
        return failed;
    }

    /**
     * If the comparison failed, gets an message describing error. Otherwise,
     * returns null.
     *
     * @return A message describing the error if the comparison has failed. Null if
     *         the comparison isn't ready or succeeded.
     */
    @Nullable
    public final String getErrorMessage() {
        return errorMessage;
    }

    // endregion Public getter methods: getIdentifier(), getLeft(), getRight(),
    // getIsPublic(), getCreationTime(), getExpiryTime(), getReady(), getFailed(),
    // getErrorMessage()

    // region toString()

    @Override
    public final String toString() {
        return String.format(
                "Comparison(identifier: %s, left: %s, right: %s, isPublic: %s, creationTime: %s, expiryTime: %s, ready: %s, failed: %s, errorMessage: %s)",
                identifier, left.toString(), right.toString(), isPublic ? "true" : "false", creationTime.toString(),
                expiryTime == null ? "null" : expiryTime.toString(), ready ? "true" : "false",
                failed == null ? "null" : (failed ? "true" : "false"),
                errorMessage == null ? "null" : '"' + errorMessage + '"');
    }

    // endregion toString()

}
