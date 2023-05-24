package org.keycloak.test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.constants.AdapterConstants;
import org.keycloak.protocol.oidc.OIDCLoginProtocolService;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.util.BasicAuthHelper;
import org.keycloak.util.JsonSerialization;
import org.keycloak.util.TokenUtil;

import static org.keycloak.test.TestsHelper.keycloakBaseUrl;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class TestUtil {

    public static AccessTokenResponse doGrantAccessTokenRequest(String realm, String username, String password, String totp,
                                                         String clientId, String clientSecret, String scope) throws Exception {
        try (CloseableHttpClient client = newCloseableHttpClient()) {
            HttpPost post = new HttpPost(getResourceOwnerPasswordCredentialGrantUrl(realm));

//            if (requestHeaders != null) {
//                for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
//                    post.addHeader(header.getKey(), header.getValue());
//                }
//            }

            List<NameValuePair> parameters = new LinkedList<>();
            parameters.add(new BasicNameValuePair(OAuth2Constants.GRANT_TYPE, OAuth2Constants.PASSWORD));
            parameters.add(new BasicNameValuePair("username", username));
            parameters.add(new BasicNameValuePair("password", password));
            if (totp != null) {
                parameters.add(new BasicNameValuePair("otp", totp));

            }

            addAuth(post, clientId, clientSecret);
//            if (clientSecret != null) {
//                String authorization = BasicAuthHelper.createHeader(clientId, clientSecret);
//                post.setHeader("Authorization", authorization);
//            } else {
//                parameters.add(new BasicNameValuePair("client_id", clientId));
//            }

//            if (origin != null) {
//                post.addHeader("Origin", origin);
//            }
//
//            if (clientSessionState != null) {
//                parameters.add(new BasicNameValuePair(AdapterConstants.CLIENT_SESSION_STATE, clientSessionState));
//            }
//            if (clientSessionHost != null) {
//                parameters.add(new BasicNameValuePair(AdapterConstants.CLIENT_SESSION_HOST, clientSessionHost));
//            }

            String scopeParam = TokenUtil.attachOIDCScope(scope);
            if (scopeParam != null && !scopeParam.isEmpty()) {
                parameters.add(new BasicNameValuePair(OAuth2Constants.SCOPE, scopeParam));
            }

//            if (userAgent != null) {
//                post.addHeader("User-Agent", userAgent);
//            }

//            if (customParameters != null) {
//                customParameters.keySet().stream()
//                        .forEach(paramName -> parameters.add(new BasicNameValuePair(paramName, customParameters.get(paramName))));
//            }

            UrlEncodedFormEntity formEntity;
            try {
                formEntity = new UrlEncodedFormEntity(parameters, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            post.setEntity(formEntity);

            return new AccessTokenResponse(client.execute(post));
        }
    }

    public static void addAuth(HttpPost httpPost, String clientId, String clientSecret) {
        if (clientSecret != null) {
            String authorization = BasicAuthHelper.createHeader(clientId, clientSecret);
            httpPost.setHeader("Authorization", authorization);
        } else {
            throw new IllegalArgumentException("clientSecret must be non-null");
            //parameters.add(new BasicNameValuePair("client_id", clientId));
        }
    }

    public static String getResourceOwnerPasswordCredentialGrantUrl(String realm) {
        UriBuilder b = OIDCLoginProtocolService.tokenUrl(UriBuilder.fromUri(keycloakBaseUrl));
        return b.build(realm).toString();
    }

    public static CloseableHttpClient newCloseableHttpClient() {
//        if (sslRequired) {
//            String keyStorePath = System.getProperty("client.certificate.keystore");
//            String keyStorePassword = System.getProperty("client.certificate.keystore.passphrase");
//            String trustStorePath = System.getProperty("client.truststore");
//            String trustStorePassword = System.getProperty("client.truststore.passphrase");
//            return newCloseableHttpClientSSL(keyStorePath, keyStorePassword, trustStorePath, trustStorePassword);
//        }
        return HttpClientBuilder.create().build();
    }



    public static class AccessTokenResponse {
        private int statusCode;

        private String idToken;
        private String accessToken;
        private String issuedTokenType;
        private String tokenType;
        private int expiresIn;
        private int refreshExpiresIn;
        private String refreshToken;
        // OIDC Financial API Read Only Profile : scope MUST be returned in the response from Token Endpoint
        private String scope;
        private String sessionState;

        private String error;
        private String errorDescription;

        private Map<String, String> headers;

        private Map<String, Object> otherClaims;

        public AccessTokenResponse(CloseableHttpResponse response) throws Exception {
            try {
                statusCode = response.getStatusLine().getStatusCode();

                headers = new HashMap<>();

                for (Header h : response.getAllHeaders()) {
                    headers.put(h.getName(), h.getValue());
                }

                Header[] contentTypeHeaders = response.getHeaders("Content-Type");
                String contentType = (contentTypeHeaders != null && contentTypeHeaders.length > 0) ? contentTypeHeaders[0].getValue() : null;
                if (!"application/json".equals(contentType)) {
                    Assert.fail("Invalid content type. Status: " + statusCode + ", contentType: " + contentType);
                }

                String s = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> responseJson = JsonSerialization.readValue(s, Map.class);

                if (statusCode == 200) {
                    otherClaims = new HashMap<>();

                    for (Map.Entry<String, Object> entry : responseJson.entrySet()) {
                        switch (entry.getKey()) {
                            case OAuth2Constants.ID_TOKEN:
                                idToken = (String) entry.getValue();
                                break;
                            case OAuth2Constants.ACCESS_TOKEN:
                                accessToken = (String) entry.getValue();
                                break;
                            case OAuth2Constants.ISSUED_TOKEN_TYPE:
                                issuedTokenType = (String) entry.getValue();
                                break;
                            case OAuth2Constants.TOKEN_TYPE:
                                tokenType = (String) entry.getValue();
                                break;
                            case OAuth2Constants.EXPIRES_IN:
                                expiresIn = (Integer) entry.getValue();
                                break;
                            case "refresh_expires_in":
                                refreshExpiresIn = (Integer) entry.getValue();
                                break;
                            case OAuth2Constants.SESSION_STATE:
                                sessionState = (String) entry.getValue();
                                break;
                            case OAuth2Constants.SCOPE:
                                scope = (String) entry.getValue();
                                break;
                            case OAuth2Constants.REFRESH_TOKEN:
                                refreshToken = (String) entry.getValue();
                                break;
                            default:
                                otherClaims.put(entry.getKey(), entry.getValue());
                                break;
                        }
                    }
                } else {
                    error = (String) responseJson.get(OAuth2Constants.ERROR);
                    errorDescription = responseJson.containsKey(OAuth2Constants.ERROR_DESCRIPTION) ? (String) responseJson.get(OAuth2Constants.ERROR_DESCRIPTION) : null;
                }
            } finally {
                response.close();
            }
        }

        public String getIdToken() {
            return idToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getError() {
            return error;
        }

        public String getErrorDescription() {
            return errorDescription;
        }

        public int getExpiresIn() {
            return expiresIn;
        }

        public int getRefreshExpiresIn() {
            return refreshExpiresIn;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String getIssuedTokenType() {
            return issuedTokenType;
        }

        public String getTokenType() {
            return tokenType;
        }

        // OIDC Financial API Read Only Profile : scope MUST be returned in the response from Token Endpoint
        public String getScope() {
            return scope;
        }

        public String getSessionState() {
            return sessionState;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public Map<String, Object> getOtherClaims() {
            return otherClaims;
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
}
