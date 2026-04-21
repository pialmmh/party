package com.telcobright.party.keycloak;

import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

public class PartyUserStorageProviderFactory
        implements UserStorageProviderFactory<PartyUserStorageProvider> {

    public static final String PROVIDER_ID = "party-user-storage";

    public static final String CONFIG_URL = "partyBaseUrl";
    public static final String CONFIG_SECRET = "partyIntegrationSecret";

    private static final List<ProviderConfigProperty> CONFIG = ProviderConfigurationBuilder.create()
            .property()
                .name(CONFIG_URL)
                .label("Party Service base URL")
                .helpText("Base URL of the Party service (e.g. http://party:18081)")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("http://party:18081")
                .add()
            .property()
                .name(CONFIG_SECRET)
                .label("Integration secret")
                .helpText("Shared secret sent in X-KC-Integration-Secret header")
                .type(ProviderConfigProperty.PASSWORD)
                .add()
            .build();

    @Override
    public PartyUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        String url = model.get(CONFIG_URL);
        String secret = model.get(CONFIG_SECRET);
        PartyClient client = new PartyClient(url, secret);
        return new PartyUserStorageProvider(session, model, client);
    }

    @Override
    public String getId() { return PROVIDER_ID; }

    @Override
    public String getHelpText() {
        return "Federates users from the Telcobright Party service. " +
               "Realm name must be 'party-operators' or 'tenant-<opShort>-<tnShort>'.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() { return CONFIG; }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config)
            throws ComponentValidationException {
        if (config.get(CONFIG_URL) == null || config.get(CONFIG_URL).isBlank()) {
            throw new ComponentValidationException("partyBaseUrl is required");
        }
        if (config.get(CONFIG_SECRET) == null || config.get(CONFIG_SECRET).isBlank()) {
            throw new ComponentValidationException("partyIntegrationSecret is required");
        }
    }
}
