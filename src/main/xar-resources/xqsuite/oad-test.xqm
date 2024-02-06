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
    xmldb:store($toad:collection-path, "string.json", $toad:comp-spec)
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
