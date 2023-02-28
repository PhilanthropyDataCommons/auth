# A Keycloak Theme for Philanthropy Data Commons authentication

See https://www.keycloak.org/docs/latest/server_development/#deploying-themes

## How to build

Start in the `pdc-keycloak-theme` directory

- `cd pdc-keycloak-theme`

### Build the jar

- `../gradlew jar`

The resulting jar should be in `build/libs`. This theme jar is what should be included in keycloak's `/providers` directory.

### Did the gradle command fail due to a missing OpenJDK?

A good one is available at https://www.azul.com/downloads/?version=java-17-lts&package=jdk

1. Unzip the JDK
2. `export JAVA_HOME=/path/to/jdk`

## License

Apache License 2.0, see LICENSE file.
