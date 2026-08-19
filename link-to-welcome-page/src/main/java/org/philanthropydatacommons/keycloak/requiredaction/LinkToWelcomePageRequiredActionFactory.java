/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 */

package org.philanthropydatacommons.keycloak.requiredaction;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.userprofile.ValidationException;
import org.keycloak.validate.ValidationError;

/**
 * Factory for {@link LinkToWelcomePageRequiredAction}. Declares the single config property shown in
 * the Admin Console's Required Action configuration dialog ({@code targetClient}) and validates at
 * save time that the named client exists. The link target is the client's home URI (root URL +
 * base URL), resolved at run time; there is no separate URL field to validate.
 */
public final class LinkToWelcomePageRequiredActionFactory implements RequiredActionFactory {

  // Validation-error message key, translated by the admin theme's message
  // bundle (see pdc-keycloak-theme admin messages). Kebab-case to match
  // Keycloak's own keys.
  private static final String MSG_CLIENT_NOT_FOUND = "link-to-welcome-page.error-client-not-found";

  private static final ProviderConfigProperty TARGET_CLIENT_PROP =
      new ProviderConfigProperty(
          LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT,
          "Target client",
          """
          Client id whose home URI (root URL + base URL) the "back to \
          application" link points to after all required actions complete.
          """,
          ProviderConfigProperty.STRING_TYPE,
          null);

  @Override
  public RequiredActionProvider create(KeycloakSession session) {
    return new LinkToWelcomePageRequiredAction();
  }

  @Override
  public String getDisplayText() {
    return "Link to Welcome Page";
  }

  @Override
  public List<ProviderConfigProperty> getConfigMetadata() {
    // Only TARGET_CLIENT_PROP, intentionally not RequiredActionFactory.super's
    // config: that contributes the AIA-only "Maximum Age of Authentication"
    // field, which has no effect for NOT_SUPPORTED providers and would clutter
    // the dialog with a second property the README does not describe.
    return List.of(TARGET_CLIENT_PROP);
  }

  @Override
  public void validateConfig(
      KeycloakSession session, RealmModel realm, RequiredActionConfigModel model) {
    // Intentionally not RequiredActionFactory.super.validateConfig: that parses
    // the AIA-only MAX_AUTH_AGE_KEY and rejects malformed values, which does not
    // apply to this NOT_SUPPORTED provider.
    String targetClient =
        model.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT);
    // Blank is allowed: the action is a no-op when unconfigured, so an admin
    // can enable it before filling in the client id.
    if (targetClient == null || targetClient.isBlank()) {
      return;
    }
    ClientModel client = realm.getClientByClientId(targetClient);
    if (client == null) {
      throw new ValidationException(
          new ValidationError(
              getId(),
              LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT,
              MSG_CLIENT_NOT_FOUND,
              targetClient));
    }
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return LinkToWelcomePageRequiredAction.PROVIDER_ID;
  }
}
