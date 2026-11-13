# syntax=docker/dockerfile:1
# Build with the Gradle wrapper, extract Spring Boot's layers (dependencies change less often than
# code, so image layers cache well), and run on a distroless JRE as a non-root user.
FROM eclipse-temurin:21-jdk@sha256:0a3f329821dcadf54b2a3033a15e2e9f007069a9db43d7f51897e6b80e726b07 AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
# The lockfile, when there is one (gradle/ carries verification-metadata.xml): every resolved
# version is the locked one, and every artifact matches its recorded checksum.
COPY gradle.lockfil[e] ./
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/app.jar extract --layers --destination /extracted

FROM gcr.io/distroless/java21-debian13:nonroot@sha256:6b60e44d652d66a9e2e584364c96adac4df7133674b2834b6f71741b7cdb1ff3
# /app belongs to the non-root user, so the training run below can write its class archive there.
COPY --chown=nonroot:nonroot --from=build /extracted/dependencies/ /app/
COPY --chown=nonroot:nonroot --from=build /extracted/spring-boot-loader/ /app/
COPY --chown=nonroot:nonroot --from=build /extracted/snapshot-dependencies/ /app/
COPY --chown=nonroot:nonroot --from=build /extracted/application/ /app/
WORKDIR /app
USER nonroot
# Class data sharing: start the application once, up to its refresh (no network, no listening),
# and keep the classes it loaded in an archive. Starting from the archive is markedly faster, which
# matters when OpenChoreo adds replicas under load. Security off for the training run only.
RUN ["java", "-XX:ArchiveClassesAtExit=app.jsa", "-Dspring.context.exit=onRefresh", "-Dapp.security.enabled=false", "-jar", "app.jar"]
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
