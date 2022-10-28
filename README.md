# Keycloak-extensions
PDC related extensions that were made for the keycloak auth service

## Keycloak installation
For installation and basic settings of keycloak, follow this document 
https://docs.google.com/document/d/1aawlITZu_6xAcVetyqvE7NrETay7eAOhVXwfnJcwC7A/edit?usp=sharing

## Compiling the custom provider through maven

```mvn -f api-key-module package```

This will create a target directory inside api-key-module directory
Goto api-key-module -> target -> deploy.
Copy the jar file.
Paste it into the providers directory of keycloak.
Restart your keycloak server.