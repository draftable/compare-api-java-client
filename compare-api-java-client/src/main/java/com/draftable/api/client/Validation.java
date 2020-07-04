package com.draftable.api.client;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Internal methods that validate common parameters, and throw
 * IllegalArgumentExceptions if invalid.
 */
class Validation {

    // region validateAccountId, validateAuthToken

    static void validateAccountId(@Nullable final String accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("`accountId` cannot be null");
        }
        if (accountId.isEmpty()) {
            throw new IllegalArgumentException("`accountId` cannot be an empty string");
        }
    }

    static void validateAuthToken(@Nullable final String authToken) {
        if (authToken == null) {
            throw new IllegalArgumentException("`authToken` cannot be null");
        }
        if (authToken.isEmpty()) {
            throw new IllegalArgumentException("`authToken` cannot be an empty string");
        }
    }

    // endregion validateAccountId, validateAuthToken

    // region validateIdentifier

    private static final int minimumIdentifierLength = 1;
    private static final int maximumIdentifierLength = 1024;

    static void validateIdentifier(@Nullable final String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("`identifier` cannot be null");
        }

        if (identifier.length() < minimumIdentifierLength) {
            throw new IllegalArgumentException(
                    String.format("`identifier` must have at least %d characters", minimumIdentifierLength));
        }

        if (identifier.length() > maximumIdentifierLength) {
            throw new IllegalArgumentException(
                    String.format("`identifier` must have at most %d characters", maximumIdentifierLength));
        }

        for (int i = 0; i < identifier.length(); ++i) {
            final char c = identifier.charAt(i);
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9')
                    && (c != '-' && c != '.' && c != '_')) {
                throw new IllegalArgumentException(
                        "`identifier` can only contain ASCII letters, numbers, and the characters \"-._\"");
            }
        }
    }

    // endregion validateIdentifier

    // region validateFileType

    /** An ordered list of the API's supported file extensions. */
    @Nonnull
    private static final List<String> allowedFileTypes = Arrays.asList(
            // PDFs
            "pdf",
            // Word documents
            "docx", "docm", "doc", "rtf",
            // PowerPoint presentations
            "pptx", "pptm", "ppt");

    /** A set of the API's supported file extensions (all lowercase). */
    @Nonnull
    private static final Set<String> allowedLowerCaseFileTypesSet = new HashSet<>(allowedFileTypes);

    static void validateFileType(@Nullable final String fileType) {
        if (fileType == null) {
            throw new IllegalArgumentException("`fileType` cannot be null");
        }

        final String lowerCaseFileType = fileType.toLowerCase();

        if (!allowedLowerCaseFileTypesSet.contains(lowerCaseFileType)) {
            throw new IllegalArgumentException(
                    String.format("`fileType` must be one of the allowed file types (%s), not \"%s\"",
                            String.join(", ", allowedFileTypes), fileType));
        }
    }

    // endregion validateFileType

    // region validateSourceURL, validateSourceURI

    private static final int maximumSourceURLLength = 2048;

    static void validateSourceURL(@Nullable final String sourceURL) {
        if (sourceURL == null) {
            throw new IllegalArgumentException("`sourceURL` cannot be null");
        }

        if (sourceURL.length() > maximumSourceURLLength) {
            throw new IllegalArgumentException("`sourceURL` must not have more than 2048 characters");
        }

        try {
            new URI(sourceURL);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("`sourceURL` cannot be parsed", ex);
        }
    }

    static void validateSourceURI(@Nullable final URI sourceURI) {
        if (sourceURI == null) {
            throw new IllegalArgumentException("`sourceURI` cannot be null");
        }

        if (sourceURI.toString().length() > maximumSourceURLLength) {
            throw new IllegalArgumentException(
                    "`sourceURI` is too long - as a string URL it exceeds the 2048 character limit");
        }
    }

    // endregion validateSourceURL, validateSourceURI

    // region validateExpires, validateValidUntil

    private static void validateInstant(@Nonnull final String parameterName, @Nullable final Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException(String.format("`%s` cannot be null", parameterName));
        }
        if (instant.isBefore(Instant.now().plus(Duration.ofSeconds(1)))) {
            throw new IllegalArgumentException(String.format("`%s` must be in the future", parameterName));
        }
    }

    private static void validateDuration(@Nonnull final String parameterName, @Nullable final Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException(String.format("`%s` cannot be null", parameterName));
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(String.format("`%s` must have positive duration", parameterName));
        }
    }

    static void validateExpires(@Nullable final Instant expires) {
        validateInstant("expires", expires);
    }

    static void validateValidUntil(@Nullable final Duration validUntil) {
        validateDuration("validUntil", validUntil);
    }

    static void validateValidUntil(@Nullable final Instant validUntil) {
        validateInstant("validUntil", validUntil);
    }

    // endregion validateExpires, validateValidUntil

}
