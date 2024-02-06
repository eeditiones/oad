package org.eeditiones.oad;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * A wrapper around swagger-parser to work with OpenAPI definitions.
 */
public class OadModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "//eeditiones.org/ns/oad";
    public static final String PREFIX = "oad";
    public static final String RELEASED_IN_VERSION = "eXist-6.2.0";

    // register the functions of the module
    public static final FunctionDef[] functions = functionDefs(
        functionDefs(OadFunctions.class,
                OadFunctions.VALIDATE_FUNCTION,
                OadFunctions.REPORT_FUNCTION,
                OadFunctions.FLATTEN_FUNCTION,
                OadFunctions.RESOLVE_FUNCTION,
                OadFunctions.CONVERT_FUNCTION
        )
    );

    public OadModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "Open API Specification Parser";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    static FunctionSignature functionSignature(final String name, final String description,
            final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI), description, returnType, paramTypes);
    }

    static class OadErrorCode extends ErrorCodes.ErrorCode {
        private OadErrorCode(final String code, final String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }
    }

    static OadErrorCode NO_API = new OadErrorCode("OAD0001", "API definition appears to be empty");
}
