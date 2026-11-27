package org.keycloak.social;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.keycloak.OAuth2Constants;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.AuthenticationRequest;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.social.models.TokenRequestBody;
import org.keycloak.vault.VaultStringSecret;

import java.io.IOException;

/**
 * @author yangsijie666
 */
public class DingTalkIdentityProvider extends AbstractOAuth2IdentityProvider<OAuth2IdentityProviderConfig>
        implements SocialIdentityProvider<OAuth2IdentityProviderConfig> {
    public static final String AUTH_URL = "https://login.dingtalk.com/oauth2/auth";
    public static final String TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    public static final String USER_INFO = "https://api.dingtalk.com/v1.0/contact/users/me";
    public static final String DEFAULT_SCOPE = "openid";

    public DingTalkIdentityProvider(KeycloakSession session, OAuth2IdentityProviderConfig config) {
        super(session, config);

        config.setAuthorizationUrl(AUTH_URL);
        config.setTokenUrl(TOKEN_URL);
        config.setUserInfoUrl(USER_INFO);
    }

    @Override
    protected boolean supportsExternalExchange() {
        return true;
    }

    @Override
    protected String getProfileEndpointForValidation(EventBuilder event) {
        return USER_INFO;
    }

    @Override
    protected UriBuilder createAuthorizationUrl(AuthenticationRequest request) {
        UriBuilder uriBuilder = super.createAuthorizationUrl(request);
        return uriBuilder.queryParam("prompt", "consent");
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode profile) {
        String unionId = getJsonProperty(profile, "unionId");
        if (unionId == null || unionId.isBlank()) {
            throw new IdentityBrokerException("DingTalk profile did not contain a unionId");
        }
        BrokeredIdentityContext user = new BrokeredIdentityContext(unionId, getConfig());
        String nick = getJsonProperty(profile, "nick");
        String email = getJsonProperty(profile, "email");

        if (nick != null && !nick.isEmpty()) {
            user.setUsername(nick);
            user.setUserAttribute("nick", getJsonProperty(profile, "nick"));
        } else if (email != null && !email.isEmpty()) {
            user.setUsername(email.split("@")[0]);
            user.setEmail(email);
            user.setUserAttribute("email", email);
        } else {
            user.setUsername(unionId);
        }

        user.setUserAttribute("mobile", getJsonProperty(profile, "mobile"));
        user.setUserAttribute("avatarUrl", getJsonProperty(profile, "avatarUrl"));
        user.setUserAttribute("stateCode", getJsonProperty(profile, "stateCode"));
        user.setUserAttribute("unionId", unionId);
        user.setUserAttribute("openId", getJsonProperty(profile, "openId"));
        user.setIdp(this);

        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, profile, getConfig().getAlias());

        return user;
    }

    public static class DingTalkEndpoint extends Endpoint {
        private final OAuth2IdentityProviderConfig providerConfig;

        public DingTalkEndpoint(AuthenticationCallback callback, RealmModel realm, EventBuilder event,
                                DingTalkIdentityProvider provider) {
            super(callback, realm, event, provider);
            providerConfig = provider.getConfig();
        }

        @Override
        @GET
        public Response authResponse(@QueryParam(OAUTH2_PARAMETER_STATE) String state,
                                     @QueryParam(OAUTH2_PARAMETER_CODE) String authorizationCode,
                                     @QueryParam(OAuth2Constants.ERROR) String error,
                                     @QueryParam(OAuth2Constants.ERROR_DESCRIPTION) String errorDescription) {
            String authCode = session.getContext().getUri().getQueryParameters().getFirst("authCode");
            if (authCode != null) {
                authorizationCode = authCode;
            }
            return super.authResponse(state, authorizationCode, error, errorDescription);
        }

        @Override
        public SimpleHttpRequest generateTokenRequest(String authorizationCode) {
            String configuredSecret = providerConfig.getClientSecret();
            String clientSecret;
            try (VaultStringSecret vaultStringSecret = session.vault().getStringSecret(configuredSecret)) {
                clientSecret = vaultStringSecret.get().orElse(configuredSecret);
            }
            TokenRequestBody requestBody = new TokenRequestBody(
                    providerConfig.getClientId(),
                    clientSecret,
                    authorizationCode,
                    OAUTH2_GRANT_TYPE_AUTHORIZATION_CODE
            );
            return SimpleHttp.create(session).doPost(providerConfig.getTokenUrl())
                    .acceptJson()
                    .json(requestBody);
        }
    }

    @Override
    public Object callback(RealmModel realm, AuthenticationCallback callback, EventBuilder event) {
        return new DingTalkEndpoint(callback, realm, event, this);
    }

    @Override
    protected String getAccessTokenResponseParameter() {
        return "accessToken";
    }

    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        try (SimpleHttpResponse response = SimpleHttp.create(session).doGet(USER_INFO)
                .header("x-acs-dingtalk-access-token", accessToken)
                .acceptJson()
                .asResponse()) {

            int status = response.getStatus();
            if (Response.Status.Family.familyOf(status) != Response.Status.Family.SUCCESSFUL) {
                logger.warnf("DingTalk profile endpoint returned HTTP %d", status);
                throw new IdentityBrokerException("Profile could not be retrieved from the dingtalk endpoint");
            }
            return extractIdentityFromProfile(null, response.asJson());
        } catch (IOException e) {
            throw new IdentityBrokerException("Profile could not be retrieved from the dingtalk endpoint", e);
        }
    }

    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }
}
