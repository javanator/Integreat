package com.integreat.integreatme.controller.oauth;

import com.integreat.integreatme.models.LoginSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

@Controller
@RequestMapping("/api/auth")
public class OAuthController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthController.class);

    private final List<OAuthHandlerIF> oauthHandlers;
    private final JpaRepository<LoginSession, Long> loginSessionRepository;
    private final HttpServletResponse httpServletResponse;
    private final String jwtSecret;

    public OAuthController(List<OAuthHandlerIF> oauthHandlers,
                           JpaRepository<LoginSession, Long> loginSessionRepository,
                           HttpServletResponse response,
                           @Value("${jwt.secret}") String jwtSecret) {
        this.oauthHandlers = oauthHandlers;
        this.loginSessionRepository = loginSessionRepository;
        this.httpServletResponse = response;
        this.jwtSecret = jwtSecret;
    }

    @RequestMapping("/login")
    public View login() throws Exception{
        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {
            String callbackUrl = handler.login();
            view = new RedirectView(callbackUrl, true, true, false);
        }

        return view;
    }

    /**
     * This is the redirect handler you configure in your app on developer.intuit.com
     * The Authorization code has a short lifetime.
     * Hence Unless a user action is quick and mandatory, proceed to exchange the Authorization Code for
     * BearerToken
     *
     * @return
     */
    @RequestMapping("/callback")
    public View callBackFromOAuth() throws Exception {
        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {
            LoginSession refreshedLoginSession = handler.callback();
            refreshedLoginSession = loginSessionRepository.save(refreshedLoginSession);

            try {
                // Create JWT claims
                Result result = getCookiesResult(refreshedLoginSession);

                httpServletResponse.addCookie(result.payloadCookie());
                httpServletResponse.addCookie(result.signatureCookie());
            } catch (JoseException e) {
                logger.error("Error creating JWT", e);
            }


            view = new RedirectView("/close.html", true, true, false);
        }
        return view;
    }

    private Result getCookiesResult(LoginSession loginSession) throws JoseException {
        JwtClaims claims = new JwtClaims();
        claims.setIssuedAtToNow();
        claims.setGeneratedJwtId();
        claims.setExpirationTimeMinutesInTheFuture(60);
        claims.setSubject(String.valueOf(loginSession.getId()));

        // Create JWS
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(new HmacKey(jwtSecret.getBytes()));
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);

        // Split token
        String[] parts = jws.getCompactSerialization().split("\\.");
        String tokenPayload = parts[0] + "." + parts[1];
        String signature = parts[2];

        // Set cookies
        Cookie payloadCookie = new Cookie("jwt-payload", tokenPayload);
        payloadCookie.setPath("/");
        //payloadCookie.setSecure(true);

        Cookie signatureCookie = new Cookie("jwt-signature", signature);
        signatureCookie.setPath("/");
        signatureCookie.setHttpOnly(true);
        //signatureCookie.setSecure(true);
        return new Result(payloadCookie, signatureCookie);
    }

    private record Result(Cookie payloadCookie, Cookie signatureCookie) {
    }

    @ResponseBody
    @RequestMapping("/logout")
    public View logout(HttpSession session, HttpServletRequest request,
                       HttpServletResponse servletResponse,
                       @CookieValue("jwt-signature") String jwtSignature,
                       @CookieValue("jwt-payload") String jwtPayload) throws Exception {

        LoginSession loginSession = getLoginSession(jwtSignature, jwtPayload);

        Cookie payloadCookie = new Cookie("jwt-payload", null);
        payloadCookie.setPath("/");
        payloadCookie.setMaxAge(0);
        servletResponse.addCookie(payloadCookie);

        Cookie signatureCookie = new Cookie("jwt-signature", null);
        signatureCookie.setPath("/");
        signatureCookie.setMaxAge(0);
        servletResponse.addCookie(signatureCookie);

        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {
            handler.logout(loginSession);
            view = new RedirectView("/close.html", true, true, false);
        }

        return view;
    }

    private LoginSession getLoginSession(String jwtSignature, String jwtPayload) throws InvalidJwtException, MalformedClaimException {
        String jwt = jwtPayload + "." + jwtSignature;
        HmacKey verificationKey = new HmacKey(jwtSecret.getBytes());
        JwtConsumer jwtConsumer = new JwtConsumerBuilder().setVerificationKey(verificationKey).setRelaxVerificationKeyValidation().build();
        JwtClaims claims = jwtConsumer.processToClaims(jwt);
        Long sessionId = Long.parseLong(claims.getSubject());
        LoginSession loginSession = loginSessionRepository.findById(sessionId).orElse(null);
        return loginSession;
    }

    /**
     * Call to refresh tokens
     *
     * @param session
     * @return
     */
    @ResponseBody
    @RequestMapping("/refresh")
    public View refreshToken(HttpSession session, HttpServletResponse response, @CookieValue(value = "jwt-payload") String jwtPayload, @CookieValue(value = "jwt-signature") String signature) throws Exception {

        LoginSession loginSession = getLoginSession(signature, jwtPayload);

        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {

            loginSession = handler.refresh(loginSession);
            loginSession = loginSessionRepository.save(loginSession);

            Result cookiesResult = getCookiesResult(loginSession);
            response.addCookie(cookiesResult.payloadCookie());
            response.addCookie(cookiesResult.signatureCookie());

            view = new RedirectView("/close.html", true, true, false);
        }

        return view;
    }
}
