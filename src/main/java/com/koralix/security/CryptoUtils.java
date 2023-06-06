package com.koralix.security;

import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for using common Crypto methods.
 *
 * @since 1.0.0
 * @author JohanVonElectrum
 */
public final class CryptoUtils {

    private CryptoUtils() {
        throw new AssertionError();
    }

    /**
     * Encodes the given data to Base64Url.
     * @param data Data to encode.
     * @return Encoded data.
     */
    public static String b64encode(byte @NotNull [] data) {
        return Base64.getUrlEncoder().encodeToString(data);
    }

    /**
     * Decodes the given Base64Url data.
     * @param data Data to decode.
     * @return Decoded data.
     */
    public static byte @NotNull [] b64decode(@NotNull String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    /**
     * Generates the HMAC SHA256 signature for the given data.
     * @param key Key used to sign the data.
     * @param data Data to sign.
     * @return Token.
     * @throws NoSuchAlgorithmException If the algorithm is not supported.
     * @throws InvalidKeyException If the key is invalid.
     */
    public static @NotNull String encode(@NotNull SecretKeySpec key, @NotNull String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(key);

        return b64encode(sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Verifies the HMAC SHA256 signature for the given data.
     * @param key Key used to sign the data.
     * @param data Data to sign.
     * @param signature Signature to verify.
     * @return True if the signature is valid, false otherwise.
     * @throws NoSuchAlgorithmException If the algorithm is not supported.
     * @throws InvalidKeyException If the key is invalid.
     */
    public static boolean verify(@NotNull SecretKeySpec key, @NotNull String data, @NotNull String signature) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] decodedSignature = b64decode(signature);

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(key);

        byte[] calculatedSignature = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

        return MessageDigest.isEqual(decodedSignature, calculatedSignature);
    }

    /**
     * Generates the SHA256 hash for the given data.
     * @param data Data to hash.
     * @param salt Salt to hash.
     * @return Hashed data.
     * @throws NoSuchAlgorithmException If the algorithm is not supported.
     */
    public static @NotNull String sha256(byte @NotNull [] data, byte @NotNull [] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset();
        digest.update(salt);
        return b64encode(digest.digest(data));
    }

    /**
     * Generates a random hex string of the given length in bytes.
     * @param i Length of the string in bytes.
     * @return Random hex string.
     */
    public static @NotNull String randomHex(int i) {
        byte[] bytes = new byte[i];
        new SecureRandom().nextBytes(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02x", b);
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Generates a random long.
     * @return Random long.
     */
    public static @NotNull Long randomLong() {
        return new SecureRandom().nextLong();
    }
}
