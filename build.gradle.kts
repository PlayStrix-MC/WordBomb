plugins {
    java
    id("com.gradleup.shadow") version "9.2.2"
}

group = "padej"

// Версия: <Minecraft>+<плагин>. Версию плагина можно задать через -PpluginVersion=... (CI подставляет из тега v*).
val mcVersion = "1.21.11"
val pluginVersion = (findProperty("pluginVersion") as String?)
    ?.removePrefix("v")
    ?.takeIf { it.isNotBlank() }
    ?: "0.1.0"
version = "$mcVersion+$pluginVersion"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
}

// Копия DisplayLib без вшитого Jumper: иначе javac находит его классы раньше jmp-0.12.0.jar
val displayLibApi by tasks.registering(Jar::class) {
    group = "build setup"
    description = "DisplayLib jar без классов me.padej.jumper (только для компиляции)"
    archiveFileName = "DisplayLib-api.jar"
    destinationDirectory = layout.buildDirectory.dir("tmp/displayLibApi")
    from(zipTree("libs/DisplayLib-1.21.11+3.1.0.jar")) {
        exclude("me/padej/jumper/**")
        exclude("META-INF/services/javax.script.ScriptEngineFactory")
    }
}

dependencies {
    // Paper API 1.21.11 - только на компиляцию, сервер даёт его в рантайме
    compileOnly("io.papermc.paper:paper-api:$mcVersion-R0.1-SNAPSHOT")

    // DisplayLib 3.1.0 - экраны и частицы. Плагин DisplayLib должен стоять на сервере (depend в plugin.yml).
    // Внутри jar лежит старый Jumper в том же пакете me.padej.jumper (без Config.Comment / loadOrCreate / update),
    // поэтому на компиляцию идёт копия без него - см. задачу displayLibApi ниже
    compileOnly(files(displayLibApi.map { it.archiveFile }))

    // Jumper 0.12.0 - конфиги (.jmc) и тексты. Вшивается в jar с relocate, чтобы не конфликтовать с DisplayLib
    implementation(files("libs/jmp-0.12.0.jar"))

    // SQLite - хранилище словаря, голосов, побед и журнала. Вшивается в jar
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    // PlaceholderAPI - необязательная интеграция
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
    filteringCharset = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier = ""
    // драйвер sqlite-jdbc и ScriptEngineFactory Jumper регистрируются через META-INF/services
    mergeServiceFiles()
    // Jumper переезжает в собственное пространство имён, чтобы не пересечься с Jumper внутри DisplayLib
    relocate("me.padej.jumper", "padej.wordbomb.libs.jumper")
}

tasks.jar {
    // Рабочий артефакт - shadow-jar; обычный jar без зависимостей не нужен
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Куда копировать готовый jar (обычно plugins/ тестового сервера).
// Задать: -PserverPlugins="D:/server/plugins"  или строкой serverPlugins=... в gradle.properties
// или переменной окружения WORDBOMB_SERVER. Не задано - копирования нет.
val serverPlugins: String? = (findProperty("serverPlugins") as String?)
    ?: System.getenv("WORDBOMB_SERVER")

val deployLocal by tasks.registering(Copy::class) {
    group = "development"
    description = "Скопировать shadow-jar в папку plugins/ сервера (-PserverPlugins=...)"
    from(tasks.shadowJar.map { it.archiveFile })
    into(serverPlugins ?: layout.buildDirectory.dir("deploy").get().asFile.path)
    // копировать всегда, даже если jar не менялся: сервер мог удалить/заменить файл
    outputs.upToDateWhen { false }
    doFirst {
        if (serverPlugins == null) {
            logger.lifecycle("WordBomb: serverPlugins не задан - jar не копируется (см. gradle.properties или -PserverPlugins=...)")
        }
    }
    onlyIf { serverPlugins != null }
    doLast {
        val jar = tasks.shadowJar.get().archiveFile.get().asFile
        logger.lifecycle("WordBomb: ${jar.name} скопирован в $serverPlugins")
    }
}

tasks.build {
    finalizedBy(deployLocal)
}
