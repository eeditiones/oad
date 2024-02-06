package org.eeditiones.oad;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import javax.xml.XMLConstants;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.exist.xquery.FunctionDSL.*;
import static org.eeditiones.oad.OadModule.functionSignature;

/**
 * OAD functions
 */
public class OadFunctions extends BasicFunction {

    private final String restURL;
    private final String restServer;

    private static final QName ERRORS = new QName("errors", XMLConstants.NULL_NS_URI);
    private static final QName ERROR = new QName("error", XMLConstants.NULL_NS_URI);
    private static final QName INFO = new QName("info", XMLConstants.NULL_NS_URI);
    private static final QName VERSION = new QName("version", XMLConstants.NULL_NS_URI);
    private static final QName DESCRIPTION = new QName("description", XMLConstants.NULL_NS_URI);
    private static final QName TITLE = new QName("title", XMLConstants.NULL_NS_URI);
    private static final QName SERVERS = new QName("servers", XMLConstants.NULL_NS_URI);
    private static final QName SERVER = new QName("server", XMLConstants.NULL_NS_URI);
    private static final QName URL = new QName("url", XMLConstants.NULL_NS_URI);


    private static final StringValue FORMAT_KEY = new StringValue("format");
    private static final StringValue MODE_KEY = new StringValue("mode");

//    private static final Logger logger = LogManager.getLogger(OasFunctions.class);

    private static final FunctionParameterSequenceType resourceParam = param("resource", Type.STRING, "URI to YAML or JSON file");

    private static final String REPORT_FUNCTION_NAME = "report";
    static final FunctionSignature REPORT_FUNCTION = functionSignature(
            REPORT_FUNCTION_NAME,
            "Parse OpenAPI and report errors specification",
            returns(Type.DOCUMENT),
            resourceParam
    );

    private static final String VALIDATE_FUNCTION_NAME = "validate";
    static final FunctionSignature VALIDATE_FUNCTION = functionSignature(
            VALIDATE_FUNCTION_NAME,
            "Validate OpenAPI specification",
            returns(Type.BOOLEAN),
            resourceParam
    );

    private static final String CONVERT_FUNCTION_NAME = "convert";
    static final FunctionSignature CONVERT_FUNCTION = functionSignature(
            CONVERT_FUNCTION_NAME,
            "Convert OpenAPI specification between formats, allowing to inline or extract $refs. Keys are 'format': 'yaml' or 'json' (default) and 'mode': 'flatten', 'resolve'",
            returns(Type.STRING),
            resourceParam,
            optParam("options", Type.MAP, "Options")
    );

    private static final String FLATTEN_FUNCTION_NAME = "flatten";
    static final FunctionSignature FLATTEN_FUNCTION = functionSignature(
            FLATTEN_FUNCTION_NAME,
            "Flatten OpenAPI specification. Extract components and add $refs.",
            returns(Type.STRING),
            resourceParam
    );

    private static final String RESOLVE_FUNCTION_NAME = "resolve";
    static final FunctionSignature RESOLVE_FUNCTION = functionSignature(
            RESOLVE_FUNCTION_NAME,
            "Resolve components in OpenAPI specification, inlining $refs",
            returns(Type.STRING),
            resourceParam
    );

    public OadFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);

        final XQueryContext.HttpContext ctx = context.getHttpContext();
        if (ctx == null) {
            this.restServer = "http://" + System.getProperty("jetty.host") + ":" + System.getProperty("jetty.port", "8080");
            this.restURL = this.restServer + "/exist/rest";
        } else {
            final RequestWrapper rw = ctx.getRequest();
            this.restServer = rw.getScheme() + "://" + rw.getServerName() + ":" + rw.getServerPort();
            this.restURL = this.restServer + rw.getContextPath() + "/rest";
        }
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final StringValue rawLocation = (StringValue) args[0].itemAt(0);
        final String location = normalizeLocation(rawLocation.toString());

        switch (getName().getLocalPart()) {
            case VALIDATE_FUNCTION_NAME: return validate(location);
            case REPORT_FUNCTION_NAME: return parse(location);
            case FLATTEN_FUNCTION_NAME: return flatten(location);
            case RESOLVE_FUNCTION_NAME: return resolve(location);
            case CONVERT_FUNCTION_NAME: return convert(location, (MapType) args[1].itemAt(0));
            default:
                throw new XPathException(this, ErrorCodes.EXXQDY0006,
                        "No function: " + getName() + "#" + getSignature().getArgumentCount());
        }
    }

    /**
     * Extract components, creating references.
     *
     * @param location to parse
     *
     * @return Sequence of validation errors or API info
     */
    private StringValue flatten(final String location) throws XPathException {
        final ParseOptions parseOptions = new ParseOptions();
        parseOptions.setFlatten(true);
        final SwaggerParseResult result = read(location, parseOptions);
        return new StringValue(serialize(result, false));
    }

    private SwaggerParseResult read(final String location, final ParseOptions parseOptions) {
        return new OpenAPIV3Parser().readLocation(location, null, parseOptions);
    }

    /**
     * Resolve components, inlining references.
     *
     * @param location to parse
     *
     * @return Sequence of validation errors or API info
     */
    private Sequence resolve(final String location) throws XPathException {
        final ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(true);
        final SwaggerParseResult result = read(location, parseOptions);
        return new StringValue(serialize(result, false));
    }

    /**
     * Validates an API definition.
     *
     * @param location to parse
     *
     * @return true() if valid, false() when errors are present
     */
    private BooleanValue validate(final String location) {
        final SwaggerParseResult result = read(location, null);
        final OpenAPI openAPI = result.getOpenAPI();
        final boolean errors = !result.getMessages().isEmpty();
        final boolean valid = !errors && openAPI != null;
        return new BooleanValue(valid);
    }

    /**
     * Creates an XML document with information about the API definition.
     * If errors are found, these are listed under <errors/>.
     *
     * @param location to parse
     *
     * @return API definition info
     */
    private Sequence parse(final String location) {
        final SwaggerParseResult result = read(location, null);
        return renderResult(result);
    }

    /**
     * Returns a pretty printed string representation in the desired format defaulting to JSON.
     * When the mode option is set, references are either inlined or extracted.
     *
     * @param location to parse
     *
     * @return pretty printed API definition in the desired format.
     */
    private Sequence convert(final String location, MapType options) throws XPathException {
        final ParseOptions parseOptions = new ParseOptions();
        switch (options.get(MODE_KEY).toString()) {
            case "flatten":
                parseOptions.setFlatten(true);
                break;
            case "resolve":
                parseOptions.setResolve(true);
                parseOptions.setResolveFully(true);
                break;
//            default: throw new IllegalArgumentException();
        }

        final SwaggerParseResult result = read(location, parseOptions);
        final boolean asYaml = options.contains(FORMAT_KEY) && Objects.equals(options.get(FORMAT_KEY).toString(), "yaml");
        return new StringValue(serialize(result, asYaml));
    }


//    ----- private methods ----

    private String serialize(final SwaggerParseResult result, final boolean asYaml) throws XPathException {
        final OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) throw new XPathException(this, OadModule.NO_API, "OpenAPI definition appears to be empty");
        final boolean v31 = result.isOpenapi31();

        final OpenAPI serversFixed = fixServerUrl(openAPI);

        if (asYaml && v31) return Yaml31.pretty(serversFixed);
        if (asYaml) return Yaml.pretty(serversFixed);
        if (v31) return Json31.pretty(serversFixed);
        return Json.pretty(serversFixed);
    }

    private Server stripRestServer (final Server srv) {
        final String url = srv.getUrl();
        if (url.startsWith(this.restServer)) {
            srv.setUrl(url.replace(this.restServer, ""));
        }
        return srv;
    }

    /**
     * Swagger-parser adds scheme, host and port to server URLs that are just an absolute path.
     * This needs to be stripped.
     *
     * @param openAPI parsed api definition
     * @return api definitino with the rest server info stripped from server URLs
     */
    private OpenAPI fixServerUrl(OpenAPI openAPI) {
        final List<Server> servers = openAPI.getServers();
        List<Server> stripped = servers.stream()
                .map(this::stripRestServer)
                .collect(Collectors.toList());
        openAPI.servers(stripped);
        return openAPI;
    }

    private Sequence renderResult(final SwaggerParseResult result) {
        final OpenAPI openAPI = result.getOpenAPI();

        final MemTreeBuilder builder = new MemTreeBuilder(context);
        builder.startDocument();


        // API info
        builder.startElement(INFO, null);
        // validation errors and warnings
        if (!result.getMessages().isEmpty()) {
            builder.startElement(ERRORS, null);
            result.getMessages().forEach(e -> {
                builder.startElement(ERROR, null);
                builder.characters(e);
                builder.endElement();
            });
            builder.endElement();
        }

        if (openAPI == null) {
            builder.characters("empty");
        } else {
            final Info apiInfo = openAPI.getInfo();
            builder.startElement(TITLE, null);
            builder.characters(apiInfo.getTitle());
            builder.endElement();
            builder.startElement(DESCRIPTION, null);
            builder.characters(apiInfo.getDescription());
            builder.endElement();
            builder.startElement(VERSION, null);
            builder.characters(apiInfo.getVersion());
            builder.endElement();
            builder.startElement(SERVERS, null);
            openAPI.getServers().forEach(srv -> {
                builder.startElement(SERVER, null);
                builder.addAttribute(URL, stripRestServer(srv).getUrl());
                builder.characters(srv.getDescription());
                builder.endElement();
            });
            builder.endElement();

        }
        builder.endElement();
        builder.endDocument();

        return builder.getDocument();
    }

    /**
     * check URI and resolve to REST lookup
     * - if URI is http request leave unchanged
     * - else extract absolute DB path and prepend REST url
     */
    private String normalizeLocation(final String uriStr) throws XPathException {
        try {
            final URI uri = new URI(uriStr);
            final String scheme = uri.getScheme();

            if (uri.isAbsolute() && (scheme.equals("http") || scheme.equals("https"))) {
                return uri.toString();
            }

            // no scheme, figure out collectionPath
            final XmldbURI xUri = XmldbURI.create(uri);
            if (xUri.isCollectionPathAbsolute()) {
                return toRestUrl(xUri.getCollectionPath());
            }

            final AnyURIValue maybeBaseUri = context.getBaseURI();
            if (maybeBaseUri == null || maybeBaseUri.equals(AnyURIValue.EMPTY_URI)) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "$uri is a relative URI but there is no base-URI set");
            }

            final URI baseUri = maybeBaseUri.toURI();
            if (!baseUri.toString().endsWith("/")) {
                final URI resolved = new URI(baseUri.toString() + '/').resolve(uri);
                return toRestUrl(resolved.getPath());
            }
            return toRestUrl(xUri.getCollectionPath());
        } catch (final URISyntaxException e) {
            throw new XPathException(context.getRootExpression(), ErrorCodes.FODC0005, e);
        }
    }

    private String toRestUrl(final String path) {
        return  this.restURL + path;
    }

}
