package com.lyshra.open.app.core.engine.expression.evaluators.graalvm.functions;

import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.IGraalvmFunction;
import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.InvalidGraalVmFunctionInputException;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
public class NowMinusXMinFunction implements IGraalvmFunction {

    @Override
    public String getScriptMemberName() {
        return "nowMinusXMin";
    }

    @Override
    public List<String> getSampleUsage() {
        return List.of(
                "nowMinusXMin(10)"
        );
    }

    @Override
    public void validate(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) throws InvalidGraalVmFunctionInputException {
        if (arguments.size() != 1) {
            throw new InvalidGraalVmFunctionInputException("Function requires single Integer parameter", this, arguments);
        }
    }

    @Override
    public Object execute(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        int minutes = (int) arguments.get(0);
        return Instant.now().minus(Duration.ofMinutes(minutes));
    }

}
