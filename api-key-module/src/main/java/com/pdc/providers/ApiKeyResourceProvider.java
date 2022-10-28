package com.pdc.providers;

import com.pdc.resources.ApiKeyResource;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class ApiKeyResourceProvider implements RealmResourceProvider {


    private KeycloakSession session;

    public ApiKeyResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new ApiKeyResource(session);
    }

    @Override
    public void close() {

    }
}
