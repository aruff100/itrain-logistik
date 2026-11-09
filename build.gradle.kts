import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    java
    application
    id("org.javamodularity.moduleplugin") version "2.0.1"
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "4.1.1"
}

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        // Liberica JDK 25.0.4.1 (BellSoft) fuer alle drei Zielplattformen
        // (Windows/macOS/Linux) - siehe Projektauftrag. Gradle kann nur die
        // Hauptversion (25) plus Hersteller anfordern; foojay liefert dafuer
        // aktuell genau den Stand 25.0.4.1 aus - sollte BellSoft zwischenzeitlich
        // einen neueren Patch 25.x veroeffentlichen, wuerde dieser gezogen. Fuer
        // eine bewusst FESTGENAGELTE Fassung: eine lokal installierte Liberica
        // 25.0.4.1 verwenden und in gradle.properties
        // "org.gradle.java.home=<Pfad zur Liberica-Installation>" setzen - das
        // uebersteuert die Toolchain-Suche.
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.BELLSOFT
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Erzeugt bei jedem Build eine build-info.properties mit Version und
// Zeitstempel (Basis fuer die "Version"-Angabe im Ueber-Dialog, siehe
// AppInfo.java) - gleiches Vorgehen wie in iTrain_import_export.
val buildTimestamp: String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    .withZone(ZoneOffset.UTC)
    .format(Instant.now())

tasks.processResources {
    inputs.property("buildTimestamp", buildTimestamp)
    // Bearbeitbare Dialog-, Hilfe- und Handbuchtexte aus dem Projektordner "texte"
    // (Anwender 27.10.2026, siehe Texte.java): landen neben den Klassen im Paket.
    from("texte") {
        include("*.txt")
        into("com/example/itrain_logistik")
    }
    // Sprachdateien der Oberfläche (Anwender 09.11.2026, siehe Uebersetzung.java)
    from("sprachen") {
        include("*.txt")
        into("com/example/itrain_logistik/sprachen")
    }
    // Mitgelieferte Geräusche (nur CC0, Anwender 09.11.2026, siehe Geraeusche.java und geraeusche/Quellen.txt)
    from("geraeusche") {
        include("*.mp3", "zuordnung.txt", "Quellen.txt")
        into("com/example/itrain_logistik/geraeusche")
    }
    filesMatching("**/build-info.properties") {
        expand("version" to project.version.toString(), "buildTimestamp" to buildTimestamp)
    }
}

application {
    mainModule.set("com.example.itrain_logistik")
    mainClass.set("com.example.itrain_logistik.LogistikApplication")
}

// JavaFX laedt seine nativen Bibliotheken (Glass/Prism) ueber System.load(),
// seit JDK 24 ein "restricted method" - ohne --enable-native-access meldet
// jeder Start eine Warnung, und laut deren Text wird der Zugriff in einer
// kuenftigen JDK-Version ganz blockiert. Gilt fuer alle drei Startwege
// ("gradle run", jlink-Startprogramm, jpackage-Installer), da jeder sein
// eigenes Kommandozeilen-/Modulsystem hat und die Angabe nicht voneinander
// erbt - siehe launcher.jvmArgs/jpackage.jvmArgs weiter unten.
val ENABLE_NATIVE_ACCESS = "--enable-native-access=javafx.graphics,javafx.media"

tasks.named<JavaExec>("run") {
    jvmArgs(ENABLE_NATIVE_ACCESS)
}

javafx {
    // 25.0.4: aktuellste JavaFX-Fassung im 25er-Zweig (Stand dieses Projekts),
    // passend zur Liberica-JDK-25-Toolchain oben.
    version = "25.0.4"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.media")
}

val junitVersion = "5.12.1"

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:${junitVersion}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${junitVersion}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jlink {
    imageZip.set(layout.buildDirectory.file("distributions/app-${javafx.platform.classifier}.zip"))
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages", "--bind-services"))

    launcher {
        // MUSS mit jpackage.imageName weiter unten uebereinstimmen - siehe
        // ausfuehrliche Begruendung in iTrain_import_export/build.gradle.kts
        // (sonst legt jpackage zwei Startprogramme/Verknuepfungen an und der
        // Windows-Installer bricht mit ICE67/ICE69 ab).
        name = "iTrain-Logistik"
        jvmArgs = listOf(ENABLE_NATIVE_ACCESS)
    }

    // Echte, plattformspezifische Installationsdateien ueber jpackage
    // (Windows: .msi, macOS: .dmg, Linux: .deb) - Aufruf ueber "./gradlew jpackage".
    // jpackage baut NUR fuer die Plattform, auf der es laeuft (kein
    // Cross-Compiling) - fuer alle drei Zielplattformen aus dem Projektauftrag
    // braucht es also einen Lauf je Betriebssystem (z.B. drei CI-Runner, wie im
    // Schwesterprojekt unter .github/workflows/release.yml).
    jpackage {
        val os = OperatingSystem.current()
        val osLabel = when {
            os.isWindows -> "Windows"
            os.isMacOsX -> "macOS"
            else -> "Linux"
        }
        imageName = "iTrain-Logistik"
        installerName = "iTrain-Logistik-$osLabel"
        // macOS verlangt eine erste Zahl ≥ 1 (Release-Lauf 09.11.2026)
        appVersion = project.version.toString()
        vendor = "Andre Ruff"

        // Wird von jpackage als "--java-options" in das installierte
        // Startprogramm eingebacken (launcher.jvmArgs oben gilt nur fuer das
        // jlink-Startprogramm, jpackage erbt das nicht automatisch).
        jvmArgs = listOf(ENABLE_NATIVE_ACCESS)

        icon = when {
            os.isWindows -> file("packaging/icons/app-icon.ico")
            os.isMacOsX -> file("packaging/icons/app-icon.icns")
            else -> file("packaging/icons/app-icon-256.png")
        }.toString()

        installerType = when {
            os.isWindows -> "msi"
            os.isMacOsX -> "dmg"
            else -> "deb"
        }

        if (os.isWindows) {
            installerOptions = listOf("--win-menu", "--win-shortcut", "--win-dir-chooser")
        } else if (os.isLinux) {
            installerOptions = listOf("--linux-shortcut")
        }
    }
}
