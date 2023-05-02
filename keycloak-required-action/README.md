# Custom Keycloak Required Action

This required action requires the user to update a mobile phone number, if not already set. The [twilio-keycloak-provider](https://github.com/PhilanthropyDataCommons/auth/tree/main/twilio-keycloak-provider) depends on this.

Forked from [dasniko/keycloak-extensions-demo@bd7948](https://github.com/dasniko/keycloak-extensions-demo/tree/bd79483548b0eebb935505b917c8dcf265160d4b/requiredaction).

## How to build

Start in the `keycloak-required-action` directory

- `cd keycloak-required-action`

Build a jar using gradle

- `../gradlew jar`

The resulting jar should be in `build/libs`. This jar is what should be included in Keycloak's `/providers` directory.

## How to include and use in Keycloak

Copy the jar to Keycloak's `/providers` directory.

See the remaining instructions in [twilio-keycloak-provider](https://github.com/PhilanthropyDataCommons/auth/tree/main/twilio-keycloak-provider#how-to-use-the-software-in-keycloak).

## License

Apache Software License 2.0 (see LICENSE file).

The license choice is based on the license of the original work by Niko Köbler at https://github.com/dasniko/keycloak-extensions-demo. This module started with that code and re-uses it, so it is a combined work. To respect the original authors' choice of a free software license, avoid license confusion, and allow improvements in this repository to be used upstream, we keep the original license.
