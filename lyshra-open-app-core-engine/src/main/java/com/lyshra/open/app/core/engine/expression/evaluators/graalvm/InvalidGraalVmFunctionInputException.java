package com.lyshra.open.app.core.engine.expression.evaluators.graalvm;

import java.util.List;

public class InvalidGraalVmFunctionInputException extends Exception {

    public InvalidGraalVmFunctionInputException(String message, IGraalvmFunction function, List<Object> arguments) {
        super(
                String.format(
                        "Error Message: [%s], Invalid GraalVM function input for function %s. Arguments : %s, Sample usage : %s",
                        message,
                        function.getScriptMemberName(),
                        arguments,
                        function.getSampleUsage()
                )
        );
    }

}
