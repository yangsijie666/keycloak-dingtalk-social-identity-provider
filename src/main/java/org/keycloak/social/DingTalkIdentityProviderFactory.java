package org.keycloak.social;

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.broker.social.SocialIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.social.github.GitHubIdentityProvider;

/**
 * @author yangsijie666
 */
public class DingTalkIdentityProviderFactory extends AbstractIdentityProviderFactory<DingTalkIdentityProvider> implements SocialIdentityProviderFactory<DingTalkIdentityProvider> {
    public static final String PROVIDER_ID = "dingtalk";

    @Override
    public String getName() {
        return "DingTalk";
    }

    @Override
    public DingTalkIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new DingTalkIdentityProvider(session, new OAuth2IdentityProviderConfig(model));
    }

    @Override
    public IdentityProviderModel createConfig() {
        return new OAuth2IdentityProviderConfig();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
