package com.lyshra.open.app.core.engine.expression.evaluators.graalvm;

import com.lyshra.open.app.core.engine.expression.evaluators.AbstractExpressionEvaluator;
import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.functions.GraalVmFunctionRegistry;
import com.lyshra.open.app.core.exception.codes.LyshraOpenAppInternalErrorCodes;
import com.lyshra.open.app.core.util.CommonUtil;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

@Slf4j
public class GraalVmJsEvaluator extends AbstractExpressionEvaluator implements ILyshraOpenAppExpressionEvaluator {

    public static final String JAVASCRIPT_LANGUAGE_ID = "js";

    @Override
    public LyshraOpenAppExpressionType getEvaluatorType() {
        return LyshraOpenAppExpressionType.GRAAALVM_JS;
    }

    @Override
    public Object evaluate(
            ILyshraOpenAppExpression expression,
            ILyshraOpenAppContext context,
            ILyshraOpenAppPluginFacade facade
    ) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Context ctx = createJsContext(outputStream)) {
            registerCustomFunctions(ctx, context, facade);

            Value bindings = ctx.getBindings(JAVASCRIPT_LANGUAGE_ID);

            // Pass objects directly (NO JSON)
            bindings.putMember("$data", context.getData());
            bindings.putMember("$variables", context.getVariables());

            // Evaluate expression once
            ctx.eval(JAVASCRIPT_LANGUAGE_ID, expression.getExpression());
            Value result = ctx.getBindings(JAVASCRIPT_LANGUAGE_ID).getMember("result");

            String jsLogs = outputStream.toString();
            if (CommonUtil.isNotBlank(jsLogs)) {
                log.info("=============== JavaScript Logs Start: ===============\n{}\n=============== JavaScript Logs End: ===============", jsLogs);
            }

            // Materialize INSIDE context
            return GraalVmValueParser.parseResult(result, facade);
        } finally {
            CommonUtil.close(outputStream);
        }
    }

    private static Context createJsContext(OutputStream outputStream) {
        return Context
                .newBuilder(JAVASCRIPT_LANGUAGE_ID)
                .out(outputStream)
                .err(outputStream)
                .allowAllAccess(false)
                .allowHostAccess(HostAccess
                        .newBuilder(HostAccess.NONE)
                        .allowMapAccess(true)
                        .allowListAccess(true)
                        .allowArrayAccess(true)
                        .allowIterableAccess(true)
                        .allowIteratorAccess(true)
                        .build())
                .build();
    }

    private static void registerCustomFunctions(Context ctx, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        Value jsBinding = ctx.getBindings(JAVASCRIPT_LANGUAGE_ID);
        GraalVmFunctionRegistry graalVmFunctionRegistry = GraalVmFunctionRegistry.getInstance();
        Map<String, IGraalvmFunction> allFunctions = graalVmFunctionRegistry.getAllFunctions();

        allFunctions.forEach((name, fn) -> jsBinding.putMember(name, (ProxyExecutable) args -> {
            List<Object> argsList = fn.parseInput(args, facade);
            try {
                fn.validate(argsList, context, facade);
            } catch (InvalidGraalVmFunctionInputException e) {
                throw new LyshraOpenAppProcessorRuntimeException(
                        LyshraOpenAppInternalErrorCodes.EXPRESSION_MEMBER_FUNCTION_INPUT_INVALID,
                        e
                );
            }
            return fn.execute(argsList, context, facade);
        }));
    }

}
