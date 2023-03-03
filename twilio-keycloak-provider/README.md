# Twilio Keycloak SMS Authentication Provider

A keycloak Authentication SPI implementation providing SMS verification.

Based on https://github.com/dasniko/keycloak-2fa-sms-authenticator at 4205a6c.

## How to build and run tests

Start in the `twilio-keycloak-provider` directory

- `cd twilio-keycloak-provider`

### Build with dependencies included in a fat jar (recommended)

- `../gradlew shadowJar`

The resulting jar should be in `build/libs`. This (fat) jar is what should be included in keycloak's `/providers` directory. See below for details and how to verify that it is OK to include.

### Build plain jar without dependencies included (not recommended for deployment but may be useful for debugging)

- `../gradlew jar`

The resulting jar should be in `build/libs`. This jar could be included in keycloak's `/providers` directory but also requires twilio and its dependencies to be included on the keycloak classpath by other means. It is assumed in this document that you will use the fat jar.

### Run tests

- `../gradlew test`

## How to include the software in keycloak

Before including the fat jar in keycloak, verify that the shadow/relocation process results are as expected. There should only be software under one package (directory): `org.philanthropydatacommons`.

In other words, verify exactly three directories in the root of the jar:
1. `META-INF`
2. `org`
3. `theme-resources`

Furthermore, verify:
1. inside the `org` directory, there is exactly one directory, `philanthropydatacommons`, and
2. inside that `philanthropydatacommons` directory, there are exactly two directories, `auth` and `shadow`.

If there are more or fewer directories than expected above, this means any of the following problems (or more) occurred:
* Twilio's transitive dependencies changed (e.g. a newer version of twilio's jar had different dependencies), and/or
* the shadow plugin changed behavior (e.g. a newer version of the shadow plugin differs), and/or
* new code was included under an unexpected package/directory structure.

To see a change in dependencies, use gradle to list dependencies, e.g. `../gradlew dependencies` or `../gradlew dependencyInsight --dependency problemDepName`. Use `git` to go back and find what version of the software worked OK and did not violate the above rules. Change the `shadowJar` task in the build script (`build.gradle.kts`) accordingly, with a view for what jars are (or are not) present in keycloak's classpath.

To see what jars are in the keycloak distribution, within a shell on the keycloak machine or container:
`find /path/to/keycloak/lib/lib/main /path/to/keycloak/providers -name "*.jar"`

Example command inside a bitnami keycloak container:
`find /opt/bitnami/keycloak/lib/lib/main /opt/bitnami/keycloak/providers -name "*.jar"`

If all appears to be OK, copy the fat jar to keycloak's `/providers` directory or make it visible there by some other means (e.g. docker volume mount).

## How to use the software in keycloak

In the keycloak administration interface for a realm:

1. Create a copy of the browser flow,
2. add the `SMS Authentication` step to the newly created flow,
3. enable this new flow as the browser flow (effectively disabling the old browser flow),
4. configure properties of the new flow (add a name), and
5. enable dasniko's "required action" in the realm (requires a jar of [dasniko's requiredaction](https://github.com/dasniko/keycloak-extensions-demo/tree/main/requiredaction) at or near commit 0ae273c in `/providers` as well).

Most details can be found at [dasniko's blog post](https://www.n-k.de/2020/12/keycloak-2fa-sms-authentication.html).

## How to configure Twilio

Use the following environment variables to configure Twilio:

- `TWILIO_PHONE_NUMBER`: the "from" phone number set up in Twilio.
- `TWILIO_ACCOUNT_SID`: the SID or username for Twilio API access.
- `TWILIO_AUTH_TOKEN`: the token or secret for Twilio API access.


## License

Expat (also called MIT) license, see LICENSE file.

The license choice is based on the license of the original work by Niko Köbler at https://github.com/dasniko/keycloak-2fa-sms-authenticator. This module started with that code and re-uses it, so it is a combined work. To respect the original authors' choice of a free software license, avoid license confusion, and allow improvements in this repository to be used upstream, we keep the original license.
