# Lightweight JWT without Keycloak for phase 1

We issue our own JWTs with `quarkus-smallrye-jwt`/`quarkus-smallrye-jwt-build` and verify with a checked-in RSA key pair for dev, instead of introducing Keycloak Dev Services. Keycloak would federate Google and local users and give SSO/refresh out of box, but adds a ~400 MB container, 15-20s startup, realm import and Google OAuth wiring — heavy for a skeleton. Owning the issuer keeps startup <2s, keeps the auth contract explicit (`POST /api/auth/register|login|verify|resend-verification`, `GET /api/me` with `Bearer`), and maps cleanly to a future Keycloak migration (swap issuer to Keycloak, keep same `mp.jwt.verify.issuer` and `groups=User`). We re-evaluate when social login or refresh tokens are required.

