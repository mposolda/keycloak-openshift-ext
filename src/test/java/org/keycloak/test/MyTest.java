package org.keycloak.test;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;

import static org.keycloak.test.TestsHelper.deleteRealm;
import static org.keycloak.test.TestsHelper.importTestRealm;
import static org.keycloak.test.TestsHelper.keycloakBaseUrl;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class MyTest {

    private static KcLifecycle keycloak;

    private static Keycloak ADMIN_CLIENT;

    @BeforeClass
    public static void beforeMe() throws IOException {
        System.out.println("BEFORE");
        keycloak = new KcLifecycle();
        keycloak.start();

        importTestRealm("admin", "admin", "/realm/testrealm.json");
        ADMIN_CLIENT = Keycloak.getInstance(keycloakBaseUrl, "master", "admin", "admin", "admin-cli");

        System.out.println("START KEYCLOAK FINISHED");
    }

    @AfterClass
    public static void afterMe() throws IOException {
        System.out.println("AFTER");

        deleteRealm("admin", "admin", "test");
        keycloak.stop();

        System.out.println("STOP KEYCLOAK FINISHED");
    }

    @Test
    public void testMe() {
        System.out.println("TEST user.dir=" + System.getProperty("user.dir") + ", baseDir=" + System.getProperty("baseDir"));
        sleep(60000);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }
    }
}
