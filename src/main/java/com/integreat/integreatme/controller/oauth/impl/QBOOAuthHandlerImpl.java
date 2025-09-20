package com.integreat.integreatme.controller.oauth.impl;

import com.integreat.integreatme.client.OAuth2PlatformClientFactory;
import com.integreat.integreatme.controller.oauth.OAuthHandlerIF;
import com.integreat.integreatme.models.LoginSession;
import com.intuit.oauth2.client.OAuth2PlatformClient;
import com.intuit.oauth2.config.OAuth2Config;
import com.intuit.oauth2.config.Scope;
import com.intuit.oauth2.data.BearerTokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class QBOOAuthHandlerImpl implements OAuthHandlerIF {

    private static final Logger logger = LoggerFactory.getLogger(QBOOAuthHandlerImpl.class);
    private static final String HANDLER_NAME = "QBO";

    private final HttpServletRequest request;
    private final OAuth2PlatformClientFactory factory;

    public QBOOAuthHandlerImpl(HttpServletRequest request, OAuth2PlatformClientFactory factory, HttpSession session) {
        this.request = request;
        this.factory = factory;
    }

    @Override
    public String handlerName() {
        return HANDLER_NAME;
    }

    @Override
    public String login() throws Exception {
        logger.info("inside signInWithIntuit ");
        OAuth2Config oauth2Config = factory.getOAuth2Config();
        String csrf = oauth2Config.generateCSRFToken();
        String redirectUri = factory.getPropertyValue("OAuth2AppRedirectUri");

        List<Scope> scopes = new ArrayList<Scope>();
        scopes.add(Scope.OpenIdAll);

        return oauth2Config.prepareUrl(scopes, redirectUri, csrf);
    }

    @Override
    public LoginSession callback() throws Exception {

        String state = request.getParameter("state");
        String realmId = request.getParameter("realmId");
        String authCode = request.getParameter("code");

        OAuth2PlatformClient client = factory.getOAuth2PlatformClient();
        String redirectUri = factory.getPropertyValue("OAuth2AppRedirectUri");
        BearerTokenResponse bearerTokenResponse = client.retrieveBearerTokens(authCode, redirectUri);

        //Session will be issued later if this is ever persisted.
        LoginSession loginSession = new LoginSession();
        loginSession.setRealmId(realmId);
        loginSession.setAccessToken(bearerTokenResponse.getAccessToken());
        loginSession.setRefreshToken(bearerTokenResponse.getRefreshToken());
        loginSession.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(bearerTokenResponse.getExpiresIn()));
        loginSession.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(bearerTokenResponse.getXRefreshTokenExpiresIn()));
        // bearerTokenResponse.getTokenType();
        // bearerTokenResponse.getAdditionalProperties();
        // bearerTokenResponse.getIntuit_tid();

        return loginSession;
    }

    @Override
    public void logout(LoginSession loginSession) throws Exception {
        OAuth2PlatformClient client = factory.getOAuth2PlatformClient();
        client.revokeToken(loginSession.getAccessToken());
    }

    @Override
    public LoginSession refresh(LoginSession loginSession) throws Exception {
        OAuth2PlatformClient client = factory.getOAuth2PlatformClient();

        // Now we have a valid refresh token, attempt to refresh
        BearerTokenResponse bearerTokenResponse = client.refreshToken(loginSession.getRefreshToken());

        LoginSession newSession = new LoginSession();
        newSession.setRealmId(loginSession.getRealmId());
        newSession.setAccessToken(bearerTokenResponse.getAccessToken());
        newSession.setRefreshToken(bearerTokenResponse.getRefreshToken());
        newSession.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(bearerTokenResponse.getExpiresIn()));
        newSession.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(bearerTokenResponse.getXRefreshTokenExpiresIn()));

        return newSession;

    }

    private String generateCSRFToken() {
        try {
            // Generate random bytes for the token
            SecureRandom secureRandom = new SecureRandom();
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);

            // Create HMAC-SHA256 key
            String secret = "YOUR_SECRET_KEY"; // Should be stored securely
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            sha256HMAC.init(secretKey);

            // Generate HMAC
            byte[] hmacBytes = sha256HMAC.doFinal(randomBytes);

            // Combine random bytes and HMAC, encode in Base64
            byte[] tokenBytes = new byte[randomBytes.length + hmacBytes.length];
            System.arraycopy(randomBytes, 0, tokenBytes, 0, randomBytes.length);
            System.arraycopy(hmacBytes, 0, tokenBytes, randomBytes.length, hmacBytes.length);

            // Encode final token
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        } catch (Exception e) {
            logger.error("Error generating CSRF token", e);
            return null;
        }
    }

    private String getCookieValue(String cookieName) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
