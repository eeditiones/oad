import org.existdb.plugin.PackageType.LIBRARY
import org.existdb.plugin.exist7orLater

plugins {
    id("java")
    id("base")
    id("org.exist-db.plugin.xar") version "1.0.0"
}

group = "org.eeditiones.oad"
description = "Validate, inspect and convert OpenAPI definitions"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// eXist-db snapshots are built from develop, like the latest Docker image
val existVersion = "7.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    // eXist-db snapshots are only published to GitHub Packages, which requires a token
    maven("https://maven.pkg.github.com/eXist-db/exist") {
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).getOrElse("token")
            password = providers.gradleProperty("gpr.token").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
        }
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    implementation("io.swagger.parser.v3:swagger-parser:2.1.19")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.3")

    compileOnly("org.exist-db:exist-core:$existVersion")

    testImplementation("org.exist-db:exist-core:$existVersion")
    // the test harness and the XQSuite JUnit engine are in the tests jar
    testImplementation("org.exist-db:exist-core:$existVersion:tests")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

xar {
    namespace = "//eeditiones.org/ns/oad"
    abbrev = "oad"
    title = "OpenAPI Definition Utility"
    description = "OpenAPI Definition Utility for eXist-db"
    author = "The e-editiones contributors"
    home = "https://github.com/eeditiones/oad"
    status = "beta"
    license = "GNU Lesser General Public License, version 3.0"
    type = LIBRARY
    javaClass = "org.eeditiones.oad.OadModule"
    requiredJars = listOf(
        "swagger-parser-core-2.1.19.jar",
        "swagger-parser-2.1.19.jar",
        "swagger-parser-safe-url-resolver-2.1.19.jar",
        "swagger-core-2.2.19.jar",
        "swagger-parser-v3-2.1.19.jar",
        "swagger-models-2.2.19.jar",
        "jackson-core-2.15.3.jar",
        "jackson-databind-2.15.3.jar",
        "jackson-dataformat-yaml-2.15.3.jar",
        "jackson-datatype-jsr310-2.15.3.jar",
        "snakeyaml-2.2.jar"
    )
    // the XQuery type constants changed in eXist-db 7, the module does not work with earlier versions
    processorDependencies = listOf( exist7orLater )
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version.toString(),
            "Implementation-URL" to "https://github.com/eeditiones/oad",
            "Source-Repository" to "scm:git:https://github.com/eeditiones/oad.git"
        )
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // the XSuite runner reads the XQSuite tests from here
    inputs.dir("src/test/xquery").withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.4.1"
    distributionType = Wrapper.DistributionType.BIN
}
