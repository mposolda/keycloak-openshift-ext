/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.test;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.authentication.authenticators.client.ClientIdAndSecretAuthenticator;
import org.keycloak.common.util.Base64Url;
import org.keycloak.crypto.Algorithm;
import org.keycloak.events.Details;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.GroupMembershipMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.openshift.OpenShiftTokenReviewRequestRepresentation;
import org.keycloak.protocol.openshift.OpenShiftTokenReviewResponseRepresentation;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.keycloak.test.TestsHelper.keycloakBaseUrl;
import static org.keycloak.utils.MediaType.APPLICATION_JSON;

public class OpenShiftTokenReviewEndpointTest extends MyTest {

    @Override
    public void configureTestRealm(RealmRepresentation testRealm) {
        ClientRepresentation client = testRealm.getClients().stream().filter(r -> r.getClientId().equals("direct-grant")).findFirst().get();

        List<ProtocolMapperRepresentation> mappers = new LinkedList<>();
        ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("groups");
        mapper.setProtocolMapper(GroupMembershipMapper.PROVIDER_ID);
        mapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        Map<String, String> config = new HashMap<>();
        config.put("full.path", "false");
        config.put(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME, "groups");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN, "true");
        config.put(OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN, "true");
        mapper.setConfig(config);
        mappers.add(mapper);

        client.setProtocolMappers(mappers);
        client.setPublicClient(false);
        client.setClientAuthenticatorType(ClientIdAndSecretAuthenticator.PROVIDER_ID);

        testRealm.getUsers().add(
                UserBuilder.create()
                        .username("groups-user")
                        .password("password")
                        .addGroups("/topGroup", "/topGroup/level2group")
                        .role("account", "view-profile")
                        .build());

        testRealm.getUsers().add(
                UserBuilder.create()
                        .username("empty-audience")
                        .password("password")
                        .build());
    }

    @Test
    public void basicTest() {
        Review r = new Review().invoke();

        String userId = testRealm().users().search(r.username).get(0).getId();

        OpenShiftTokenReviewResponseRepresentation.User user = r.response.getStatus().getUser();

        assertEquals(userId, user.getUid());
        assertEquals("test-user@localhost", user.getUsername());
        assertNotNull(user.getExtra());

        r.assertScope("openid", "email", "profile");
    }

    @Test
    public void longExpiration() {
        ClientResource client = TestUtil.findClientByClientId(ADMIN_CLIENT.realm("test"), "direct-grant");
        ClientRepresentation clientRep = client.toRepresentation();

        try {
            clientRep.getAttributes().put(OIDCConfigAttributes.ACCESS_TOKEN_LIFESPAN, "-1");
            client.update(clientRep);

            // Set time offset just before SSO idle, to get session last refresh updated
            // TODO: Ignore tests using time offset for now. Figure how to test timeOffset if needed
            //setTimeOffset(1500);

            Review review = new Review();

            review.invoke().assertSuccess();

            // Bump last refresh updated again

            //setTimeOffset(3000);

            review.invoke().assertSuccess();

            // And, again

            //setTimeOffset(4500);

            // Token should still be valid as session last refresh should have been updated

            review.invoke().assertSuccess();
        } finally {
            clientRep.getAttributes().put(OIDCConfigAttributes.ACCESS_TOKEN_LIFESPAN, null);
            client.update(clientRep);
        }
    }

    @Test
    public void hs256() {
        RealmResource realm = ADMIN_CLIENT.realm("test");
        RealmRepresentation rep = realm.toRepresentation();

        try {
            rep.setDefaultSignatureAlgorithm(Algorithm.HS256);
            realm.update(rep);

            Review r = new Review().algorithm(Algorithm.HS256).invoke()
                    .assertSuccess();

            String userId = testRealm().users().search(r.username).get(0).getId();

            OpenShiftTokenReviewResponseRepresentation.User user = r.response.getStatus().getUser();

            assertEquals(userId, user.getUid());
            assertEquals("test-user@localhost", user.getUsername());
            assertNotNull(user.getExtra());

            r.assertScope("openid", "email", "profile");
        } finally {
            rep.setDefaultSignatureAlgorithm(null);
            realm.update(rep);
        }
    }

    @Test
    public void groups() {
        new Review().username("groups-user")
                .invoke()
                .assertSuccess().assertGroups("topGroup", "level2group");
    }

    @Test
    public void customScopes() {
        ClientScopeRepresentation clientScope = new ClientScopeRepresentation();
        clientScope.setProtocol("openid-connect");
        clientScope.setName("user:info");

        String id;
        try (Response r = testRealm().clientScopes().create(clientScope)) {
            id = TestsHelper.getCreatedId(r);
        }

        ClientRepresentation clientRep = testRealm().clients().findByClientId("direct-grant").get(0);

        testRealm().clients().get(clientRep.getId()).addOptionalClientScope(id);

        try {
            //oauth.scope("user:info");
            new Review()
                    .scope("user:info")
                    .invoke()
                    .assertSuccess().assertScope("openid", "user:info", "profile", "email");
        } finally {
            testRealm().clients().get(clientRep.getId()).removeOptionalClientScope(id);
        }
    }


    @Test
    public void invalidPublicKey() {
        new Review()
                .runAfterTokenRequest(i -> {
                    String header = i.token.split("\\.")[0];
                    String s = new String(Base64Url.decode(header));
                    s = s.replace(",\"kid\" : \"", ",\"kid\" : \"x");
                    String newHeader = Base64Url.encode(s.getBytes());
                    i.token = i.token.replaceFirst(header, newHeader);
                })
                .invoke()
                .assertError(401);
    }

    @Test
    public void noUserSession() {
        new Review()
                .runAfterTokenRequest(i -> {
                    String userId = testRealm().users().search(i.username).get(0).getId();
                    testRealm().users().get(userId).logout();
                })
                .invoke()
                .assertError(401);
    }

    @Test
    public void invalidTokenSignature() {
        new Review()
                .runAfterTokenRequest(i -> i.token += "x")
                .invoke()
                .assertError(401);
    }

    @Test
    public void realmDisabled() {
        RealmRepresentation r = testRealm().toRepresentation();
        try {
            new Review().runAfterTokenRequest(i -> {
                r.setEnabled(false);
                testRealm().update(r);
            }).invoke().assertError(401);


        } finally {
            r.setEnabled(true);
            testRealm().update(r);
        }
    }

    @Test
    public void publicClientNotPermitted() {
        ClientRepresentation clientRep = testRealm().clients().findByClientId("direct-grant").get(0);
        clientRep.setPublicClient(true);
        testRealm().clients().get(clientRep.getId()).update(clientRep);
        try {
            new Review()
                    .clientAuthMethod(ClientIdAndSecretAuthenticator.PROVIDER_ID)
                    .invoke().assertError(401);
        } finally {
            clientRep.setPublicClient(false);
            clientRep.setSecret("password");
            testRealm().clients().get(clientRep.getId()).update(clientRep);
        }
    }

    @Test
    public void checkPropertyValidation() throws IOException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            String url = keycloakBaseUrl + "/realms/" + "test" + "/protocol/openid-connect/ext/openshift-token-review/";

            HttpPost post = new HttpPost(url);
            post.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
            post.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
            post.setEntity(new StringEntity("{\"<img src=alert(1)>\":1}"));

            try (CloseableHttpResponse response = client.execute(post)) {
                Header header = response.getFirstHeader("Content-Type");
                assertThat(header, notNullValue());

                // Verify the Content-Type is not text/html
                assertThat(Arrays.stream(header.getElements())
                        .map(HeaderElement::getName)
                        .filter(Objects::nonNull)
                        .anyMatch(f -> f.equals(APPLICATION_JSON)), is(true));

                // OpenShiftTokenReviewRequestRepresentation ignore unknown attributes and is returned default representation
                assertThat(EntityUtils.toString(response.getEntity()).contains("Unrecognized field \\\"<img src=alert(1)>\\\""), is(false));
            }
        }
    }

    private class Review {

        private String realm = "test";
        private String clientId = "direct-grant";
        private String username = "test-user@localhost";
        private String password = "password";

        private String scope;

        private String algorithm = Algorithm.RS256;
        private InvokeRunnable runAfterTokenRequest;

        private String token;
        private String clientAuthMethod = "testsuite-client-dummy";
        private int responseStatus;
        private OpenShiftTokenReviewResponseRepresentation response;

        public Review username(String username) {
            this.username = username;
            return this;
        }

        public Review algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Review clientAuthMethod(String clientAuthMethod) {
            this.clientAuthMethod = clientAuthMethod;
            return this;
        }

        public Review scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Review runAfterTokenRequest(InvokeRunnable runnable) {
            this.runAfterTokenRequest = runnable;
            return this;
        }

        public Review invoke() {
            try {
                if (token == null) {
                    TestUtil.AccessTokenResponse accessTokenResponse = TestUtil.doGrantAccessTokenRequest("test", username, password, null,
                            "direct-grant", "password", scope);

                    token = accessTokenResponse.getAccessToken();
                }

                assertEquals(algorithm, new JWSInput(token).getHeader().getAlgorithm().name());

                if (runAfterTokenRequest != null) {
                    runAfterTokenRequest.run(this);
                }

                try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
                    String url = keycloakBaseUrl + "/realms/" + realm + "/protocol/openid-connect/ext/openshift-token-review/" + clientId;

                    OpenShiftTokenReviewRequestRepresentation request = new OpenShiftTokenReviewRequestRepresentation();
                    OpenShiftTokenReviewRequestRepresentation.Spec spec = new OpenShiftTokenReviewRequestRepresentation.Spec();
                    spec.setToken(token);
                    spec.setAudiences(new String[]{"account"});
                    request.setSpec(spec);

                    HttpPost post = new HttpPost(url);
                    post.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
                    post.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
                    post.setEntity(new StringEntity(JsonSerialization.writeValueAsString(request)));

                    TestUtil.addAuth(post, clientId, "password");

                    try (CloseableHttpResponse resp = client.execute(post)) {
                        responseStatus = resp.getStatusLine().getStatusCode();
                        response = JsonSerialization.readValue(resp.getEntity().getContent(), OpenShiftTokenReviewResponseRepresentation.class);
                    }

                    assertEquals("authentication.k8s.io/v1beta1", response.getApiVersion());
                    assertEquals("TokenReview", response.getKind());
                }
                return this;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Review assertSuccess() {
            assertEquals(200, responseStatus);
            assertTrue(response.getStatus().isAuthenticated());
            assertNotNull(response.getStatus().getUser());
            return this;
        }

        private Review assertError(int expectedStatus) {
            assertEquals(expectedStatus, responseStatus);
            assertFalse(response.getStatus().isAuthenticated());
            assertNull(response.getStatus().getUser());
            return this;
        }

        private void assertScope(String... expectedScope) {
            List<String> actualScopes = Arrays.asList(response.getStatus().getUser().getExtra().getScopes());
            assertEquals(expectedScope.length, actualScopes.size());
            assertThat(actualScopes, containsInAnyOrder(expectedScope));
        }

        private void assertEmptyScope() {
            assertNull(response.getStatus().getUser().getExtra());
        }

        private void assertGroups(String... expectedGroups) {
            List<String> actualGroups = new LinkedList<>(response.getStatus().getUser().getGroups());
            assertEquals(expectedGroups.length, actualGroups.size());
            assertThat(actualGroups, containsInAnyOrder(expectedGroups));
        }

    }

    private interface InvokeRunnable {
        void run(Review i);
    }

    protected RealmResource testRealm() {
        return ADMIN_CLIENT.realm(TEST_REALM_NAME);
    }

}
