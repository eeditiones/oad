# Gradle Migration Guide

This project has been configured for Gradle build system. The following files have been created:

## Files Created

1. **build.gradle** - Main Gradle build configuration with:
   - Java plugin configuration (Java 1.8)
   - Dependencies from Maven pom.xml
   - JAR manifest configuration
   - Publishing configuration for Maven artifacts
   - XAR resource file handling

2. **settings.gradle** - Gradle settings file with project name

3. **gradle.properties** - Gradle configuration properties

4. **gradlew** - Unix/Linux/Mac Gradle wrapper script

5. **gradlew.bat** - Windows Gradle wrapper script

6. **gradle/wrapper/gradle-wrapper.properties** - Gradle wrapper configuration

## Building the Project

### Using Gradle wrapper (recommended):

```bash
./gradlew build
```

### Using installed Gradle:

```bash
gradle build
```

## Available Tasks

- `./gradlew build` - Compile, test, and package the project
- `./gradlew test` - Run unit tests
- `./gradlew jar` - Create JAR file
- `./gradlew javadoc` - Generate JavaDoc
- `./gradlew clean` - Remove build artifacts
- `./gradlew publish` - Publish to Maven repositories

## Configuration Notes

The build.gradle includes:

- **Source compatibility**: Java 1.8
- **Target compatibility**: Java 1.8
- **Main dependencies**:
  - io.swagger.parser.v3:swagger-parser:2.1.19
  - com.fasterxml.jackson.core:jackson-core:2.15.2
  - org.exist-db:exist-core:6.2.0 (provided)
  
- **Test dependencies**:
  - junit:junit:4.13.2
  - org.xmlunit:xmlunit-core:2.9.0

- **Repositories**:
  - Maven Central
  - eXist-db repository (https://repo.exist-db.oorg/repository/exist-db/)

- **Publishing**: Configured for Maven publication with complete POM metadata

## Important Notes

1. The wrapper JAR needs to be downloaded. You can either:
   - Run `gradle wrapper` if you have Gradle installed locally
   - Download the jar from: https://github.com/gradle/gradle/releases/download/v8.5/gradle-wrapper.jar
   - Place it in `gradle/wrapper/gradle-wrapper.jar`

2. To remove Maven configuration:
   ```bash
   rm pom.xml xar-assembly.xml oad.iml
   ```

3. The XAR package creation task is available as `./gradlew makeXar` (currently a placeholder - additional configuration may be needed for eXist-db specific packaging)

4. All Maven repository references have been mapped to equivalent Gradle repositories

## Next Steps

1. Install Gradle locally or download the wrapper JAR
2. Run `./gradlew build` to compile and test
3. Update `gradle/wrapper/gradle-wrapper.jar` if needed
4. Consider creating a Gradle plugin for XAR package creation (kuberam-expath-plugin equivalent)
