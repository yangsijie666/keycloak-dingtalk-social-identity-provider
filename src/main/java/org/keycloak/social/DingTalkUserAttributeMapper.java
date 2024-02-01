package org.keycloak.social;

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.social.github.GitHubIdentityProviderFactory;

/**
 * @author yangsijie666
 */
public class DingTalkUserAttributeMapper extends AbstractJsonUserAttributeMapper {
    public static final String PROVIDER_ID = "dingtalk-user-attribute-mapper";
    private static final String[] cp = new String[] { DingTalkIdentityProviderFactory.PROVIDER_ID };

    @Override
    public String[] getCompatibleProviders() {
        return cp;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
