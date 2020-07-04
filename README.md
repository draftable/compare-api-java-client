Draftable Compare API - Java Client Library
===========================================

[![maven](https://img.shields.io/maven-central/v/com.draftable.api.client/draftable-compare-api)](https://search.maven.org/artifact/com.draftable.api.client/draftable-compare-api)
[![license](https://img.shields.io/github/license/draftable/compare-api-java-client)](https://choosealicense.com/licenses/mit/)

A thin Java client for the [Draftable API](https://draftable.com/rest-api) which wraps all available endpoints and handles authentication and signing.

See the [full API documentation](https://api.draftable.com) for an introduction to the API, usage notes, and other reference material.

- [Requirements](#requirements)
- [Getting started](#getting-started)
- [API reference](#api-reference)
  - [Design notes](#design-notes)
  - [Initializing the client](#initializing-the-client)
  - [Retrieving comparisons](#retrieving-comparisons)
  - [Deleting comparisons](#deleting-comparisons)
  - [Creating comparisons](#creating-comparisons)
  - [Displaying comparisons](#displaying-comparisons)
  - [Utility methods](#utility-methods)
- [Other information](#other-information)
  - [Network & proxy configuration](#network--proxy-configuration)
  - [Self-signed certificates](#self-signed-certificates)

Requirements
------------

- Operating system: Any maintained Linux, macOS, or Windows release
- Java runtime: Java SE 8+ or compatible implementation

Getting started
---------------

- Create a free [API account](https://api.draftable.com)
- Retrieve your [credentials](https://api.draftable.com/account/credentials)
- Add the [draftable-compare-api](https://search.maven.org/search?q=a:draftable-compare-api) library
- Instantiate a client

```java
import com.draftable.api.client.Comparisons;
import com.draftable.api.client.Comparisons.Side;
import com.draftable.api.client.Comparison;

Comparisons comparisons = new Comparisons("<yourAccountId>", "<yourAuthToken>");
```

- Start creating comparisons

```java
Comparison comparison = comparisons.createComparison(
    Side.create("https://api.draftable.com/static/test-documents/code-of-conduct/left.rtf", "rtf"),
    Side.create("https://api.draftable.com/static/test-documents/code-of-conduct/right.pdf", "pdf")
);
System.out.println(String.format("Comparison created: %s", comparison));

// Generate a signed viewer URL to access the private comparison. The expiry
// time defaults to 30 minutes if the validUntil parameter is not provided.
String viewerURL = comparisons.signedViewerURL(comparison.identifier, Duration.ofMinutes(30), false);
System.out.println(String.format("Viewer URL (expires in 30 mins): %s", viewerURL));
```

API reference
-------------

### Design notes

#### Exceptions and error handling

Method calls immediately validate parameters. Parameter validation failures throw `IllegalArgumentException`.

Java exceptions are categorised as either checked or unchecked. In this library:

- I/O failures (e.g. network connectivity) throw a checked `IOException`.
- Unchecked exceptions may be thrown as documented in _Javadoc_ and below.

In practice, while you may elect to handle unchecked exceptions, it should be possible to write your application such that they're never thrown.

#### Synchronous and asynchronous requests

- Requests may be made synchronously, or asynchronously using the methods suffixed with `Async`.
- Asynchronous methods return a `CompletableFuture`, which when awaited, will complete successfully or throw an exception.

#### Thread safety

The API client class, `Comparisons`, is thread-safe. If `close()` is called prematurely future requests will re-open the underlying HTTP clients.

### Initializing the client

The package provides a module, `com.draftable.api.client`, with which a `Comparisons` instance can be created for your API account.

`Comparisons` provides methods to manage the comparisons for your API account and return individual `Comparison` objects.

Creating a `Comparisons` instance differs slightly based on the API endpoint being used:

```java
import com.draftable.api.client.Comparisons;
import com.draftable.api.client.Comparison;

// Draftable API (default endpoint)
Comparisons comparisons = new Comparisons(
    "<yourAccountId>",  // Replace with your API credentials from:
    "<yourAuthToken>"   // https://api.draftable.com/account/credentials
);

// Draftable API regional endpoint or Self-hosted
Comparisons comparisons = new Comparisons(
    "<yourAccountId>",  // Replace with your API credentials from the regional
    "<yourAuthToken>",  // Draftable API endpoint or your Self-hosted container
    'https://draftable.example.com/api/v1'  // Replace with the endpoint URL
);
```

The `Comparisons` instance can be closed by calling `close()`.

For API Self-hosted you may need to [suppress TLS certificate validation](#self-signed-certificates) if the server is using a self-signed certificate (the default).

### Retrieving comparisons

- `getAllComparisons()`  
  Returns a `List<Comparison>` of all your comparisons, ordered from newest to oldest. This is potentially an expensive operation.
- `getComparison(String identifier)`  
  Returns the specified `Comparison` or raises a `Comparisons.ComparisonNotFoundException` exception if the specified comparison identifier does not exist.

`Comparison` objects have the following getter methods:

- `getIdentifier(): String`  
  The unique identifier of the comparison
- `getLeft(): Comparison.Side` / `getRight(): Comparison.Side`  
  Information about each side of the comparison
  - `getFileType(): String`  
    The file extension
  - `getSourceURL(): String`  
    The URL for the file if the original request was specified by URL, otherwise `null`
  - `getDisplayName(): String`  
    The display name for the file if given in the original request, otherwise `null`
- `getIsPublic(): boolean`  
  Indicates if the comparison is public
- `getCreationTime(): Instant`  
  Time in UTC when the comparison was created
- `getExpiryTime(): Instant`  
  The expiry time if the comparison is set to expire, otherwise `null`
- `getReady(): boolean`  
  Indicates if the comparison is ready to display

If a `Comparison` is _ready_ (i.e. it has been processed) the following additional getter methods are meaningful:

- `getReadyTime(): Instant`  
  Time in UTC the comparison became ready
- `getFailed(): boolean`  
  Indicates if comparison processing failed
- `getErrorMessage(): String` _(only present if `failed`)_  
  Reason processing of the comparison failed

#### Example usage

```java
String identifier = "<identifier>";

try {
    Comparison comparison = comparisons.getComparison(identifier);

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
            System.out.println(String.format(
                "The comparison failed with error: %s",
                comparison.getErrorMessage()
            ));
        }
    }
} catch (Comparisons.ComparisonNotFoundException ex) {
    System.out.println(String.format("Comparison '%s' does not exist.", identifier));
}
```

### Deleting comparisons

- `deleteComparison(String identifier)`  
  Returns nothing on successfully deleting the specified comparison or raises a `Comparisons.ComparisonNotFoundException` exception if no such comparison exists.

#### Example usage

```java
List<Comparison> allComparisons = comparisons.getAllComparisons();
List<Comparison> oldestComparisons = allComparisons.subList(Math.max(allComparisons.size() - 10, 0), allComparisons.size());
System.out.println(String.format("Deleting oldest %s comparisons ...", oldestComparisons.size()));

for (Comparison comparison : oldestComparisons) {
    comparisons.deleteComparison(comparison.getIdentifier());
    System.out.println(String.format("Comparison '%s' deleted.", comparison.getIdentifier()));
}
```

### Creating comparisons

- `createComparison(Comparisons.Side left, Comparisons.Side right, String identifier, boolean isPublic, Instant expires)`  
  Returns a `Comparison` representing the newly created comparison.

`createComparison` accepts the following arguments:

- `left` / `right`  
  Describes the left and right files (see following section)
- `identifier` _(nullable)_  
  Identifier to use for the comparison:
  - If specified, the identifier must be unique (i.e. not already be in use)
  - If `null`, the API will automatically generate a unique identifier
- `isPublic`  
  Specifies the comparison visibility:
  - If `false` authentication is required to view the comparison
  - If `true` the comparison can be accessed by anyone with knowledge of the URL
- `expires` _(nullable)_  
  Time at which the comparison will be deleted:
  - If specified, the provided expiry time must be UTC and in the future
  - If `null`, the comparison will never expire (but may be explicitly deleted)

The following exceptions may be raised in addition to [parameter validation exceptions](#exceptions-and-error-handling):

- `BadRequestException`  
  The request could not be processed (e.g. `identifier` already in use)

#### Creating comparison sides

The two most common static constructors for creating `Comparisons.Side` objects are:

- `Comparisons.Side.create(File file, String fileType, String displayName)`  
  Returns a `Comparisons.Side` for a locally accessible file.
- `Comparisons.Side.create(String sourceURL, String fileType, String displayName)`  
  Returns a `Comparisons.Side` for a remotely accessible file referenced by URL.

These constructors accept the following arguments:

- `file`  
  A file object to be read and uploaded
- `sourceURL`  
  The URL from which the server will download the file
- `fileType`  
  The type of file being submitted:
  - PDF: `pdf`
  - Word: `docx`, `docm`, `doc`, `rtf`
  - PowerPoint: `pptx`, `pptm`, `ppt`
- `displayName` _(nullable)_  
  The name of the file shown in the comparison viewer

#### Example usage

```java
File rightFile = new File(...);

Comparison comparison = comparisons.createComparison(
    Comparisons.Side.create("https://domain.com/path/to/left.pdf", "pdf"),
    Comparisons.Side.create(new File("path/to/right/file.docx"), "docx"),
    // identifier: null indicates the library should generate an identifier
    null,
    // isPublic: false ensures the comparison requires a signed URL to access
    false,
    // expires: The system should delete this comparison after two hours
    Instant.now().plus(Duration.ofHours(2))
);
System.out.println(String.format("Created comparison: %s", comparison));
```

### Displaying comparisons

- `publicViewerURL(String identifier, boolean wait)`  
  Generates a public viewer URL for the specified comparison
- `signedViewerURL(String identifier, Instant|Duration validUntil, boolean wait)`  
  Generates a signed viewer URL for the specified comparison

Both methods use the following common parameters:

- `identifier`  
  Identifier of the comparison for which to generate a _viewer URL_
- `wait`  
  Specifies the behaviour of the viewer if the provided comparison does not exist
  - If `false`, the viewer will show an error if the `identifier` does not exist
  - If `true`, the viewer will wait for a comparison with the provided `identifier` to exist  
    Note this will result in a perpetual loading animation if the `identifier` is never created

The `signedViewerURL` method also supports the following parameters:

- `validUntil` _(nullable)_  
  Time at which the URL will expire (no longer load)
  - If specified, the provided expiry time must be UTC and in the future
  - If `null`, the URL will be generated with the default 30 minute expiry

See the displaying comparisons section in the [API documentation](https://api.draftable.com) for additional details.

#### Example usage

```java
String identifier = "<identifier>";

// Retrieve a signed viewer URL which is valid for 1 hour. The viewer will wait
// for the comparison to exist in the event processing has not yet completed.
String viewerURL = comparisons.signedViewerURL(identifier, Duration.ofHours(1), true);
System.out.println(String.format("Viewer URL (expires in 1 hour): %s", viewerURL));
```

### Utility methods

- `generateIdentifier()`
  Generates a random unique comparison identifier

Other information
-----------------

### Network & proxy configuration

The library utilises the Apache `httpclient` and `httpasyncclient` packages for performing HTTP requests, which respect configured system properties pertaining to network configuration. The full list of consulted system properties can be found in the [HttpClientBuilder class](https://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/impl/client/HttpClientBuilder.html) documentation.

### Self-signed certificates

If connecting to an API Self-hosted endpoint which is using a self-signed certificate (the default) you will need to suppress certificate validation. The recommended approach is to import the self-signed certificate into the _KeyStore_ of your Java installation, which will ensure the Java runtime trusts the certificate.

Alternatively, you can suppress certificate validation by creating customised `X509TrustManager` and `HostnameVerifier` instances. A sample implementation can be found in the example project within the `SetupIgnoreSSLCheck()` method.

Disabling certificate validation in production environments is strongly discouraged as it significantly lowers security. We only recommend using this approach in development environments if configuring a CA signed certificate for API Self-hosted is not possible.
