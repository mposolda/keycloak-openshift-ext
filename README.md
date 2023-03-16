# OpenShift integration extension for Keycloak

Experimental extension to Keycloak that supports replacing the internal OpenShift identity provider with Keycloak. There's two core parts to this extension:

* Token review endpoint: custom token introspection endpoint for OpenShift
* Client federation: ability to federate OAuth clients registered in OpenShift

## Deploying to Keycloak

