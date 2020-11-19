plugins {
    java
    id("com.github.johnrengelman.shadow") version "6.1.0"
    `maven-publish`
}

group = "com.github.xjcyan1de"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation("io.netty", "netty-all", "4.1.54.Final")
    implementation(
        "io.netty.incubator",
        "netty-incubator-transport-native-io_uring",
        "0.0.1.Final",
        classifier = "linux-x86_64"
    )
}

tasks {
    jar {
        manifest {
            attributes("Main-Class" to "com.github.xjcyan1de.simpleproxy.Main")
        }
        finalizedBy("shadowJar")
    }
    shadowJar {
        minimize()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }

    repositories {
        if (System.getenv("GITHUB_TOKEN") != null) {
            maven(url = "https://maven.pkg.github.com/XjCyan1de/SimpleProxy") {
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}


