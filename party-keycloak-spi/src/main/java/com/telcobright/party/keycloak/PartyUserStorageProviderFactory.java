package com.telcobright.party.keycloak;

import com.telcobright.party.keycloak.api.spi.PartyClient;
import com.telcobright.party.keycloak.internal.HttpPartyClient;
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

    public static final String CONFIG_URL              = "partyBaseUrl";
    public static final String CONFIG_TENANT_OVERRIDE  = "partyTenantOverride";

    private static final List<ProviderConfigProperty> CONFIG = ProviderConfigurationBuilder.create()
            .property()
                .name(CONFIG_URL)
                .label("Party Service base URL")
                .helpText("Base URL of the Party service (e.g. http://party:18081). "
                        + "The SPI calls {baseUrl}/api/v1/v2/auth/validate.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("http://127.0.0.1:18081")
                .add()
            .property()
                .name(CONFIG_TENANT_OVERRIDE)
                .label("Tenant ID override (optional)")
                .helpText("If set, every request sent to Party uses this tenantId. "
                        + "Otherwise the realm name is used as the tenantId, with the "
                        + "convention that realms named 'tenant-<op>-<tn>' map to '<tn>'.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .add()
            .build();

    @Override
    public PartyUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        String url            = model.get(CONFIG_URL);
        String tenantOverride = model.get(CONFIG_TENANT_OVERRIDE);
        PartyClient client = new HttpPartyClient(
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(3)).build(),
                url, tenantOverride);
        return new PartyUserStorageProvider(session, model, client);
    }

    @Override
    public String getId() { return PROVIDER_ID; }

    @Override
    public String getHelpText() {
        return "Federates users from the Telcobright Party service via /v2/auth/validate. "
                + "Party (stateless) runs a per-endpoint policy chain with Cisco-ACL semantics; "
                + "each backend (Odoo, LDAP, Routesphere, custom) is implemented as its own "
                + "self-contained login policy.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() { return CONFIG; }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config)
            throws ComponentValidationException {
        if (config.get(CONFIG_URL) == null || config.get(CONFIG_URL).isBlank()) {
            throw new ComponentValidationException("partyBaseUrl is required");
        }
    }
}
