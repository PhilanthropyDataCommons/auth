# A Keycloak Theme for Philanthropy Data Commons authentication

See https://www.keycloak.org/docs/latest/server_development/#deploying-themes

## How to build

Start in the `pdc-keycloak-theme` directory

- `cd pdc-keycloak-theme`

### Build the jar

- `../gradlew jar`

The resulting jar should be in `build/libs`. This theme jar is what should be included in keycloak's `/providers` directory.

If deploying this jar, you should rename it based on the git commit. Example:
- `cp build/libs/pdc-keycloak-theme.jar pdc-keycloak-theme-20240313-05793e4.jar`

The version id here is the UTC date of the commit in YYYMMDD format followed by a hyphen and the first seven digits of the commit SHA1 sum. There may be commits that are on one day in one part of the world but another day in another part of the world, hence the use of the UTC zone of the commit date.

## How to deploy

If there is already a `pdc-keycloak-theme...jar` present in the Keycloak `providers` directory, move this one out when deploying a new one. The goal is to have exactly one version of this jar present on the classpath.

Example commands that copied a new jar, moved the old jar, and chowned the new:

- `sudo cp pdc-keycloak-theme-20240313-05793e4.jar /opt/keycloak/keycloak-23.0.5/providers/`
- `sudo mv /opt/keycloak/keycloak-23.0.5/providers/pdc-keycloak-theme-20230407-6b4b74d.jar /opt/keycloak/`
- `sudo chown keycloak:keycloak /opt/keycloak/keycloak-23.0.5/providers/pdc-keycloak-theme*.jar`

Rebuild Keycloak's configuration. Example:

- `sudo -u keycloak /bin/bash`
- `cd /opt/keycloak/keycloak-23.0.5`
- `bin/kc.sh build`
- `exit`
- `sudo systemctl restart keycloak`

To verify that everything is OK, look at the logs. Example:

- `sudo journalctl --since '2024-03-13 00:00:00' | grep -C 5 -i keycloak`

### Deployment tip regarding Keycloak authentication workflow interface

If you are changing authentication workflows, start in your development environment or the test environment to safely figure them out. When making the same change in production, avoid trying to copy and paste text from the test environment UI pages to the production environment UI pages because the workflow can be altered with drag-and-drop.

## Source Sans Pro font asset handling

Font assets, including font-specific CSS, are added to the jar during the build via the `unpackSourceSansPro` task on which the `jar` task depends. There is no need to explicitly run this task but if you change the `unpackSourceSansPro` task code you may need to `../gradlew clean jar` to get up-to-date results.

## License

Apache License 2.0, see the LICENSE file.

The license choice is based on the license of the expected code to be copied and modified from the original work by the keycloak team at https://github.com/keycloak/keycloak. This module is expected to start with keycloak theme code and re-use it, so it is a combined work. See [the keycloak theme creation documentation](https://www.keycloak.org/docs/latest/server_development/index.html#creating-a-custom-html-template). To respect the original authors' choice of a free software license and avoid license confusion, we keep the original license. One might point out that a sibling extension uses a license that differs from keycloak. A difference in the `twilio-keycloak-provider` case is it does not appear to have copied code from keycloak, rather it implements a keycloak API. If no copy pasta from the from the original keycloak repository is needed, we could switch to a strong copyleft license, e.g. AGPL.
