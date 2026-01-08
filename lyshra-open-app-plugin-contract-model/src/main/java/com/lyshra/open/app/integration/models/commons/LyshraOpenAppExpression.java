package com.lyshra.open.app.integration.models.commons;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import lombok.Data;

import java.io.Serializable;

@Data
public class LyshraOpenAppExpression implements ILyshraOpenAppExpression, Serializable {
    private final LyshraOpenAppExpressionType expressionType; // e.g., spEL
    private final String expression;
}
