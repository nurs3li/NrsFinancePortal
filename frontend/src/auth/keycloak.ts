import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
    url: import.meta.env.VITE_KEYCLOAK_URL,
    realm: import.meta.env.VITE_KEYCLOAK_REALM || 'nrs-finance',
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'nrs-finance-backend',
});

export default keycloak;