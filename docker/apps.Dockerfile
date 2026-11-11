# syntax=docker/dockerfile:1
#
# The three samples and the libraries they use (build context: the folder
# holding trust-getting-started and trust-security - see docker-compose.yml).
# One image, three entry points: the service's command picks the JAR.

FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /workspace
COPY . .
# The Maven repository is cached between builds (BuildKit cache mount).
# trust-getting-started/.mvn/jvm.config carries the JVM options NullAway
# needs: Maven only reads the one of the top-level project.
RUN --mount=type=cache,target=/root/.m2 \
    cd trust-getting-started \
    && mvn -B -q -DskipTests install \
    && mkdir /out \
    && for app in trust-sample-downstream-api trust-sample-api trust-sample-bff; do \
         cp "$app/target/$app-0.0.1-SNAPSHOT.jar" "/out/$app.jar"; \
       done

FROM eclipse-temurin:21.0.8_9-jre@sha256:66bb900643426ad01996d25bada7d56751913f9cec3b827fcb715d2ec9a0fbfc
COPY --from=build /out/ /app/
USER 10001
ENTRYPOINT ["java", "-jar"]
