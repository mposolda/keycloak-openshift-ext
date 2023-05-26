# OpenShift integration extension for Keycloak

Experimental extension to Keycloak that supports replacing the internal OpenShift identity provider with Keycloak. There's two core parts to this extension:

* Token review endpoint: custom token introspection endpoint for OpenShift
* Client federation: ability to federate OAuth clients registered in OpenShift

NOTE: The code from this extension was part of the Keycloak main codebase until Keycloak 21 and was available as a preview feature `openshift-integration`.
From Keycloak 22, the `openshift-integration` preview feature was moved to this extension. 

## System requirements

The steps below are tested with:
* OpenJDK 17.0.7
* maven 3.6.3
* Keycloak 22

The version of OpenShift is supposed to be 3.X (X to be clarified) 

## Build and deploy to Keycloak
First build project with this command. It will also run automated tests with embedded Keycloak server on port 8180, so make sure that there is no other process running
on your laptop on this port:

```
mvn clean install
```

After it is done, you can copy built files and required dependencies to Keycloak server:
```
cp target/openshift-ext-1.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
cp target/lib/*.jar $KEYCLOAK_HOME/providers/
```

## Run the tests against your Keycloak server

If you want to test if extension works as expected with your Keycloak server, you can first start the server on `http://localhost:8180` as the automated tests assume
server running on this host and port (For real integration, this host and port is not a requirement. It is just for the automated tests).

```
cd $KEYCLOAK_HOME/bin
./kc.sh start-dev --http-port=8180
```

Make sure you have user `admin` with password `admin` in the realm `master`. It can be created on the welcome page on `http://localhost:8180`

Then run the tests against your Keycloak server.

NOTE: The test will create realm `test` on your Keycloak server and then will delete it after the test. So make sure you don't have realm 
of this name before running the test:

```
mvn test -Dkeycloak.lifecycle=remote
```

## How to make this working against OpenShift server

TODO. Anyone is welcome to contribute instructions here how to make this working with real OpenShift 3.X environment. Also it is welcome
to make it working with newer versions of OpenShift.
