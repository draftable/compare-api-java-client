package com.draftable.api.client;

import org.apache.commons.codec.Charsets;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.util.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * A simplified client for our REST endpoints that supports synchronous and
 * asynchronous HTTP requests. Built on top of Apache's HTTPClient and
 * HTTPAsyncClient libraries.
 */
@SuppressWarnings({ "ConstantConditions", "unused", "WeakerAccess", "SameParameterValue" })
class RESTClient implements Closeable {

    // region Exceptions - HTTP404NotFoundException, HTTP400BadRequestException,
    // HTTPInvalidAuthenticationException, UnknownResponseException

    static class ClientException extends Exception {
        ClientException() {
        }

        ClientException(@Nonnull String details) {
            super(details);
        }
    }

    static class HTTP404NotFoundException extends ClientException {
    }

    static class HTTP400BadRequestException extends ClientException {
        HTTP400BadRequestException(@Nonnull final String details) {
            super(details);
        }
    }

    static class HTTPInvalidAuthenticationException extends ClientException {
        HTTPInvalidAuthenticationException(@Nonnull final String details) {
            super(details);
        }
    }

    static class UnknownResponseException extends ClientException {
        UnknownResponseException(final int statusCode, @Nullable final String details) {
            super(String.format("Unknown response with status code '%d':%n%s", statusCode, details));
        }
    }

    // endregion Exceptions - HTTP404NotFoundException, HTTP400BadRequestException,
    // HTTPInvalidAuthenticationException, UnknownResponseException

    // region Fields - authToken

    @Nonnull
    private final String authToken;

    // endregion Fields - authToken

    // region Constructor

    /**
     * Creates and sets up a new RESTClient with the given authorization token.
     *
     * @param authToken The authorization token to pass in the request headers.
     */
    RESTClient(@Nonnull final String authToken) {
        if (authToken == null) {
            throw new IllegalArgumentException("`authToken` cannot be null");
        }
        this.authToken = authToken;
    }

    // endregion Constructor

    // region getClient(), getAsyncClient(), close()

    @Nonnull
    private final Object clientSync = new Object();
    @Nullable
    private CloseableHttpClient client;

    @Nonnull
    private final Object asyncClientSync = new Object();
    @Nullable
    private CloseableHttpAsyncClient asyncClient;

    @Nonnull
    private static CloseableHttpClient createClient() {
        return HttpClients.createSystem();
    }

    @Nonnull
    private static CloseableHttpAsyncClient createAsyncClient() {
        CloseableHttpAsyncClient asyncClient = HttpAsyncClients.createSystem();
        asyncClient.start();
        return asyncClient;
    }

    @Nonnull
    private HttpClient getClient() {
        HttpClient currentClient = client;
        if (currentClient == null) {
            synchronized (clientSync) {
                currentClient = client;
                if (currentClient == null) {
                    currentClient = client = createClient();
                }
            }
        }
        return currentClient;
    }

    @Nonnull
    private HttpAsyncClient getAsyncClient() {
        HttpAsyncClient currentAsyncClient = asyncClient;
        if (currentAsyncClient == null) {
            synchronized (clientSync) {
                currentAsyncClient = asyncClient;
                if (currentAsyncClient == null) {
                    currentAsyncClient = asyncClient = createAsyncClient();
                }
            }
        }
        return currentAsyncClient;
    }

    /**
     * Closes any open inner HTTP clients, and ends any async event loops.
     *
     * @throws IOException An error occurred closing an HTTP client.
     */
    public void close() throws IOException {
        if (client != null) {
            CloseableHttpClient currentClient;
            synchronized (clientSync) {
                currentClient = client;
                client = null;
            }
            currentClient.close();
        }

        if (asyncClient != null) {
            CloseableHttpAsyncClient currentAsyncClient;
            synchronized (asyncClientSync) {
                currentAsyncClient = asyncClient;
                asyncClient = null;
            }
            currentAsyncClient.close();
        }
    }

    // endregion getClient(), getAsyncClient(), close()

    // region execute(request), executeAsync(request)

    // region setupRequestHeaders(request), getHostForRequest(request)

    private void setupRequestHeaders(@Nonnull final HttpRequestBase request) {
        // These headers are common to all our requests.
        request.setHeader("Authorization", "Token " + authToken);
        request.setHeader("Accept", "application/json");
    }

    private static HttpHost getHostForRequest(@Nonnull final HttpRequestBase request) {
        URI uri = request.getURI();
        return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    }

    // endregion setupRequestHeaders(request), getHostForRequest(request)

    // region consumeResponse(HttpResponse)

    @Nullable
    private static byte[] getResponseBytes(@Nonnull final HttpResponse response) throws IOException {

        HttpEntity responseEntity = response.getEntity();
        if (responseEntity == null) {
            return null;
        }

        InputStream responseStream = responseEntity.getContent();
        if (responseStream == null) {
            return null;
        }

        if (responseStream instanceof ByteArrayInputStream) {
            int size = responseStream.available();
            byte[] bytes = new byte[responseStream.available()];
            int length = responseStream.read(bytes, 0, size);
            assert size == length;
            responseStream.close();
            return bytes;

        } else {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final int bufferSize = 2048;
            byte[] buffer = new byte[bufferSize];

            int bytesRead;
            while ((bytesRead = responseStream.read(buffer, 0, bufferSize)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            byte[] bytes = outputStream.toByteArray();
            outputStream.close();
            responseStream.close();
            return bytes;
        }
    }

    @Nullable
    private static String getResponseString(@Nonnull final HttpResponse response) throws IOException {
        byte[] responseBytes = getResponseBytes(response);
        if (responseBytes == null) {
            return null;
        }
        return new String(responseBytes, Charset.forName("UTF-8"));
    }

    @Nullable
    private static String consumeResponse(@Nonnull final HttpResponse response, final int expectedStatusCode)
            throws HTTP404NotFoundException, HTTP400BadRequestException, HTTPInvalidAuthenticationException,
            UnknownResponseException, IOException {

        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != expectedStatusCode) {
            // Status code is not the success code the callee is expecting.

            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                // 404s are expected, should the client attempt to access non-existent items.
                // We quietly eat up anything sent our way then bail out.
                EntityUtils.consumeQuietly(response.getEntity());
                throw new HTTP404NotFoundException();

            } else if (statusCode == HttpStatus.SC_BAD_REQUEST) {
                // 400s can happen when the client provides a malformed request.
                throw new HTTP400BadRequestException(getResponseString(response));

            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN) {
                // This means that the client's auth token is invalid.
                throw new HTTPInvalidAuthenticationException(getResponseString(response));

            } else {
                // An unknown kind of response.
                throw new UnknownResponseException(statusCode, getResponseString(response));
            }
        }

        return getResponseString(response);
    }

    // endregion consumeResponse(HttpResponse)

    // region execute(request, expectedStatusCode)

    /**
     * Synchronously executes and consumes a given request, returning the String
     * response. Throws an exception upon an unexpected status code.
     *
     * @param request            A HttpRequestBase giving the request to execute.
     *                           This request object is modified and consumed.
     * @param expectedStatusCode The expected response status code. Should be e.g.
     *                           200, 201, 204. Must not be 404, 400, or other error
     *                           codes.
     * @return A String giving the response from the server.
     */
    @Nullable
    private String execute(@Nonnull final HttpRequestBase request, final int expectedStatusCode)
            throws HTTP404NotFoundException, HTTP400BadRequestException, HTTPInvalidAuthenticationException,
            UnknownResponseException, IOException {

        setupRequestHeaders(request);

        HttpClient client = getClient();
        try {
            HttpResponse response = client.execute(getHostForRequest(request), request);
            return consumeResponse(response, expectedStatusCode);
        } finally {
            request.releaseConnection();
        }
    }

    // endregion execute(request, expectedStatusCode)

    // region executeAsync(request, expectedStatusCode)

    private static class AsyncHTTPOperation extends CompletableFuture<String> {

        @Nonnull
        final Future innerFuture;

        AsyncHTTPOperation(@Nonnull HttpAsyncClient asyncClient, @Nonnull HttpRequestBase request,
                int expectedStatusCode) {
            super();

            AsyncHTTPOperation outerFuture = this;

            innerFuture = asyncClient.execute(getHostForRequest(request), request, new FutureCallback<HttpResponse>() {
                @Override
                public void completed(@Nonnull HttpResponse response) {
                    try {
                        outerFuture.completeInternal(consumeResponse(response, expectedStatusCode));
                    } catch (Exception ex) {
                        outerFuture.completeExceptionallyInternal(ex);
                    } finally {
                        request.releaseConnection();
                    }
                }

                @Override
                public void failed(@Nonnull Exception ex) {
                    // This should be an IOException, but it's possible there are others.
                    // If more than an IOException is possible, we should document them or update
                    // the logic accordingly.
                    // ~ James
                    outerFuture.completeExceptionallyInternal(ex);
                }

                @Override
                public void cancelled() {
                    // Nothing to do. It was the outer future that triggered cancellation.
                }
            });
        }

        // Internal methods for setting completion.

        private boolean completeInternal(@Nullable String response) {
            return super.complete(response);
        }

        private boolean completeExceptionallyInternal(@Nonnull Throwable throwable) {
            return super.completeExceptionally(throwable);
        }

        // Public interface. We allow cancellation by passing on to the inner future. We
        // do not allow the outside world to set completion.

        @Override
        public boolean complete(@Nullable String response) {
            throw new UnsupportedOperationException("This CompletableFuture cannot have its completion manually set.");
        }

        @Override
        public boolean completeExceptionally(@Nonnull Throwable throwable) {
            throw new UnsupportedOperationException("This CompletableFuture cannot have its completion manually set.");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return innerFuture.cancel(mayInterruptIfRunning);
        }
    }

    /**
     * Asynchronously executes and consumes a given request, returning the String
     * response. Completes exceptionally upon an unexpected status code or other
     * failure. Exceptions are as
     *
     * @param request            A HttpRequestBase giving the request to execute.
     *                           This request object is modified and consumed.
     * @param expectedStatusCode The expected response status code. Should be e.g.
     *                           200, 201, 204. Must not be 404, 400, or other error
     *                           codes.
     * @return A CompletionStage that will give the String response from the server,
     *         or the error as encountered.
     */
    @Nonnull
    private CompletableFuture<String> executeAsync(@Nonnull final HttpRequestBase request,
            final int expectedStatusCode) {
        setupRequestHeaders(request);
        return new AsyncHTTPOperation(getAsyncClient(), request, expectedStatusCode);
    }

    // endregion executeAsync(request, expectedStatusCode)

    // endregion execute(request), executeAsync(request)

    // region Static ContentBody builders - buildContentBody(File | byte[] |
    // InputStream)

    @Nonnull
    static ContentBody buildContentBody(@Nonnull File file) {
        return new FileBody(file, ContentType.APPLICATION_OCTET_STREAM);
    }

    @Nonnull
    static ContentBody buildContentBody(@Nonnull byte[] data) {
        // Note: We have to give a file name as the third parameter, but for our
        // purposes it doesn't matter what the name is.
        // (Django Rest Framework doesn't seem to like it if we don't give a file name,
        // we need to pass one in here.) ~ James
        return new ByteArrayBody(data, ContentType.APPLICATION_OCTET_STREAM, "filename");
    }

    @Nonnull
    static ContentBody buildContentBody(@Nonnull InputStream stream) {
        // Note: We have to give a file name as the third parameter, but for our
        // purposes it doesn't matter what the name is.
        // (Django Rest Framework doesn't seem to like it if we don't give a file name,
        // we need to pass one in here.) ~ James
        return new InputStreamBody(stream, ContentType.APPLICATION_OCTET_STREAM, "filename");
    }

    // endregion Static ContentBody builders - buildContentBody(File | byte[] |
    // InputStream)

    // region HTTP operations: get(endpoint), getAsync(endpoint), delete(endpoint),
    // deleteAsync(endpoint), post(endpoint, data, files), postAsync(endpoint, data,
    // files)

    // region Request builders: buildGetRequest(endpoint, parameters),
    // buildDeleteRequest(endpoint), buildPostRequest(endpoint, parameters, files)

    /**
     * Builds a GET request for the given endpoint URI, with the given GET
     * parameters.
     *
     * @param endpoint   A string giving the URI of the endpoint to query.
     * @param parameters The GET parameters, or null for no parameters.
     * @return A new HttpGet request instance for the given endpoint and parameters.
     * @throws IllegalArgumentException The given endpoint is not a valid URI.
     */
    @Nonnull
    private static HttpGet buildGetRequest(@Nonnull final String endpoint,
            @Nullable final Map<String, String> parameters) throws IllegalArgumentException {
        return buildGetRequest(URI.create(endpoint), parameters);
    }

    /**
     * Builds a GET request for the given endpoint URI, with the given GET
     * parameters.
     *
     * @param endpoint   A string giving the URI of the endpoint to query.
     * @param parameters The GET parameters, or null for no parameters.
     * @return A new HttpGet request instance for the given endpoint and parameters.
     */
    @Nonnull
    private static HttpGet buildGetRequest(@Nonnull final URI endpoint, @Nullable final Map<String, String> parameters)
            throws IllegalArgumentException {
        if (parameters == null) {
            return new HttpGet(endpoint);
        }

        try {
            URIBuilder uriBuilder = new URIBuilder(endpoint);
            for (final Map.Entry<String, String> entry : parameters.entrySet()) {
                uriBuilder.addParameter(entry.getKey(), entry.getValue());
            }
            return new HttpGet(uriBuilder.build());
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("An invalid endpoint URI was given.", ex);
        }
    }

    /**
     * Builds a DELETE request for the given endpoint URI.
     *
     * @param endpoint A string giving the URI of the endpoint to DELETE.
     * @return A new HttpDelete request instance for the given endpoint.
     * @throws IllegalArgumentException The given endpoint is not a valid URI.
     */
    @Nonnull
    private static HttpDelete buildDeleteRequest(@Nonnull final String endpoint) throws IllegalArgumentException {
        return buildDeleteRequest(URI.create(endpoint));
    }

    /**
     * Builds a DELETE request for the given endpoint URI.
     *
     * @param endpoint A string giving the URI of the endpoint to DELETE.
     * @return A new HttpDelete request instance for the given endpoint.
     */
    @Nonnull
    private static HttpDelete buildDeleteRequest(@Nonnull final URI endpoint) throws IllegalArgumentException {
        return new HttpDelete(endpoint);
    }

    /**
     * Builds a POST request for the given endpoint URI, with the given parameters
     * and content.
     *
     * @param endpoint   A string giving the URI of the endpoint to POST to.
     * @param parameters The string data to provide in the POST request.
     * @param content    Files to provide in the POST request.
     * @return A new HttpPost request instance for the given endpoint and data.
     * @throws IOException              Unable to read the given content.
     * @throws IllegalArgumentException The given endpoint is not a valid URI.
     */
    @Nonnull
    private static HttpPost buildPostRequest(@Nonnull final String endpoint,
            @Nullable final Map<String, String> parameters, @Nullable final Map<String, ContentBody> content)
            throws IOException, IllegalArgumentException {
        return buildPostRequest(URI.create(endpoint), parameters, content);
    }

    /**
     * Builds a POST request for the given endpoint URI, with the given parameters
     * and files.
     *
     * @param endpoint   A string giving the URI of the endpoint to POST to.
     * @param parameters The string data to provide in the POST request.
     * @param content    Files to provide in the POST request.
     * @throws IOException Unable to read the given content.
     * @return A new HttpPost request instance for the given endpoint and data.
     */
    @Nonnull
    private static HttpPost buildPostRequest(@Nonnull final URI endpoint,
            @Nullable final Map<String, String> parameters, @Nullable final Map<String, ContentBody> content)
            throws IOException {
        final HttpPost request = new HttpPost(endpoint);

        if (content != null) {
            final MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.setCharset(Charsets.UTF_8);

            if (parameters != null) {
                for (final String key : parameters.keySet()) {
                    builder.addTextBody(key, parameters.get(key));
                }
            }

            for (final String key : content.keySet()) {
                builder.addPart(key, content.get(key));
            }

            // Unfortunately we can't use the MultipartEntityBuilder's entity straight out
            // of the box with HttpAsyncClient...
            // Instead we buffer the whole thing. It's quite sad, but there isn't source
            // available that does it without the buffering.
            final HttpEntity multipartEntity = builder.build();
            // This ugly wrapper is to force BufferedHttpEntity to actually buffer the
            // entity - we need to make it claim to be not streamable...
            request.setEntity(new BufferedHttpEntity(new HttpEntity() {
                @Override
                public boolean isRepeatable() {
                    return false;
                }

                @Override
                public boolean isChunked() {
                    return multipartEntity.isChunked();
                }

                @Override
                public long getContentLength() {
                    return multipartEntity.getContentLength();
                }

                @Override
                public Header getContentType() {
                    return multipartEntity.getContentType();
                }

                @Override
                public Header getContentEncoding() {
                    return multipartEntity.getContentEncoding();
                }

                @Override
                public InputStream getContent() throws IOException, UnsupportedOperationException {
                    return multipartEntity.getContent();
                }

                @Override
                public void writeTo(OutputStream outputStream) throws IOException {
                    multipartEntity.writeTo(outputStream);
                }

                @Override
                public boolean isStreaming() {
                    return multipartEntity.isStreaming();
                }

                @Deprecated
                public void consumeContent() throws IOException {
                }
            }));

        } else {
            final EntityBuilder builder = EntityBuilder.create();

            final List<NameValuePair> parameterCollection = new ArrayList<>();

            if (parameters != null) {
                for (final Map.Entry<String, String> entry : parameters.entrySet()) {
                    parameterCollection.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
                }
            }

            builder.setParameters(parameterCollection);

            request.setEntity(builder.build());
        }

        return request;
    }

    // endregion Request builders: buildGetRequest(endpoint, parameters),
    // buildDeleteRequest(endpoint), buildPostRequest(endpoint, parameters, files)

    // region get(endpoint, [parameters]), getAsync(endpoint, [parameters])

    /**
     * Synchronously queries a given endpoint with a GET request.
     *
     * @param endpoint The URI to query.
     * @return The response from the server, as a String.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 200 OK.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    @Nullable
    String get(@Nonnull final URI endpoint) throws HTTP404NotFoundException, HTTP400BadRequestException,
            HTTPInvalidAuthenticationException, UnknownResponseException, IOException {
        return get(endpoint, null);
    }

    /**
     * Synchronously queries a given endpoint with a GET request.
     *
     * @param endpoint   The URI to query.
     * @param parameters The GET parameters to pass in the query.
     * @return The response from the server, as a String.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 200 OK.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    @Nullable
    String get(@Nonnull final URI endpoint, @Nullable final Map<String, String> parameters)
            throws HTTP404NotFoundException, HTTP400BadRequestException, HTTPInvalidAuthenticationException,
            UnknownResponseException, IOException {
        return execute(buildGetRequest(endpoint, parameters), HttpStatus.SC_OK);
    }

    /**
     * Synchronously queries a given endpoint with a GET request.
     *
     * @param endpoint The URI to query.
     * @return The response from the server, as a String.
     * @throws IllegalArgumentException           The given endpoint is not a valid
     *                                            URI.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 200 OK.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    @Nullable
    String get(@Nonnull final String endpoint) throws IllegalArgumentException, HTTP404NotFoundException,
            HTTP400BadRequestException, HTTPInvalidAuthenticationException, UnknownResponseException, IOException {
        return get(endpoint, null);
    }

    /**
     * Synchronously queries a given endpoint with a GET request.
     *
     * @param endpoint   The URI to query.
     * @param parameters The GET parameters to pass in the query.
     * @return The response from the server, as a String.
     * @throws IllegalArgumentException           The given endpoint is not a valid
     *                                            URI.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 200 OK.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    @Nullable
    String get(@Nonnull final String endpoint, @Nullable final Map<String, String> parameters)
            throws IllegalArgumentException, HTTP404NotFoundException, HTTP400BadRequestException,
            HTTPInvalidAuthenticationException, UnknownResponseException, IOException {
        return execute(buildGetRequest(endpoint, parameters), HttpStatus.SC_OK);
    }

    /**
     * Asynchronously queries a given endpoint with a GET request.
     *
     * @param endpoint The URI to query.
     * @return A CompletableFuture giving the response from the server, as a String.
     *         Exceptional completions as documented in the synchronous get()
     *         methods are possible.
     */
    @Nonnull
    CompletableFuture<String> getAsync(@Nonnull final URI endpoint) {
        return getAsync(endpoint, null);
    }

    /**
     * Asynchronously queries a given endpoint with a GET request.
     *
     * @param endpoint   The URI to query.
     * @param parameters The GET parameters to pass in the query.
     * @return A CompletableFuture giving the response from the server, as a String.
     *         Exceptional completions as documented in the synchronous get()
     *         methods are possible.
     */
    @Nonnull
    CompletableFuture<String> getAsync(@Nonnull final URI endpoint, @Nullable final Map<String, String> parameters) {
        return executeAsync(buildGetRequest(endpoint, parameters), HttpStatus.SC_OK);
    }

    /**
     * Asynchronously queries a given endpoint with a GET request.
     *
     * @param endpoint The URI to query.
     * @return A CompletableFuture giving the response from the server, as a String.
     *         Exceptional completions as documented in the synchronous get()
     *         methods are possible.
     * @throws IllegalArgumentException The given endpoint is not a valid URI.
     */
    @Nonnull
    CompletableFuture<String> getAsync(@Nonnull final String endpoint) throws IllegalArgumentException {
        return getAsync(endpoint, null);
    }

    /**
     * Asynchronously queries a given endpoint with a GET request.
     *
     * @param endpoint   The URI to query.
     * @param parameters The GET parameters to pass in the query.
     * @return A CompletableFuture giving the response from the server, as a String.
     *         Exceptional completions as documented in the synchronous get()
     *         methods are possible.
     * @throws IllegalArgumentException The given endpoint is not a valid URI.
     */
    @Nonnull
    CompletableFuture<String> getAsync(@Nonnull final String endpoint, @Nullable final Map<String, String> parameters)
            throws IllegalArgumentException {
        return executeAsync(buildGetRequest(endpoint, parameters), HttpStatus.SC_OK);
    }

    // endregion get(endpoint), getAsync(endpoint)

    // region delete(endpoint), deleteAsync(endpoint)

    /**
     * Synchronously submits a DELETE request to a given endpoint.
     *
     * @param endpoint The URI to submit the DELETE request to.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 204 NO
     *                                            CONTENT.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    void delete(@Nonnull final URI endpoint) throws HTTP404NotFoundException, HTTP400BadRequestException,
            HTTPInvalidAuthenticationException, UnknownResponseException, IOException {
        execute(buildDeleteRequest(endpoint), HttpStatus.SC_NO_CONTENT);
    }

    /**
     * Synchronously submits a DELETE request to a given endpoint.
     *
     * @param endpoint The URI to submit the DELETE request to.
     * @throws IllegalArgumentException           The given endpoint is not a valid
     *                                            URI.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 204 NO
     *                                            CONTENT.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    void delete(@Nonnull final String endpoint) throws IllegalArgumentException, HTTP404NotFoundException,
            HTTP400BadRequestException, HTTPInvalidAuthenticationException, UnknownResponseException, IOException {
        execute(buildDeleteRequest(endpoint), HttpStatus.SC_NO_CONTENT);
    }

    /**
     * Asynchronously submits a DELETE request to a given endpoint. Exceptional
     * completions as documented in the synchronous delete() methods are possible.
     *
     * @param endpoint The URI to submit the DELETE request to.
     */
    @Nonnull
    CompletableFuture<Void> deleteAsync(@Nonnull final URI endpoint) {
        // Execute, then consume the result.
        return executeAsync(buildDeleteRequest(endpoint), HttpStatus.SC_NO_CONTENT).thenApply(result -> (Void) null);
    }

    /**
     * Asynchronously submits a DELETE request to a given endpoint. Exceptional
     * completions as documented in the synchronous delete() methods are possible.
     *
     * @param endpoint The URI to submit the DELETE request to.
     * @throws IllegalArgumentException The given endpoint is not a valid URI.
     */
    @Nonnull
    CompletableFuture<Void> deleteAsync(@Nonnull final String endpoint) throws IllegalArgumentException {
        // Execute, then consume the result.
        return executeAsync(buildDeleteRequest(endpoint), HttpStatus.SC_NO_CONTENT).thenApply(result -> (Void) null);
    }

    // endregion delete(endpoint), deleteAsync(endpoint)

    // region post(endpoint, parameters, files), postAsync(endpoint, parameters,
    // files)

    /**
     * Synchronously submits a POST request to a given endpoint.
     *
     * @param endpoint   The URI to submit the POST request to.
     * @param parameters Parameters to include in the POST request body.
     * @param content    Other content (including files) to include in the POST
     *                   request body.
     * @return The response from the server, as a String.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 201 CREATED.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    @Nullable
    String post(@Nonnull final URI endpoint, @Nullable final Map<String, String> parameters,
            @Nullable final Map<String, ContentBody> content) throws HTTP404NotFoundException,
            HTTP400BadRequestException, HTTPInvalidAuthenticationException, UnknownResponseException, IOException {
        return execute(buildPostRequest(endpoint, parameters, content), HttpStatus.SC_CREATED);
    }

    /**
     * Synchronously submits a POST request to a given endpoint.
     *
     * @param endpoint   The URI to submit the POST request to.
     * @param parameters Parameters to include in the POST request body.
     * @param content    Other content (including files) to include in the POST
     *                   request body.
     * @return The response from the server, as a String.
     * @throws IllegalArgumentException           The given endpoint is not a valid
     *                                            URI.
     * @throws HTTP404NotFoundException           The response code was 404 NOT
     *                                            FOUND.
     * @throws HTTP400BadRequestException         The response code was 400 BAD
     *                                            REQUEST.
     * @throws HTTPInvalidAuthenticationException The response code indicated that
     *                                            the given authentication was
     *                                            invalid.
     * @throws UnknownResponseException           The server returned an unknown
     *                                            response. (We expect 201 CREATED.)
     * @throws IOException                        Unable to communicate with the
     *                                            server.
     */
    @Nullable
    String post(@Nonnull final String endpoint, @Nullable final Map<String, String> parameters,
            @Nullable final Map<String, ContentBody> content) throws IllegalArgumentException, HTTP404NotFoundException,
            HTTP400BadRequestException, HTTPInvalidAuthenticationException, UnknownResponseException, IOException {
        return execute(buildPostRequest(endpoint, parameters, content), HttpStatus.SC_CREATED);
    }

    /**
     * Asynchronously submits a POST request to a given endpoint.
     *
     * @param endpoint   The URI to submit the POST request to.
     * @param parameters Parameters to include in the POST request body.
     * @param content    Other content (including files) to include in the POST
     *                   request body.
     * @return A CompletableFuture giving the response from the server, as a String.
     *         Exceptional completions as documented in the synchronous post()
     *         methods are possible.
     */
    @Nonnull
    CompletableFuture<String> postAsync(@Nonnull final URI endpoint, @Nullable final Map<String, String> parameters,
            @Nullable final Map<String, ContentBody> content) {
        try {
            return executeAsync(buildPostRequest(endpoint, parameters, content), HttpStatus.SC_CREATED);
        } catch (IOException ex) {
            CompletableFuture<String> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(ex);
            return failedFuture;
        }
    }

    /**
     * Asynchronously submits a POST request to a given endpoint.
     *
     * @param endpoint   The URI to submit the POST request to.
     * @param parameters Parameters to include in the POST request body.
     * @param content    Other content (including files) to include in the POST
     *                   request body.
     * @return A CompletableFuture giving the response from the server, as a String.
     *         Exceptional completions as documented in the synchronous post()
     *         methods are possible.
     * @throws IllegalArgumentException The given endpoint is not a valid URI.
     */
    @Nonnull
    CompletableFuture<String> postAsync(@Nonnull final String endpoint, @Nullable final Map<String, String> parameters,
            @Nullable final Map<String, ContentBody> content) throws IllegalArgumentException {
        try {
            return executeAsync(buildPostRequest(endpoint, parameters, content), HttpStatus.SC_CREATED);
        } catch (IOException ex) {
            CompletableFuture<String> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(ex);
            return failedFuture;
        }
    }

    // endregion post(endpoint, data, files), postAsync(endpoint, data, files)

    // endregion HTTP operations: get(endpoint), getAsync(endpoint),
    // delete(endpoint), deleteAsync(endpoint), post(endpoint, data, files),
    // postAsync(endpoint, data, files)
}
