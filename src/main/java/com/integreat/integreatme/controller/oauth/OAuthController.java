package com.integreat.integreatme.controller.oauth;

import com.integreat.integreatme.config.JwtService;
import com.integreat.integreatme.models.LoginSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
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
    private final JwtService jwtService;
    private final boolean cookieSecure;

    public OAuthController(List<OAuthHandlerIF> oauthHandlers,
                          JpaRepository<LoginSession, Long> loginSessionRepository,
                          HttpServletResponse response,
                          JwtService jwtService,
                          @Value("${app.cookie.secure:false}") boolean cookieSecure) {
        this.oauthHandlers = oauthHandlers;
        this.loginSessionRepository = loginSessionRepository;
        this.httpServletResponse = response;
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
    }

    @RequestMapping("/login")
    public View login() throws Exception {
        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {
            String callbackUrl = handler.login();
            view = new RedirectView(callbackUrl, true, true, false);
        }
        return view;
    }

    /**
     * Redirect handler configured in your app on developer.intuit.com.
     * The authorization code is short-lived, so we exchange it for a bearer
     * token immediately, persist the session, then hand back signed cookies.
     */
    @RequestMapping("/callback")
    public View callBackFromOAuth() throws Exception {
        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {
            LoginSession refreshedLoginSession = handler.callback();
            refreshedLoginSession = loginSessionRepository.save(refreshedLoginSession);

            try {
                issueSessionCookies(refreshedLoginSession);
            } catch (JoseException e) {
                logger.error("Error creating JWT", e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create session");
            }

            view = new RedirectView("/close.html", true, true, false);
        }
        return view;
    }

    /** Signs a token for the session and writes the split payload/signature cookies. */
    private void issueSessionCookies(LoginSession loginSession) throws JoseException {
        String[] token = jwtService.issue(String.valueOf(loginSession.getId()));

        // Readable-by-JS half (so the SPA can detect "logged in").
        Cookie payloadCookie = buildCookie("jwt-payload", token[0], false);
        // Signature half is HttpOnly: JS can never read it, so a token can't be
        // reassembled/forged client-side.
        Cookie signatureCookie = buildCookie("jwt-signature", token[1], true);

        httpServletResponse.addCookie(payloadCookie);
        httpServletResponse.addCookie(signatureCookie);
    }

    private Cookie buildCookie(String name, String value, boolean httpOnly) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(cookieSecure);          // true once served over HTTPS (APP_COOKIE_SECURE=true)
        cookie.setAttribute("SameSite", "Lax");  // mitigates CSRF on the auth cookies
        return cookie;
    }

    @ResponseBody
    @RequestMapping("/logout")
    public View logout(HttpSession session, HttpServletRequest request,
                       HttpServletResponse servletResponse,
                       @CookieValue("jwt-signature") String jwtSignature,
                       @CookieValue("jwt-payload") String jwtPayload) throws Exception {

        LoginSession loginSession = getVerifiedLoginSession(jwtSignature, jwtPayload);

        // Clear both halves regardless of handler outcome.
        Cookie payloadCookie = buildCookie("jwt-payload", null, false);
        payloadCookie.setMaxAge(0);
        servletResponse.addCookie(payloadCookie);

        Cookie signatureCookie = buildCookie("jwt-signature", null, true);
        signatureCookie.setMaxAge(0);
        servletResponse.addCookie(signatureCookie);

        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {
            handler.logout(loginSession);
            view = new RedirectView("/close.html", true, true, false);
        }
        return view;
    }

    /** Call to refresh tokens. */
    @ResponseBody
    @RequestMapping("/refresh")
    public View refreshToken(HttpSession session, HttpServletResponse response,
                             @CookieValue(value = "jwt-payload") String jwtPayload,
                             @CookieValue(value = "jwt-signature") String signature) throws Exception {

        LoginSession loginSession = getVerifiedLoginSession(signature, jwtPayload);

        View view = null;
        for (OAuthHandlerIF handler : oauthHandlers) {
            loginSession = handler.refresh(loginSession);
            loginSession = loginSessionRepository.save(loginSession);

            issueSessionCookies(loginSession);
            view = new RedirectView("/close.html", true, true, false);
        }
        return view;
    }

    /**
     * Verifies the signature AND expiration of the cookie pair, then loads the
     * session it points at. Any tampered, expired, or unknown token yields 401
     * instead of being trusted.
     */
    private LoginSession getVerifiedLoginSession(String jwtSignature, String jwtPayload) {
        final String subject;
        try {
            subject = jwtService.verifySubject(jwtPayload, jwtSignature);
        } catch (Exception e) {
            logger.warn("Rejected invalid or expired JWT: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session");
        }

        LoginSession loginSession = loginSessionRepository.findById(Long.parseLong(subject)).orElse(null);
        if (loginSession == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session not found");
        }
        return loginSession;
    }
}
