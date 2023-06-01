package org.keycloak.test;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ClientHttpEngineBuilder43;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionRepresentation;
import org.keycloak.representations.idm.AuthenticationFlowRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.BasicAuthHelper;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class TestUtil {

    public static final int NUMBER_OF_CONNECTIONS = 10;

    public static void addAuth(HttpPost httpPost, String clientId, String clientSecret) {
        if (clientSecret != null) {
            String authorization = BasicAuthHelper.createHeader(clientId, clientSecret);
            httpPost.setHeader("Authorization", authorization);
        } else {
            throw new IllegalArgumentException("clientSecret must be non-null");
            //parameters.add(new BasicNameValuePair("client_id", clientId));
        }
    }

    public static ClientResource findClientByClientId(RealmResource realm, String clientId) {
        for (ClientRepresentation c : realm.clients().findAll()) {
            if (clientId.equals(c.getClientId())) {
                return realm.clients().get(c.getId());
            }
        }
        return null;
    }

    public static UserRepresentation findUserByUsername(RealmResource realm, String username) {
        UserRepresentation user = null;
        List<UserRepresentation> ur = realm.users().search(username, null, null, null, 0, -1);
        if (ur.size() == 1) {
            user = ur.get(0);
        }

        if (ur.size() > 1) { // try to be more specific
            for (UserRepresentation rep : ur) {
                if (rep.getUsername().equalsIgnoreCase(username)) {
                    return rep;
                }
            }
        }

        return user;
    }


    public static ResteasyClient createResteasyClient() {
        try {
            return createResteasyClient(false, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ResteasyClient createResteasyClient(boolean ignoreUnknownProperties, Boolean followRedirects) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, KeyManagementException {
        ResteasyClientBuilder resteasyClientBuilder = (ResteasyClientBuilder) ResteasyClientBuilder.newBuilder();

//        if ("true".equals(System.getProperty("auth.server.ssl.required"))) {
//            File trustore = new File(PROJECT_BUILD_DIRECTORY, "dependency/keystore/keycloak.truststore");
//            resteasyClientBuilder.sslContext(getSSLContextWithTrustore(trustore, "secret"));
//
//            System.setProperty("javax.net.ssl.trustStore", trustore.getAbsolutePath());
//        }

        // We need to ignore unknown JSON properties e.g. in the adapter configuration representation
        // during adapter backward compatibility testing
        if (ignoreUnknownProperties) {
            // We need to use anonymous class to avoid the following error from RESTEasy:
            // Provider class org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider is already registered.  2nd registration is being ignored.
            ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider() {};
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            jacksonProvider.setMapper(objectMapper);
            resteasyClientBuilder.register(jacksonProvider, 100);
        }

        resteasyClientBuilder
                .hostnameVerification(ResteasyClientBuilder.HostnameVerificationPolicy.WILDCARD)
                .connectionPoolSize(NUMBER_OF_CONNECTIONS)
                .httpEngine(getCustomClientHttpEngine(resteasyClientBuilder, 1, followRedirects));

        return resteasyClientBuilder.build();
    }

    public static ClientHttpEngine getCustomClientHttpEngine(ResteasyClientBuilder resteasyClientBuilder, int validateAfterInactivity, Boolean followRedirects) {
        return new CustomClientHttpEngineBuilder43(validateAfterInactivity, followRedirects).resteasyClientBuilder(resteasyClientBuilder).build();
    }

    /**
     * Adds a possibility to pass validateAfterInactivity parameter into underlying ConnectionManager. The parameter affects how
     * long the connection is being used without testing if it became stale, default value is 2000ms
     */
    private static class CustomClientHttpEngineBuilder43 extends ClientHttpEngineBuilder43 {

        private final int validateAfterInactivity;
        private final Boolean followRedirects;

        private CustomClientHttpEngineBuilder43(int validateAfterInactivity, Boolean followRedirects) {
            this.validateAfterInactivity = validateAfterInactivity;
            this.followRedirects = followRedirects;
        }

        @Override
        protected ClientHttpEngine createEngine(final HttpClientConnectionManager cm, final RequestConfig.Builder rcBuilder,
                                                final HttpHost defaultProxy, final int responseBufferSize, final HostnameVerifier verifier, final SSLContext theContext) {
            final ClientHttpEngine engine;
            if (cm instanceof PoolingHttpClientConnectionManager) {
                PoolingHttpClientConnectionManager pcm = (PoolingHttpClientConnectionManager) cm;
                pcm.setValidateAfterInactivity(validateAfterInactivity);
                engine = super.createEngine(pcm, rcBuilder, defaultProxy, responseBufferSize, verifier, theContext);
            } else {
                engine = super.createEngine(cm, rcBuilder, defaultProxy, responseBufferSize, verifier, theContext);
            }
            if (followRedirects != null) {
                engine.setFollowRedirects(followRedirects);
            }
            return engine;
        }
    }


    /**
     * Create'http challenge' authentication flow
     *
     * @param adminClient
     * @param realmName
     * @return ID of the newly created 'http challenge' flow
     */
    public static String createHttpChallengeFlow(Keycloak adminClient, String realmName) {
        AuthenticationManagementResource authResource = adminClient.realm(realmName).flows();

        AuthenticationFlowRepresentation challengeFlow = new AuthenticationFlowRepresentation();
        challengeFlow.setAlias(FlowOverrideHttpChallengeTest.HTTP_CHALLENGE_FLOW);
        challengeFlow.setDescription("An authentication flow based on challenge-response HTTP Authentication Schemes");
        challengeFlow.setProviderId("basic-flow");
        challengeFlow.setTopLevel(true);
        challengeFlow.setBuiltIn(false);
        Response response = authResource.createFlow(challengeFlow);
        String challengeFlowId = TestsHelper.getCreatedId(response);
        response.close();

        AuthenticationExecutionRepresentation execution = new AuthenticationExecutionRepresentation();
        execution.setParentFlow(challengeFlowId);
        execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED.toString());
        execution.setAuthenticator("no-cookie-redirect");
        execution.setPriority(10);
        execution.setAuthenticatorFlow(false);
        response = authResource.addExecution(execution);
        response.close();

        String childFlowId = addFlowToParent(adminClient, realmName, FlowOverrideHttpChallengeTest.HTTP_CHALLENGE_FLOW, "Authentication Options");

        execution = new AuthenticationExecutionRepresentation();
        execution.setParentFlow(childFlowId);
        execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED.toString());
        execution.setAuthenticator("basic-auth");
        execution.setPriority(10);
        execution.setAuthenticatorFlow(false);
        response = authResource.addExecution(execution);
        response.close();

        execution = new AuthenticationExecutionRepresentation();
        execution.setParentFlow(childFlowId);
        execution.setRequirement(AuthenticationExecutionModel.Requirement.DISABLED.toString());
        execution.setAuthenticator("basic-auth-otp");
        execution.setPriority(20);
        execution.setAuthenticatorFlow(false);
        response = authResource.addExecution(execution);
        response.close();

        execution = new AuthenticationExecutionRepresentation();
        execution.setParentFlow(childFlowId);
        execution.setRequirement(AuthenticationExecutionModel.Requirement.DISABLED.toString());
        execution.setAuthenticator("auth-spnego");
        execution.setPriority(30);
        execution.setAuthenticatorFlow(false);
        response = authResource.addExecution(execution);
        response.close();
        return challengeFlowId;
    }

    private static String addFlowToParent(Keycloak adminClient, String realmName, String parentAlias, String childAlias) {
        Map<String, String> data = new HashMap<>();
        data.put("alias", childAlias);
        data.put("type", "basic-flow");
        data.put("description", childAlias + " flow");
        AuthenticationManagementResource authMgmtResource = adminClient.realm(realmName).flows();
        authMgmtResource.addExecutionFlow(parentAlias, data);

        // Find our execution
        List<AuthenticationExecutionInfoRepresentation> infos = authMgmtResource.getExecutions(parentAlias);
        AuthenticationExecutionInfoRepresentation ourInfo = infos.stream().filter(info -> childAlias.equals(info.getDisplayName())).findFirst().get();

        // set execution to REQUIRED
        ourInfo.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED.toString());
        authMgmtResource.updateExecutions(parentAlias, ourInfo);

        return ourInfo.getFlowId();
    }
}
