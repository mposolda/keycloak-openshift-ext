package org.keycloak.test;

import java.util.Arrays;
import java.util.List;

import org.keycloak.Keycloak;
import org.keycloak.common.Version;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class KeycloakLifecycle {

    private static final String KEYCLOAK_VERSION = Version.VERSION;

    // TODO: Read from pom if needed (For instance like org.keycloak.common.Version)
    private static final String MY_VERSION = "1.0-SNAPSHOT";

    private static final int HTTP_PORT = 8180;
    private static final int HTTPS_PORT = 8543;

    private Keycloak keycloak;

    public void start() {
        try {
            keycloak = Keycloak.builder()
                    //.setHomeDir(configuration.getProvidersPath())
                    .setVersion(KEYCLOAK_VERSION)
                    .addDependency("org.keycloak.ext", "openshift-ext", MY_VERSION)
                    .start(getArgs());
            // waitForReadiness();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getArgs() {
        System.setProperty("quarkus.http.test-port", String.valueOf(HTTP_PORT));
        System.setProperty("quarkus.http.test-ssl-port", String.valueOf(HTTPS_PORT));

        return Arrays.asList("-v",
                "start-dev",
                "--db=dev-mem",
                "--cache=local");
    }

    public void stop() {
        if (keycloak != null) {
            try {
                keycloak.stop();
            } catch (Exception e) {
                throw new RuntimeException("Failed to stop the server", e);
            } finally {
                keycloak = null;
            }
        }
    }
}
