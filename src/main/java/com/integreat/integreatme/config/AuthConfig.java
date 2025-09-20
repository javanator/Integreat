package com.integreat.integreatme.config;

import com.integreat.integreatme.controller.oauth.OAuthHandlerIF;
import com.integreat.integreatme.controller.oauth.impl.QBOOAuthHandlerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AuthConfig {
    @Bean
    public List<OAuthHandlerIF> oauthHandlers(QBOOAuthHandlerImpl qboAuthHandler) {
        return List.of(qboAuthHandler);
    }
}
