# Twilio Keycloak SMS Authentication Provider

A keycloak Authentication SPI implementation providing SMS verification.

Based on https://github.com/dasniko/keycloak-2fa-sms-authenticator at 4205a6c.

## How to build and run tests

Start in the `twilio-keycloak-provider` directory

- `cd twilio-keycloak-provider`

### Build the jar

- `../gradlew jar`

The resulting jar should be in `build/libs`. This jar is what should be included in keycloak's `/providers` directory.

### Run tests

- `../gradlew test`

## How to include the software in keycloak

The jar bundles no dependencies: the code it needs from org.keycloak is already on keycloak's classpath, and it calls the Twilio REST API over HTTPS using keycloak's own HTTP client. Copy the jar to keycloak's `/providers` directory or make it visible there by some other means (e.g. docker volume mount).

## Security

The SMS call rides the keycloak server's shared HTTP client, so its TLS trust management must stay enabled: do not set `disable-trust-manager` in the keycloak configuration, or the Twilio credentials could be exposed to a man-in-the-middle attacker.

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
- `TWILIO_ACCOUNT_SID`: the Twilio Account SID, used in the API URL path and as the Basic auth username.
- `TWILIO_AUTH_TOKEN`: the Twilio Auth Token, used as the Basic auth password.


## License

Expat (also called MIT) license, see LICENSE file.

The license choice is based on the license of the original work by Niko Köbler at https://github.com/dasniko/keycloak-2fa-sms-authenticator. This module started with that code and re-uses it, so it is a combined work. To respect the original authors' choice of a free software license, avoid license confusion, and allow improvements in this repository to be used upstream, we keep the original license.
