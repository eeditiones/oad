package org.eeditiones.oad;

import org.exist.test.runner.XSuite;
import org.junit.runner.RunWith;

/**
 * Runs the XQSuite tests in src/test/xquery against an embedded database
 * with the oad module registered in src/test/resources/conf.xml.
 */
@RunWith(XSuite.class)
@XSuite.XSuiteFiles({
    "src/test/xquery"
})
public class XQSuiteTest {
}
