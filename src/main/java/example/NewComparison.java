/**
 * Example program that creates a new comparison using the cloud API between two documents
 * identified by either local file path or URL, and prints a viewer URL to the console.
 *
 * Usage:
 *
 *     $ ACCOUNT_ID=XXX AUTH_TOKEN=YYY java NewComparison ./doc/version-1.pdf ./docs/version-2.docx
 *
 * For self-hosted installations, also provide the BASE_URL environment variable, which
 * will look like `https://your-url-or-ip-address/api/v1` -- with no trailing slash.
 *
 * See `usage() below or run without arguments for more.
 */
package example;

import com.draftable.api.client.Comparison;
import com.draftable.api.client.Comparisons;
import com.draftable.api.client.Comparisons.Side;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

public class NewComparison {

    public static void main(String[] args) {
        String accountId = System.getenv("ACCOUNT_ID"); // From https://api.draftable.com/account/credentials under "Account ID"
        String authToken = System.getenv("AUTH_TOKEN"); // From the same page, under "Auth Token"
        String baseUrl = System.getenv("BASE_URL"); // Omit this to use normal cloud API

        if (accountId == null || accountId.trim().isEmpty()) {
            usage("ACCOUNT_ID missing. See https://api.draftable.com/account/credentials");
        }

        if (authToken == null || authToken.trim().isEmpty()) {
            usage("AUTH_TOKEN missing. See https://api.draftable.com/account/credentials");
        }

        if (baseUrl != null && !baseUrl.startsWith("http")) {
            usage("BASE_URL specified but does not start with http(s)");
        }

        if (args.length < 2) {
            usage("Specify two (left then right) file-or-url paths");
        }

        Comparisons comparisons = new Comparisons(accountId, authToken, baseUrl);

        try {
            Side leftSide = createSide(args[0]); // can be a full URL starting with http/https, or a path
            Side rightSide = createSide(args[1]);
            Comparison comparison = comparisons.createComparison(leftSide, rightSide);
            String viewerURL = comparisons.signedViewerURL(comparison.getIdentifier(), Duration.ofMinutes(30), false);

            System.out.println("Comparison created: " + comparison);
            System.out.println("Viewer URL (expires in 30 min): " + viewerURL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void usage(String error) {
        System.err.println("usage: NewComparison <left-url-or-path> <right-url-or-path>\n");
        System.err.println("Set the following environment:");
        System.err.println("  ACCOUNT_ID=<account id from https://api.draftable.com/account/credentials under 'Account ID'");
        System.err.println("  AUTH_TOKEN=<token from the same page, under 'Auth Token'\n");
        System.err.println("Optionally, set:");
        System.err.println("  BASE_URL=<url such as https://.../v1 (no trailing lash)");
        if (error != null || !error.isEmpty()) {
            System.err.println("\nERROR: " + error);
        }
        System.exit(1);
    }

    private static String getExtension(String path) {
        return path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "";
    }

    private static Side createSide(String urlOrPath) throws IOException {
        String extension = getExtension(urlOrPath);
        System.out.println(String.format("Creating side '%s' (file type '%s')", urlOrPath, extension));

        // If the urlOrPath doesn't start with http, assume it's a file path.
        if (urlOrPath.startsWith("http")) {
            return Side.create(urlOrPath, extension);
        }

        // Uncomment this version to load content as a byte array
        return Side.create(Files.readAllBytes(Paths.get(urlOrPath)), extension);

        // Uncomment this version to load content as a File object
        //return Side.create(new File(urlOrPath), extension);
    }
}
