package com.lyshra.open.app.core.engine.expression.evaluators.spel;

import com.lyshra.open.app.core.engine.expression.evaluators.AbstractExpressionEvaluator;
import com.lyshra.open.app.core.exception.LyshraOpenAppRuntimeException;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
public class SpElJsonEvaluator extends AbstractExpressionEvaluator implements ILyshraOpenAppExpressionEvaluator {

    private final ExpressionParser parser;
    private final Map<String, Method> customFunctions;

    public SpElJsonEvaluator() {
        try {
            parser = new SpelExpressionParser();
            customFunctions = Map.of(
                    "nowMinus10Min", SpElJsonEvaluator.class.getMethod("nowMinus10Min"),
                    "nowMinusXMin", SpElJsonEvaluator.class.getMethod("nowMinusXMin", int.class)
            );
        } catch (NoSuchMethodException e) {
            throw new LyshraOpenAppRuntimeException(e);
        }
    }

    @Override
    public LyshraOpenAppExpressionType getEvaluatorType() {
        return LyshraOpenAppExpressionType.SPEL;
    }

    @Override
    public Object evaluate(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        try {
            StandardEvaluationContext evaluationContext = createEvaluationContext(context);
            customFunctions.forEach(evaluationContext::registerFunction);
            Expression expr = parser.parseExpression(expression.getExpression());
            return expr.getValue(evaluationContext);
        } catch (Exception e) {
            throw new LyshraOpenAppRuntimeException("Expression evaluation failed: " + expression.getExpression(), e);
        }
    }

    private static StandardEvaluationContext createEvaluationContext(ILyshraOpenAppContext data) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setRootObject(data);
        attachPropertyAccessors(context);
        return context;
    }

    private static void attachPropertyAccessors(StandardEvaluationContext context) {
        context.addPropertyAccessor(new MapAccessor());
        context.addPropertyAccessor(new ReflectivePropertyAccessor());
        context.addPropertyAccessor(DataBindingPropertyAccessor.forReadOnlyAccess());
    }

    // public static helper function that will be callable from SpEL as nowMinus10Min()
    public static LocalDateTime nowMinus10Min() {
        return LocalDateTime.now().minusMinutes(10);
    }

    // public static helper function that will be callable from SpEL as nowMinus10Min()
    public static LocalDateTime nowMinusXMin(int x) {
        return LocalDateTime.now().minusMinutes(x);
    }

}
