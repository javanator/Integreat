package com.integreat.integreatme.controller.oauth;

import com.integreat.integreatme.models.LoginSession;

public interface OAuthHandlerIF {
    public String handlerName();

    public String login() throws Exception;

    public void logout(LoginSession loginSession) throws Exception;

    public LoginSession refresh(LoginSession loginSession) throws Exception;

    public LoginSession callback() throws Exception;
}
