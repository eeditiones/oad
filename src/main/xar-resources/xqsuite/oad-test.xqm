xquery version "3.1";

module namespace toad="//eeditiones.org/ns/oad/tests";

import module namespace oad="//eeditiones.org/ns/oad";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $toad:collection-name := "oad-test";
declare variable $toad:collection-path := "/db/" || $toad:collection-name || "/";

declare variable $toad:valid-json-spec := '
{
  "openapi" : "3.0.2",
  "info" : { "title" : "VALID JSON", "version": "1.0.0" },
  "servers": [ { "url": "/test/" } ],
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
  }}}}}}}}
}
';

declare variable $toad:with-ext-ref-json-spec := '
{
  "openapi" : "3.0.2",
  "info" : { "title" : "VALID JSON", "version": "1.0.0" },
  "paths" : {
    "/find" : {
      "get" : {
        "responses" : {
          "200" : {
            "description" : "result",
            "content" : {
              "text/plain" : {
                "schema" : {
                  "$ref": "./string.json"
  }}}}}}}}
}
';

declare variable $toad:comp-spec := '{ "type" : "string" }';

(: references across collections: api.yaml -> schemas/pet.json -> ../shared/name.yaml :)
declare variable $toad:nested-ref-yaml-spec := '
openapi: 3.0.2
info:
  title: NESTED REFS
  version: 1.0.0
paths:
  /pets:
    get:
      responses:
        "200":
          description: a pet
          content:
            application/json:
              schema:
                $ref: "./schemas/pet.json#/components/schemas/Pet"
';

declare variable $toad:pet-spec := '
{
  "components": {
    "schemas": {
      "Pet": {
        "type": "object",
        "properties": {
          "name": { "$ref": "../shared/name.yaml" }
        }
      }
    }
  }
}
';

declare variable $toad:nested-ref-yaml-spec-31 := '
openapi: 3.1.0
info:
  title: NESTED REFS 3.1
  version: 1.0.0
paths:
  /pets:
    get:
      responses:
        "200":
          description: a pet
          content:
            application/json:
              schema:
                $ref: "./schemas/pet.json#/components/schemas/Pet"
';

declare variable $toad:absolute-ref-json-spec := '
{
  "openapi" : "3.0.2",
  "info" : { "title" : "ABSOLUTE REF", "version": "1.0.0" },
  "paths" : {
    "/find" : {
      "get" : {
        "responses" : {
          "200" : {
            "description" : "result",
            "content" : {
              "text/plain" : {
                "schema" : {
                  "$ref": "/db/oad-test/nested/shared/name.yaml"
  }}}}}}}}
}
';

(: must never be read from the file system of the server :)
declare variable $toad:escaping-ref-json-spec := '
{
  "openapi" : "3.0.2",
  "info" : { "title" : "ESCAPING REF", "version": "1.0.0" },
  "paths" : {
    "/find" : {
      "get" : {
        "responses" : {
          "200" : {
            "description" : "result",
            "content" : {
              "text/plain" : {
                "schema" : {
                  "$ref": "../../../../../../../../etc/hosts"
  }}}}}}}}
}
';

declare variable $toad:name-spec := 'type: string
minLength: 1
';


declare variable $toad:invalid-json-spec := '
{
  "openapi" : "3.0.2",
  "info" : { "title" : "INVALID JSON" },
  "paths" : {
    "/find" : {
      "get" : {
        "responses" : {
          "200" : {
            "description" : "result",
            "content" : {
              "text/plain" : {
                "schema": {
                  "$ref" : "#/components/missing"
  }}}}}}}},
  "components" : { }
}
';

declare variable $toad:valid-yaml-spec := '
openapi: 3.0.2
info:
  title: VALID YAML
  version: 1.0.0
paths:
  /find:
    get:
      responses:
        "200":
          description: result
          content:
            text/plain:
              schema:
                type: string
';
declare variable $toad:invalid-yaml-spec := '
openapi: 3.0.2
info:
  title: INVALID YAML
paths:
  /find:
    get:
      responses:
        "200":
          description: result
          content:
            text/plain:
              schema:
                $ref: #/components/missing
components:
';

declare variable $toad:external-specs := map{
    "json": "https://petstore3.swagger.io/api/v3/openapi.json",
    "yaml": "https://petstore3.swagger.io/api/v3/openapi.yaml"
};

declare
    %test:setUp
function toad:setup() {
    xmldb:create-collection("/db", $toad:collection-name),

    xmldb:store($toad:collection-path, "valid.json", $toad:valid-json-spec),
    xmldb:store($toad:collection-path, "valid.yaml", $toad:valid-yaml-spec),

    xmldb:store($toad:collection-path, "invalid.json", $toad:invalid-json-spec),
    xmldb:store($toad:collection-path, "invalid.yaml", $toad:invalid-yaml-spec),

    xmldb:store($toad:collection-path, "with-ext-ref.json", $toad:with-ext-ref-json-spec),
    xmldb:store($toad:collection-path, "string.json", $toad:comp-spec),

    xmldb:create-collection($toad:collection-path, "nested"),
    xmldb:create-collection($toad:collection-path || "nested", "schemas"),
    xmldb:create-collection($toad:collection-path || "nested", "shared"),
    xmldb:store($toad:collection-path || "nested", "api.yaml", $toad:nested-ref-yaml-spec),
    xmldb:store($toad:collection-path || "nested/schemas", "pet.json", $toad:pet-spec),
    xmldb:store($toad:collection-path || "nested/shared", "name.yaml", $toad:name-spec),
    xmldb:store($toad:collection-path || "nested", "api31.yaml", $toad:nested-ref-yaml-spec-31),

    xmldb:store($toad:collection-path, "absolute-ref.json", $toad:absolute-ref-json-spec),
    xmldb:store($toad:collection-path, "escaping-ref.json", $toad:escaping-ref-json-spec)
};

declare
    %test:tearDown
function toad:teardown() {
    xmldb:remove($toad:collection-path)
};

declare
    %test:args("valid.json") %test:assertTrue
    %test:args("valid.yaml") %test:assertTrue

    %test:args("invalid.json") %test:assertFalse
    %test:args("invalid.yaml") %test:assertFalse
function toad:validate-local ($file as xs:string) as xs:boolean {
    oad:validate($toad:collection-path || $file)
};

declare
    %test:args("nonexistent.json") %test:assertFalse
function toad:validate-local-non-existent ($file as xs:string) as xs:boolean {
    oad:validate($toad:collection-path || $file)
};

declare
    %test:args("json") %test:assertTrue
    %test:args("yaml") %test:assertTrue
function toad:validate-external ($external-file-format as xs:string) as xs:boolean {
    oad:validate(
        $toad:external-specs?($external-file-format))
};

declare
    %test:args("valid.json") %test:assertTrue
    %test:args("valid.yaml") %test:assertTrue
function toad:report-local-valid-has-info($file as xs:string) as xs:boolean {
    exists(
        oad:report($toad:collection-path || $file)
            //info)
};

declare
    %test:args("valid.json") %test:assertEquals("/test/")
function toad:report-local-server-url-without-host($file as xs:string) as xs:string {
    oad:report($toad:collection-path || $file)/node()
        //server/@url/string()
};

declare
    %test:args("valid.json") %test:assertFalse
    %test:args("valid.yaml") %test:assertFalse
function toad:report-local-valid-has-error($file as xs:string) as xs:boolean {
    exists(
        oad:report($toad:collection-path || $file)
            //error)
};

declare
    %test:args("invalid.json") %test:assertTrue
    %test:args("invalid.yaml") %test:assertTrue
function toad:report-local-invalid-has-info($file as xs:string) as xs:boolean {
    exists(
        oad:report($toad:collection-path || $file)
            //info)
};

declare
    %test:args("invalid.json") %test:assertTrue
    %test:args("invalid.yaml") %test:assertTrue
function toad:report-local-invalid-has-error($file as xs:string) as xs:boolean{
    exists(
        oad:report($toad:collection-path || $file)
            //error)
};

declare
    %test:args("json") %test:assertTrue
    %test:args("yaml") %test:assertTrue
function toad:report-external($external-file-format as xs:string) as xs:boolean {
    exists(oad:report($toad:external-specs?($external-file-format))//info)
};

declare
    %test:args("valid.json") %test:assertTrue
    %test:args("valid.yaml") %test:assertTrue
function toad:flatten-adds-components($file as xs:string) as xs:boolean {
    exists(
        parse-json(
            oad:flatten($toad:collection-path || $file)
        )?components)
};

declare
    %test:args("valid.json") %test:assertTrue
    %test:args("valid.yaml") %test:assertTrue
function toad:resolve($file as xs:string) as xs:boolean {
    let $resolved := parse-json(oad:resolve($toad:collection-path || $file))
    return not(
        exists($resolved?components) and
        exists($resolved?paths?("/find")?get?responses?("200")?schema?("$refs"))
    )
};

declare
    %test:assertTrue
function toad:resolve-external-returns() as xs:boolean {
    exists(oad:resolve($toad:collection-path || "with-ext-ref.json"))
};

declare
    %test:assertTrue
function toad:resolve-external-adds-component() as xs:boolean {
    exists(
        parse-json(oad:resolve($toad:collection-path || "with-ext-ref.json"))
            ?components?schemas?string)
};

declare
    %test:assertFalse
function toad:resolve-external-resolves-fully() as xs:boolean {
    exists(
        parse-json(oad:resolve($toad:collection-path || "with-ext-ref.json"))
            ?paths?("/find")?get?responses?("200")?schema?("$refs"))
};

declare
    %test:args("valid.json") %test:assertTrue
function toad:convert-json-to-yaml($file as xs:string) as xs:boolean {
    exists(oad:convert($toad:collection-path || $file, map{"format": "yaml"}))
};

declare
    %test:args("valid.yaml") %test:assertTrue
function toad:convert-flatten-yaml($file as xs:string) as xs:boolean {
    exists(oad:convert($toad:collection-path || $file, map{"method": "flatten"}))
};

declare
    %test:assertTrue
function toad:validate-nested-refs() as xs:boolean {
    oad:validate($toad:collection-path || "nested/api.yaml")
};

declare
    %test:assertEquals("string", 1)
function toad:resolve-nested-refs() {
    let $resolved := parse-json(oad:resolve($toad:collection-path || "nested/api.yaml"))
    let $name := $resolved?paths?("/pets")?get?responses?("200")?content?("application/json")?schema?properties?name
    return ($name?type, $name?minLength)
};

declare
    %test:assertEquals("Unable to read location `/db/oad-test/nonexistent.json`")
function toad:report-non-existent-error-has-db-path() as xs:string {
    oad:report($toad:collection-path || "nonexistent.json")//error/string()
};

declare
    %test:args("nested/api.yaml") %test:assertEquals("string", 1)
    %test:args("nested/api31.yaml") %test:assertEquals("string", 1)
function toad:resolve-nested-refs-all-versions($file as xs:string) {
    let $resolved := parse-json(oad:resolve($toad:collection-path || $file))
    let $name := $resolved?paths?("/pets")?get?responses?("200")?content?("application/json")?schema?properties?name
    return ($name?type, $name?minLength)
};

declare
    %test:assertEquals("string")
function toad:resolve-absolute-db-ref() as xs:string? {
    parse-json(oad:resolve($toad:collection-path || "absolute-ref.json"))
        ?paths?("/find")?get?responses?("200")?content?("text/plain")?schema?type
};

declare
    %test:assertFalse
function toad:resolve-does-not-read-file-system() as xs:boolean {
    contains(oad:resolve($toad:collection-path || "escaping-ref.json"), "localhost")
};

