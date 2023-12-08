package org.eeditiones.roaster;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * A very simple example XQuery Library Module implemented
 * in Java.
 */
public class RoasterNextModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "//eeditiones.org/ns/roaster/next";
    public static final String PREFIX = "roaster";
    public static final String RELEASED_IN_VERSION = "eXist-6.2.0";

    // register the functions of the module
    public static final FunctionDef[] functions = functionDefs(
        functionDefs(RoasterNextFunctions.class,
                RoasterNextFunctions.LOAD_FUNCTION,
                RoasterNextFunctions.PARSE_FUNCTION,
                RoasterNextFunctions.FLATTEN_FUNCTION,
                RoasterNextFunctions.RESOLVE_FUNCTION
        )
    );

    public RoasterNextModule(final Map<String, List<? extends Object>> parameters) {
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
        return "Roaster Next";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    static FunctionSignature functionSignature(final String name, final String description,
            final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description,
            final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI), description, returnType, variableParamTypes);
    }

    static class RoasterNextModuleErrorCode extends ErrorCodes.ErrorCode {
        private RoasterNextModuleErrorCode(final String code, final String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }
    }
}
