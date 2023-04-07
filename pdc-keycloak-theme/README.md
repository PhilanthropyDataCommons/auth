# A Keycloak Theme for Philanthropy Data Commons authentication

See https://www.keycloak.org/docs/latest/server_development/#deploying-themes

## How to build

Start in the `pdc-keycloak-theme` directory

- `cd pdc-keycloak-theme`

### Build the jar

- `../gradlew jar`

The resulting jar should be in `build/libs`. This theme jar is what should be included in keycloak's `/providers` directory.

## Source Sans Pro font asset handling

Font assets, including font-specific CSS, are added to the jar during the build via the `unpackSourceSansPro` task on which the `jar` task depends. There is no need to explicitly run this task but if you change the `unpackSourceSansPro` task code you may need to `../gradlew clean jar` to get up-to-date results.

## License

Apache License 2.0, see the LICENSE file.

The license choice is based on the license of the expected code to be copied and modified from the original work by the keycloak team at https://github.com/keycloak/keycloak. This module is expected to start with keycloak theme code and re-use it, so it is a combined work. See [the keycloak theme creation documentation](https://www.keycloak.org/docs/latest/server_development/index.html#creating-a-custom-html-template). To respect the original authors' choice of a free software license and avoid license confusion, we keep the original license. One might point out that a sibling extension uses a license that differs from keycloak. A difference in the `twilio-keycloak-provider` case is it does not appear to have copied code from keycloak, rather it implements a keycloak API. If no copy pasta from the from the original keycloak repository is needed, we could switch to a strong copyleft license, e.g. AGPL.
