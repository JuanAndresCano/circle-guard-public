plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-ldap")
    implementation("org.springframework.security:spring-security-ldap")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.wiremock:wiremock-standalone:3.5.2")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
}
tasks.withType<Test> {
    maxParallelForks = 1
    setForkEvery(0)
    jvmArgs("-Xmx256m", "-Dfile.encoding=UTF-8")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("e2e", "integration")
    }
}