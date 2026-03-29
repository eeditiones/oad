# OAD [ōd]

<img alt="OAD utility logo" src="src/main/xar-resources/icon.svg" width="128" />

> Parse, validate and convert **O**pen**A**PI **D**efinitions in exist-db.

This is a wrapper around the [Swagger-Parser](https://github.com/swagger-api/swagger-parser) library.

## Installation

Built packages of OAD are available on [GitHub releases](https://github.com/eeditiones/oad/releases/latest)
and the [public package repository](https://exist-db.org/exist/apps/public-repo/packages/oad?eXist-db-min-version=6.2.0).

You can visit one of the pages, download the XAR and then install it in your exist-db instance using the package manager.
Alternatively, you can run the [xst](https://github.com/exist-db/xst) command below.

```sh
xst packages install from-registry oad
```

**NOTE:** This XAR comes with addtional JAR files that need to be available at runtime.
It is safer to restart the database after installation - or upgrade - in order to make sure that
the classpath was updated.

## Usage

Try
```xquery
import module namespace oad="//eeditiones.org/ns/oad";

oad:report('https://petstore3.swagger.io/api/v3/openapi.json')
```

All module functions assume you have a API specification _stored_ in exist or available via HTTP. The spec can
be YAML or JSON. It allows to work with internal and external references.

In order for the Swagger-parser to be able to resolve external, relative references all of them need to be public.

If `$uri` is a DB-path (with or without `xmldb:`) **it will be converted to a REST-lookup first**. This means
the REST endpoint must be accessible.

### Example

If you have a specification stored in `/db/apps/myapp/api.json`

```json
{
  "openapi" : "3.0.2",
  "info" : { "title" : "my specification", "version": "1.0.0" },
  "paths" : {
    "/find" : {
      "get" : {
        "responses" : {
          "200" : {
            "description" : "result",
            "content" : {
              "text/plain" : {
                "schema" : {
                  "type" : "string"
                }
              }
            }
          }
        }
      }
    }
  }
}
```

Then

```xquery
import module namespace oad="//eeditiones.org/ns/oad";

oad:report('/db/apps/myapp/api.json')
```

will return

```xml
<info>
    <title>my specification</title>
    <description/>
    <version>1.0.0</version>
    <servers>
        <server url="/"/>
    </servers>
</info>
```

and

```xquery
import module namespace oad="//eeditiones.org/ns/oad";

oad:validate('/db/apps/myapp/api.json')
```

yields

```xquery
true()
```

## Functions

### `oad:validate($uri as xs:string) as xs:boolean`

Returns true() if the API definition is valid, false() otherwise (use `oad:report` to see the list of issues found).

### `oad:report($uri as xs:string) as document()`

Inspect the given definition and summarize the information into an XML document with an info-element at its root.
If the parser encounters issues these are listed as separate error-elements under errors. 

### `oad:flatten($uri as xs:string) as xs:string`

Flatten will inspect the given definition and extract schemas, parameters and such into components. They are then
replaced by references.

### `oad:resolve($uri as xs:string) as xs:string`

Resolves both internal and external references in an API definition. This will allow you to use it with Roaster, for 
example.

### `oad:convert($uri as xs:string, map(xs:string, *)) as xs:string`

The swiss-army knife of the available functions. Can convert definitions from one format to the other (JSON to YAML/
YAML to JSON) while _also_ allowing you to either resolve or flatten the definition.

The available options are listed in the table below:

| option   | description                 | allowed values            | default  |
|----------|-----------------------------|---------------------------|----------|
| `format` | serialization format        | `"json"`, `"yaml"`        | `"json"` | 
| `method` | what to do with references? | `"flatten"`, `"resolve""` | _none_   |

## Build

* Requirements
  * Java 17
  * Gradle 8.5

```bash
./gradlew build
```

will create a `oad-<version>.xar` file in the folder `build/libs`.

## Tests

There are no unit tests that will be executed when building the project.
The built package does include [xqsuite tests](src/main/xar-resources/xqsuite/oad-test.xqm) which test the integration
into exist-db works as intended.

You can call that as part of your development workflow by running

```bash
./run-integration-tests.sh
```

## Release

- Update the version in build.gradle.kts
- commit, tag and push the new version
- build the project
- draft a new release and add the XAR file to it

**NOTE:** The release process is work in progress after switching to gradle as the build tool.

## Sponsors

This package was made possible thanks to the contributions from following institutions

<a href="https://www.adwmainz.de/startseite.html"><img alt="ADW logo" src="resources/sponsor-logo-adw.png" height="100" /></a> &nbsp; <a href="https://eured.de"><img alt="EuReD logo" src="resources/sponsor-logo-eured.png" height="100" /></a> &nbsp; <a href="https://www.ulb.tu-darmstadt.de/die_bibliothek/index.de.jsp"><img alt="ULB logo" src="resources/sponsor-logo-ulb.png" height="100" /></a>
