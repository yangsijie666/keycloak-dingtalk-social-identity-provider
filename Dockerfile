FROM quay.io/keycloak/keycloak:26.8.2
ARG KEYCLOAK_DB=dev-file
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
WORKDIR /opt/keycloak
COPY --chown=keycloak:keycloak target/keycloak-dingtalk-social-identity-provider-26.8.2.jar /opt/keycloak/providers/
RUN touch -m --date=@1743465600 /opt/keycloak/providers/keycloak-dingtalk-social-identity-provider-26.8.2.jar \
    && /opt/keycloak/bin/kc.sh build --db="${KEYCLOAK_DB}"
