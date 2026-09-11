/*
 * Copyright (c) 2026 Philanthropy Data Commons
 * License: Apache Software License 2.0.
 */

package org.philanthropydatacommons.keycloak.requiredaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredActionConfigModel;
import org.keycloak.userprofile.ValidationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link LinkToWelcomePageRequiredActionFactory}. */
@ExtendWith(MockitoExtension.class)
public class LinkToWelcomePageRequiredActionFactoryTest {

  @Mock private KeycloakSession session;
  @Mock private RealmModel realm;
  @Mock private RequiredActionConfigModel model;
  @Mock private ClientModel client;

  private final LinkToWelcomePageRequiredActionFactory factory =
      new LinkToWelcomePageRequiredActionFactory();

  private void stubConfig(String targetClient) {
    when(model.getConfigValue(LinkToWelcomePageRequiredAction.CONFIG_TARGET_CLIENT))
        .thenReturn(targetClient);
  }

  @Test
  void unconfiguredIsValid() {
    // Blank targetClient is allowed: the action is a no-op when unconfigured,
    // so an admin can enable it before filling in the client id.
    stubConfig("");
    assertDoesNotThrow(() -> factory.validateConfig(session, realm, model));
  }

  @Test
  void knownClientIsValid() {
    stubConfig("my-client");
    when(realm.getClientByClientId("my-client")).thenReturn(client);
    assertDoesNotThrow(() -> factory.validateConfig(session, realm, model));
  }

  @Test
  void unknownClientIsInvalid() {
    stubConfig("ghost");
    when(realm.getClientByClientId("ghost")).thenReturn(null);
    assertThrows(ValidationException.class, () -> factory.validateConfig(session, realm, model));
  }

  @Test
  void configMetadataExposesOnlyTargetClient() {
    // The dialog must show exactly one property: the superclass contributes a
    // Maximum-Age-of-Authentication field that does nothing for this NOT_SUPPORTED
    // provider, so it is intentionally not surfaced.
    List<?> props = factory.getConfigMetadata();
    assertEquals(1, props.size(), "expected exactly one config property");
  }
}
