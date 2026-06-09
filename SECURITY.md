# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| `main` branch (active development) | Yes |
| Tagged releases | Best-effort |

## Reporting a vulnerability

If you discover a security issue, **do not** open a public GitHub issue with exploit details.

1. Email the maintainers with a description, impact, and reproduction steps.
2. Allow reasonable time for a fix before public disclosure.

For architecture and hardening context, see [docs/security/README.md](docs/security/README.md).

## Security practices in this repo

- Keycloak JWT validation on backend services (OAuth2 Resource Server)
- Optional TOTP 2FA for end users
- Secrets via `.env` / environment variables — never commit credentials
- Demo accounts and passwords are for **local development only**
