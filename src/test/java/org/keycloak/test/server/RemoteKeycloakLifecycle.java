package org.keycloak.test.server;

import org.jboss.logging.Logger;

/**
 * This class can be used when Keycloak server is already started on current laptop and hence start/stop of the Keycloak
 * server won't be provided by this test itself. It is the responsibility of the admin to start/stop the server and deploy corresponding providers to it.
 *
 * The testsuite itself will be responsible for import "test" realm to the specified Keycloak server and cleanup of this realm after the test.
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class RemoteKeycloakLifecycle implements KeycloakLifecycle {

    private static final Logger logger = Logger.getLogger(RemoteKeycloakLifecycle.class);

    @Override
    public void start() {
        logger.infof("Ignored start of Keycloak server as it is externally managed");
    }

    @Override
    public void stop() {
        logger.infof("Ignored stop of Keycloak server as it is externally managed");
    }
}
