package com.lyshra.open.app.core.engine.expression.evaluators.graalvm.functions;

import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.IGraalvmFunction;
import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.InvalidGraalVmFunctionInputException;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class SetContextVariableFunction implements IGraalvmFunction {

    @Override
    public String getScriptMemberName() {
        return "setLyshraOpenAppContextVariable";
    }

    @Override
    public List<String> getSampleUsage() {
        return List.of(
                "setLyshraOpenAppContextVariable('variableName', 1)",
                "setLyshraOpenAppContextVariable('variableName', true)",
                "setLyshraOpenAppContextVariable('variableName', 'abcd')",
                "setLyshraOpenAppContextVariable('variableName', {'foo': 'bar'})",
                "setLyshraOpenAppContextVariable('variableName', [1,2,3])"
        );
    }

    @Override
    public void validate(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) throws InvalidGraalVmFunctionInputException {
        if (arguments.size() != 2) {
            throw new InvalidGraalVmFunctionInputException("Function requires 2 arguments", this, arguments);
        }
        Object o = arguments.get(0);
        if (!(o instanceof String)) {
            throw new InvalidGraalVmFunctionInputException("First argument must be a String", this, arguments);
        }
    }

    @Override
    public Object execute(List<Object> arguments, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        String variableName = (String) arguments.get(0);
        Object obj = arguments.get(1);
        context.getVariables().put(variableName, obj);
        return arguments;
    }

}
