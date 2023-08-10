package ch.vilki.jfxldap.backend;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;

public class MySSLSocketFactory extends SocketFactory {
    static Logger logger = LogManager.getLogger(MySSLSocketFactory.class);

    private static final AtomicReference<MySSLSocketFactory> defaultFactory = new AtomicReference<>();
    private SSLSocketFactory sf;
    private static Config _config;

    public static void set_config(Config config) {
        if(config == null) return;
        MySSLSocketFactory._config = config;
    }
    public MySSLSocketFactory() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        KeyStore keyStore =  _config.get_keyStore();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        sf = ctx.getSocketFactory();
    }

    public static SocketFactory getDefault() {
        final MySSLSocketFactory value = defaultFactory.get();
        if (value == null) {
            try {
                defaultFactory.compareAndSet(null, new MySSLSocketFactory());
            } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
                logger.error("Exception occured during creation of socket factory",e);
                return null;
            }
            return defaultFactory.get();
        }
        return value;
    }

    @Override
    public Socket createSocket(final String s, final int i) throws IOException {
        return sf.createSocket(s, i);
    }

    @Override
    public Socket createSocket(final String s, final int i, final InetAddress inetAddress, final int i1) throws IOException {
        return sf.createSocket(s, i, inetAddress, i1);
    }

    @Override
    public Socket createSocket(final InetAddress inetAddress, final int i) throws IOException {
        return sf.createSocket(inetAddress, i);
    }

    @Override
    public Socket createSocket(final InetAddress inetAddress, final int i, final InetAddress inetAddress1, final int i1) throws IOException {
        return sf.createSocket(inetAddress, i, inetAddress1, i1);
    }
}
