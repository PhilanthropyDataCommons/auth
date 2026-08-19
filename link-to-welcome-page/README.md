# link-to-welcome-page

A small, reusable Keycloak **required action** that, after the other required
actions in a flow complete, presents Keycloak's standard "your account was
updated" page with a **"back to application" link** to the configured client's
home URL.

Originally written for the Philanthropy Data Commons so that an
admin-initiated credential reset (or other "execute actions" email) lands the
user on a known application URL instead of dead-ending on Keycloak's account
console. It is generic and reusable by any Keycloak deployment with the same
need.

## What it does

When this action runs (and it should run **last** — see below), it sets the
same auth-session notes Keycloak's own action-token handlers use
(`END_AFTER_REQUIRED_ACTIONS` and `SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS`)
together with the target client's home URI. `AuthenticationManager` then
renders its standard "your account was updated" info page with a
**"back to application" link** to that home URI. The user clicks the
link; there is no automatic HTTP redirect.

It does **not** re-implement any email, action-token, or redirect-validation
logic. It only sets two auth notes and the auth session's redirect URI; the
rest is Keycloak's existing, unchanged code path.

## What it does NOT do

- It does not change the email link (`{0}` in the email templates). The
  action-token link still points at `.../login-actions/action-token?key=...`;
  this action only affects where the user goes *after* the required actions.
- It does not issue an OIDC authorization code to the target client. The user
  is shown the "account updated" page with a "back to application" link to
  the configured URL (no automatic HTTP redirect, no login to the target
  app). If you need a real OIDC login/redirect to the app, this is the wrong
  tool.
- It does not auto-add itself to users. An admin must select it (e.g. in the
  credential-reset dialog) or set it as a realm default required action.

## Why a link, not an auto-redirect?

This action ends the flow on Keycloak's standard "your account was updated"
info page, which renders a "back to application" **link** to the target
client's home URI (`info.ftl`'s `pageRedirectUri`). There is no automatic HTTP
redirect.

A true 302 from inside a required action would mean calling
`context.challenge(Response.seeOther(...))` instead of `context.success()`.
That bypasses `AuthenticationManager.finishedRequiredActions`, which is the
single place Keycloak (a) revokes the consumed action token (the
`INVALIDATE_ACTION_TOKEN` auth note) and (b) removes the authentication
session. Re-implementing that cleanup in this action would duplicate Keycloak
internals, so this action deliberately stays on the `success()` ->
`finishedRequiredActions` path and presents a link.

If you want an actual auto-redirect, the least-invasive way is a theme
override: copy `theme/base/login/info.ftl` into your theme and add a
`<meta http-equiv="refresh" content="0; url=${pageRedirectUri}">` (or a small
JS `window.location`) when `pageRedirectUri?has_content`. That keeps
`finishedRequiredActions`'s cleanup intact while turning the link into a
redirect. That is a theme change, not a change to this provider jar.

## Configure

1. Deploy the jar (below) and restart Keycloak so the action is registered.
2. In the Admin Console, go to **Authentication -> Required Actions**.
3. Find the row **"Link to Welcome Page"** and enable it.
4. **Drag the row to the bottom of the list.** Required actions run in
   priority order (top row first, bottom row last); dragging it to the bottom
   makes it run after all built-in actions (`VERIFY_EMAIL`, `UPDATE_PASSWORD`,
   etc.), which is what you want.
5. **Create a dedicated client for this link** (if you do not already have one).
   The "back to application" link points at an OIDC client's **home URI**
   (its root URL + base URL, the same value Keycloak uses for its own
   `client.baseUrl` "back to application" fallback in `info.ftl`), so create
   a fresh **public** OIDC client dedicated to this purpose rather than
   reusing an existing application client (whose base URL serves its own
   login flow). Under **Clients -> Create client**:
   - **Client type**: `public` (this client never receives an authorization
     code; it exists only to own the welcome-page URL).
   - **Root URL** and/or **Base URL**: the URL you want users to land on, e.g.
     a root URL of `https://example.com` and a base URL of `/welcome`
     resolve to `https://example.com/welcome`. Keycloak resolves the home
     URI from root URL + base URL the same way it resolves the org-invite
     fallback (`ResolveRelative.resolveRelativeUri`).
6. Click the **configuration button** (the gear on the required-action row)
   and set **Target client** — the client id of the client from step 5. Its
   home URI (root URL + base URL) is the URL the "back to application" link
   points to. Leaving it blank makes the action a no-op, so you can enable
   the action before filling this in.

   **Display name.** The friendly label "Link to Welcome Page" comes from
   the theme's `requiredAction.link-to-welcome-page` message (added to the
   PDC keycloak theme) and the factory's `getDisplayText`. If your realm
   shows the literal key `requiredAction.link-to-welcome-page` instead, the
   theme message is missing or the action was registered with a key-style
   `name`; re-register via the Admin Console or add the theme message / set
   the required action's `name` in the realm import.

   **Config-validation messages.** When validation rejects a config (unknown
   client), the Admin Console localizes the error via the message key
   `link-to-welcome-page.error-client-not-found` in the PDC keycloak theme's
   **admin** message bundle. `ValidationError`'s message field is a message
   key (per Keycloak's `ValidationError`: "Holds the message key for
   translation"), looked up in the admin theme's `messages_*.properties`;
   without this override the console would render the literal key.

## Use

For an admin-initiated credential reset, select this action in the credential
reset dialog alongside the actions you want the user to perform (e.g.
`UPDATE_PASSWORD`). The order in which you tick actions in the dialog does not
matter — Keycloak re-sorts by the Required Actions priority at runtime, and
you dragged this one to the bottom, so it runs last.

After the user finishes the other actions, they see the "your account was
updated" page with a "back to application" link to the configured URL (no
automatic redirect).

## Idempotency

Setting the two auth notes is an idempotent overwrite with the constant
`"true"` (matching Keycloak's own action-token handlers). Keycloak
deduplicates required actions by alias at runtime, so the action runs at most
once per flow.

The action is a no-op when unconfigured, so enabling it without configuring
does not change any flow.

## No-op logging

Each no-op path logs a single line (per invocation) via Keycloak's logger
(`org.philanthropydatacommons.keycloak.requiredaction.LinkToWelcomePageRequiredAction`)
so a misconfigured action does not silently do nothing. The log level is tied
to the reason, so an admin who accidentally enables this action as a realm
default does not flood the logs with warnings on every login:

- **WARN** — genuine misconfigurations:
  - `no target client is configured for this required action (set the
    'targetClient' config property).`
  - `configured target client 'my-client' no longer exists.`
  - `could not resolve a welcome-page URI: target client 'my-client' has
    neither a root URL nor a base URL.`
- **DEBUG** — the expected/normal no-op (running outside the action-token flow,
  e.g. the action left enabled as a realm default):
  - `not running in an action-token flow (the INVALIDATE_ACTION_TOKEN auth note
    is absent); this action only presents a link after action-token-driven
    required actions (e.g. execute-actions-email), and is a no-op in a normal
    login flow.`

The message includes the realm name and a short, operator-readable reason,
formatted with slf4j `{}` placeholders; no user/PII is logged.

## Build

```sh
./gradlew :link-to-welcome-page:clean :link-to-welcome-page:jar
```

The jar is written to `link-to-welcome-page/build/libs/`.

To run the full local verification that CI enforces — Checkstyle, Error Prone
with NullAway, the JUnit suite, and JaCoCo coverage — use:

```sh
./gradlew :link-to-welcome-page:check
```

See the top-level README for the repo-wide lint configuration (Checkstyle with
Google's style config, Error Prone, NullAway in JSpecify mode).

## Deploy

```sh
rm -f /opt/keycloak/providers/link-to-welcome-page-*.jar
cp link-to-welcome-page/build/libs/link-to-welcome-page-*.jar /opt/keycloak/providers/
/opt/keycloak/bin/kc.sh build
systemctl restart keycloak   # or equivalent
```

The `pdc-versioning` Gradle plugin stamps the jar with a date-and-git-sha
version, so the filename is `link-to-welcome-page-<version>.jar` (for example
`link-to-welcome-page-20260819-c0386c9.jar`); the wildcard above matches it
without hardcoding the version.

## Compatibility

Built and tested against Keycloak 26.7.2. Targets JRE 17.

## Security

### No open link target via end-user input

The link target is the configured target client's **home URI** (its
`rootUrl` + `baseUrl`, resolved via `ResolveRelative.resolveRelativeUri` —
the same call Keycloak's own org-invite fallback uses). It is read from
**realm-level required-action config** (`targetClient`), editable only by
admins with realm `manage` permission, and from the named client's
`rootUrl`/`baseUrl`, editable only by admins with `manage-clients` — the same
trust level as editing any client or required action. The end user clicking
the action-token link cannot influence either value; they are server-side
config, not request parameters or token claims the user controls.

### No privilege escalation via AIA

`initiatedActionSupport()` returns `NOT_SUPPORTED`: this action only presents
a link inside the action-token flow (it is a no-op outside it, gated on the
`INVALIDATE_ACTION_TOKEN` auth note), so it is not exposed as an
application-initiated action. It is meant to run as a required action added by
`execute-actions-email`, not self-triggered by a user. There is no AIA surface
to abuse: the link target is always the admin-configured URL, never
user-controlled.

### No premature flow termination

Setting `END_AFTER_REQUIRED_ACTIONS` does not skip pending required actions.
`finishedRequiredActions` (which reads that note) only runs when
`nextRequiredAction` returns null — i.e. when no actions remain. Because this
action is intended to run last (lowest priority), the note takes effect only
after all other actions complete, which is the desired behavior.

### No short-circuit of normal logins

The action sets `END_AFTER_REQUIRED_ACTIONS` **only inside the action-token
flow** (e.g. `execute-actions-email`). It gates on the
`INVALIDATE_ACTION_TOKEN` auth note, which Keycloak sets in that flow
(`LoginActionsService` ~line 710; `AbstractActionTokenHandler` ~line 100 sets
`END_AFTER_REQUIRED_ACTIONS` itself for the same flow). In a normal login the
auth session proceeds to issue an authorization code
(`AuthenticationManager.redirectAfterSuccessfulFlow`); setting
`END_AFTER_REQUIRED_ACTIONS` there would instead render the info page and
remove the auth session (`finishedRequiredActions` ~lines 1098-1116),
breaking every login. So if an admin accidentally enables this action as a
realm default required action, normal logins are unaffected: the action is a
no-op and logs at DEBUG rather than terminating the flow.

### Idempotency / no state corruption

Setting the two auth notes is an idempotent overwrite with the constant
`"true"` (matching Keycloak's own action-token handlers). Keycloak
deduplicates required actions by alias at runtime, so the action runs at most
once per flow.

### Non-issues (considered, no action needed)

- **Template XSS via `pageRedirectUri`.** The home URI is passed to Keycloak's
  info-page template (`info.ftl` ~line 15) as `pageRedirectUri`, rendered as
  `<a href="${pageRedirectUri}">`. FreeMarker **does** HTML-escape this
  interpolation: `DefaultFreeMarkerProvider` (~lines 59-65) configures
  `HTMLOutputFormat` for `.ftl` files, so `${...}` auto-escapes (the template
  uses `?no_esc` only where it deliberately bypasses escaping, e.g. lines 5
  and 11). So the value is not rendered "unescaped." HTML-escaping does not,
  however, block a `javascript:`/`data:` scheme in the href. The home URI
  comes from the target client's `rootUrl`/`baseUrl` (admin-controlled,
  `manage-clients`) — the same source Keycloak's own `client.baseUrl` fallback
  link in `info.ftl` (~lines 18-19) uses for every flow. Keycloak does not
  validate the http(s) scheme of `rootUrl`/`baseUrl` at save or render time,
  so that residual `javascript:`-scheme surface is pre-existing Keycloak
  behavior this action mirrors rather than introduces; it is not widened by
  this action, which never reads a URL from end-user input.
- **Logging.** No-op paths log via an `Outcome` enum that carries each
  reason's level: genuine misconfigurations at `WARN`, the expected non-action-
  token-flow no-op at `DEBUG` (so an admin who accidentally enables this as a
  realm default does not flood the logs). The log line contains only the realm
  name and a short, operator-readable reason (slf4j `{}` placeholders); it logs
  no usernames, emails, tokens, or other PII/secrets.

### Dependency security

`compileOnly`/`testImplementation` Keycloak jars at 26.7.2 (matches `main`).
No other runtime dependencies; the jar is self-contained. Build-time
`pdc-versioning` shells out to `git` (build-time only, not in the jar).

## License

Apache Software License 2.0 (see the `LICENSE` file in this directory).
