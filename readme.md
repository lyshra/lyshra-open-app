# LyshraOpenApp

Lyshra Open App is an application that allows millions of applications to register themselves with just a json configuration and their full backend application is ready without any code. The intention is "Backend as config". Each application can be configured using just configuration where in they can integrate with multiple integrations to achieve their functionalities like, their own workflows, datasource's (mysql, mongodb, etc) and their own db objects with queries.

Lyshra OpenApp is basically an workflow engine (like n8n) + db objects (like IBM openpages). A plugin can be developed by implementing the integration interface to provide support for any database (relational, non-relational, etc) and any authentication method (email password, username password, otp based, oauth2, saml, kerberos, etc). Once the integration plugin is developed, it can be registered with Lyshra OpenApp Core Engine and then any backend application configuration can use that integration to achieve their functionalities.

The plugins must keep in mind, that they should be context-heavy and should not use spring components, and should be just raw java. Every plugin lets say for mongodb integration, should implement the required interfaces and developed in such a way that when millions of applications register themselves with Lyshra OpenApp, they can use that integration to achieve their functionalities.

The plugins should also ensure that the connection details are completely independent and mutually exclusive for each backend application configuration. For example, if a mongodb plugin is developed, so a single lyshra open app instance can support millions of backend applications using mongodb, so mongodb plugin should ensure that the connection details are completely independent and mutually exclusive for each backend application configuration.

Each configuration may have its own datasource (via integration) which will be totally independent and mutually exclusive with other configurations' data sources. Since one instance of the Lyshra OpenApp can support millions of backend applications (highly vertically scaled), it handles all data sources (via integration) intelligently and ensures they don't collide with each other.

