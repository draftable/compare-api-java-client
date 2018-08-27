# Draftable Compare API - Java Client Library

This is a thin Java client for Draftable's [document comparison API](https://draftable.com/comparison-api).
It wraps the available endpoints, and handles authentication and signing for you.
The library is [available on Maven](http://mavenrepository.com/artifact/com.draftable.api.client/draftable-compare-api)
with artifact ID `draftable-compare-api` (group ID `com.draftable.api.client`).

See the [full API documentation](https://api.draftable.com) for an introduction to the API, usage notes, and other references.

### Getting started - Cloud API

- Sign up for free at [api.draftable.com](https://api.draftable.com) to get your credentials.

- Add `draftable-compare-api` to your project's dependencies.

- Instantiate the client:
    ```
    import com.draftable.api.client.Comparisons;
    import com.draftable.api.client.Comparisons.Side;
    import com.draftable.api.client.Comparison;
    ...
    Comparisons comparisons = new Comparisons(<your account ID>, <your auth token>);
    ```

- Start creating comparisons:
    ```   
    Comparison comparison = comparisons.createComparison(
        Side.create("https://api.draftable.com/static/test-documents/code-of-conduct/left.rtf", "rtf"),
        Side.create("https://api.draftable.com/static/test-documents/code-of-conduct/right.pdf", "pdf")
    );
    
    String viewerURL = comparisons.signedViewerURL(comparison.identifier, Duration.ofMinutes(30), false);

    System.out.println("Comparison created: " + comparison);
    System.out.println("Viewer URL (expires in 30 min): " + viewerURL);
    ```

### Getting started - Enterprise self-hosted

- Your system administrator will provide you with the URL to complete setting up your
  account on your locally installed version of Draftable.

- Once you log in, navigate to the API explorer - the URL will be something like
  https://draftable.your.com/api/api-explorer - and experiment with the API there.

- To start making API calls, make a note of your account credentials from the API explorer page
  or the account credentials page, which have a URL like https://draftable.your.com/account/credientials

- In particular, make a note of the base URL, which will be something like https://draftable.your.com/api/v1
  with no trailing slash at the end.


-----

# Client API

### Dependencies
The client depends on `org.apache.httpcomponents` (in particular, `httpclient`, `httpasyncclient`, and `httpmime` are used to make API requests).
The only other dependency is on `org.json`.

### Design notes

###### Synchronous and asynchronous requests

All requests can be made synchronously or asynchronously (using the methods suffixed with `Async`, e.g. `getAllComparisonsAsync()`).
All asynchronous methods return `CompletableFuture` instances that will either complete successfully,
or with one of the exceptions documented for the synchronous version.

As an example, in normal usage `getAllComparisons()` will only ever throw an `IOException`.
So `getComparisonsAsync()` will only fail with an `IOException`. Here's an example of asynchronously handling the result:

    CompletableFuture<List<Comparison>> allComparisonsFuture = comparisons.getAllComparisonsAsync();

    allComparisonsFuture.whenComplete((allComparisonsList, error) -> {
        if (error != null) {
            // CompletableFuture wraps errors in a CompletionException.
            assert error instanceof CompletionException;
            // The CompletionException's cause should be an IOException.
            assert error.getCause() instanceof IOException;
            
            // Handle the IOException here.
            
        } else {
            assert allComparisonsList != null;
            // Handle the result here.
        }
    }

###### Errors and error handling

The API is designed such that _requests should always succeed_ and _comparisons should always succeed_ in production. This means:
- Exceptions when making requests will only occur upon network failure, or when you provide invalid credentials or data.
- Comparisons will only fail when the files are unreadable, or exceed your account's size limits.

As such, `IOException` is the only checked exception that request methods throw.
Methods will also throw other unchecked exceptions, as documented in javadoc and below.
You may choose to handle these unchecked exceptions, but you should be able to write your application so that they're never thrown.

###### Thread safety

The API client class, `Comparisons`, is completely thread-safe.
If you `close()` it prematurely, further requests will reopen the underlying HTTP clients.

### Initializing the client

The package `draftable-compare-api` provides a module, `com.draftable.api.client`, which provides two classes, `Comparisons` and `Comparison`.

An instance of `Comparisons` lets you create new comparisons, retrieve all or specific comparisons, and delete specific comparisons.

Instances of `Comparison` are returned by methods, and provide metadata for a given comparison.

Accessing the Draftable cloud API requires only the account ID and auth token. Create an instance of `Comparisons` like this:

    import com.draftable.api.client.Comparisons;
    import com.draftable.api.client.Comparison;
    ...
    Comparisons comparisons = new Comparisons(<your account ID>, <your auth token>);

Enterprise self-hosted users need to provide the base URL as a third parameter, like this:

    import com.draftable.api.client.Comparisons;
    import com.draftable.api.client.Comparison;
    ...
    Comparisons comparisons = new Comparisons(<your account ID>, <your auth token>, "http://draftable.your.com/api/v1");


### Getting comparisons

The `Comparisons` instance provides methods:

- `getAllComparisons()` returns a `List<Comparison>` giving metadata for _all your comparisons_, ordered from newest to oldest. This is a potentially expensive operation.

- `getComparison(String identifier)` returns a single `Comparison` object, or raises `Comparisons.ComparisonNotFoundException` if there isn't a comparison with that identifier.

###### Comparison objects

`Comparison` objects have the following properties:

- `identifier`: a `String` giving the identifier.
- `left`, `right`: `Comparison.Side` objects giving information about each side, with properties:
    - `fileType`: the file extension.
    - `sourceURL`  _(optional)_: if the file was specified as a URL, this will be a string with the URL. Otherwise, `null`.
    - `displayName` _(optional)_: the display name, if one was given. Otherwise, `null`.
- `isPublic`: a `boolean` giving whether the comparison is public, or requires authentication to view.
- `creationTime`: an `Instant` giving when the comparison was created.
- `expiryTime` _(optional)_: if the comparison will expire, an `Instant` giving the expiry time. Otherwise, `null` (indicating no expiry).
- `ready`: `boolean` indicating whether the comparison is ready for display.

If a `Comparison` is `ready` (i.e. it has been processed and is ready for display), it will have the following additional properties:
- `readyTime`: an `Instant` giving the time the comparison became ready.
- `failed`: `Boolean` indicating whether the comparison succeeded or failed.
- `errorMessage` _(only present if `failed`)_: a string providing the developer with the reason the comparison failed.

###### Example usage

The following snippet retrieves a specific comparison and prints key data to the console:

    String identifier = "<identifier>";
    
    try {
        Comparison comparison = comparisons.getComparison(identifier);
        assert comparison.getIdentifier().equals(identifier);

        System.out.println(String.format(
            "Comparison '%s' (%s) is %s.",
            identifier,
            comparison.getIsPublic() ? "public" : "private",
            comparison.getReady() ? "ready" : "not ready"
        ));

        if (comparison.getReady()) {
            System.out.println(String.format(
                "The comparison took %s seconds.",
                comparison.getReadyTime().getEpochSecond() - comparison.getCreationTime().getEpochSecond()
            ));

            if (comparison.getFailed()) {
                System.out.println("The comparison failed. Error message:" + comparison.getErrorMessage());
            }
        }

    } catch (Comparisons.ComparisonNotFoundException ex) {
        System.out.println(String.format("Comparison '%s' doesn't exist.", identifier));
    }


### Deleting comparisons

`Comparisons` provides `deleteComparison(String identifier)`, which attempts to delete the comparison with that identifier.

It has no return value, and raises `Comparisons.ComparisonNotFoundException` if there isn't a comparison with that identifier.

###### Example usage

    List<Comparison> allComparisons = comparisons.getAllComparisons();
    List<Comparison> oldestComparisons = allComparisons.subList(Math.max(allComparisons.size() - 10, 0), allComparisons.size());

    System.out.println(String.format("Deleting oldest %s comparisons...", oldestComparisons.size()));

    for (Comparison comparison : oldestComparisons) {
        comparisons.deleteComparison(comparison.getIdentifier());
        System.out.println(String.format("Deleted comparison '%s'.", comparison.getIdentifier()));
    }

### Creating comparisons

`Comparisons` provides `createComparison(left, right, [identifier, isPublic, expires])`, which returns a `Comparison` object representing the newly created comparison.

For a complete, runnable example that creates a new comparison, see file [NewComparison.java](src/main/java/example/NewComparison.java).

###### Creation options

`createComparison` accepts the following arguments:

- `left`, `right`: `Comparisons.Side` objects describing the left and right files. These are described below.
- `identifier` _(optional)_: the identifier to use for the comparison.
    - If specified, the identifier can't clash with an existing comparison. (If so, a `Comparisons.BadRequestException` is thrown.)
    - If left unspecified, the API will automatically generate one for you.
- `public` _(optional)_: whether the comparison is publicly accessible.
    - Defaults to `false`. If `true`, then the comparison viewer can be accessed by anyone, without authentication.
    - See the full API documentation for details.
- `expires` _(optional)_: an `Instant` specifying when the comparison will be automatically deleted.
    - If given, must in the future.
    - Defaults to `null`, meaning the comparison will never expire.

To specify `left` and `right`, create `Comparisons.Side` instances using one of the static constructors.
The full set of overloads are documented in javadoc, but here are the main ones:

- `Comparisons.Side.create(url, fileType, [displayName])`
    - Specifies a file via a URL. You must give a fully qualified URL from which Draftable can download the file.
    - `fileType` is required, given as the file extension
    - `displayName` is an optional name for the file, to be shown in the comparison

- `Comparisons.Side.create(file, fileType, [displayName])`
    - Specifies a file to be uploaded in the request. You can provide a `File`, byte array, or `InputStream`.
    - `fileType` and `displayName` are as before.

###### Supported file types

The following file types are supported:
- PDF: `pdf`
- Word: `docx`, `docm`, `doc`, `rtf`
- PowerPoint: `pptx`, `pptm`, `ppt`

###### Exceptions

If you try to create a `Comparisons.Side` with an invalid `fileType` or malformed `url`, an `IllegalArgumentException` will be thrown.

Exceptions are raised by `createComparison` in the following cases:
- If a parameter is invalid (e.g. `expires` is set to a time in the past), it will throw an `IllegalArgumentException`.
    - All parameters should be validated client-side by this library. If not, a `Comparisons.BadRequestException` will be thrown. Please submit an issue on GitHub if you encounter such behaviour.
- If `identifier` is already in use by another comparison, `Comparisons.BadRequestException` is thrown.

###### Example usage

    File rightFile = new File(...);

    Comparison comparison = comparisons.createComparison(
        Comparisons.Side.create("https://domain.com/path/to/left.pdf", "pdf"),
        Comparisons.Side.create(rightFile),
        // identifier: not specified, so Draftable will generate one
        null,
        // isPublic: false, so that the comparison is private
        false,
        // expires: 30 minutes in the future, so the comparison will be automatically deleted then
        Instant.now().plus(Duration.ofMinutes(30))
    );

    System.out.println("Created comparison: " + comparison);

    // This generates a signed viewer URL that can be used to access the private comparison for the next 10 minutes.
    String viewerURL = comparisons.signedViewerURL(
        // identifier: The identifier of the comparison
        comparison.identifier,
        // validUntil: The amount of time before the link expires
        Duration.ofMinutes(10),
        // wait: Whether the viewer should wait for a comparison with the given identifier to exist.
        //       (This is simply `false` for normal usage.)
        false
    );
    System.out.println("Viewer URL (expires in 10 min): " + viewerURL);



### Displaying comparisons

Comparisons are displayed using a _viewer URL_. See the section on displaying comparisons in the [API documentation](https://api.draftable.com) for details.

Viewer URLs are generated with the following methods:

- `comparisons.publicViewerURL(String identifier, [boolean wait])`
    - Viewer URL for a public comparison with the given `identifier`.
    - `wait` is `false` by default, meaning the viewer will show an error if no such comparison exists.
    - If `wait` is `true`, the viewer will wait for a comparison with the given `identifier` to exist (potentially displaying a loading animation forever).

- `comparisons.signedViewerURL(String identifier, [Instant/Duration validUntil], [boolean wait])`
    - Gets a signed viewer URL for a comparison with the given `identifier`. (The signature is an HMAC based on your credentials.)
    - `validUntil` gives when the URL will expire. It's specified as an `Instant` or a `Duration`.
        - If `validUntil` is `None`, the URL defaults to expiring 30 minutes in the future (more than enough time to load the page). 
    - Again, if `wait` is `true`, the viewer will wait for a comparison with the given `identifier` to exist.


###### Example usage

In this example, we'll start creating a comparison in the background, but immediately direct our user to a viewer.
The comparison viewer will display a loading animation, waiting for the comparison to be created and processed.

    // This generates a unique identifier we can use.
    String identifier = Comparisons.generateIdentifier();

    CompletableFuture<Comparison> future = comparisons.createComparisonAsync(
        Side.create("https://api.draftable.com/static/test-documents/code-of-conduct/left.rtf", "rtf"),
        Side.create("https://api.draftable.com/static/test-documents/code-of-conduct/right.pdf", "pdf"),
        // identifier: the identifier we just generated
        identifier,
        // isPublic: false, for a private comparison
        false,
        // expires: null, so the comparison will never expire
        null
    );

    // At some point, we will have created the comparison.
    // (The operation could take some time if we're uploading files...)
    // In the mean time, we can immediately give the user a viewer URL, using `wait=true`:
    String viewerURL = comparisons.signedViewerURL(identifier, Duration.ofMinutes(30), true);

    // This URL is valid for 30 minutes, and will show a loading screen until the comparison is ready.
    System.out.println("Comparison is being created. View it here: " + viewerURL);

    // For the purposes of this example, we'll just block until the request finishes.
    future.join();


### Utility methods

- `Comparisons.generateIdentifier()` generates a random unique identifier for you to use.


### Proxying and advanced configuration

The underlying `httpclient` and `httpasyncclient` objects are configured to respect system properties. The full list of options considered are given in the [Apache HttpClient documentation](http://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/client/HttpClientBuilder.html). These options allow configuring the use of a proxy server, as well as other request parameters.

-----

That's it! Please report issues you encounter, and we'll work quickly to resolve them. Contact us at [support@draftable.com](mailto://support@draftable.com) if you need assistance.
