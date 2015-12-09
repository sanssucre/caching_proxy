package ca.sanssucre.proxy;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * By: Alain Gaeremynck(alain.gaeremynck@ypg.com) (@sanssucre)
 * On: 12-05-16 / 2:42 PM
 * TODO : add Unit Tests AND javadoc for this class
 */
public class NonSecureHttpClient extends DefaultHttpClient {

    public NonSecureHttpClient(ClientConnectionManager conman, HttpParams params) {
        super(conman, params);
    }

    public static HttpClient getInstance() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("SSL");

            // set up a TrustManager that trusts everything
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    //System.out.println("getAcceptedIssuers =============");
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    //System.out.println("checkClientTrusted =============");
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    //System.out.println("checkServerTrusted =============");
                }
            }}, new SecureRandom());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        SSLSocketFactory sf = new SSLSocketFactory(sslContext,SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        Scheme httpsScheme = new Scheme("https", sf, 443);
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(httpsScheme);

        HttpParams params = new BasicHttpParams();
        ClientConnectionManager cm = new SingleClientConnManager(params, schemeRegistry);
        return new NonSecureHttpClient(cm, params);
    }
}
