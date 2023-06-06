package com.koralix.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Map;import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main class for handling security.
 * Make an instance of this class and use it to generate and verify tokens.
 *
 * @since 1.0.0
 * @author JohanVonElectrum
 */
public class SimpleSecurityProvider {

    private final long salt;
    private final SecretKeySpec secretKey;
    private final Expiration expiration;
    private final Map<String, Instant> creationTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> chainIds = new ConcurrentHashMap<>();
    private final Logger logger;

    /**
     * Creates a new security provider.
     * @param key Key used to sign the tokens.
     * @param salt Salt used to hash the uuid.
     * @param expiration Expiration time for the tokens.
     * @param logger Logger to use.
     */
    public SimpleSecurityProvider(@NotNull String key, long salt, @NotNull Expiration expiration, Logger logger) {
        this.salt = salt;
        secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.expiration = expiration;
        this.logger = logger;
    }

    /**
     * Creates a new security provider.
     * @param key Key used to sign the tokens.
     * @param salt Salt used to hash the uuid.
     * @param expiration Expiration time for the tokens.
     */
    public SimpleSecurityProvider(@NotNull String key, long salt, @NotNull Expiration expiration) {
        this(key, salt, expiration, LoggerFactory.getLogger(SimpleSecurityProvider.class));
    }

    /**
     * Gets the hashed id for the given uuid.
     * @param uuid UUID to hash.
     * @return Hashed id.
     * @throws NoSuchAlgorithmException If the algorithm is not supported.
     */
    public @NotNull String getHid(@NotNull UUID uuid) throws NoSuchAlgorithmException {
        byte[] derivedSalt = ByteBuffer.allocate(32)
                .putLong(salt)
                .putLong(uuid.getLeastSignificantBits())
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits() ^ salt).array();
        byte[] uuidBytes = ByteBuffer.allocate(16)
                .putLong(uuid.getLeastSignificantBits())
                .putLong(uuid.getMostSignificantBits()).array();
        return CryptoUtils.sha256(uuidBytes, derivedSalt);
    }

    /**
     * Generates a new token for the given hashed id.
     * @param hid Hashed id.
     * @return A new token.
     */
    public @NotNull String generateToken(@NotNull String hid) {
        long chainId = chainIds.computeIfAbsent(hid, k -> CryptoUtils.randomLong());
        Instant now = Instant.now();
        String body = hid +
                ":" +
                chainId +
                ":" +
                now.getEpochSecond() +
                "." +
                String.format("%09d", now.getNano()) +
                ":" +
                (expiration.unit.equals(ChronoUnit.FOREVER) ? 0 : Duration.of(expiration.time, expiration.unit).getSeconds());
        body = CryptoUtils.b64encode(body.getBytes(StandardCharsets.UTF_8));

        try {
            String signature = CryptoUtils.encode(secretKey, body);
            creationTimes.put(hid, now);
            this.logger.info("Token generated for hid {} with chain id {} and creation time {}", hid, chainId, now);
            return body + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies the given token.
     * @param token Token to verify.
     * @return True if the token is valid, false otherwise.
     */
    public boolean verifyToken(@NotNull String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            this.logger.info("Token {} is invalid", token);
            return false;
        }

        String body = new String(CryptoUtils.b64decode(parts[0]), StandardCharsets.UTF_8);
        String[] bodyParts = body.split(":");
        if (bodyParts.length != 4) {
            this.logger.info("Token {} is invalid", token);
            return false;
        }

        String hid = bodyParts[0];
        long chainId = Long.parseLong(bodyParts[1]);
        if (!chainIds.containsKey(hid) || chainId != chainIds.get(hid)) {
            this.logger.info("Someone tried to use a token from a different chain for hid {}", hid);
            return false;
        }

        try {
            if (!CryptoUtils.verify(secretKey, parts[0], parts[1])) {
                this.logger.info("Token {} has an invalid signature", token);
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String[] timeParts = bodyParts[2].split("\\.");
        if (timeParts.length != 2) {
            this.logger.info("Token {} is invalid", token);
            return false;
        }
        long seconds = Long.parseLong(timeParts[0]);
        int nano = Integer.parseInt(timeParts[1]);
        Instant instant = Instant.ofEpochSecond(seconds, nano);
        long expirationTime = Long.parseLong(bodyParts[3]);
        if ((expirationTime > 0 && Instant.now().isAfter(instant.plus(expirationTime, ChronoUnit.SECONDS))) ||
                (!creationTimes.containsKey(hid) || !instant.equals(creationTimes.get(hid)))
        ) {
            creationTimes.remove(hid);
            chainIds.remove(hid);
            this.logger.info("Token chain for hid {} was broken", hid);
            return false;
        }

        return true;
    }

    /**
     * Gets the hashed id from the given token if it is valid.
     * The token is valid if it is not expired, the signature is valid and the body is valid.
     * @param token Token to get the hashed id from.
     * @return Hashed id if the token is valid, empty otherwise.
     */
    public Optional<String> getHidIfValid(@NotNull String token) {
        if (!verifyToken(token)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getHidFromToken(token));
    }

    private static @Nullable String getHidFromToken(@NotNull String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return null;
        }

        String body = new String(CryptoUtils.b64decode(parts[0]), StandardCharsets.UTF_8);
        String[] bodyParts = body.split(":");
        if (bodyParts.length != 4) {
            return null;
        }

        return bodyParts[0];
    }

    /**
     * Expiration time for the tokens.
     */
    public record Expiration(long time, TemporalUnit unit) {
        /**
         * Forever expiration time.
         */
        public static final Expiration FOREVER = new Expiration(0, ChronoUnit.FOREVER);
        /**
         * One hour expiration time.
         */
        public static final Expiration ONE_HOUR = new Expiration(1, ChronoUnit.HOURS);

        /**
         * Creates a new expiration time.
         * @param time Time.
         * @param unit Unit.
         * @return A new expiration time.
         */
        public static @NotNull Expiration of(long time, @NotNull TemporalUnit unit) {
            return new Expiration(time, unit);
        }
    }

}
