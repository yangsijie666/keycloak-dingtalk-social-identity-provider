FROM fightingsj/maven:3.8-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml /app
COPY ./settings.xml /usr/share/maven/conf/settings.xml
RUN mvn verify --fail-never -s /usr/share/maven/conf/settings.xml
COPY . /app
RUN mvn clean install -s /usr/share/maven/conf/settings.xml

FROM quay.io/keycloak/keycloak:23.0.4
COPY --from=builder /app/target/*.jar /opt/keycloak/providers/
