# OpenShift integration extension for Keycloak

NOTE: This extension is not officially supported by Keycloak team. In case of troubles, you can ask on keycloak-user mailing list
rather create pull request to improve this extension (if you find some issues etc).

Experimental extension to Keycloak that supports replacing the internal OpenShift identity provider with Keycloak. There's three core parts to this extension:

* Token review endpoint: custom token introspection endpoint for OpenShift
* Client federation: ability to federate OAuth clients registered in OpenShift
* Http challenge authenticators: authenticator implementations, which can be used to create the "Http challenge" authentication flow, which then can be used by
Keycloak clients, which are supposed to integrate with Openshift server

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

After it is done, you can copy built files and required dependencies to your Keycloak server:
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

## Create HTTP Challenge authentication flow

Previous versions of Keycloak (Keycloak 21 and older) contain the authentication flow called `http challenge`, which was removed from Keycloak in
Keycloak 22 release. The corresponding authenticators were moved to this extension. If you want the `http challenge` authentication flow to be added
again to your Keycloak server, then you need to have this extension deployed. As long as you did previous steps, you can use this command to automatically create the `Http Challenge`
flow in your specified realm (in this example realm with name `foo` is used):

```
mvn exec:java -Phttp-challenge-create -DrealmName=foo
```

In case you prefer to create authentication flow manually, you can take a look at [img/http-challenge-flow.png](img/http-challenge-flow.png) 
to see how it looked in previous version. Then you can do your own customizations to the flow if needed, for example if you need OTP or Kerberos/SPNEGO authentication.

## How to make this working against OpenShift server

TODO. Anyone is welcome to contribute instructions here how to make this working with real OpenShift 3.X environment. Also it is welcome
to make it working with newer versions of OpenShift.
