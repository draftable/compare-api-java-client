package example;

import com.draftable.api.client.Comparison;
import com.draftable.api.client.Comparisons;
import com.draftable.api.client.KnownURLs;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Program {
    // Cloud API credentials
    private static final String CloudAccountId = "";
    private static final String CloudAuthToken = "";

    // Self-hosted API credentials
    private static final String SelfHostedAccountId = "";
    private static final String SelfHostedAuthToken = "";
    private static final String SelfHostedBaseUrl = "";

    public static void main(String[] args) {
        System.out.println("Starting the test run ...");

        try {
            RunComparisonInCloud();
            RunComparisonWithSelfHosted();
        } catch (Exception e) {
            System.out.println("Failure occurred during test run:");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Finished the test run.");
    }

    private static void RunComparisonInCloud() throws IOException, InterruptedException, TimeoutException {
        RunTestsCore("CLOUD", CloudAccountId, CloudAuthToken, KnownURLs.CloudBaseURL);
    }

    private static void RunComparisonWithSelfHosted() throws IOException, InterruptedException, TimeoutException {
        // Disable certificate & hostname validation.
        //
        // This should *NEVER* be used in production!
        // SetupIgnoreSSLCheck();

        RunTestsCore("APISH", SelfHostedAccountId, SelfHostedAuthToken, SelfHostedBaseUrl);
    }

    private static void RunTestsCore(String label, String accountId, String authToken, String apiUrl)
            throws IOException, InterruptedException, TimeoutException {
        if (StringIsNullOrEmpty(accountId)) {
            throw new IllegalArgumentException("AccountId must be provided.");
        }

        if (StringIsNullOrEmpty(authToken)) {
            throw new IllegalArgumentException("AuthToken must be provided.");
        }

        if (StringIsNullOrEmpty(apiUrl)) {
            throw new IllegalArgumentException("ApiUrl must be provided.");
        }

        Comparisons comparisons = new Comparisons(accountId, authToken, apiUrl);

        List<Comparison> comparisonsList = comparisons.getAllComparisons();
        System.out.println(String.format("[%s] Existing comparisons: %d", label, comparisonsList.size()));

        System.out.println(String.format("[%s] Creating comparison from URLs ...", label));
        String comparisonId1 = CreateComparison(label, comparisons, new UrlComparisonCreator());

        System.out.println(String.format("[%s] Creating comparison from files ...", label));
        String comparisonId2 = CreateComparison(label, comparisons, new DiskComparisonCreator());

        comparisons.deleteComparison(comparisonId1);
        System.out.println(String.format("[%s] Deleted comparison with ID: %s", label, comparisonId1));
        comparisons.deleteComparison(comparisonId2);
        System.out.println(String.format("[%s] Deleted comparison with ID: %s", label, comparisonId2));

        comparisons.close();
    }

    private static String CreateComparison(String label, Comparisons comparisons, IComparisonCreator comparisonCreator)
            throws IOException, InterruptedException, TimeoutException {
        String indent = String.join("", Collections.nCopies(label.length() + 2, " "));

        Comparison comparison = comparisonCreator.CreateComparison(comparisons);
        String comparisonId = comparison.getIdentifier();

        System.out.println(String.format("%s ID: %s", indent, comparisonId));
        System.out.println(String.format("%s Public URL: %s", indent, comparisons.publicViewerURL(comparisonId)));
        System.out.println(String.format("%s Signed URL: %s", indent, comparisons.signedViewerURL(comparisonId)));

        System.out.print(String.format("%s Waiting for comparison ..", indent));
        int timeoutSecs = 0;
        do {
            if (timeoutSecs > 20) {
                System.out.println(" timeout!");
                throw new TimeoutException("Timeout exceeded while waiting for comparison.");
            }

            timeoutSecs++;
            TimeUnit.SECONDS.sleep(1);
            System.out.print(".");
            comparison = comparisons.getComparison(comparisonId);
        } while (!comparison.getReady());
        System.out.println(" ready.");

        if (comparison.getFailed()) {
            System.out.println(
                    String.format("%s Comparison failed with message: %s", indent, comparison.getErrorMessage()));
        } else {
            System.out.println(String.format("%s Comparison succeeded!", indent));
        }

        return comparisonId;
    }

    private static Boolean StringIsNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    private static void SetupIgnoreSSLCheck() {
        // Create a TrustManager which performs no validation
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }
        } };

        // Install the TrustManager for HTTPS connections
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create a HostnameVerifier which always succeeds
        HostnameVerifier trustAllHosts = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the HostnameVerifier for HTTPS connections
        HttpsURLConnection.setDefaultHostnameVerifier(trustAllHosts);
    }
}

interface IComparisonCreator {
    Comparison CreateComparison(Comparisons comparisons) throws IOException;
}

class UrlComparisonCreator implements IComparisonCreator {
    @Override
    public Comparison CreateComparison(Comparisons comparisons) throws IOException {
        String identifier = Comparisons.generateIdentifier();

        return comparisons.createComparison(
                Comparisons.Side.create("https://api.draftable.com/static/test-documents/paper/left.pdf", "pdf"),
                Comparisons.Side.create("https://api.draftable.com/static/test-documents/paper/right.pdf", "pdf"),
                identifier, true, Instant.now().plusSeconds(30 * 60));
    }
}

class DiskComparisonCreator implements IComparisonCreator {
    @Override
    public Comparison CreateComparison(Comparisons comparisons) throws IOException {
        String identifier = Comparisons.generateIdentifier();

        // TODO: Include test documents as resources & fix file paths
        return comparisons.createComparison(Comparisons.Side.create(new File("C:\\draftable\\testing\\old.pdf")),
                Comparisons.Side.create(new File("C:\\draftable\\testing\\new.pdf")), identifier, true,
                Instant.now().plusSeconds(30 * 60));
    }
}
