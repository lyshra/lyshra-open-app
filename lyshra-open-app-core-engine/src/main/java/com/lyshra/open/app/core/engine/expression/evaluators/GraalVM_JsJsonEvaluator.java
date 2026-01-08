package com.lyshra.open.app.core.engine.expression.evaluators;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GraalVM_JsJsonEvaluator extends AbstractExpressionEvaluator implements ILyshraOpenAppExpressionEvaluator {

    @Override
    public LyshraOpenAppExpressionType getEvaluatorType() {
        return LyshraOpenAppExpressionType.GRAAALVM_JS;
    }

    @Override
    public Object evaluate(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        Context ctx = Context
                .newBuilder("js")
                .allowAllAccess(true)
                .build();

        registerCustomFunctions(ctx);

        ctx.getBindings("js").putMember("$contextJsonStr", "json");
        ctx.eval("js", "const $context = JSON.parse($contextJsonStr);");
        Value result = ctx.eval("js", expression.getExpression());
        return result.as(Object.class);
    }

    private static void registerCustomFunctions(Context ctx) {
        // register nowMinus10Min() -> returns ISO_LOCAL_DATE_TIME string
        ctx.getBindings("js").putMember("nowMinus10Min", (ProxyExecutable) args -> {
            LocalDateTime dt = LocalDateTime.now().minusMinutes(10);
            return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        });

        // register nowMinusXMin(x)
        ctx.getBindings("js").putMember("nowMinusXMin", (ProxyExecutable) args -> {
            int x = 0;
            if (args != null && args.length > 0) {
                Value v = args[0];
                if (v.isNumber()) x = v.asInt();
                else if (v.isString()) {
                    try { x = Integer.parseInt(v.asString()); } catch (NumberFormatException ignored) { x = 0; }
                }
            }
            LocalDateTime dt = LocalDateTime.now().minusMinutes(x);
            return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        });
    }
}
