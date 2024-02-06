package org.eeditiones.oad;


import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 */
public class OadModuleTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(false, true);


    @Test
    @Ignore("JUnit tests do not run due to dependencies that cannot be resolved in this environment")
    public void testLoad() throws XPathException, PermissionDeniedException, EXistException {
        final String query =
                "declare namespace oad = \"//eeditiones.org/ns/oad\";\n" +
                "oad:report(\"https://teipublisher.com/exist/apps/tei-publisher/modules/lib/api.json\")\n";
        final Sequence result = executeQuery(query);

        assertTrue(result.hasOne());

        final Source inExpected = Input.fromString("<info></info>").build();
        final Source inActual = Input.fromDocument((Document) result.itemAt(0)).build();

        final Diff diff = DiffBuilder.compare(inExpected)
                .withTest(inActual)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    private Sequence executeQuery(final String xquery) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return xqueryService.execute(broker, xquery, null);
        }
    }
}
