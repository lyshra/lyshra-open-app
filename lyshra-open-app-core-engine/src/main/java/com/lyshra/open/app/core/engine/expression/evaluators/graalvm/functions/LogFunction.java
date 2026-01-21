package com.lyshra.open.app.core.engine.expression.evaluators.graalvm.functions;

import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.IGraalvmFunction;
import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.InvalidGraalVmFunctionInputException;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class LogFunction implements IGraalvmFunction {

    @Override
    public String getScriptMemberName() {
        return "jlog";
    }

    @Override
    public List<String> getSampleUsage() {
        return List.of(
                "jlog('Hello World', 1234, true, [], {})"
        );
    }

    @Override
    public void validate(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) throws InvalidGraalVmFunctionInputException {
        if (arguments.isEmpty()) {
            throw new InvalidGraalVmFunctionInputException("Function requires at least one argument", this, arguments);
        }
    }

    @Override
    public Object execute(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        log.info("Arguments : {}", arguments);
        return arguments;
    }

}
