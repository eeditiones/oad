package org.eeditiones.oad;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.source.StringSource;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolves definitions stored in an embedded database with {@link DbResolver} directly.
 */
public class DbResolverTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String COLLECTION = "/db/dbresolver-test";

    private static final String API_30 =
            "openapi: 3.0.2\n" +
            "info: { title: NESTED, version: 1.0.0 }\n" +
            "paths:\n" +
            "  /pets:\n" +
            "    get:\n" +
            "      responses:\n" +
            "        '200':\n" +
            "          description: a pet\n" +
            "          content:\n" +
            "            application/json:\n" +
            "              schema:\n" +
            "                $ref: './schemas/pet.json#/components/schemas/Pet'\n";

    private static final String API_31 = API_30.replace("3.0.2", "3.1.0");

    private static final String PET =
            "{ \"components\": { \"schemas\": { \"Pet\": { \"type\": \"object\"," +
            " \"properties\": { \"name\": { \"$ref\": \"../shared/name.yaml\" } } } } } }";

    private static final String NAME = "type: string\nminLength: 1\n";

    @BeforeAll
    static void storeDefinitions() throws EXistException, PermissionDeniedException, XPathException, IOException {
        final String query =
                "declare variable $api30 external;\n" +
                "declare variable $api31 external;\n" +
                "declare variable $pet external;\n" +
                "declare variable $name external;\n" +
                "xmldb:create-collection('/db', 'dbresolver-test'),\n" +
                "xmldb:create-collection('" + COLLECTION + "', 'schemas'),\n" +
                "xmldb:create-collection('" + COLLECTION + "', 'shared'),\n" +
                "xmldb:store('" + COLLECTION + "', 'api.yaml', $api30),\n" +
                "xmldb:store('" + COLLECTION + "', 'api31.yaml', $api31),\n" +
                "xmldb:store('" + COLLECTION + "/schemas', 'pet.json', $pet),\n" +
                "xmldb:store('" + COLLECTION + "/shared', 'name.yaml', $name),\n" +
                "xmldb:store('" + COLLECTION + "', 'absolute.yaml', replace($api30, './schemas/pet.json', '" + COLLECTION + "/schemas/pet.json')),\n" +
                "xmldb:store('" + COLLECTION + "', 'missing-ref.yaml', replace($api30, './schemas/pet.json', './schemas/missing.json')),\n" +
                "xmldb:store('" + COLLECTION + "', 'escaping-ref.yaml', replace($api30, './schemas/pet.json', '../../../../../../etc/hosts')),\n" +
                "xmldb:store('" + COLLECTION + "', 'private.yaml', $api30),\n" +
                "sm:chmod(xs:anyURI('" + COLLECTION + "/private.yaml'), 'rw-------'),\n" +
                "xmldb:store('" + COLLECTION + "', 'private-ref.yaml', replace($api30, './schemas/pet.json', './private.yaml')),\n" +
                "xmldb:store('" + COLLECTION + "', 'not-json.xml', <api/>)";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final XQueryContext context = new XQueryContext(pool);
            context.declareVariable("api30", API_30);
            context.declareVariable("api31", API_31);
            context.declareVariable("pet", PET);
            context.declareVariable("name", NAME);
            xquery.execute(broker, xquery.compile(context, new StringSource(query)), null);
        }
    }

    @Test
    public void resolvesNestedReferences30() throws EXistException {
        assertResolvedName(resolve(COLLECTION + "/api.yaml"));
    }

    @Test
    public void resolvesNestedReferences31() throws EXistException {
        assertResolvedName(resolve(COLLECTION + "/api31.yaml"));
    }

    @Test
    public void resolvesAbsoluteDatabaseReference() throws EXistException {
        assertResolvedName(resolve(COLLECTION + "/absolute.yaml"));
    }

    @Test
    public void parsesWithoutResolving() throws EXistException {
        final SwaggerParseResult result = read(adminSubject(), COLLECTION + "/api.yaml", null);
        assertEquals("./schemas/pet.json#/components/schemas/Pet", petSchema(result.getOpenAPI()).get$ref());
    }

    @Test
    public void reportsMissingDefinition() throws EXistException {
        final SwaggerParseResult result = resolve(COLLECTION + "/missing.yaml");
        assertNull(result.getOpenAPI());
        assertEquals("Unable to read location `" + COLLECTION + "/missing.yaml`", result.getMessages().get(0));
    }

    @Test
    public void treatsXmlDocumentAsMissing() throws EXistException {
        assertNull(resolve(COLLECTION + "/not-json.xml").getOpenAPI());
    }

    @Test
    public void reportsMissingReference() throws EXistException {
        assertUnresolved(resolve(COLLECTION + "/missing-ref.yaml"), "./schemas/missing.json");
    }

    @Test
    public void doesNotReadFileSystem() throws EXistException {
        assertUnresolved(resolve(COLLECTION + "/escaping-ref.yaml"), "../../../../../../etc/hosts");
    }

    @Test
    public void readsWithPermissionsOfUser() throws EXistException {
        final Subject guest = existEmbeddedServer.getBrokerPool().getSecurityManager().getGuestSubject();
        assertNull(read(guest, COLLECTION + "/private.yaml", resolveOptions()).getOpenAPI());
        assertUnresolved(read(guest, COLLECTION + "/private-ref.yaml", resolveOptions()), "./private.yaml");
        assertNotNull(read(adminSubject(), COLLECTION + "/private-ref.yaml", resolveOptions()).getOpenAPI());
    }

    private static void assertResolvedName(final SwaggerParseResult result) {
        assertTrue(result.getMessages().isEmpty(), result.getMessages().toString());
        final Schema<?> name = petSchema(result.getOpenAPI()).getProperties().get("name");
        assertEquals("string", name.getType() != null ? name.getType() : name.getTypes().iterator().next());
        assertEquals(Integer.valueOf(1), name.getMinLength());
    }

    private static void assertUnresolved(final SwaggerParseResult result, final String file) {
        assertTrue(result.getMessages().stream().anyMatch(m -> m != null && m.contains(file)), result.getMessages().toString());
    }

    private static Schema<?> petSchema(final OpenAPI openAPI) {
        return openAPI.getPaths().get("/pets").getGet().getResponses().get("200")
                .getContent().get("application/json").getSchema();
    }

    private static SwaggerParseResult resolve(final String path) throws EXistException {
        return read(adminSubject(), path, resolveOptions());
    }

    private static SwaggerParseResult read(final Subject subject, final String path, final ParseOptions options) throws EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(subject))) {
            return new DbResolver(broker).read(path, options);
        }
    }

    private static ParseOptions resolveOptions() {
        final ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        return options;
    }

    private static Subject adminSubject() {
        return existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject();
    }
}
