#!/usr/bin/env bash

# Run integration checks in CI.
#
# Can be useful for development, as well. Keep in mind that existdb needs to be restarted after each installation
# for changes to take effect.

version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

response=$(curl "http://admin@localhost:8080/exist/rest/db/system/repo/oad-$version/xqsuite/testrunner.xq" -s)

echo "$response" | jq .

if [[ $? -ne 0 ]]; then
  echo "ERROR: testrunner did not return valid JSON"
  exit 1
elif [[ $(echo "$response" | jq .errors | grep -v 0) ]]; then
  echo "ERRORS!"
  exit 1
elif [[ $(echo "$response" | jq .failures | grep -v 0) ]]; then
  echo "FAILURES!"
  exit 2
else
  echo "PASSED"
  exit 0
fi
