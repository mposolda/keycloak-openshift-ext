package org.keycloak.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.authentication.authenticators.challenge.BasicAuthOTPAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticationFlowBindings;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.models.utils.TimeBasedOTP;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.test.builders.ClientBuilder;
import org.keycloak.test.builders.UserBuilder;
import org.keycloak.util.BasicAuthHelper;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class FlowOverrideHttpChallengeTest extends AbstractOpenshiftTest {

    public static final String HTTP_CHALLENGE_FLOW = "http challenge";
    public static final String TEST_APP_HTTP_CHALLENGE = "http-challenge-client";
    public static final String TEST_APP_HTTP_CHALLENGE_OTP = "http-challenge-otp-client";

    OAuthClient oauth;

    private TimeBasedOTP totp = new TimeBasedOTP();

    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
    }

    @Before
    public void setupEnv() {
        oauth = new OAuthClient();
        oauth.init(null);
    }

    @Before
    public void setupFlowsAndClients() {
        // Create original 'http challenge' flow
        String challengeFlowId = TestUtil.createHttpChallengeFlow(adminClient, "test");

        // Another challenge flow
        AuthenticationManagementResource authResource = adminClient.realm("test").flows();
        AuthenticationFlowRepresentation challengeOTP = new AuthenticationFlowRepresentation();
        challengeOTP.setAlias("challenge-override-flow");
        challengeOTP.setDescription("challenge grant based authentication");
        challengeOTP.setProviderId("basic-flow");
        challengeOTP.setTopLevel(true);
        challengeOTP.setBuiltIn(false);
        Response response = authResource.createFlow(challengeOTP);
        String challengeFlowId2 = TestsHelper.getCreatedId(response);
        response.close();

        AuthenticationExecutionRepresentation execution = new AuthenticationExecutionRepresentation();
        execution.setParentFlow(challengeFlowId2);
        execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED.toString());
        execution.setAuthenticator(BasicAuthOTPAuthenticatorFactory.PROVIDER_ID);
        execution.setPriority(10);
        response = authResource.addExecution(execution);
        response.close();

        // Clients
        ClientRepresentation client = ClientBuilder.create(TEST_APP_HTTP_CHALLENGE)
                .secret("password")
                .baseUrl(oauth.APP_AUTH_ROOT)
                .redirectUri(oauth.APP_AUTH_ROOT + "/*")
                .accessType(ClientBuilder.AccessType.PUBLIC);
        client.setDirectAccessGrantsEnabled(true);

        Map<String, String> authFlowsOverrides1 = new HashMap<>();
        authFlowsOverrides1.put(AuthenticationFlowBindings.DIRECT_GRANT_BINDING, challengeFlowId);
        authFlowsOverrides1.put(AuthenticationFlowBindings.BROWSER_BINDING, challengeFlowId);
        client.setAuthenticationFlowBindingOverrides(authFlowsOverrides1);
        adminClient.realm("test").clients().create(client);

        // Client 2
        client.setClientId(TEST_APP_HTTP_CHALLENGE_OTP);
        authFlowsOverrides1.put(AuthenticationFlowBindings.DIRECT_GRANT_BINDING, challengeFlowId2);
        authFlowsOverrides1.put(AuthenticationFlowBindings.BROWSER_BINDING, challengeFlowId2);
        client.setAuthenticationFlowBindingOverrides(authFlowsOverrides1);
        adminClient.realm("test").clients().create(client);
    }

    public static AuthenticationFlowRepresentation findFlowByAlias(String alias, List<AuthenticationFlowRepresentation> flows) {
        for (AuthenticationFlowRepresentation flow : flows) {
            if (alias.equals(flow.getAlias())) {
                return flow;
            }
        }
        return null;
    }

//    @Test
//    public void testMe() {
//        sleep(10000000);
//    }

    @Test
    public void testDirectGrantHttpChallengeOTP() {
        UserRepresentation user = adminClient.realm("test").users().search("test-user@localhost").get(0);
        UserRepresentation userUpdate = UserBuilder.edit(user).totpSecret("totpSecret").otpEnabled().build();
        adminClient.realm("test").users().get(user.getId()).update(userUpdate);

        CredentialRepresentation totpCredential = adminClient.realm("test").users()
                .get(user.getId()).credentials().stream().filter(c -> OTPCredentialModel.TYPE.equals(c.getType())).findFirst().get();

        setupBruteForce();

        Client httpClient = TestUtil.createResteasyClient();
        String grantUri = oauth.getResourceOwnerPasswordCredentialGrantUrl();
        WebTarget grantTarget = httpClient.target(grantUri);

        Form form = new Form();
        form.param(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD);
        form.param(OAuth2Constants.CLIENT_ID, TEST_APP_HTTP_CHALLENGE_OTP);

        // correct password + totp
        String totpCode = totp.generateTOTP("totpSecret");
        Response response = grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password" + totpCode))
                .post(Entity.form(form));
        assertEquals(200, response.getStatus());
        response.close();

        // correct password + wrong totp 2x
        response = grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password123456"))
                .post(Entity.form(form));
        assertEquals(401, response.getStatus());
        response = grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password123456"))
                .post(Entity.form(form));
        assertEquals(401, response.getStatus());

        // correct password + totp but user is temporarily locked
        totpCode = totp.generateTOTP("totpSecret");
        response = grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password" + totpCode))
                .post(Entity.form(form));
        assertEquals(401, response.getStatus());
        response.close();

        clearBruteForce();
        adminClient.realm("test").users().get(user.getId()).removeCredential(totpCredential.getId());
    }

    @Test
    public void testDirectGrantHttpChallengeUserDisabled() {
        setupBruteForce();

        Client httpClient = TestUtil.createResteasyClient();
        String grantUri = oauth.getResourceOwnerPasswordCredentialGrantUrl();
        WebTarget grantTarget = httpClient.target(grantUri);

        Form form = new Form();
        form.param(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD);
        form.param(OAuth2Constants.CLIENT_ID, TEST_APP_HTTP_CHALLENGE);

        UserRepresentation user = adminClient.realm("test").users().search("test-user@localhost").get(0);
        user.setEnabled(false);
        adminClient.realm("test").users().get(user.getId()).update(user);

        // user disabled
        Response response = grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password"))
                .post(Entity.form(form));
        assertEquals(401, response.getStatus());
        assertEquals("Unauthorized", response.getStatusInfo().getReasonPhrase());
        response.close();

        user.setEnabled(true);
        adminClient.realm("test").users().get(user.getId()).update(user);

        // lock the user account
        grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "wrongpassword"))
                .post(Entity.form(form));
        grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "wrongpassword"))
                .post(Entity.form(form));
        // user is temporarily disabled
        response = grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password"))
                .post(Entity.form(form));
        assertEquals(401, response.getStatus());
        assertEquals("Unauthorized", response.getStatusInfo().getReasonPhrase());
        response.close();

        clearBruteForce();

        httpClient.close();
    }

    @Test
    public void testClientOverrideFlowUsingBrowserHttpChallenge() {
        Client httpClient = TestUtil.createResteasyClient();
        oauth.clientId(TEST_APP_HTTP_CHALLENGE);
        String grantUri = oauth.getLoginFormUrl();
        WebTarget grantTarget = httpClient.target(grantUri);

        Response response = grantTarget.request().get();
        assertEquals(302, response.getStatus());
        String location = response.getHeaderString(HttpHeaders.LOCATION);
        response.close();

        // first challenge
        response = httpClient.target(location).request().get();
        assertEquals("Basic realm=\"test\"", response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals(401, response.getStatus());
        response.close();

        // now, username password using basic challenge response
        response = httpClient.target(location).request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password"))
                .post(Entity.form(new Form()));
        assertEquals(302, response.getStatus());
        location = response.getHeaderString(HttpHeaders.LOCATION);
        response.close();

        Form form = new Form();

        form.param(OAuth2Constants.GRANT_TYPE, OAuth2Constants.AUTHORIZATION_CODE);
        form.param(OAuth2Constants.CLIENT_ID, TEST_APP_HTTP_CHALLENGE);
        form.param(OAuth2Constants.REDIRECT_URI, oauth.APP_AUTH_ROOT);
        form.param(OAuth2Constants.CODE, location.substring(location.indexOf(OAuth2Constants.CODE) + OAuth2Constants.CODE.length() + 1));

        // exchange code to token
        response = httpClient.target(oauth.getAccessTokenUrl()).request()
                .post(Entity.form(form));
        assertEquals(200, response.getStatus());
        response.close();

        httpClient.close();
    }

    @Test
    public void testClientOverrideFlowUsingDirectGrantHttpChallenge() {
        Client httpClient = TestUtil.createResteasyClient();
        String grantUri = oauth.getResourceOwnerPasswordCredentialGrantUrl();
        WebTarget grantTarget = httpClient.target(grantUri);

        // no username/password
        Form form = new Form();
        form.param(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD);
        form.param(OAuth2Constants.CLIENT_ID, TEST_APP_HTTP_CHALLENGE);
        Response response = grantTarget.request()
                .post(Entity.form(form));
        assertEquals("Basic realm=\"test\"", response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals(401, response.getStatus());
        response.close();

        // now, username password using basic challenge response
        response = grantTarget.request()
                .header(HttpHeaders.AUTHORIZATION, BasicAuthHelper.createHeader("test-user@localhost", "password"))
                .post(Entity.form(form));
        assertEquals(200, response.getStatus());
        response.close();

        httpClient.close();
    }

    private void setupBruteForce() {
        RealmRepresentation testRealm = adminClient.realm("test").toRepresentation();
        testRealm.setBruteForceProtected(true);
        testRealm.setFailureFactor(2);
        testRealm.setMaxDeltaTimeSeconds(20);
        testRealm.setMaxFailureWaitSeconds(100);
        testRealm.setWaitIncrementSeconds(5);
        adminClient.realm("test").update(testRealm);
    }

    private void clearBruteForce() {
        RealmRepresentation testRealm = adminClient.realm("test").toRepresentation();
        testRealm.setBruteForceProtected(false);
        adminClient.realm("test").attackDetection().clearAllBruteForce();
        adminClient.realm("test").update(testRealm);
    }
}
