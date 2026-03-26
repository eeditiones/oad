plugins {
    id("java")
    id("maven-publish")
    id("base")
}

group = "org.eeditiones.oad"
version = "1.0.7"
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

// Create the xar-resources directory
tasks.register("createXarResources") {
    group = "prepare"
    description = "Creates the folder for XAR resources"
    doLast {
        val xarDir = file("$buildDir/xar-resources")
        xarDir.mkdirs()
        file("$buildDir/xar-resources/content").mkdirs()
    }
}

tasks.register<Copy>("copyXarResources") {
    group = "build"
    description = "Copies necessary resources for XAR packaging"
    dependsOn(":createXarResources")
    from("src/main/xar-resources")
    into("$buildDir/xar-resources")
}
    
tasks.register<Copy>("copyXarResources2") {
    group = "build"
    description = "Copies necessary resources for XAR packaging"
    dependsOn(":createXarResources")
    from(".") {
        include("README.md")
        include("LICENSE")
    }
    into("$buildDir/xar-resources")
}

tasks.register<Delete>("cleanXarResources") {
    group = "build"
    description = "Cleans the XAR resources directory"
    delete("$buildDir/xar-resources")
}

tasks.named<Delete>("clean") {
    dependsOn(":cleanXarResources")
}

tasks.register("createExpathPackageDescriptor") {
    group = "build"
    description = "Creates the expath-pkg.xml descriptor for the XAR package"
    dependsOn(":createXarResources")

    // Generate expath-pkg.xml
    doLast {
        file("$buildDir/xar-resources/expath-pkg.xml").writeText("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://expath.org/ns/pkg"
         xmlns:xs="http://www.w3.org/2001/XMLSchema"
         name="//eeditiones.org/ns/oad"
         abbrev="oad"
         version="$version"
         spec="1.0">
   <title>OpenAPI Definition Utility</title>
   <home>https://github.com/eeditiones/oad</home>
   <dependency processor="http://exist-db.org" semver-min="6.2.0"/>
</package>""")
    }
}

tasks.register("createRepoXml") {
    group = "build"
    description = "Creates the repo.xml descriptor for the XAR package"
    dependsOn(":createXarResources")
    // Generate repo.xml
    doLast {
        file("$buildDir/xar-resources/repo.xml").writeText("""<?xml version="1.0" encoding="UTF-8"?>
<meta xmlns="http://exist-db.org/xquery/repo"
      xmlns:repo="http://exist-db.org/xquery/repo"
      xmlns:xs="http://www.w3.org/2001/XMLSchema">
   <description/>
   <author id="org.eeditiones">eeditiones.org</author>
   <website>https://github.com/eeditiones/oad</website>
   <status>beta</status>
   <license>GNU Lesser General Public License, version 3.0</license>
   <copyright>true</copyright>
   <type>library</type>
</meta>""")
    }
}

tasks.register("createExistXml") {
    dependsOn(":createXarResources")
    dependsOn(":jar")
    group = "build"

    doLast {
        // Hardcoded jar list that must be placed into content/
        val projectJarName = "${project.name}-${project.version}.jar"
        val requiredJarNames = listOf(
            projectJarName,
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

        // Generate exist.xml with JAR list
        val jarList = requiredJarNames.joinToString("\n") { "   <jar>$it</jar>" }

        file("$buildDir/xar-resources/exist.xml").writeText("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://exist-db.org/ns/expath-pkg"
         xmlns:xs="http://www.w3.org/2001/XMLSchema">
   <java>
      <namespace>//eeditiones.org/ns/oad</namespace>
      <class>org.eeditiones.oad.OadModule</class>
   </java>
$jarList
</package>""")

        val compiledJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile

        val runtimeAndProjectJars = configurations.runtimeClasspath.get().files + compiledJar
        val selectedJarFiles = requiredJarNames.mapNotNull { name -> runtimeAndProjectJars.find { it.name == name } }

        if (selectedJarFiles.size != requiredJarNames.size) {
            val missing = requiredJarNames - selectedJarFiles.map { it.name }
            throw GradleException("Missing required JAR(s) for XAR: $missing")
        }

        selectedJarFiles.forEach { jarFile ->
            copy {
                from(jarFile)
                into("$buildDir/xar-resources/content")
            }
        }

        // Also copy all runtime dependencies to content (safe, but not required)
        // copy {
        //     from(configurations.runtimeClasspath.get())
        //     into("$buildDir/xar-resources/content")
        // }
    }

}

tasks.register<Zip>("makeXar") {
    dependsOn(":createXarResources")
    dependsOn(":copyXarResources")
    dependsOn(":copyXarResources2")
    dependsOn(":createExpathPackageDescriptor")
    dependsOn(":createRepoXml")
    dependsOn(":createExistXml")
    group = "package"
    description = "Creates the XAR package for eXist-db"


    from("$buildDir/xar-resources") {
        include("**/*")
    }

    archiveFileName.set("${project.name}-${project.version}.xar")
    destinationDirectory.set(file("$buildDir/libs"))
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

sourceSets {
    main {
        resources {
            srcDirs("src/main/xar-resources")
            include("**/*")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://github.com/eeditiones/oad")

                organization {
                    name.set("e-editiones")
                    url.set("http://eeditiones.org")
                }

                licenses {
                    license {
                        name.set("GNU Lesser General Public License, version 3.0")
                        url.set("http://opensource.org/licenses/LGPL-3.0")
                        distribution.set("repo")
                    }
                }

                scm {
                    url.set("https://github.com/eeditiones/oad")
                    connection.set("scm:git:https://github.com/eeditiones/oad.git")
                    developerConnection.set("scm:git:https://github.com/eeditiones/oad.git")
                    tag.set("HEAD")
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/eeditiones/oad/issues")
                }
            }
        }
    }
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.BIN
}
