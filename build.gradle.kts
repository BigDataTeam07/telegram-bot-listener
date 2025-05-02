plugins {
    id("java")
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.iss.bigdata.practice"
version = "1.0.0"

application {
    mainClass.set("org.iss.bigdata.practice.TelegramBotListenerApp")
}

repositories {
    mavenCentral()
}

dependencies {
    // Telegram API
    implementation("org.telegram:telegrambots:6.9.7.1")
    implementation("org.telegram:telegrambotsextensions:6.9.7.1")

    // https://mvnrepository.com/artifact/org.apache.kafka/kafka-clients
    implementation("org.apache.kafka:kafka-clients:3.6.0")


    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.4.14") // Added logback implementation
    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "org.iss.bigdata.amazon.music.TelegramBotListenerApp"
    }
}

tasks.shadowJar {
    archiveBaseName.set("telegram-bot-listener")
    archiveClassifier.set("")
    archiveVersion.set("")
}