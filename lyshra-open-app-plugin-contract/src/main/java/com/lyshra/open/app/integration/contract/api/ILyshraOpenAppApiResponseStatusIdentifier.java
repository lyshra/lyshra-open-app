package com.lyshra.open.app.integration.contract.api;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;

public interface ILyshraOpenAppApiResponseStatusIdentifier {
    ILyshraOpenAppExpression getSuccess();
    ILyshraOpenAppExpression getFailure();
    ILyshraOpenAppExpression getRetry();
}
