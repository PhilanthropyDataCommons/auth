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

This "exactly three / exactly two" invariant is checked automatically by the `checkKeycloakShading` CI task described below, but **only for shipped `.class` entries**: the task builds the fat jar and FAILs on any `.class` that is not the provider's own package or a relocated entry, so it catches the common case of stray class directories at the jar root. It does not inspect non-class resources, so the manual directory check below remains the authoritative way to catch a resource-only directory (for example `com/twilio/` resources) left at the jar root.

If there are more or fewer directories than expected above, this means any of the following problems (or more) occurred:
* Twilio's transitive dependencies changed (e.g. a newer version of twilio's jar had different dependencies), and/or
* the shadow plugin changed behavior (e.g. a newer version of the shadow plugin differs), and/or
* new code was included under an unexpected package/directory structure.

To see a change in dependencies, use gradle to list dependencies, e.g. `../gradlew dependencies` or `../gradlew dependencyInsight --dependency problemDepName`. Use `git` to go back and find what version of the software worked OK and did not violate the above rules. Change the `shadowJar` task in the build script (`build.gradle.kts`) accordingly, with a view for what jars are (or are not) present in keycloak's classpath.

To see what jars are in the keycloak distribution, within a shell on the keycloak machine or container:
`find /path/to/keycloak/lib/lib/main /path/to/keycloak/providers -name "*.jar"`

Example command inside a bitnami keycloak container:
`find /opt/bitnami/keycloak/lib/lib/main /opt/bitnami/keycloak/providers -name "*.jar"`

There are two automated checks for class-overlap and shading, both registered inline in `twilio-keycloak-provider/build.gradle.kts` and run by the `keycloak-classpath-overlap` CI workflow (`.github/workflows/keycloak-classpath-overlap.yml`).

The first, **`checkKeycloakClasspathOverlap`** (PRE-shade), resolves the twilio runtime classpath (twilio + transitive deps, before the shadow plugin strips anything), downloads the targeted Keycloak distribution (version derived from the resolved `keycloak-core` compileOnly dep, so no version drift and no regex over `build.gradle.kts`), and reports any `.class` in a twilio-side jar also present in a Keycloak runtime jar. It reads the `exclude(dependency(...))` directives live from the `shadowJar` task's `dependencyFilter`, so a commented-out or misplaced exclude is not mistaken for active. Relocation does not make an overlap pass; per project policy an overlap is acceptable only when the jar is excluded (keeping the fat jar small), so the check FAILS on any overlapping twilio jar that is not excluded.

Run the same check locally:

```
../gradlew checkKeycloakClasspathOverlap
```

The second, **`checkKeycloakShading`** (POST-shade), automates the manual "exactly three directories / two subdirectories" verification below. It builds the `shadowJar` and FAILs unless every shipped `.class` is (1) from an excluded jar (never reaches the fat jar), (2) a relocated entry under `org.philanthropydatacommons.shadow.*`, or (3) the provider's own package (`org.philanthropydatacommons.auth`). It also FAILs on a relocate miss: a relocator is configured but the class it claims was not renamed in the built jar (e.g. a multi-release-jar versioned entry that slipped through, or a new twilio transitive dep in a package nothing relocates). It also FAILs on a misdirected relocator whose computed destination is outside `org.philanthropydatacommons.shadow.*`, so a no-op or wrong-namespace rule (e.g. `relocate("com.twilio", "com.twilio2")`) cannot validate itself. "Relocated" is verified via the public `Relocator` API (`canRelocateClass` + `relocateClass`), not by reflecting on private `SimpleRelocator` fields, so a misconfigured relocator cannot satisfy the check by string-matching a declared pattern -- the built jar must contain the renamed classes. The pre-shade exclude check cannot catch these defects because excludes act on whole jars, not on classes within the shipped jars.

Run the same check locally:

```
../gradlew checkKeycloakShading
```

The tasks print, per twilio-side jar and per Keycloak jar, the colliding classes (pre-shade) and the leftover un-relocated classes (post-shade). They throw (failing the build) on the conditions described above; add the missing `exclude(dependency(...))` or `relocate(...)` in the `shadowJar` task to fix them. The first pre-shade run downloads and unpacks the targeted Keycloak distribution under `twilio-keycloak-provider/build/keycloak-overlap-dist/`; subsequent runs reuse it.

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
