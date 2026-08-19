# link-to-welcome-page

A small, reusable Keycloak **required action** that, after the other required
actions in a flow complete, presents Keycloak's standard "your account was
updated" page with a **"back to application" link** to a configured welcome-page
URL.

Originally written for the Philanthropy Data Commons so that an
admin-initiated credential reset (or other "execute actions" email) lands the
user on a known application URL instead of dead-ending on Keycloak's account
console. It is generic and reusable by any Keycloak deployment with the same
need.

## What it does

When this action runs (and it should run **last** — see below), it sets the
same auth-session notes Keycloak's own action-token handlers use
(`END_AFTER_REQUIRED_ACTIONS` and `SET_REDIRECT_URI_AFTER_REQUIRED_ACTIONS`)
together with the configured welcome-page URI. `AuthenticationManager` then
renders its standard "your account was updated" info page with a
**"back to application" link** to the configured URL. The user clicks the
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
info page, which renders a "back to application" **link** to the configured
URL (`info.ftl`'s `pageRedirectUri`). There is no automatic HTTP redirect.

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
   The "back to application" link targets an OIDC client, and the welcome-page
   URI is validated against that client's registered valid redirect URIs, so
   create a fresh **public** OIDC client dedicated to this purpose rather than
   reusing an existing application client (whose redirect URIs and base URL
   serve its own login flow). Under **Clients -> Create client**:
   - **Client type**: `public` (this client never receives an authorization
     code; it exists only to own the welcome-page URL and its validation).
   - **Base URL**: the origin of the page you want users to land on, e.g.
     `https://example.com`.
   - **Valid redirect URIs**: one or more paths under that origin that you
     want to allow as welcome pages, e.g. `/welcome`. Keycloak resolves a
     configured welcome-page URI against this client's base URL, so use a
     base URL plus a relative valid redirect URI (e.g. base URL
     `https://example.com` + valid redirect URI `/welcome`) rather than a
     bare `+`.
6. Click the **configuration button** (the gear on the required-action row)
   and set:
   - **Target client** (required) — the client id of the client from step 5.
     Its home URI (root URL + base URL, resolved the same way Keycloak
     resolves the org-invite fallback) is used as the default welcome page.
   - **Target welcome page URI** (optional) — the URL the "back to
     application" link points to (e.g. `https://example.com/welcome`). If you
     leave this blank, the target client's home URI is used. When you set it,
     it must be a registered valid redirect URI for the target client.

   When set, the welcome-page URI is validated against the target client's
   registered valid redirect URIs **when you save** (the same check Keycloak
   applies to `execute-actions-email`), so a misconfigured or open target
   cannot be saved. Both fields blank is allowed (the action becomes a
   no-op).

   **Display name.** The friendly label "Link to Welcome Page" comes from
   the theme's `requiredAction.link-to-welcome-page` message (added to the
   PDC keycloak theme) and the factory's `getDisplayText`. If your realm
   shows the literal key `requiredAction.link-to-welcome-page` instead, the
   theme message is missing or the action was registered with a key-style
   `name`; re-register via the Admin Console or add the theme message / set
   the required action's `name` in the realm import.

   **Config-validation messages.** When validation rejects a config (missing
   target client, unknown client, unregistered redirect URI), the Admin
   Console localizes the error via three message keys in the PDC keycloak
   theme's **admin** message bundle:
   `link-to-welcome-page.error-target-client-required`,
   `link-to-welcome-page.error-client-not-found`, and
   `link-to-welcome-page.error-invalid-redirect-uri`. `ValidationError`'s
   message field is a message key (per Keycloak's `ValidationError`: "Holds
   the message key for translation"), looked up in the admin theme's
   `messages_*.properties`; without these overrides the console would render
   the literal key.

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
  - `could not resolve a welcome-page URI: target welcome-page URI is not
    configured and target client 'my-client' has neither a root URL nor a base
    URL to default to.`
  - `resolved welcome-page URI '<uri>' is not an http(s) URL; refusing to set
    it as the link target (info.ftl renders pageRedirectUri in an href).`
    (default home-URI path only)
  - `configured welcome-page URI '<uri>' is not a valid redirect URI for the
    target client at run time (RedirectUtils.verifyRedirectUri returned null).`
    (configured path: unregistered, bad scheme, or the client's redirect URIs
    changed since save)
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

The link target is read from **realm-level required-action config**
(`targetClient`, `targetWelcomePageUri`), editable only by admins with realm
`manage` permission — the same trust level as editing any client or required
action. The end user clicking the action-token link cannot influence either
value; they are server-side config, not request parameters or token claims
the user controls.

- When `targetWelcomePageUri` is set, it is validated against the target
  client's registered valid redirect URIs **at save time** AND revalidated at
  run time (`RedirectUtils.verifyRedirectUri`, the same check Keycloak applies
  to `execute-actions-email`). The run-time call also resolves a relative URI
  (e.g. `/welcome`) against the client's root URL to an absolute http(s) URL
  (RedirectUtils ~lines 135-140) and re-enforces the http(s) scheme, so a value
  the save-time check accepted cannot become invalid or absolute-mismatched by
  the time the action runs. An admin cannot save an unregistered target, and a
  target whose client's redirect URIs or root URL later change no-ops rather
  than link somewhere unvalidated.
- When `targetWelcomePageUri` is blank, the default is the target client's
  home URI (`rootUrl` + `baseUrl` via `ResolveRelative.resolveRelativeUri`) —
  the client's own configured URL, same call Keycloak's org-invite fallback
  uses. Not user-controllable. Because the home URI is not a registered
  redirect URI, this path is not redirect-URI-validated; it is instead guarded
  by an http(s)-only scheme check (`isAllowedRedirectScheme`) before it reaches
  the template.

### No privilege escalation via AIA

`initiatedActionSupport()` returns `SUPPORTED`, so a user can self-trigger
this as an application-initiated action. The link target is still the
admin-configured URL; the user cannot choose where the link points.
Self-triggering only presents the admin-chosen link. The AIA caveat (the user
gets the link to the configured URL instead of returning to their app) is a
UX consideration, not a security hole.

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
no-op and logs a single warning rather than terminating the flow.

### Idempotency / no state corruption

Setting the two auth notes is an idempotent overwrite with the constant
`"true"` (matching Keycloak's own action-token handlers). Keycloak
deduplicates required actions by alias at runtime, so the action runs at most
once per flow.

### Non-issues (considered, no action needed)

- **Template XSS via `pageRedirectUri`.** The welcome-page URI is passed to
  Keycloak's info-page template (`info.ftl` ~line 15) as `pageRedirectUri`,
  rendered as `<a href="${pageRedirectUri}">`. FreeMarker **does** HTML-escape
  this interpolation: `DefaultFreeMarkerProvider` (~lines 59-65) configures
  `HTMLOutputFormat` for `.ftl` files, so `${...}` auto-escapes (the template
  uses `?no_esc` only where it deliberately bypasses escaping, e.g. lines 5
  and 11). So the value is not rendered "unescaped." HTML-escaping does not,
  however, block a `javascript:`/`data:` scheme in the href, so both code
  paths reject non-http(s) values before setting the link target: the
  configured path via `RedirectUtils.verifyRedirectUri` (which enforces the
  http(s) scheme, ~lines 143-147) and the default home-URI path via
  `isAllowedRedirectScheme`. The configured path is admin-controlled
  (`manage-realm`); the default home-URI path uses the client's
  `rootUrl`/`baseUrl` (`manage-clients`), which mirrors Keycloak's own
  `client.baseUrl` fallback link in `info.ftl` (~lines 18-19) — the residual
  `javascript:`-scheme surface there is pre-existing Keycloak behavior, not
  something this action introduces.
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
