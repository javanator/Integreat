package com.integreat.integreatme.config;

import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Single place that both ISSUES and VERIFIES the app's JWTs.
 *
 * <p>Previously the controller signed tokens inline and verified them with
 * {@code setRelaxVerificationKeyValidation()} and no expiration check — so an
 * expired (or short-key) token was still accepted. This class:
 * <ul>
 *   <li>enforces a 256-bit minimum key (drops the "relax" escape hatch),</li>
 *   <li>pins the algorithm to HS256 so a forged {@code "alg":"none"} token is rejected,</li>
 *   <li>requires and checks the expiration claim on every verify.</li>
 * </ul>
 *
 * <p>Dev ergonomics: if no {@code jwt.secret} is configured it generates an
 * ephemeral key at startup so the app runs with zero setup. That key is not
 * persisted, so restarting invalidates sessions — fine for a desktop, never for
 * a shared/prod host (set {@code JWT_SECRET} there).
 */
@Component
public class JwtService {

    private static final Logger LOG = LoggerFactory.getLogger(JwtService.class);
    private static final int TOKEN_TTL_MINUTES = 60;
    private static final int MIN_KEY_BYTES = 32; // 256-bit minimum for HS256

    private final byte[] key;

    public JwtService(@Value("${jwt.secret:}") String configuredSecret) {
        byte[] secretBytes = (configuredSecret == null)
                ? new byte[0]
                : configuredSecret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length >= MIN_KEY_BYTES) {
            this.key = secretBytes;
            LOG.info("JWT signing key loaded from configuration ({} bytes).", secretBytes.length);
        } else {
            byte[] generated = new byte[MIN_KEY_BYTES];
            new SecureRandom().nextBytes(generated);
            this.key = generated;
            if (secretBytes.length == 0) {
                LOG.warn("No jwt.secret configured — generated an EPHEMERAL 256-bit key. "
                        + "Sessions will not survive a restart. Set JWT_SECRET for stable sessions.");
            } else {
                LOG.warn("Configured jwt.secret is too short ({} bytes, need >= {}). "
                                + "Ignoring it and using an EPHEMERAL key. Set a longer JWT_SECRET.",
                        secretBytes.length, MIN_KEY_BYTES);
            }
        }
    }

    /**
     * Issues a signed, expiring token for the given subject.
     *
     * @return a 2-element array: [0] = "header.payload", [1] = "signature".
     */
    public String[] issue(String subject) throws JoseException {
        JwtClaims claims = new JwtClaims();
        claims.setIssuedAtToNow();
        claims.setGeneratedJwtId();
        claims.setExpirationTimeMinutesInTheFuture(TOKEN_TTL_MINUTES);
        claims.setSubject(subject);

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(new HmacKey(key));
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);

        String[] parts = jws.getCompactSerialization().split("\\.");
        return new String[]{parts[0] + "." + parts[1], parts[2]};
    }

    /**
     * Verifies signature AND expiration, then returns the subject.
     *
     * @throws org.jose4j.jwt.consumer.InvalidJwtException if the token is
     *         tampered, expired, uses the wrong algorithm, or lacks required claims.
     */
    public String verifySubject(String payload, String signature) throws Exception {
        String jwt = payload + "." + signature;
        JwtConsumer consumer = new JwtConsumerBuilder()
                .setVerificationKey(new HmacKey(key))
                .setJwsAlgorithmConstraints(
                        AlgorithmConstraints.ConstraintType.PERMIT,
                        AlgorithmIdentifiers.HMAC_SHA256) // pin alg; blocks "alg":"none" forgery
                .setRequireExpirationTime()               // reject tokens with no exp...
                .setRequireSubject()                      // ...and tokens with no subject
                .setAllowedClockSkewInSeconds(30)
                .build();
        return consumer.processToClaims(jwt).getSubject(); // throws on bad sig / expired
    }
}
