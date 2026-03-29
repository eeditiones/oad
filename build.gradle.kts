import org.existdb.plugin.PackageType.LIBRARY
import org.existdb.plugin.exist6only

plugins {
    id("java")
    id("base")
    id("org.exist-db.plugin.xar") version "1.0.0"
}

group = "org.eeditiones.oad"
description = "Validate, inspect and convert OpenAPI definitions"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.swagger.parser.v3:swagger-parser:2.1.19")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.3")

    compileOnly("org.exist-db:exist-core:6.4.1")

    testImplementation("org.exist-db:exist-core:6.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xmlunit:xmlunit-core:2.9.0")
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
    processorDependencies = listOf( exist6only )
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor-Id" to group,
            "Implementation-URL" to "https://github.com/eeditiones/oad",
            "Source-Repository" to "scm:git:https://github.com/eeditiones/oad.git",
            "Description" to project.description
        )
    }
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.BIN
}
