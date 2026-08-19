/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 *
 * Author: GLM-5.2
 */
package org.philanthropydatacommons.keycloak.requiredaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.protocol.oidc.utils.RedirectUtils;
import org.keycloak.userprofile.ValidationException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LinkToWelcomePageRequiredActionFactoryTest {

    @Mock private KeycloakSession session;
    @Mock private RealmModel realm;
    @Mock private RequiredActionConfigModel model;
    @Mock private ClientModel client;

    private final LinkToWelcomePageRequiredActionFactory factory =
            new LinkToWelcomePageRequiredActionFactory();

    private void stubConfig(String targetClient, String targetWelcomePageUri) {
        when(model.getConfigValue(Constants.MAX_AUTH_AGE_KEY)).thenReturn(null);
        when(model.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT)).thenReturn(targetClient);
        when(model.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_WELCOME_PAGE_URI)).thenReturn(targetWelcomePageUri);
    }

    @Test
    void bothBlankIsValid() {
        stubConfig("", "");

        assertDoesNotThrow(() -> factory.validateConfig(session, realm, model));
    }

    @Test
    void clientBlankWithWelcomePageUriIsInvalid() {
        stubConfig("", "https://example.com/");

        assertThrows(ValidationException.class,
                () -> factory.validateConfig(session, realm, model));
    }

    @Test
    void clientSetWithBlankWelcomePageUriIsValid() {
        stubConfig("my-client", "");
        when(realm.getClientByClientId("my-client")).thenReturn(client);

        assertDoesNotThrow(() -> factory.validateConfig(session, realm, model));
    }

    @Test
    void clientSetWithValidWelcomePageUriIsValid() {
        stubConfig("my-client", "https://example.com/");
        when(realm.getClientByClientId("my-client")).thenReturn(client);

        try (MockedStatic<RedirectUtils> redirectUtils = Mockito.mockStatic(RedirectUtils.class)) {
            redirectUtils.when(() -> RedirectUtils.verifyRedirectUri(any(), eq("https://example.com/"), any()))
                    .thenReturn("https://example.com/");

            assertDoesNotThrow(() -> factory.validateConfig(session, realm, model));
        }
    }

    @Test
    void clientSetWithUnregisteredWelcomePageUriIsInvalid() {
        stubConfig("my-client", "https://evil.example.com/");
        when(realm.getClientByClientId("my-client")).thenReturn(client);

        try (MockedStatic<RedirectUtils> redirectUtils = Mockito.mockStatic(RedirectUtils.class)) {
            redirectUtils.when(() -> RedirectUtils.verifyRedirectUri(any(), eq("https://evil.example.com/"), any()))
                    .thenReturn(null);

            assertThrows(ValidationException.class,
                    () -> factory.validateConfig(session, realm, model));
        }
    }

    @Test
    void unknownClientIsInvalid() {
        stubConfig("ghost", "");
        when(realm.getClientByClientId("ghost")).thenReturn(null);

        assertThrows(ValidationException.class,
                () -> factory.validateConfig(session, realm, model));
    }
}
