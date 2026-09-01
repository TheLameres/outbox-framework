/**
 * Для модулей, которые ПУБЛИКУЮТСЯ в Nexus как библиотека:
 * outbox-core, outbox-jpa, outbox-kafka, outbox-spring-boot-starter.
 *
 * НЕ применяется к outbox-sample-service — приложение собирает bootJar
 * и как библиотека не публикуется.
 */
plugins {
    id("outbox.java-conventions")
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Outbox Framework :: ${project.name}")
                // description задаётся в build-скрипте модуля ПОСЛЕ применения
                // плагина, поэтому читаем лениво через provider — иначе null.
                description.set(provider { project.description ?: project.name })
            }
        }
    }
    repositories {
        maven {
            name = "nexus"
            url = uri(
                if (version.toString().endsWith("SNAPSHOT")) {
                    "https://nexus.company.com/repository/maven-snapshots/"
                } else {
                    "https://nexus.company.com/repository/maven-releases/"
                }
            )
            // Ожидает nexusUsername / nexusPassword в ~/.gradle/gradle.properties
            // либо ORG_GRADLE_PROJECT_nexusUsername/Password в CI.
            credentials(PasswordCredentials::class)
        }
    }
}
