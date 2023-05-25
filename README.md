# OpenShift integration extension for Keycloak

Experimental extension to Keycloak that supports replacing the internal OpenShift identity provider with Keycloak. There's two core parts to this extension:

* Token review endpoint: custom token introspection endpoint for OpenShift
* Client federation: ability to federate OAuth clients registered in OpenShift

## Deploying to Keycloak

```
mvn clean package
cp target/openshift-ext-1.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
cp target/lib/*.jar $KEYCLOAK_HOME/providers/
```

## TODO

* Fix UserBuilder from Keycloak test helper?
* Fix test for client storage and make it working
* Clean-up dependencies - and test manually somehow that both clientStorage and review endpoint works
* Fix README and update instructions with details about Openshift etc.
