FROM maven:3.8-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml /app
COPY ./settings.xml /usr/share/maven/conf/settings.xml
RUN mvn verify --fail-never -s /usr/share/maven/conf/settings.xml
COPY . /app
RUN mvn clean install -s /usr/share/maven/conf/settings.xml

FROM quay.io/keycloak/keycloak:23.0.7 as kcbuilder

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

WORKDIR /opt/keycloak
COPY --from=builder --chown=keycloak:keycloak /app/target/*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:23.0.7
COPY --from=kcbuilder /opt/keycloak/ /opt/keycloak/
ENTRYPOINT [ "/opt/keycloak/bin/kc.sh" ]
