<#--
  Maintenance / upgrade notice shown at the bottom of every
  login page.

  The text is read from the "maintenanceNotice" message key so it
  can be changed from the Admin Console (Realm Settings ->
  Localization -> Realm Overrides) WITHOUT editing this file or
  restarting the server.

  The value may contain a safe subset of HTML: it is passed through
  Keycloak's kcSanitize, so tags like <strong>, <a href>, <br>, <p>,
  <em> are allowed (see KeycloakSanitizerPolicy). This is the same
  mechanism the built-in "Terms and Conditions" page uses for its
  termsText.

  Styling uses the theme's own PatternFly v4 alert classes (inherited
  from keycloak v1 via pdc-keycloak-theme), so the notice matches the
  rest of the login UI. The pf-c-alert__icon class is hardcoded
  here to match the base v1 template.ftl convention
  (kcAlertIconClass is only defined in v2+).

  To hide the notice, switch the realm's Login Theme back to
  "pdc-keycloak-theme" in the Admin Console (no restart required).
-->
<#macro content>
  <div class="${properties.kcAlertClass!} pf-m-warning"
       style="margin-top: 1rem;" role="status">
    <div class="pf-c-alert__icon">
      <span class="${properties.kcFeedbackWarningIcon!}"
            aria-hidden="true"></span>
    </div>
    <span class="${properties.kcAlertTitleClass!}"
      >${kcSanitize(msg("maintenanceNotice"))?no_esc}</span>
  </div>
</#macro>
