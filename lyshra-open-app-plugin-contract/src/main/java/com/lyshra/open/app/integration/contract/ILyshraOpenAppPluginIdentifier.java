package com.lyshra.open.app.integration.contract;

public interface ILyshraOpenAppPluginIdentifier {
    String getOrganization();
    String getModule();
    String getVersion();
    String toString();
}
