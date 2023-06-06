plugins {
    id("java")
    id("maven-publish")
    id("signing")
}

group = "com.koralix.security"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.slf4j:slf4j-api:2.0.7")

    implementation("org.jetbrains:annotations:24.0.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.apache.logging.log4j:log4j-core:2.20.0")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
    systemProperty("log4j.configurationFile", "log4j2.xml")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("com.koralix.security:mc-simple-security")
                description.set("Step Functions is a java library to create functions step-by-step with multi-branch logic.")
                url.set("https://github.com/Koralix-Studios/step-functions")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://github.com/Koralix-Studios/step-functions/blob/master/LICENSE")
                    }
                }

                developers {
                    developer {
                        name.set("JohanVonElectrum")
                        email.set("johanvonelectrum@gmail.com")
                        organization.set("Koralix Studios")
                        organizationUrl.set("https://www.koralix.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/koralix-studios/mc-simple-security.git")
                    developerConnection.set("scm:git:ssh://github.com/koralix-studios/mc-simple-security.git")
                    url.set("https://github.com/Koralix-Studios/step-functions/tree/master")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/koralix-studios/mc-simple-security")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: project.findProperty("ossrh.user") as String?
                password = System.getenv("OSSRH_PASSWORD") ?: project.findProperty("ossrh.password") as String?
            }
        }
    }
}


signing {
    if (
        System.getenv("SIGNING_KEY") != null &&
        System.getenv("SIGNING_PASSWORD") != null
    ) {
        useInMemoryPgpKeys(System.getenv("SIGNING_KEY"), System.getenv("SIGNING_PASSWORD"))
    } else if (
        project.findProperty("signing.keyId") == null ||
        project.findProperty("signing.password") == null ||
        project.findProperty("signing.secretKeyRingFile") == null
    ) {
        println("Signing key is not configured and not available in env")
        return@signing
    } else {
        useGpgCmd()
    }

    println("Signing key is configured")

    sign(publishing.publications["mavenJava"])
}