package org.keycloak.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;

import static org.keycloak.test.TestsHelper.deleteRealm;
import static org.keycloak.test.TestsHelper.importTestRealm;
import static org.keycloak.test.TestsHelper.keycloakBaseUrl;

/**
 * TODO:mposolda rename this class?
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public abstract class MyTest {

    private static KcLifecycle keycloak;

    protected static Keycloak ADMIN_CLIENT;

    protected static final String TEST_REALM_NAME = "test";

    @BeforeClass
    public static void beforeMe() throws IOException {
        System.out.println("BEFORE");
        keycloak = new KcLifecycle();
        keycloak.start();

        ADMIN_CLIENT = Keycloak.getInstance(TestsHelper.keycloakBaseUrl, "master", "admin", "admin", "admin-cli");

        System.out.println("START KEYCLOAK FINISHED");
    }

    @AfterClass
    public static void afterMe() throws IOException {
        System.out.println("AFTER CLASS");

        keycloak.stop();

        System.out.println("STOP KEYCLOAK FINISHED");
    }

    @Before
    public void before() throws IOException {
        importTestRealm("admin", "admin", "/realm/testrealm.json");
    }

    @After
    public void after() throws IOException {
        TestsHelper.deleteRealm("admin", "admin", "test");
    }

    private boolean importTestRealm(String username, String password, String realmJsonPath) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = TestsHelper.class.getResourceAsStream(realmJsonPath)) {
            RealmRepresentation realmRepresentation = mapper.readValue(stream, RealmRepresentation.class);

            configureTestRealm(realmRepresentation);

            ADMIN_CLIENT.realms().create(realmRepresentation);
//            testRealm = realmRepresentation.getRealm();
//            generateInitialAccessToken(keycloak);
            return true;
        }

    }

    protected abstract void configureTestRealm(RealmRepresentation realmRep);

//    @Test
//    public void testMe() {
//        System.out.println("TEST user.dir=" + System.getProperty("user.dir") + ", baseDir=" + System.getProperty("baseDir"));
//        sleep(60000);
//    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }
    }
}
