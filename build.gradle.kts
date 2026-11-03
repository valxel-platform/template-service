import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
}

group = "com.vrixel"
description = "Vrixel service template: Spring Boot API with tests, contracts and observability"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories { mavenCentral() }

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.pact.provider.junit5)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-parameters"))
}

// Unit tests: everything except contract tests. Coverage is measured here.
tasks.test {
    useJUnitPlatform { excludeTags("contract", "deployed") }
    finalizedBy(tasks.jacocoTestReport)
}

// Provider contract tests: verify the pacts consumers publish (src/test/resources/pacts, or a
// Pact Broker once you run one). CI job "contract".
val contractTest by tasks.registering(Test::class) {
    description = "Verifies consumer pacts against this provider."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("contract") }
    shouldRunAfter(tasks.test)
}

// Checks against a deployed environment (BASE_URL); run by the post-deploy workflow.
val deployedTest by tasks.registering(Test::class) {
    description = "Runs integration, end-to-end and smoke checks against BASE_URL."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("deployed") }
    environment("BASE_URL", providers.environmentVariable("BASE_URL").getOrElse(""))
    outputs.upToDateWhen { false }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

// The platform's floor; products may raise it (governance/product.yaml quality.unit.min_coverage).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule { limit { counter = "LINE"; minimum = "0.90".toBigDecimal() } }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, contractTest)
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

tasks.jar {
    enabled = false // only the executable bootJar is published
}
