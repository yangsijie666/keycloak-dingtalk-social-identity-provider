package org.keycloak.social.models;

/**
 * @author yangsijie666
 */
public class TokenRequestBody {
    public String clientId;
    public String clientSecret;
    public String code;
    public String grantType;

    public TokenRequestBody(String clientId, String clientSecret, String code, String grantType) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.code = code;
        this.grantType = grantType;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getCode() {
        return code;
    }

    public String getGrantType() {
        return grantType;
    }
}
