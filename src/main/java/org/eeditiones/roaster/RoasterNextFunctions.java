package org.eeditiones.roaster;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.oas.models.info.Info;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;

import static org.exist.xquery.FunctionDSL.*;
import static org.eeditiones.roaster.RoasterNextModule.functionSignature;

/**
 * Roaster Next functions
 */
public class RoasterNextFunctions extends BasicFunction {
    final private static QName errors;
    final private static QName error;
    final private static QName info;
    final private static QName title;
    final private static QName version;
    final private static QName description;
    final private static QName servers;
    final private static QName server;
    final private static QName url;

    static {
        try {
            errors = new QName("errors");
            error = new QName("error");
            info = new QName("info");
            version = new QName("version");
            description = new QName("description");
            title = new QName("title");
            servers = new QName("servers");
            server = new QName("server");
            url = new QName("url");
        } catch (QName.IllegalQNameException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String PARSE_FUNCTION_NAME = "parse";
    static final FunctionSignature PARSE_FUNCTION = functionSignature(
            PARSE_FUNCTION_NAME,
            "Parse OpenAPI specification",
            returnsOptMany(Type.ITEM),
            param("file", Type.STRING, "A YAML or JSON file to parse")
    );

    private static final String LOAD_FUNCTION_NAME = "load";
    static final FunctionSignature LOAD_FUNCTION = functionSignature(
            LOAD_FUNCTION_NAME,
            "Load OpenAPI specification and parse it",
            returnsOptMany(Type.ITEM),
            param("url", Type.STRING, "A URL pointing to a YAML or JSON file to parse")
    );

    private static final String FLATTEN_FUNCTION_NAME = "flatten";
    static final FunctionSignature FLATTEN_FUNCTION = functionSignature(
            FLATTEN_FUNCTION_NAME,
            "Flatten OpenAPI specification",
            returns(Type.STRING),
            param("content", Type.STRING, "A YAML or JSON file to flatten")
    );

    private static final String RESOLVE_FUNCTION_NAME = "resolve";
    static final FunctionSignature RESOLVE_FUNCTION = functionSignature(
            RESOLVE_FUNCTION_NAME,
            "Resolve components in OpenAPI specification",
            returns(Type.STRING),
            param("content", Type.STRING, "A YAML or JSON file to resolve")
    );

    public RoasterNextFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        switch (getName().getLocalPart()) {
            case PARSE_FUNCTION_NAME:
                return parse((StringValue) args[0].itemAt(0));

            case LOAD_FUNCTION_NAME:
                return load((StringValue) args[0].itemAt(0));

            case FLATTEN_FUNCTION_NAME:
                return flatten((StringValue) args[0].itemAt(0));

            case RESOLVE_FUNCTION_NAME:
                return resolve((StringValue) args[0].itemAt(0));

            default:
                throw new XPathException(this, "No function: " + getName() + "#" + getSignature().getArgumentCount());
        }
    }

    /**
     * Creates an XML document like <valid>name</valid>.
     *
     * @param contents to parse
     *
     * @return Sequence of validation errors or API info
     */
    private Sequence flatten(final StringValue contents) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setFlatten(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(contents.toString(), null, parseOptions);
        OpenAPI openAPI = result.getOpenAPI();
        return new StringValue(Json.pretty(openAPI));
    }

    /**
     * Creates an XML document like <valid>name</valid>.
     *
     * @param contents to parse
     *
     * @return Sequence of validation errors or API info
     */
    private Sequence resolve(final StringValue contents) {
        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
//        parseOptions.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(contents.toString(), null, parseOptions);
        OpenAPI openAPI = result.getOpenAPI();
        return new StringValue(Json.pretty(openAPI));
    }

    /**
     * Creates an XML document like <valid>name</valid>.
     *
     * @param contents to parse
     *
     * @return Sequence of validation errors or API info
     */
    private Sequence parse(final StringValue contents) {
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(contents.toString(), null, null);
        return renderResult(result);
    }

    /**
     * Creates an XML document like <valid>name</valid>.
     *
     * @param url location of the spec to parse
     *
     * @return Sequence of validation errors or API info
     */
    private Sequence load(final StringValue url) {
        final SwaggerParseResult result = new OpenAPIV3Parser().readLocation(url.toString(), null, null);
        return renderResult(result);
    }

    private Sequence renderResult(final SwaggerParseResult result) {
        // the parsed POJO
        final OpenAPI openAPI = result.getOpenAPI();

        final MemTreeBuilder builder = new MemTreeBuilder(context);
        builder.startDocument();


        // API info
        builder.startElement(info, null);
        // validation errors and warnings
        if (!result.getMessages().isEmpty()) {
            builder.startElement(errors, null);
            result.getMessages().forEach(e -> {
                builder.startElement(error, null);
                builder.characters(e.toString());
                builder.endElement();
            });
            builder.endElement();
        }

        if (openAPI == null) {
            builder.characters("empty");
        } else {
            final Info apiInfo = openAPI.getInfo();
            builder.startElement(title, null);
            builder.characters(apiInfo.getTitle());
            builder.endElement();
            builder.startElement(description, null);
            builder.characters(apiInfo.getDescription());
            builder.endElement();
            builder.startElement(version, null);
            builder.characters(apiInfo.getVersion());
            builder.endElement();
            builder.startElement(servers, null);
            openAPI.getServers().forEach(srv -> {
                builder.startElement(server, null);
                builder.addAttribute(url, srv.getUrl());
                builder.characters(srv.getDescription());
                builder.endElement();
            });
            builder.endElement();

        }
        builder.endElement();
        builder.endDocument();

        return builder.getDocument();
    }
}
