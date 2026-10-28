package org.eeditiones.oad;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIResolver;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.ResolverCache;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.models.RefFormat;
import io.swagger.v3.parser.processors.ComponentsProcessor;
import io.swagger.v3.parser.processors.OperationProcessor;
import io.swagger.v3.parser.processors.PathsProcessor;
import io.swagger.v3.parser.reference.DereferencerContext;
import io.swagger.v3.parser.reference.OpenAPIDereferencer31;
import io.swagger.v3.parser.reference.Reference;
import io.swagger.v3.parser.reference.ReferenceVisitor;
import io.swagger.v3.parser.reference.Traverser;
import io.swagger.v3.parser.util.InlineModelResolver;
import io.swagger.v3.parser.util.ResolverFully;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses API definitions stored in the database and resolves their references
 * to other definitions in the database, reading them with the permissions of the current user.
 *
 * swagger-parser can only load references over HTTP or from the file system.
 * Instead of letting it load them, the documents are read from the database and
 * handed to the parser through its reference caches:
 * - OpenAPI 3.0: the external file cache of the {@link ResolverCache}
 * - OpenAPI 3.1: the reference set of the {@link OpenAPIDereferencer31}, keyed by xmldb: URIs
 */
final class DbResolver {

    private static final String SCHEME = "xmldb:";

    private final DBBroker broker;
    private final Map<String, Optional<String>> documents = new HashMap<>();

    DbResolver(final DBBroker broker) {
        this.broker = broker;
    }

    /**
     * @param dbPath absolute database path of the definition
     * @param options parse options, null to only parse
     */
    SwaggerParseResult read(final String dbPath, final ParseOptions options) {
        final String content = readDocument(dbPath);
        if (content == null) {
            return SwaggerParseResult.ofError(String.format("Unable to read location `%s`", dbPath));
        }

        // parse only, references are resolved below
        final SwaggerParseResult result = new OpenAPIV3Parser()
                .readContents(content, null, options == null ? null : new ParseOptions(), dbPath);
        if (options != null && result.getOpenAPI() != null) {
            resolve(result, dbPath, options);
        }
        return result;
    }

    /**
     * Mirrors the resolution steps of OpenAPIV3Parser, using the database aware caches.
     */
    private void resolve(final SwaggerParseResult result, final String dbPath, final ParseOptions options) {
        try {
            if (options.isResolve() || options.isResolveFully()) {
                final OpenAPI openAPI = result.getOpenAPI();
                if (openAPI.getOpenapi() != null && openAPI.getOpenapi().startsWith("3.1")) {
                    final DereferencerContext context = new DereferencerContext(
                            result, Collections.emptyList(), SCHEME + dbPath, options, null, null, true);
                    new DbDereferencer31().dereference(context, Collections.emptyIterator());
                } else {
                    resolve30(result, dbPath, options);
                }
                if (options.isResolveFully()) {
                    new ResolverFully(options.isResolveCombinators()).resolveFully(result.getOpenAPI());
                }
            }
            if (options.isFlatten() && result.getOpenAPI() != null) {
                new InlineModelResolver(options.isFlattenComposedSchemas(), options.isCamelCaseFlattenNaming(),
                        options.isSkipMatches()).flatten(result.getOpenAPI());
            }
        } catch (final Exception e) {
            result.getMessages().add(e.getMessage());
        }
    }

    /**
     * Same steps as {@link OpenAPIResolver#resolve()}, which does not allow to provide the cache.
     */
    private void resolve30(final SwaggerParseResult result, final String dbPath, final ParseOptions options) {
        final OpenAPI openAPI = result.getOpenAPI();
        final Set<String> messages = new HashSet<>();
        final ResolverCache cache = new DbResolverCache(openAPI, dbPath, messages, options);

        new PathsProcessor(cache, openAPI, new OpenAPIResolver.Settings()).processPaths();
        new ComponentsProcessor(openAPI, cache).processComponents();
        if (openAPI.getPaths() != null) {
            final OperationProcessor operationProcessor = new OperationProcessor(cache, openAPI);
            for (final PathItem pathItem : openAPI.getPaths().values()) {
                if (pathItem.readOperations() != null) {
                    for (final Operation operation : pathItem.readOperations()) {
                        operationProcessor.processOperation(operation);
                    }
                }
            }
        }
        result.getMessages().addAll(messages);
    }

    /**
     * Loads relative references from the database before the parser would look for them on the file system.
     * The parser rewrites nested references to be relative to the root definition.
     */
    private final class DbResolverCache extends ResolverCache {
        private final URI root;
        private final Map<String, String> externalFiles;

        DbResolverCache(final OpenAPI openAPI, final String dbPath, final Set<String> messages, final ParseOptions options) {
            super(openAPI, Collections.emptyList(), dbPath, messages, options);
            this.root = URI.create(dbPath);
            this.externalFiles = externalFileCache(this);
        }

        @Override
        public <T> T loadRef(final String ref, final RefFormat refFormat, final Class<T> expectedType) {
            if (refFormat == RefFormat.RELATIVE) {
                final String file = ref.split("#/")[0];
                if (!externalFiles.containsKey(file)) {
                    final String content = readDocument(root.resolve(file).normalize().getPath());
                    if (content == null) {
                        throw new RuntimeException("Unable to load " + refFormat + " ref: " + file);
                    }
                    externalFiles.put(file, content);
                }
            }
            return super.loadRef(ref, refFormat, expectedType);
        }
    }

    /**
     * ResolverCache only exposes an unmodifiable view of the cache it loads external files into.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> externalFileCache(final ResolverCache cache) {
        try {
            final Field field = ResolverCache.class.getDeclaredField("externalFileCache");
            field.setAccessible(true);
            return (Map<String, String>) field.get(cache);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unsupported swagger-parser version, cannot resolve references in the database", e);
        }
    }

    /**
     * Adds all definitions in the database, that the root definition references directly or indirectly,
     * to the reference set before the references are resolved.
     */
    private final class DbDereferencer31 extends OpenAPIDereferencer31 {
        @Override
        public ReferenceVisitor buildReferenceVisitorWithContext(final DereferencerContext context,
                final Reference reference, final Traverser traverser) {
            final Map<String, Reference> referenceSet = reference.getReferenceSet();
            final Deque<URI> queue = new ArrayDeque<>();
            queue.add(URI.create(context.getRootUri()));

            while (!queue.isEmpty()) {
                final URI uri = queue.poll();
                if (referenceSet.containsKey(uri.toString())) {
                    continue;
                }
                final String path = uri.getSchemeSpecificPart();
                final String content = path.startsWith("/db/") ? readDocument(path) : null;
                if (content == null) {
                    continue;
                }
                final JsonNode node = parse31(content);
                referenceSet.put(uri.toString(), new Reference()
                        .uri(uri.toString())
                        .jsonNode(node)
                        .referenceSet(referenceSet)
                        .messages(reference.getMessages())
                        .auths(reference.getAuths()));
                addReferencedFiles(node, uri, queue);
            }
            return super.buildReferenceVisitorWithContext(context, reference, traverser);
        }
    }

    /** Same as ReferenceVisitor#deserializeIntoTree */
    private static JsonNode parse31(final String content) {
        try {
            return content.trim().startsWith("{") ? Json31.mapper().readTree(content) : Yaml31.mapper().readTree(content);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void addReferencedFiles(final JsonNode node, final URI base, final Deque<URI> queue) {
        if (node == null) {
            return;
        }
        final JsonNode ref = node.get("$ref");
        if (ref != null && ref.isTextual()) {
            final String file = ref.asText().split("#", 2)[0];
            if (!file.isEmpty()) {
                final URI referenced = base.resolve(file).normalize();
                if (SCHEME.equals(referenced.getScheme() + ":")) {
                    queue.add(referenced);
                }
            }
        }
        final Iterator<JsonNode> children = node.elements();
        while (children.hasNext()) {
            addReferencedFiles(children.next(), base, queue);
        }
    }

    /**
     * @return the content of a binary document or null if it does not exist, cannot be read or is XML
     */
    private String readDocument(final String path) {
        return documents.computeIfAbsent(path, p -> Optional.ofNullable(readBinary(p))).orElse(null);
    }

    private String readBinary(final String path) {
        try (final LockedDocument locked = broker.getXMLResource(XmldbURI.create(path), LockMode.READ_LOCK)) {
            if (locked == null) {
                return null;
            }
            final DocumentImpl doc = locked.getDocument();
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                return null;
            }
            try (final InputStream is = broker.getBinaryResource((BinaryDocument) doc)) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (final PermissionDeniedException e) {
            return null;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
