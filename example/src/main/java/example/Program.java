package example;

import com.draftable.api.client.Comparison;
import com.draftable.api.client.Comparisons;
import com.draftable.api.client.KnownURLs;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Program {

    // TODO, fill these fields with proper values
    private static String CloudAccountId = "";
    private static String CloudAuthToken = "";
    private static String SelfHostedAccountId = "";
    private static String SelfHostedAuthToken = "";
    private static String SelfHostedBaseUrl = "";

    public static void main(String[] args) {

        System.out.println("Starting the test run");

        try {
            RunComparisonInCloud();
            RunComparisonWithSelfHosted();
        } catch (Exception e) {
            System.out.println("Failure occured when running the test");
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void RunComparisonWithSelfHosted() throws IOException, InterruptedException, TimeoutException {
        if (StringNullOrEmpty(SelfHostedBaseUrl)) {
            throw new IllegalArgumentException("To continue, you must specify Self-Hosted base URL");
        }

        // Run this line to ignore SSL certificate validation (but be careful with that,
        // it should NEVER be done in production).
        SetupIgnoreSSLCheck();

        RunTestsCore("SELF-HOSTED", SelfHostedAccountId, SelfHostedAuthToken, SelfHostedBaseUrl);
    }

    private static void RunComparisonInCloud() throws IOException, InterruptedException, TimeoutException {
        RunTestsCore("CLOUD", CloudAccountId, CloudAuthToken, KnownURLs.CloudBaseURL);
    }

    private static void RunTestsCore(String label, String accountId, String authToken, String compareServiceBaseUrl)
            throws IOException, InterruptedException, TimeoutException {
        if (StringNullOrEmpty(accountId)) {
            throw new IllegalArgumentException("AccountId must be configured to run the tests");
        }

        if (StringNullOrEmpty(authToken)) {
            throw new IllegalArgumentException("AuthToken must be configured to run the tests");
        }

        Comparisons comparisons = new Comparisons(accountId, authToken, compareServiceBaseUrl);

        List<Comparison> list = comparisons.getAllComparisons();
        int count1 = list.size();
        System.out.println(String.format("[%s] Comparisons count: %d", label, count1));

        String comparisonId1 = CreateComparison(label, comparisons, new UrlComparisonCreator());
        String comparisonId2 = CreateComparison(label, comparisons, new DiskComparisonCreator());

        int count2 = comparisons.getAllComparisons().size();
        System.out.println(String.format("[%s] Comparisons count: %d", label, count2));

        comparisons.deleteComparison(comparisonId1);
        comparisons.deleteComparison(comparisonId2);
        int count3 = comparisons.getAllComparisons().size();
        System.out.println(String.format("[%s] After delete, count: %d", label, count3));

        comparisons.close();
    }

    private static String CreateComparison(String label, Comparisons comparisons, IComparisonCreator comparisonCreator)
            throws IOException, InterruptedException, TimeoutException {

        Comparison newComparison = comparisonCreator.CreateComparison(comparisons);
        String newId = newComparison.getIdentifier();
        System.out.println(String.format("[%s] New comparison: %s, isReady: %b, public url: %s, signed url: %s", label,
                newId, newComparison.getReady(), comparisons.publicViewerURL(newId),
                comparisons.signedViewerURL(newId)));

        int timeoutCount = 0;
        while (!newComparison.getReady()) {
            if (timeoutCount > 20) {
                throw new TimeoutException("Timeout exceeded while waiting for comparison to get ready");
            }
            TimeUnit.SECONDS.sleep(1);
            newComparison = comparisons.getComparison(newId);
            timeoutCount++;
        }

        Comparison comparisonAgain = comparisons.getComparison(newId);
        System.out.println(
                String.format("[%s] Retrieved again: %s, isReady: %b, has failed: %b, error message: %s", label, newId,
                        comparisonAgain.getReady(), comparisonAgain.getFailed(), comparisonAgain.getErrorMessage()));
        return newId;
    }

    private static Boolean StringNullOrEmpty(String x) {
        return x == null || x.isEmpty();
    }

    private static void SetupIgnoreSSLCheck() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        } };

        // Install the all-trusting trust manager
        SSLContext sc = null;
        try {
            sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());

        } catch (Exception e) {
            e.printStackTrace();
        }

        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
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

        // TODO: use proper file paths
        return comparisons.createComparison(Comparisons.Side.create(new File("C:\\draftable\\testing\\old.pdf")),
                Comparisons.Side.create(new File("C:\\draftable\\testing\\new.pdf")), identifier, true,
                Instant.now().plusSeconds(30 * 60));
    }
}
