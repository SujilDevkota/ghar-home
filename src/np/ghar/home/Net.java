package np.ghar.home;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Secure connections from old TVs. Android 4.4 can speak TLS 1.2 but leaves it switched off,
 * and doesn't know some of the certificate authorities today's sites use.
 */
final class Net {

    private Net() { }

    /** A connection that asks for TLS 1.2 where the TV supports it. */
    static HttpURLConnection open(String address, boolean tls12) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "GharHome");
        if (tls12 && c instanceof HttpsURLConnection) ((HttpsURLConnection) c).setSSLSocketFactory(new Tls12());
        return c;
    }

    /** Tries a few sites both ways and says what happened: tells whether this TV can fetch app updates by itself. */
    static String probe() {
        String[] sites = {
                "https://api.github.com/repos/yuliskov/SmartTube/releases/latest",
                "https://github.com/",
                "https://objects.githubusercontent.com/",
                "https://release-assets.githubusercontent.com/",
                "https://api.open-meteo.com/v1/forecast?latitude=27.7&longitude=85.3&current=temperature_2m",
                "https://www.youtube.com/",
        };
        StringBuilder b = new StringBuilder();
        for (String site : sites) {
            b.append(site).append('\n');
            for (int way = 0; way < 2; way++) {
                b.append(way == 0 ? "   as the TV is: " : "   with TLS 1.2:  ");
                try {
                    HttpURLConnection c = open(site, way == 1);
                    c.setRequestMethod("GET");
                    b.append("answered ").append(c.getResponseCode()).append('\n');
                    c.disconnect();
                } catch (Throwable t) {
                    Throwable root = t;
                    while (root.getCause() != null) root = root.getCause();
                    b.append(t.getClass().getSimpleName()).append(": ").append(root.getMessage()).append('\n');
                }
            }
        }
        return b.toString();
    }

    /** Sockets with TLS 1.2 (and 1.1) switched on, which Android 4.4 supports but does not use by default. */
    private static final class Tls12 extends SSLSocketFactory {
        private final SSLSocketFactory inner;

        Tls12() throws Exception {
            SSLContext c = SSLContext.getInstance("TLS");
            c.init(null, null, null);
            inner = c.getSocketFactory();
        }

        private static Socket on(Socket s) {
            if (s instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) s;
                java.util.List<String> want = new java.util.ArrayList<String>();
                for (String p : ssl.getSupportedProtocols()) {
                    if (p.equals("TLSv1.2") || p.equals("TLSv1.1") || p.equals("TLSv1.3")) want.add(p);
                }
                if (!want.isEmpty()) ssl.setEnabledProtocols(want.toArray(new String[want.size()]));
            }
            return s;
        }

        @Override public String[] getDefaultCipherSuites() { return inner.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return inner.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String host, int port, boolean close) throws IOException { return on(inner.createSocket(s, host, port, close)); }
        @Override public Socket createSocket(String host, int port) throws IOException { return on(inner.createSocket(host, port)); }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { return on(inner.createSocket(host, port, local, localPort)); }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException { return on(inner.createSocket(host, port)); }
        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException { return on(inner.createSocket(host, port, local, localPort)); }
    }
}
