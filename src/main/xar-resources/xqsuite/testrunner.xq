xquery version "3.1";

import module namespace test="http://exist-db.org/xquery/xqsuite"
  at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace tests="//eeditiones.org/ns/oad/tests"
  at "./oad-test.xqm";

declare variable $path := "./oad-test.xqm";

let $test-result := test:suite(inspect:module-functions(xs:anyURI($path)))/node()

return map {
  "package": $test-result/@package/string(),
  "failures": $test-result/@failures/number(),
  "pending": $test-result/@pending/number(),
  "errors": $test-result/@errors/number(),
  "timestamp": xs:dateTime($test-result/@timestamp),
  "time": xs:dayTimeDuration($test-result/@time) div xs:dayTimeDuration("PT0.001S"),
  "tests": $test-result/@tests/number(),
  "testcases": for-each(
      $test-result/testcase,
      function ($case) { map{ $case/@class/string() : ($case/node(), true())[1] }
      })
      => map:merge()
}
=> serialize(map { "method": "json" })