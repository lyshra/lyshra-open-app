package com.lyshra.open.app.integration;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;

public interface ILyshraOpenAppPluginProvider {
    ILyshraOpenAppPlugin create(ILyshraOpenAppPluginFacade lyshraOpenAppFacade);
}
