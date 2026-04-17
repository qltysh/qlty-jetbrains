import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

group = "com.qlty"
version = "0.1.1"

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = "Qlty"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "243"
        }

        description =
            """
            Code quality and linting powered by the <a href="https://qlty.sh">Qlty CLI</a>.

            Surfaces diagnostics from 200+ linters directly in your editor with inline quick fixes
            and bulk "Fix all in file" support. Works with all JetBrains IDEs including Rider,
            IntelliJ IDEA, WebStorm, PyCharm, and more.
            """.trimIndent()

        changeNotes =
            """
            <h3>0.1.1</h3>
            <ul>
                <li>Improve monorepo config detection for attached content roots</li>
                <li>Harden plugin verification and JSON parsing behavior</li>
                <li>Add regression test coverage across analysis, status bar, and save flows</li>
            </ul>
            """.trimIndent()

        vendor {
            name = "Qlty Software, Inc."
            url = "https://qlty.sh"
            email = "support@qlty.sh"
        }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.Rider, "2025.3")
        }
    }
}

tasks {
    test {
        useJUnit()
    }
}
