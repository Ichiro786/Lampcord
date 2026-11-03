plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {

            if (requested.group == "io.ktor") {
                useVersion(libs.versions.ktor.get())
                because("all ktor modules must resolve to a single version")
            }

            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-reflect") {
                useVersion(libs.versions.kotlin.get())
                because("kotlin-reflect must match kotlin-stdlib")
            }
        }
    }
}
