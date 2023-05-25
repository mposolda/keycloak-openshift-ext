package org.keycloak.test;

import java.util.Arrays;
import java.util.List;

import org.keycloak.Keycloak;
import org.keycloak.common.Version;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class KcLifecycle {

    private static final String KEYCLOAK_VERSION = Version.VERSION;

    private static final int HTTP_PORT = 8180;
    private static final int HTTPS_PORT = 8543;

    // Used by KeycloakMain class
    private static final String KEYCLOAK_ADMIN_ENV_VAR = "KEYCLOAK_ADMIN";
    private static final String KEYCLOAK_ADMIN_PASSWORD_ENV_VAR = "KEYCLOAK_ADMIN_PASSWORD";

    private Keycloak keycloak;

    public void start() {
        try {
            keycloak = configure().start(getArgs());

            // TODO:mposolda uncomment and implement
            // waitForReadiness();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getArgs() {
        System.setProperty("quarkus.http.test-port", String.valueOf(HTTP_PORT));
        System.setProperty("quarkus.http.test-ssl-port", String.valueOf(HTTPS_PORT));

        // TODO:mposolda drop this?
//        TestUtil.setEnvVariable(KEYCLOAK_ADMIN_ENV_VAR, "admin");
//        TestUtil.setEnvVariable(KEYCLOAK_ADMIN_PASSWORD_ENV_VAR, "admin");

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

    private Keycloak.Builder configure() {
        return Keycloak.builder()
                // TODO:mposolda do we need this?
                //.setHomeDir(configuration.getProvidersPath())
                .setVersion(KEYCLOAK_VERSION)
                .addDependency("org.keycloak.ext", "openshift-ext", "1.0-SNAPSHOT");
    }
}
