// Erlaubt Gradle, das per Toolchain geforderte JDK bei Bedarf selbst
// herunterzuladen (Liberica JDK 25.0.4.1), statt den Build mit
// "Cannot find a Java installation ..." abzubrechen. Siehe build.gradle.kts.
// Gleiches Vorgehen wie im Schwesterprojekt iTrain_import_export.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "iTrain-Logistik"
