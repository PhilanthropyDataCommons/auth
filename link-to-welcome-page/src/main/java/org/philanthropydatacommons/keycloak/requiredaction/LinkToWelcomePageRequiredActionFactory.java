/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 *
 * Author: GLM-5.2
 */
package org.philanthropydatacommons.keycloak.requiredaction;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.protocol.oidc.utils.RedirectUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.userprofile.ValidationException;
import org.keycloak.validate.ValidationError;

/**
 * Factory for {@link LinkToWelcomePageRequiredAction}. Declares the two
 * config properties shown in the Admin Console's Required Action configuration
 * dialog, and validates the welcome-page URI against the target client's
 * registered valid redirect URIs at save time.
 *
 * @author GLM-5.2
 */
public final class LinkToWelcomePageRequiredActionFactory implements RequiredActionFactory {

    // Validation-error message keys, translated by the admin theme's message
    // bundle (see pdc-keycloak-theme admin messages). Kebab-case to match
    // Keycloak's own keys. Author: GLM-5.2
    private static final String MSG_TARGET_CLIENT_REQUIRED = "link-to-welcome-page.error-target-client-required";
    private static final String MSG_CLIENT_NOT_FOUND = "link-to-welcome-page.error-client-not-found";
    private static final String MSG_INVALID_REDIRECT_URI = "link-to-welcome-page.error-invalid-redirect-uri";

    private static final ProviderConfigProperty TARGET_CLIENT_PROP = new ProviderConfigProperty(
            LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT,
            "Target client",
            """
            Client id whose context is used. When no target welcome-page URI is set, the \
            link points to this client's home URI (root URL + base URL).""",
            ProviderConfigProperty.STRING_TYPE,
            null);

    private static final ProviderConfigProperty TARGET_WELCOME_PAGE_URI_PROP = new ProviderConfigProperty(
            LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI,
            "Target welcome page URI (optional)",
            """
            URL the "back to application" link points to after all required actions \
            complete. Optional: if blank, the target client's home URI is used. When \
            set, it must be a registered valid redirect URI for the target client. \
            Tip: drag this action to the bottom of the Required Actions list so it \
            runs after all other actions.""",
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
        return Stream.concat(
                RequiredActionFactory.super.getConfigMetadata().stream(),
                Stream.of(TARGET_CLIENT_PROP, TARGET_WELCOME_PAGE_URI_PROP)
        ).collect(Collectors.toList());
    }

    @Override
    public void validateConfig(KeycloakSession session, RealmModel realm, RequiredActionConfigModel model) {
        RequiredActionFactory.super.validateConfig(session, realm, model);

        String targetClient = model.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT);
        String targetWelcomePageUri = model.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI);

        boolean clientBlank = targetClient == null || targetClient.isBlank();
        boolean welcomePageBlank = targetWelcomePageUri == null || targetWelcomePageUri.isBlank();

        if (clientBlank && welcomePageBlank) {
            return;
        }
        if (clientBlank) {
            throw new ValidationException(new ValidationError(getId(),
                    LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT,
                    MSG_TARGET_CLIENT_REQUIRED));
        }

        ClientModel client = realm.getClientByClientId(targetClient);
        if (client == null) {
            throw new ValidationException(new ValidationError(getId(),
                    LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT,
                    MSG_CLIENT_NOT_FOUND, targetClient));
        }

        // The welcome-page URI must be a registered valid redirect URI for the
        // target client (the same check Keycloak applies to
        // execute-actions-email), so an open/misconfigured target cannot be
        // saved. When blank, the client's home URI is used at run time.
        if (!welcomePageBlank && RedirectUtils.verifyRedirectUri(session, targetWelcomePageUri, client) == null) {
            throw new ValidationException(new ValidationError(getId(),
                    LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI,
                    MSG_INVALID_REDIRECT_URI, targetWelcomePageUri, targetClient));
        }
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return LinkToWelcomePageRequiredAction.PROVIDER_ID;
    }
}
