// :core - the randomizer engine as a plain JVM library (BUILD_SPEC 3).
// The engine source stays where it is (../src); the Swing GUI and desktop launcher are excluded.
plugins {
    `java-library`
}

sourceSets {
    main {
        java {
            srcDir("src/main/java")      // facade (uprzx.core)
            srcDir("../src")             // engine, unchanged location
            exclude("com/dabomstew/pkrandom/newgui/**/*.java", "launcher/**")
        }
        resources {
            srcDir("../src")             // config/, patches/, newgui/Bundle.properties at their package paths
            exclude("**/*.java", "**/*.form", "launcher/**")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"           // required: engine string literals with non-ASCII text end up in ROMs
    options.release.set(17)
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The engine reads SysConstants.ROOT_PATH from this property; tests use a fresh folder under build/.
    val root = layout.buildDirectory.dir("test-root").get().asFile
    doFirst { root.mkdirs() }
    systemProperty("uprzx.rootPath", root.absolutePath)
    systemProperty("java.awt.headless", "true")
}
