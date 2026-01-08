package com.lyshra.open.app.integration.contract;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;

public interface ILyshraOpenAppExpression {
    default LyshraOpenAppExpressionType getExpressionType() {
        return LyshraOpenAppExpressionType.SPEL;
    }
    
    String getExpression();
}
