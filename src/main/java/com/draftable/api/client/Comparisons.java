package com.draftable.api.client;

import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;


/**
 * Client for the `comparisons` endpoint in the Draftable Comparison API.
 */
@SuppressWarnings({"ConstantConditions", "WeakerAccess", "TryWithIdenticalCatches", "unused", "SameParameterValue"})
public class Comparisons implements Closeable {

    //region Private: URLs

    private static class URLs {
        @Nonnull private static final String apiBase = "https://api.draftable.com/v1";

        @Nonnull static final String comparisons = apiBase + "/comparisons";

        @Nonnull
        static String comparison(@Nonnull String identifier) {
            return comparisons + "/" + identifier;
        }

        @Nonnull
        static String comparisonViewer(@Nonnull String accountId, @Nonnull String identifier) {
            return comparisons + "/viewer/" + accountId + "/" + identifier;
        }
    }

    //endregion Private: URLs

    //region Public exceptions - ComparisonNotFoundException, BadRequestException, InvalidAuthenticationException, UnknownErrorException

    /**
     * Thrown when the a {@link #getComparison(String)} or {@link #deleteComparison(String)} request is made for a non-existent comparison.
     */
    public static class ComparisonNotFoundException extends RuntimeException {
        @Nonnull private final String accountId;
        @Nonnull private final String identifier;

        ComparisonNotFoundException(@Nonnull final String accountId, @Nonnull final String identifier) {
            super(String.format("No comparison with identifier \"%s\" exists for account ID \"%s\".", identifier, accountId));
            this.accountId = accountId;
            this.identifier = identifier;
        }

        @Nonnull
        public String getAccountId() {
            return accountId;
        }

        @Nonnull
        public String getIdentifier() {
            return identifier;
        }
    }

    /**
     * Thrown when a {@link #createComparison} request is made with bad parameters.
     * (For instance, when a source URL is invalid, or the given comparison identifier is already in use.)
     */
    public static class BadRequestException extends RuntimeException {
        BadRequestException(@Nullable final String details) {
            super(details);
        }
    }

    /**
     * Thrown when a request is made, but the provided auth token is invalid.
     */
    public static class InvalidAuthenticationException extends RuntimeException {
        public InvalidAuthenticationException(@Nullable final String details) {
            super(details);
        }
    }

    /**
     * Thrown when an unknown error occurs while making a request.
     * This should never occur, but the library guarantees that any extraneous exceptions are wrapped in an {@link UnknownErrorException}.
     */
    public static class UnknownErrorException extends RuntimeException {
        public UnknownErrorException(@Nonnull final Throwable error) {
            super(error);
        }
    }

    //endregion Public exceptions - ComparisonNotFoundException, BadRequestException, InvalidAuthenticationException, UnknownErrorException

    //region Private fields - accountId, authToken, client

    @Nonnull private final String accountId;
    @Nonnull private final String authToken;
    @Nonnull private final RESTClient client;

    //endregion Private fields - accountId, authToken, client

    //region Public constructor

    /**
     * Constructs a {@link Comparisons} instance for the given credentials, which can then be used to make API requests.
     * @param accountId The account ID to make requests for. This is located in your <a href="https://api.draftable.com/account">account console</a>.
     * @param authToken The auth token for the account. This is located in your <a href="https://api.draftable.com/account">account console</a>.
     */
    public Comparisons(@Nonnull String accountId, @Nonnull String authToken) {
        Validation.validateAccountId(accountId);
        Validation.validateAuthToken(authToken);

        this.accountId = accountId;
        this.authToken = authToken;

        client = new RESTClient(authToken);
    }

    //endregion Public constructor

    //region close()

    /**
     * Closes any open inner HTTP clients, and ends any async event loops.
     * @throws IOException An error occurred closing an HTTP client.
     */
    @Override
    public void close() throws IOException {
        client.close();
    }

    //endregion close()

    //region Methods - getAllComparisons[Async], getComparison[Async], deleteComparison[Async], createComparison[Async]

    //region Private: comparisonFromJSONResponse(responseString), comparisonListFromJSONResponse(responseString)

    @Nonnull
    private static Comparison.Side comparisonSideFromJSONObject(@Nonnull JSONObject side) throws JSONException {
        return new Comparison.Side(
            side.getString("file_type"),
            side.has("source_url") ? side.getString("source_url") : null,
            side.has("display_name") ? side.getString("display_name") : null
        );
    }

    @Nonnull
    private static Comparison comparisonFromJSONObject(@Nonnull JSONObject comparison) throws JSONException {
        JSONObject left = comparison.getJSONObject("left");
        JSONObject right = comparison.getJSONObject("right");
        boolean ready = comparison.getBoolean("ready");
        boolean failed = ready && comparison.getBoolean("failed");

        return new Comparison(
            comparison.getString("identifier"),
            comparisonSideFromJSONObject(left),
            comparisonSideFromJSONObject(right),
            comparison.has("public") && comparison.getBoolean("public"),
            Instant.parse(comparison.getString("creation_time")),
            comparison.has("expiry_time") ? Instant.parse(comparison.getString("expiry_time")) : null,
            ready,
            ready ? Instant.parse(comparison.getString("ready_time")) : null,
            ready ? failed : null,
            failed ? comparison.getString("error_message") : null
        );
    }

    @Nonnull
    private static Comparison comparisonFromJSONResponse(@Nonnull String responseString) throws JSONException {
        return comparisonFromJSONObject(new JSONObject(responseString));
    }

    @Nonnull
    private static List<Comparison> comparisonListFromJSONResponse(@Nonnull String responseString) throws JSONException {
        JSONObject response = new JSONObject(responseString);
        JSONArray results = response.getJSONArray("results");

        List<Comparison> comparisons = new ArrayList<>();
        for (int i = 0; i < results.length(); ++i) {
            comparisons.add(comparisonFromJSONObject(results.getJSONObject(i)));
        }
        return comparisons;
    }

    //endregion Private: comparisonFromJSONResponse(responseString), comparisonListFromJSONResponse(responseString)

    //region getAllComparisons(), getAllComparisonsAsync()

    /**
     * Synchronously gets a list of all of the account's comparisons.
     * @return A {@link List List&lt;Comparison&gt;} giving all of the account's comparisons.
     * @throws IOException If an error occurs communicating with the server.
     * @throws InvalidAuthenticationException If the given auth token is invalid.
     * @throws UnknownErrorException If an unknown error occurs internally. This should never be thrown, but guarantees that no other kinds of exceptions are thrown.
     */
    @Nonnull
    public List<Comparison> getAllComparisons() throws IOException, InvalidAuthenticationException, UnknownErrorException {
        try {
            return comparisonListFromJSONResponse(client.get(URLs.comparisons));
        } catch (IOException ex) {
            throw ex;
        } catch (RESTClient.HTTPInvalidAuthenticationException ex) {
            throw new InvalidAuthenticationException(ex.getMessage());
        } catch (Throwable ex) {
            throw new UnknownErrorException(ex);
        }
    }

    /**
     * Asynchronously gets a list of all of the account's comparisons.
     * @return A {@link CompletableFuture CompletableFuture&lt;List&lt;Comparison&gt;&gt;} that will complete with a list all of the account's comparisons, or with one of the exceptions documented in {@link #getAllComparisons()}.
     */
    @Nonnull
    public CompletableFuture<List<Comparison>> getAllComparisonsAsync() {
        return client.getAsync(URLs.comparisons).thenApply(Comparisons::comparisonListFromJSONResponse).exceptionally(error -> {
            if (error instanceof CompletionException) {
                // Errors seem to be wrapped in a CompletionException - perhaps all the time, or perhaps only when one is thrown when
                // executing a callback. We check for them just to be safe, and unwrap them to get the cause.
                error = error.getCause();
            }
            if (error instanceof IOException) {
                // Preserve the error. We wrap it in a CompletionException to make it throwable from here.
                // (Note: CompletableFuture internally checks if things are already wrapped in a CompletionException, and leaves them alone if so.)
                throw new CompletionException(error);
            } else if (error instanceof RESTClient.HTTPInvalidAuthenticationException) {
                // Override error with InvalidAuthenticationException.
                throw new InvalidAuthenticationException(error.getMessage());
            } else {
                // Unknown error. Override with our UnknownErrorException.
                throw new UnknownErrorException(error);
            }
        });
    }

    //endregion getAllComparisons(), getAllComparisonsAsync()

    //region getComparison(identifier), getComparisonAsync(identifier)

    /**
     * Synchronously gets metadata for a given comparison.
     * @param identifier The comparison's identifier.
     * @return A {@link Comparison} giving the comparison's metadata.
     * @throws ComparisonNotFoundException If no comparison with the given identifier exists.
     * @throws IOException If an error occurs communicating with the server.
     * @throws InvalidAuthenticationException If the given auth token is invalid.
     * @throws UnknownErrorException If an unknown error occurs internally. This should never be thrown, but guarantees that no other kinds of exceptions are thrown.
     */
    @Nonnull
    public Comparison getComparison(@Nonnull String identifier) throws ComparisonNotFoundException, IOException, InvalidAuthenticationException, UnknownErrorException {
        Validation.validateIdentifier(identifier);
        try {
            return comparisonFromJSONResponse(client.get(URLs.comparison(identifier)));
        } catch (RESTClient.HTTP404NotFoundException ex) {
            throw new ComparisonNotFoundException(accountId, identifier);
        } catch (IOException ex) {
            throw ex;
        } catch (RESTClient.HTTPInvalidAuthenticationException ex) {
            throw new InvalidAuthenticationException(ex.getMessage());
        } catch (Throwable ex) {
            throw new UnknownErrorException(ex);
        }
    }

    /**
     * Asynchronously gets metadata for a given comparison.
     * @param identifier The comparison's identifier.
     * @return A {@link CompletableFuture CompletableFuture&lt;Comparison&gt;} that will complete with a {@link Comparison} giving the metadata, or with one of the exceptions documented in {@link #getComparison}.
     */
    @Nonnull
    public CompletableFuture<Comparison> getComparisonAsync(@Nonnull String identifier) {
        Validation.validateIdentifier(identifier);
        return client.getAsync(URLs.comparison(identifier)).thenApply(Comparisons::comparisonFromJSONResponse).exceptionally(error -> {
            if (error instanceof CompletionException) {
                // Errors seem to be wrapped in a CompletionException - perhaps all the time, or perhaps only when one is thrown when
                // executing a callback. We check for them just to be safe, and unwrap them to get the cause.
                error = error.getCause();
            }
            if (error instanceof RESTClient.HTTP404NotFoundException) {
                // Override error with ComparisonNotFoundException.
                throw new ComparisonNotFoundException(accountId, identifier);
            } else if (error instanceof IOException) {
                // Preserve the error. We wrap it in a CompletionException to make it throwable from here.
                // (Note: CompletableFuture internally checks if things are already wrapped in a CompletionException, and leaves them alone if so.)
                throw new CompletionException(error);
            } else if (error instanceof RESTClient.HTTPInvalidAuthenticationException) {
                // Override error with InvalidAuthenticationException.
                throw new InvalidAuthenticationException(error.getMessage());
            } else {
                // Unknown error. Override with our UnknownErrorException.
                throw new UnknownErrorException(error);
            }
        });
    }

    //endregion getComparison(identifier), getComparisonAsync(identifier)

    //region deleteComparison(identifier), deleteComparisonAsync(identifier)

    /**
     * Synchronously deletes a given comparison.
     * @param identifier The comparison's identifier.
     * @throws ComparisonNotFoundException If no comparison with the given identifier exists.
     * @throws IOException If an error occurs communicating with the server.
     * @throws InvalidAuthenticationException If the given auth token is invalid.
     * @throws UnknownErrorException If an unknown error occurs internally. This should never be thrown, but guarantees that no other kinds of exceptions are thrown.
     */
    public void deleteComparison(@Nonnull String identifier) throws ComparisonNotFoundException, IOException, InvalidAuthenticationException, UnknownErrorException {
        Validation.validateIdentifier(identifier);
        try {
            client.delete(URLs.comparison(identifier));
        } catch (RESTClient.HTTP404NotFoundException ex) {
            throw new ComparisonNotFoundException(accountId, identifier);
        } catch (IOException ex) {
            throw ex;
        } catch (RESTClient.HTTPInvalidAuthenticationException ex) {
            throw new InvalidAuthenticationException(ex.getMessage());
        } catch (Throwable ex) {
            throw new UnknownErrorException(ex);
        }
    }

    /**
     * Asynchronously deletes a given comparison.
     * @param identifier The comparison's identifier.
     * @return A {@link CompletableFuture} that will complete with {@link Void} if the comparison is successfully deleted, or with one of the exceptions documented in {@link #deleteComparison}.
     */
    @Nonnull
    public CompletableFuture<Void> deleteComparisonAsync(@Nonnull String identifier) {
        Validation.validateIdentifier(identifier);
        return client.deleteAsync(URLs.comparison(identifier)).exceptionally(error -> {
            if (error instanceof CompletionException) {
                // Errors seem to be wrapped in a CompletionException - perhaps all the time, or perhaps only when one is thrown when
                // executing a callback. We check for them just to be safe, and unwrap them to get the cause.
                error = error.getCause();
            }
            if (error instanceof RESTClient.HTTP404NotFoundException) {
                // Override error with ComparisonNotFoundException.
                throw new ComparisonNotFoundException(accountId, identifier);
            } else if (error instanceof IOException) {
                // Preserve the error. We wrap it in a CompletionException to make it throwable from here.
                // (Note: CompletableFuture internally checks if things are already wrapped in a CompletionException, and leaves them alone if so.)
                throw new CompletionException(error);
            } else if (error instanceof RESTClient.HTTPInvalidAuthenticationException) {
                // Override error with InvalidAuthenticationException.
                throw new InvalidAuthenticationException(error.getMessage());
            } else {
                // Unknown error. Override with our UnknownErrorException.
                throw new UnknownErrorException(error);
            }
        });
    }

    //endregion deleteComparison(identifier), deleteComparisonAsync(identifier)

    //region createComparison(...), createComparisonAsync(...)

    //region Side (represents sides of a new comparison)

    /**
     * Represents a file passed in as one side of a comparison. {@link Side} instances provided to {@link #createComparison} to provide the left and right files.
     */
    public static final class Side {

        //region Private fields and constructor

        @Nullable private final ContentBody content;
        @Nullable private final String sourceURL;
        @Nonnull private final String fileType;
        @Nullable private final String displayName;

        private Side(@Nullable final ContentBody content, @Nullable final String sourceURL, @Nonnull final String fileType, @Nullable final String displayName) {
            // We should have exactly one of `content` and `sourceURL` specified.
            assert (content != null && sourceURL == null) || (content == null && sourceURL != null);
            // `fileType` should not be null.
            assert fileType != null;

            this.content = content;
            this.sourceURL = sourceURL;
            this.fileType = fileType.toLowerCase();
            this.displayName = displayName;
        }

        //endregion Private fields and constructor

        //region Package-private getter methods - getContent(), getSourceURL(), getFileType(), getDisplayName()

        /**
         * A ContentBody giving the file content, or null if the file is specified as a URL.
         * @return A ContentBody giving the file content, or null if the file is specified as a URL.
         */
        @Nullable
        final ContentBody getContent() {
            return content;
        }

        /**
         * The URL specifying the file, or null if the file is given directly as content.
         * @return A String giving the URL for the file, or null if the file is given directly as content.
         */
        @Nullable
        final String getSourceURL() {
            return sourceURL;
        }

        /**
         * The file type for this file.
         * @return A String giving the file extension of this file.
         */
        @Nonnull
        final String getFileType() {
            return fileType;
        }

        /**
         * The display name given for the file, or null if unspecified.
         * @return A String giving the display name for the file, or null if unspecified.
         */
        @Nullable
        final String getDisplayName() {
            return displayName;
        }

        //endregion Package-private getter methods - getContent(), getSourceURL(), getFileType(), getDisplayName()

        //region Public static constructors - create(...) overloads

        //region create(sourceURL | sourceURI, fileType, [displayName])

        /**
         * Creates a {@link Side} for a file provided by a URL.
         * @param sourceURL The URL at which the file can be accessed by the Draftable servers.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @param displayName An optional name for the file, to be displayed in the comparison.
         * @return A {@link Side} instance representing the given source URL and file information.
         */
        @Nonnull
        public static Side create(@Nonnull String sourceURL, @Nonnull String fileType, @Nullable String displayName) {
            Validation.validateSourceURL(sourceURL);
            Validation.validateFileType(fileType);
            return new Side(null, sourceURL, fileType, displayName);
        }

        /**
         * Creates a {@link Side} for a file provided by a URL.
         * @param sourceURL The URL at which the file can be accessed by the Draftable servers.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @return A {@link Side} instance representing the given source URL and file information.
         */
        @Nonnull
        public static Side create(@Nonnull String sourceURL, @Nonnull String fileType) {
            return create(sourceURL, fileType, null);
        }

        /**
         * Creates a {@link Side} for a file provided by a URI.
         * @param sourceURI The {@link URI} at which the file can be accessed by the Draftable servers.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @param displayName An optional name for the file, to be displayed in the comparison.
         * @return A {@link Side} instance representing the given source URI and file information.
         */
        @Nonnull
        public static Side create(@Nonnull URI sourceURI, @Nonnull String fileType, @Nullable String displayName) {
            Validation.validateSourceURI(sourceURI);
            Validation.validateFileType(fileType);
            return new Side(null, sourceURI.toString(), fileType, displayName);
        }

        /**
         * Creates a {@link Side} for a file provided by a URI.
         * @param sourceURI The {@link URI} at which the file can be accessed by the Draftable servers.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @return A {@link Side} instance representing the given source URI and file information.
         */
        @Nonnull
        public static Side create(@Nonnull URI sourceURI, @Nonnull String fileType) {
            return create(sourceURI, fileType, null);
        }

        //endregion create(sourceURL | sourceURI, fileType, [displayName])

        //region create(file | fileBytes | fileStream, fileType, [displayName])

        /**
         * Internal method - creates a {@link Side} for a file provided by a given {@link ContentBody}.
         * @param content The {@link ContentBody} at which the file can be accessed by the Draftable servers.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @param displayName An optional name for the file, to be displayed in the comparison.
         * @return A {@link Side} instance representing the given file content and information.
         */
        @Nonnull
        private static Side create(@Nonnull ContentBody content, @Nonnull String fileType, @Nullable String displayName) {
            return new Side(content, null, fileType, displayName);
        }

        /**
         * Creates a {@link Side} for a file provided by a given {@link File} instance.
         * @param file The {@link File} object providing the content.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @param displayName An optional name for the file, to be displayed in the comparison.
         * @return A {@link Side} instance representing the given {@link File} and file information.
         */
        @Nonnull
        public static Side create(@Nonnull File file, @Nonnull String fileType, @Nullable String displayName) {
            if (file == null) {
                throw new IllegalArgumentException("`file` cannot be null");
            }
            Validation.validateFileType(fileType);

            return create(RESTClient.buildContentBody(file), fileType, displayName);
        }

        /**
         * Creates a {@link Side} for a file provided by a given {@link File} instance.
         * @param file The {@link File} object providing the content.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @return A {@link Side} instance representing the given {@link File} and file information.
         */
        @Nonnull
        public static Side create(@Nonnull File file, @Nonnull String fileType) {
            return create(file, fileType, null);
        }

        /**
         * Creates a {@link Side} for a file provided by a given {@link File} instance, with an inferred file type and no display name.
         * @param file The {@link File} object providing the content.
         * @return A {@link Side} instance representing the given {@link File}, with an inferred file type and no display name.
         */
        @Nonnull
        public static Side create(@Nonnull File file) {
            if (file == null) {
                throw new IllegalArgumentException("`file` cannot be null");
            }

            final String fileName = file.getName();
            if (fileName == null || fileName.isEmpty()) {
                throw new IllegalArgumentException("If `fileType` is not provided, the given `file` must have a non-empty name from which we can infer its type.");
            }

            final String fileType = Utils.getExtension(fileName);
            if (fileType == null || fileType.isEmpty()) {
                throw new IllegalArgumentException("If `fileType` is not provided, the given `file` must have a name with a file extension, but it has none.");
            }

            try {
                Validation.validateFileType(fileType);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("The file type inferred from `file` is invalid.", ex);
            }

            return create(file, fileType, null);
        }

        /**
         * Creates a {@link Side} for a file provided by as a byte array.
         * @param fileBytes The byte array providing the file's content.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @param displayName An optional name for the file, to be displayed in the comparison.
         * @return A {@link Side} instance representing the a file with the given content and information.
         */
        @Nonnull
        public static Side create(@Nonnull byte[] fileBytes, @Nonnull String fileType, @Nullable String displayName) {
            if (fileBytes == null) {
                throw new IllegalArgumentException("`fileBytes` cannot be null");
            }
            Validation.validateFileType(fileType);
            return create(RESTClient.buildContentBody(fileBytes), fileType, displayName);
        }

        /**
         * Creates a {@link Side} for a file provided by as a byte array.
         * @param fileBytes The byte array providing the file's content.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @return A {@link Side} instance representing the a file with the given content and information.
         */
        @Nonnull
        public static Side create(@Nonnull byte[] fileBytes, @Nonnull String fileType) {
            return create(fileBytes, fileType, null);
        }

        /**
         * Creates a {@link Side} for a file provided by as an {@link InputStream}.
         * @param fileStream The {@link InputStream} providing the file's content.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @param displayName An optional name for the file, to be displayed in the comparison.
         * @return A {@link Side} instance representing the a file with the given content and information.
         */
        @Nonnull
        public static Side create(@Nonnull InputStream fileStream, @Nonnull String fileType, @Nullable String displayName) {
            if (fileStream == null) {
                throw new IllegalArgumentException("`fileStream` cannot be null");
            }
            Validation.validateFileType(fileType);
            return create(RESTClient.buildContentBody(fileStream), fileType, displayName);
        }

        /**
         * Creates a {@link Side} for a file provided by as an {@link InputStream}.
         * @param fileStream The {@link InputStream} providing the file's content.
         * @param fileType The file's extension. This must be one of the API's supported file extensions (PDF, Word, PowerPoint).
         * @return A {@link Side} instance representing the a file with the given content and information.
         */
        @Nonnull
        public static Side create(@Nonnull InputStream fileStream, @Nonnull String fileType) {
            return create(fileStream, fileType, null);
        }

        //endregion create(file | fileBytes | fileStream, fileType, [displayName])

        //endregion Public static constructors - create(...) overloads

    }

    //endregion Side (represents sides of a new comparison)

    //region Private helpers: getComparisonsPostParameters(left, right, identifier, isPublic, expires), getComparisonsPostContent(left, right)

    //region addComparisonsPostParametersForSide(...), addComparisonsPostContentForSide(...)

    private static void addComparisonsPostParametersForSide(@Nonnull Map<String, String> parameters, @Nonnull String sideName, @Nonnull Side side) {
        if (side.getSourceURL() != null) {
            parameters.put(sideName + ".source_url", side.getSourceURL());
        }

        parameters.put(sideName + ".file_type", side.getFileType());

        if (side.getDisplayName() != null) {
            parameters.put(sideName + ".display_name", side.getDisplayName());
        }
    }

    private static void addComparisonsPostContentForSide(@Nonnull Map<String, ContentBody> content, @Nonnull String sideName, @Nonnull Side side) {
        if (side.getContent() != null) {
            content.put(sideName + ".file", side.getContent());
        }
    }

    //endregion addComparisonsPostParametersForSide(...), addComparisonsPostContentForSide(...)

    @Nonnull
    private static Map<String, String> getComparisonsPostParameters(@Nonnull Side left, @Nonnull Side right, @Nullable String identifier, boolean isPublic, @Nullable Instant expires) {
        Map<String, String> parameters = new HashMap<>();

        addComparisonsPostParametersForSide(parameters, "left", left);
        addComparisonsPostParametersForSide(parameters, "right", right);

        if (identifier != null && !identifier.isEmpty()) {
            parameters.put("identifier", identifier);
        }

        if (isPublic) {
            parameters.put("public", "true");
        }

        if (expires != null) {
            parameters.put("expiry_time", expires.toString());
        }

        return parameters;
    }

    @Nonnull
    private static Map<String, ContentBody> getComparisonsPostContent(@Nonnull Side left, @Nonnull Side right) {
        Map<String, ContentBody> content = new HashMap<>();

        addComparisonsPostContentForSide(content, "left", left);
        addComparisonsPostContentForSide(content, "right", right);

        return content;
    }

    //endregion Private helpers: getComparisonsPostParameters(left, right, identifier, isPublic, expires), getComparisonsPostContent(left, right)

    /**
     * Synchronously creates a *private* comparison that never expires with the given sides and an automatically generated identifier.
     * @param left A {@link Side} representing the left file.
     * @param right A {@link Side} representing the right file.
     * @return A {@link Comparison} instance representing the newly created comparison.
     * @throws IOException If an error occurs communicating with the server.
     * @throws InvalidAuthenticationException If the given auth token is invalid.
     * @throws UnknownErrorException If an unknown error occurs internally. This should never be thrown, but guarantees that no other kinds of exceptions are thrown.
     */
    @Nonnull
    public Comparison createComparison(@Nonnull Side left, @Nonnull Side right)
            throws IOException, InvalidAuthenticationException, UnknownErrorException {
        return createComparison(left, right, null, false, null);
    }

    /**
     * Synchronously creates a comparison with the given sides and properties.
     * @param left A {@link Side} representing the left file.
     * @param right A {@link Side} representing the right file.
     * @param identifier The identifier to use, or null to use an automatically generated one. If you provide an identifier that clashes with an existing comparison, a {@link BadRequestException} is thrown.
     * @param isPublic Whether the comparison is publicly accessible, or requires authentication to view.
     * @param expires An {@link Instant} at which the comparison will expire and be automatically deleted, or null for no expiry. If provided, the expiry time must be in the future.
     * @return A {@link Comparison} instance representing the newly created comparison.
     * @throws BadRequestException If you provide an identifier that is already in use, or other invalid information.
     * @throws IOException If an error occurs communicating with the server.
     * @throws InvalidAuthenticationException If the given auth token is invalid.
     * @throws UnknownErrorException If an unknown error occurs internally. This should never be thrown, but guarantees that no other kinds of exceptions are thrown.
     */
    @Nonnull
    public Comparison createComparison(@Nonnull Side left, @Nonnull Side right, @Nullable String identifier, boolean isPublic, @Nullable Instant expires)
            throws BadRequestException, IOException, InvalidAuthenticationException, UnknownErrorException {

        if (identifier != null) {
            Validation.validateIdentifier(identifier);
        }
        if (expires != null) {
            Validation.validateExpires(expires);
        }

        try {
            return comparisonFromJSONResponse(
                    client.post(URLs.comparisons, getComparisonsPostParameters(left, right, identifier, isPublic, expires), getComparisonsPostContent(left, right)));
        } catch (RESTClient.HTTP400BadRequestException ex) {
            throw new BadRequestException(ex.getMessage());
        } catch (IOException ex) {
            throw ex;
        } catch (RESTClient.HTTPInvalidAuthenticationException ex) {
            throw new InvalidAuthenticationException(ex.getMessage());
        } catch (Throwable ex) {
            throw new UnknownErrorException(ex);
        }
    }

    /**
     * Asynchronously creates a *private* comparison that never expires with the given sides and an automatically generated identifier.
     * @param left A {@link Side} representing the left file.
     * @param right A {@link Side} representing the right file.
     * @return A {@link CompletableFuture CompletableFuture&lt;Comparison&gt;} that will complete with the newly created comparison, or an exception as documented in {@link #createComparison(Side, Side)}.
     */
    @Nonnull
    public CompletableFuture<Comparison> createComparisonAsync(@Nonnull Side left, @Nonnull Side right) {
        return createComparisonAsync(left, right, null, false, null);
    }

    /**
     * Asynchronously creates a comparison with the given sides and properties.
     * @param left A {@link Side} representing the left file.
     * @param right A {@link Side} representing the right file.
     * @param identifier The identifier to use, or null to use an automatically generated one. If you provide an identifier that clashes with an existing comparison, a {@link BadRequestException} is thrown.
     * @param isPublic Whether the comparison is publicly accessible, or requires authentication to view.
     * @param expires An {@link Instant} at which the comparison will expire and be automatically deleted, or null for no expiry. If provided, the expiry time must be in the future.
     * @return A {@link CompletableFuture CompletableFuture&lt;Comparison&gt;} that will complete with the newly created comparison, or an exception as documented in {@link #createComparison(Side, Side, String, boolean, Instant)}.
     */
    @Nonnull
    public CompletableFuture<Comparison> createComparisonAsync(@Nonnull Side left, @Nonnull Side right, @Nullable String identifier, boolean isPublic, @Nullable Instant expires) {

        if (identifier != null) {
            Validation.validateIdentifier(identifier);
        }
        if (expires != null) {
            Validation.validateExpires(expires);
        }

        return client.postAsync(URLs.comparisons, getComparisonsPostParameters(left, right, identifier, isPublic, expires), getComparisonsPostContent(left, right))
                     .thenApply(Comparisons::comparisonFromJSONResponse).exceptionally(error -> {
            if (error instanceof CompletionException) {
                // Errors seem to be wrapped in a CompletionException - perhaps all the time, or perhaps only when one is thrown when
                // executing a callback. We check for them just to be safe, and unwrap them to get the cause.
                error = error.getCause();
            }
            if (error instanceof RESTClient.HTTP400BadRequestException) {
                // Override error with BadRequestException.
                throw new BadRequestException(error.getMessage());
            } else if (error instanceof IOException) {
                // Preserve the error. We wrap it in a CompletionException to make it throwable from here.
                // (Note: CompletableFuture internally checks if things are already wrapped in a CompletionException, and leaves them alone if so.)
                throw new CompletionException(error);
            } else if (error instanceof RESTClient.HTTPInvalidAuthenticationException) {
                // Override error with InvalidAuthenticationException.
                throw new InvalidAuthenticationException(error.getMessage());
            } else {
                // Unknown error. Override with our UnknownErrorException.
                throw new UnknownErrorException(error);
            }
        });
    }

    //endregion createComparison(...), createComparisonAsync(...)

    //endregion Methods - getAllComparisons[Async], getComparison[Async], deleteComparison[Async], createComparison[Async]

    //region Viewer URLs - publicViewerURL(identifier, [wait]), signedViewerURL(identifier, [validUntil, wait])

    @Nonnull
    public String publicViewerURL(@Nonnull final String identifier) {
        return publicViewerURL(identifier, false);
    }

    @Nonnull
    public String publicViewerURL(@Nonnull final String identifier, final boolean wait) {
        Validation.validateIdentifier(identifier);
        return URLs.comparisonViewer(accountId, identifier) + (wait ? "?wait" : "");
    }

    @Nonnull
    public String signedViewerURL(@Nonnull final String identifier) {
        return signedViewerURL(identifier, Duration.ofMinutes(30), false);
    }

    @Nonnull
    public String signedViewerURL(@Nonnull final String identifier, @Nonnull final Duration validUntil, boolean wait) {
        Validation.validateValidUntil(validUntil);
        return signedViewerURL(identifier, Instant.now().plus(validUntil), wait);
    }

    @Nonnull
    public String signedViewerURL(@Nonnull final String identifier, @Nonnull final Instant validUntil, boolean wait) {
        Validation.validateIdentifier(identifier);
        Validation.validateValidUntil(validUntil);

        try {
            return new URIBuilder(URLs.comparisonViewer(accountId, identifier))
                    .addParameter("valid_until", Long.toString(validUntil.getEpochSecond()))
                    .addParameter("signature", Utils.getViewerURLSignature(accountId, authToken, identifier, validUntil))
                    .toString() + (wait ? "&wait" : "");
        } catch (URISyntaxException ex) {
            // This should never happen - in this case the base URL for the comparison viewer was invalid.
            throw new RuntimeException(ex);
        }
    }

    //endregion Viewer URLs - publicViewerURL(identifier, [wait]), signedViewerURL(identifier, [validUntil, wait])

    //region Helpers - generateIdentifier()

    @Nonnull
    private static final String randomIdentifierCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // Sufficiently long to guarantee we never experience clashes in practice.
    private static final int randomIdentifierLength = 12;

    @Nonnull
    public static String generateIdentifier() {
        return Utils.getRandomString(randomIdentifierCharacters, randomIdentifierLength);
    }

    //endregion Helpers - generateIdentifier()

}
