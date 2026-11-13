plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("java")
    id("org.flywaydb.flyway") version "10.21.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.freefair.lombok") version "8.10"
    id("jacoco")
}

group = "ar.com.cocos.challenge"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
        mavenBom("io.github.resilience4j:resilience4j-bom:2.2.0")
    }
    dependencies {
        // El BOM de Boot importa prometheus-metrics-bom, que declara sus módulos
        // con ${project.version}. io.spring.dependency-management no resuelve esa
        // expresión y le asigna project.version (1.0.0-SNAPSHOT), rompiendo la
        // resolución. Fijamos explícitamente las versiones que pide micrometer 1.13.
        dependency("io.prometheus:prometheus-metrics-core:1.2.1")
        dependency("io.prometheus:prometheus-metrics-model:1.2.1")
        dependency("io.prometheus:prometheus-metrics-config:1.2.1")
        dependency("io.prometheus:prometheus-metrics-exposition-formats:1.2.1")
        dependency("io.prometheus:prometheus-metrics-tracer:1.2.1")
        dependency("io.prometheus:prometheus-metrics-tracer-common:1.2.1")
        dependency("io.prometheus:prometheus-metrics-simpleclient-bridge:1.2.1")
        dependency("io.prometheus:prometheus-metrics-shaded-protobuf:1.2.1")
    }
}

dependencies {
    // Core Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    // Flyway 10 separó el soporte de bases de datos en módulos independientes
    implementation("org.flywaydb:flyway-database-postgresql")
    // Exclude problematic jaxb-runtime SNAPSHOT from hibernate
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") {
        exclude(group = "org.glassfish.jaxb", module = "jaxb-runtime")
    }
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.2") {
        exclude(group = "org.glassfish.jaxb", module = "jaxb-core")
    }
    implementation("org.glassfish.jaxb:jaxb-core:4.0.2") {
        exclude(group = "org.glassfish.jaxb", module = "txw2")
    }
    implementation("org.glassfish.jaxb:txw2:4.0.2")
    
    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    
    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-spring-boot3")
    implementation("io.github.resilience4j:resilience4j-micrometer")
    implementation("io.github.resilience4j:resilience4j-retry")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker")
    implementation("io.github.resilience4j:resilience4j-bulkhead")
    implementation("io.github.resilience4j:resilience4j-timelimiter")
    
    // Cache - Caffeine
    implementation("com.github.ben-manes.caffeine:caffeine")
    
    // Observabilidad
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Utilidades
    implementation("org.apache.commons:commons-lang3:3.15.0")
    
    // Lombok - using annotationProcessor configuration to ensure it runs
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // MCP (stretch goal) - commented out, requires Spring AI milestone repo
    // implementation("org.springframework.ai:spring-ai-mcp-server:1.0.0-M6")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
    // Docker Engine 29 exige API >= 1.44; docker-java (vía Testcontainers)
    // default a 1.32 y recibe 400. Forzamos una versión soportada.
    systemProperty("api.version", System.getProperty("api.version", "1.44"))
    // Gradle sólo descubre *Test por defecto; incluimos *IT para que
    // `./gradlew test` también corra los integration tests de Testcontainers.
    include("**/*Test.class", "**/*IT.class")
    // Docker Desktop en Windows expone el engine Linux en este named pipe.
    // Sin esto, Testcontainers busca docker_engine y falla con 400.
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "npipe:////./pipe/dockerDesktopLinuxEngine")
    }
}

tasks.named<Test>("test") {
    finalizedBy("jacocoTestReport")
}

// La verificación de cobertura necesita el exec de TODA la suite (unit + IT).
// Por eso no se engancha al `test` filtrado, sino al `check`.
tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}

tasks.withType<JacocoReport> {
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf(
                "ar.com.cocos.challenge.domain.service.PortfolioCalculator",
                "ar.com.cocos.challenge.domain.service.OrderMatcher",
                "ar.com.cocos.challenge.domain.service.BalanceValidator"
            )
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// Lombok configuration
lombok {
    version = "1.18.34"
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation", "-parameters"))
}