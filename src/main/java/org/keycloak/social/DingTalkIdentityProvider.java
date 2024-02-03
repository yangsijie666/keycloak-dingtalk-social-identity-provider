package org.keycloak.social;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.AuthenticationRequest;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.Urls;
import org.keycloak.social.models.TokenRequestBody;
import org.keycloak.vault.VaultStringSecret;

/**
 * @author yangsijie666
 */
public class DingTalkIdentityProvider extends AbstractOAuth2IdentityProvider implements SocialIdentityProvider {
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
        logger.info("extractIdentityFromProfile=" + profile.toString());

        BrokeredIdentityContext user = new BrokeredIdentityContext((getJsonProperty(profile, "unionId")));

        String email = getJsonProperty(profile, "email");
        user.setUsername(email.split("@")[0]);
        user.setEmail(email);
        user.setUserAttribute("mobile", getJsonProperty(profile, "mobile"));
        user.setUserAttribute("avatarUrl", getJsonProperty(profile, "avatarUrl"));
        user.setUserAttribute("nick", getJsonProperty(profile, "nick"));
        user.setUserAttribute("stateCode", getJsonProperty(profile, "stateCode"));
        user.setUserAttribute("unionId", getJsonProperty(profile, "unionId"));
        user.setUserAttribute("openId", getJsonProperty(profile, "openId"));
        user.setIdpConfig(getConfig());
        user.setIdp(this);

        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, profile, getConfig().getAlias());

        return user;
    }

    // extractTokenFromResponse

    protected static class DingTalkEndpoint extends Endpoint {
        private final AbstractOAuth2IdentityProvider provider;

        public DingTalkEndpoint(AuthenticationCallback callback, RealmModel realm, EventBuilder event, AbstractOAuth2IdentityProvider provider) {
            super(callback, realm, event, provider);
            this.provider = provider;
        }

        @Override
        public SimpleHttp generateTokenRequest(String authorizationCode) {
            OAuth2IdentityProviderConfig providerConfig = this.provider.getConfig();

            String clientSecret = "";
            try (VaultStringSecret vaultStringSecret = session.vault().getStringSecret(this.provider.getConfig().getClientSecret())) {
                clientSecret = vaultStringSecret.get().orElse(this.provider.getConfig().getClientSecret());
            }

            TokenRequestBody requestBody = new TokenRequestBody(
                    this.provider.getConfig().getClientId(),
                    clientSecret,
                    authorizationCode,
                    "authorization_code"
            );

            return SimpleHttp.doPost(providerConfig.getTokenUrl(), this.session)
                    .header("Content-Type", "application/json")
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
        try (SimpleHttp.Response response = SimpleHttp.doGet(USER_INFO, session)
                .header("x-acs-dingtalk-access-token", accessToken)
                .header("Content-Type", "application/json")
                .asResponse()) {

            if (Response.Status.fromStatusCode(response.getStatus()).getFamily() != Response.Status.Family.SUCCESSFUL) {
                logger.warnf("Profile endpoint returned an error (%d): %s", response.getStatus(), response.asString());
                throw new IdentityBrokerException("Profile could not be retrieved from the dingtalk endpoint");
            }

            JsonNode profile = response.asJson();
            logger.tracef("profile retrieved from dingtalk: %s", profile);
            return extractIdentityFromProfile(null, profile);
        } catch (Exception e) {
            throw new IdentityBrokerException("Profile could not be retrieved from the dingtalk endpoint", e);
        }
    }

    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }
}
