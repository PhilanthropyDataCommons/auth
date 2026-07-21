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

## Maintenance notice theme (pdc-keycloak-theme-notice)

This jar also ships a second, thin login theme called `pdc-keycloak-theme-notice`
that extends `pdc-keycloak-theme` and adds a maintenance/upgrade notice banner to
the bottom of every login page. It uses the Custom Footer hook introduced in
Keycloak 26.0.0, so it does not override `template.ftl` and is safe across
Keycloak upgrades.

### How to show the notice

1. Build and deploy this jar (see above) so both themes are on the classpath.
2. In the Admin Console, go to **Realm Settings → Themes → Login Theme** and
   select **`pdc-keycloak-theme-notice`**, then **Save**.

The notice appears immediately — no restart is needed to switch themes.

### How to hide the notice

Switch the Login Theme back to **`pdc-keycloak-theme`** and **Save**. No restart
and no file changes are required.

### How to change the notice text (no file edits, no restart)

The notice text is the message key `maintenanceNotice` (with a default in
`theme/pdc-keycloak-theme-notice/login/messages/messages_en.properties`). The
default is intentionally **generic and placeholder-free** ("Scheduled maintenance
may briefly interrupt access to this service...") so that if the notice theme
is enabled before the text is customized, users see a sensible message rather
than something like "YYYY-MM-DD between HH:MM and HH:MM".

To set the actual maintenance window at runtime:

1. Go to **Realm Settings → Localization**.
2. Enable internationalization if it isn't already (you can keep `en` as the
   only supported locale — users won't see anything different).
3. Open the **Realm Overrides** tab.
4. Add/edit the key `maintenanceNotice` for locale `en` and set your text.
5. Save.

Realm Overrides are stored in the database and take effect immediately. The
value may contain a safe subset of HTML (passed through Keycloak's `kcSanitize`):
`<strong>`, `<a href>`, `<br>`, `<p>`, `<em>`, `<b>`, `<ul>/<li>`, etc.

Example override (replace the placeholders with your actual window):

```
<strong>Upgrade notice:</strong> Keycloak will be unavailable on
<strong>YYYY-MM-DD</strong> from <strong>HH:MM</strong> to <strong>HH:MM</strong> UTC.
See <a href="https://status.example.com/">status.example.com</a> for details.
```

## License

Apache License 2.0, see the LICENSE file.

The license choice is based on the license of the expected code to be copied and modified from the original work by the keycloak team at https://github.com/keycloak/keycloak. This module is expected to start with keycloak theme code and re-use it, so it is a combined work. See [the keycloak theme creation documentation](https://www.keycloak.org/docs/latest/server_development/index.html#creating-a-custom-html-template). To respect the original authors' choice of a free software license and avoid license confusion, we keep the original license. One might point out that a sibling extension uses a license that differs from keycloak. A difference in the `twilio-keycloak-provider` case is it does not appear to have copied code from keycloak, rather it implements a keycloak API. If no copy pasta from the from the original keycloak repository is needed, we could switch to a strong copyleft license, e.g. AGPL.
