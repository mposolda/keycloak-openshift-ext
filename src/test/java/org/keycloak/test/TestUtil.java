package org.keycloak.test;

import java.util.List;
import org.apache.http.client.methods.HttpPost;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.util.BasicAuthHelper;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class TestUtil {

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
}
