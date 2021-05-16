package com.draftable.api.client;

import org.apache.commons.codec.Charsets;
import org.json.JSONArray;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.security.SecureRandom;

/** Internal utility methods. */
class Utils {
    private static final SecureRandom random = new SecureRandom();

    private Utils() {
        throw new AssertionError("`Utils` should never be instantiated");
    }

    @Nullable
    static String getExtension(@Nonnull final String fileName) {
        int lastIndexOfPeriod = fileName.lastIndexOf(".");
        if (lastIndexOfPeriod < 0) {
            return null;
        }
        return fileName.substring(lastIndexOfPeriod + 1);
    }

    @Nonnull
    static String getRandomString(@Nonnull final CharSequence charset, final int length) {
        final StringBuilder builder = new StringBuilder();
        final int chars = charset.length();
        for (int i = 0; i < length; ++i) {
            builder.append(charset.charAt(random.nextInt(chars)));
        }
        return builder.toString();
    }

    // Lowercase hex characters look better in the URL.
    @Nonnull
    private static final char[] hexCharacters = "0123456789abcdef".toCharArray();

    @Nonnull
    private static String toHexString(@Nonnull final byte[] bytes) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(hexCharacters[(b >> 4) & 0xF]).append(hexCharacters[b & 0xF]);
        }
        return builder.toString();
    }

    @Nonnull
    private static byte[] SHA256HMAC(@Nonnull final String secretKey, @Nonnull final String message) {
        try {
            final Mac SHA256HMAC = Mac.getInstance("HmacSHA256");
            SHA256HMAC.init(new SecretKeySpec(secretKey.getBytes(Charsets.UTF_8), "HmacSHA256"));
            return SHA256HMAC.doFinal(message.getBytes(Charsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            // This should never happen.
            throw new RuntimeException(ex);
        } catch (InvalidKeyException ex) {
            // This should never occur - this is when we've passed invalid parameter has
            // been passed for the secret key.
            // We'll throw a description exception anyway.
            throw new RuntimeException("Invalid auth token provided - unable to generate signature.", ex);
        }
    }

    @Nonnull
    static String getViewerURLSignature(@Nonnull final String accountId, @Nonnull final String authToken,
            @Nonnull final String identifier, Instant validUntil) {
        Validation.validateAccountId(accountId);
        Validation.validateAuthToken(authToken);
        Validation.validateIdentifier(identifier);
        Validation.validateValidUntil(validUntil);

        // First we create the policy object, which is as follows:
        // [
        // account_id (string),
        // identifier (string),
        // valid_until (number)
        // ]
        //
        // Then, we serialize it as JSON, with no spaces, to get the policy that we'll
        // sign.

        final String policyJSON = new JSONArray().put(accountId).put(identifier).put(validUntil.getEpochSecond())
                .toString();

        // The signature is the SHA-256 HMAC (hash-based message authentication code) of
        // the JSON policy, using our auth token as the secret key. It's given as hex.
        return toHexString(SHA256HMAC(authToken, policyJSON));
    }
}
