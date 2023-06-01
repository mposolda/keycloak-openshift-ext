package org.keycloak.test;

import org.jboss.logging.Logger;
import org.keycloak.admin.client.Keycloak;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class HttpChallengeFlowHelper {

    private static final Logger logger = Logger.getLogger(HttpChallengeFlowHelper.class);

    public static void main(String[] args) {
        String realmName = System.getProperty("realmName");
        if (realmName == null) {
            logger.error("System property 'realmName' must be provided when running this application. This points to the realm where 'http challenge' authentication flow is going to be created.");
            System.exit(1);
        }

        logger.infof("Creating '%s' authentication flow in realm '%s'", FlowOverrideHttpChallengeTest.HTTP_CHALLENGE_FLOW, realmName);
        logger.infof("Assumption is Keycloak running on http://localhost:8180 with user admin/admin and realm with name '%s' and extension providers deployed", realmName);

        Keycloak adminClient = Keycloak.getInstance(TestsHelper.keycloakBaseUrl, "master", "admin", "admin", "admin-cli");
        TestUtil.createHttpChallengeFlow(adminClient, realmName);
        logger.infof("Successfully created '%s' authentication flow in realm '%s'", FlowOverrideHttpChallengeTest.HTTP_CHALLENGE_FLOW, realmName);
    }
}
