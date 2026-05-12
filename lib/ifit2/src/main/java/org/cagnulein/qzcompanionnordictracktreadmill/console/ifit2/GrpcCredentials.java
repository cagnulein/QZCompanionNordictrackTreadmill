package org.cagnulein.qzcompanionnordictracktreadmill.console.ifit2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class GrpcCredentials {
    public static final String CLIENT_ID_HEADER_VALUE = "com.ifit.rivendell";

    private static final String[] RESOURCE_PACKAGES = {
            "com.ifit.rivendell",
            "com.ifit.glassos_service"
    };

    private final SSLContext sslContext;

    private GrpcCredentials(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public SSLContext sslContext() {
        return sslContext;
    }

    public static GrpcCredentials load(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        SharedPreferences prefs = context.getSharedPreferences("glassos_cred_v2", Context.MODE_PRIVATE);
        List<Exception> failures = new ArrayList<>();
        for (String packageName : RESOURCE_PACKAGES) {
            try {
                DiscoveredKeys keys = resolveKeys(pm, prefs, packageName);
                Resources resources = pm.getResourcesForApplication(packageName);
                String certPem = readPem(resources, packageName, keys.cert, "CERTIFICATE");
                String keyPem  = readPem(resources, packageName, keys.key,  "PRIVATE KEY");
                String caPem   = readPem(resources, packageName, keys.ca,   "CERTIFICATE");
                return new GrpcCredentials(buildSslContext(certPem, keyPem, caPem));
            } catch (Exception e) {
                failures.add(e);
            }
        }
        Exception e = new Exception("GlassOS credential resources not found in any known package");
        for (Exception f : failures) e.addSuppressed(f);
        throw e;
    }

    private static DiscoveredKeys resolveKeys(PackageManager pm, SharedPreferences prefs,
            String packageName) throws Exception {
        long currentVersion = pm.getPackageInfo(packageName, 0).versionCode;
        long cachedVersion  = prefs.getLong(packageName + "_version", -1);

        if (cachedVersion == currentVersion) {
            String cert = prefs.getString(packageName + "_cert", null);
            String key  = prefs.getString(packageName + "_key",  null);
            String ca   = prefs.getString(packageName + "_ca",   null);
            if (cert != null && key != null && ca != null) return new DiscoveredKeys(cert, key, ca);
        }

        DiscoveredKeys keys = discoverKeys(pm, packageName);
        prefs.edit()
                .putLong(packageName + "_version", currentVersion)
                .putString(packageName + "_cert", keys.cert)
                .putString(packageName + "_key",  keys.key)
                .putString(packageName + "_ca",   keys.ca)
                .apply();
        return keys;
    }

    // Discovers the CA cert, client cert, and matching private key for the gRPC mTLS channel.
    // Enumerates img_icon_* raw resources via the resources.arsc key string pool (works regardless
    // of whether the APK uses full paths like res/raw/img_icon_x or obfuscated paths like res/I6.jpg),
    // then identifies the correct three resources by their cryptographic properties.
    private static DiscoveredKeys discoverKeys(PackageManager pm, String packageName) throws Exception {
        String apkPath = pm.getApplicationInfo(packageName, 0).sourceDir;
        Resources resources = pm.getResourcesForApplication(packageName);

        List<CandidateCert> certCandidates = new ArrayList<>();
        List<CandidateKey>  keyCandidates  = new ArrayList<>();

        byte[] arsc = readArsc(apkPath);
        for (String fullName : extractImgIconNames(arsc)) {
            int id = resources.getIdentifier(fullName, "raw", packageName);
            if (id <= 0) continue;
            String key = fullName.substring("img_icon_".length());
            String content = stripJpegMarkers(
                    new String(readFully(resources.openRawResource(id)), StandardCharsets.UTF_8));

            try {
                String pem = "-----BEGIN CERTIFICATE-----\n" + content + "-----END CERTIFICATE-----\n";
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(bytes(pem));
                certCandidates.add(new CandidateCert(key, cert));
            } catch (Exception ignored) {}

            try {
                String pem = "-----BEGIN PRIVATE KEY-----\n" + content + "-----END PRIVATE KEY-----\n";
                keyCandidates.add(new CandidateKey(key, parsePrivateKey(pem)));
            } catch (Exception ignored) {}
        }

        CandidateCert ca     = findCa(certCandidates, packageName);
        CandidateCert client = findClientCert(certCandidates, ca, packageName);
        CandidateKey  key    = findMatchingKey(keyCandidates, client, packageName);

        return new DiscoveredKeys(client.name, key.name, ca.name);
    }

    private static byte[] readArsc(String apkPath) throws Exception {
        try (ZipFile apk = new ZipFile(apkPath)) {
            ZipEntry entry = apk.getEntry("resources.arsc");
            if (entry == null) throw new Exception("resources.arsc not found in " + apkPath);
            return readFully(apk.getInputStream(entry));
        }
    }

    // Scans the binary resources.arsc for img_icon_* key strings. These are stored as UTF-8 in the
    // package key string pool, so a simple byte scan finds them regardless of obfuscated file paths.
    private static List<String> extractImgIconNames(byte[] arsc) {
        List<String> names = new ArrayList<>();
        byte[] prefix = "img_icon_".getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= arsc.length - prefix.length; i++) {
            for (int j = 0; j < prefix.length; j++) {
                if (arsc[i + j] != prefix[j]) continue outer;
            }
            int end = i + prefix.length;
            while (end < arsc.length) {
                byte b = arsc[end];
                if ((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f')) end++;
                else break;
            }
            String name = new String(arsc, i, end - i, StandardCharsets.UTF_8);
            if (!names.contains(name)) names.add(name);
        }
        return names;
    }

    private static CandidateCert findCa(List<CandidateCert> certs, String pkg) throws Exception {
        for (CandidateCert c : certs)
            if (c.cert.getSubjectX500Principal().equals(c.cert.getIssuerX500Principal())) return c;
        throw new Exception("No self-signed CA certificate found in " + pkg);
    }

    private static CandidateCert findClientCert(List<CandidateCert> certs, CandidateCert ca,
            String pkg) throws Exception {
        CandidateCert first = null;
        for (CandidateCert c : certs) {
            if (c == ca) continue;
            try {
                c.cert.verify(ca.cert.getPublicKey());
                if (CLIENT_ID_HEADER_VALUE.equals(certCn(c.cert))) return c;
                if (first == null) first = c;
            } catch (Exception ignored) {}
        }
        if (first != null) return first;
        throw new Exception("No client certificate signed by CA found in " + pkg);
    }

    private static String certCn(X509Certificate cert) {
        for (String part : cert.getSubjectX500Principal().getName().split(",")) {
            part = part.trim();
            if (part.startsWith("CN=")) return part.substring(3);
        }
        return "";
    }

    private static CandidateKey findMatchingKey(List<CandidateKey> keys, CandidateCert client,
            String pkg) throws Exception {
        if (!(client.cert.getPublicKey() instanceof RSAPublicKey))
            throw new Exception("Client cert is not RSA in " + pkg);
        BigInteger modulus = ((RSAPublicKey) client.cert.getPublicKey()).getModulus();
        for (CandidateKey k : keys)
            if (k.key instanceof RSAPrivateKey && ((RSAPrivateKey) k.key).getModulus().equals(modulus))
                return k;
        throw new Exception("No RSA private key matching client cert found in " + pkg);
    }

    private static String stripJpegMarkers(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !"FFD8".equals(t) && !"FFD9".equals(t)) sb.append(t).append('\n');
        }
        return sb.toString();
    }

    private static byte[] readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static String readPem(Resources resources, String packageName, String key, String type)
            throws Exception {
        int id = resources.getIdentifier("img_icon_" + key, "raw", packageName);
        if (id <= 0) throw new Resources.NotFoundException("img_icon_" + key);

        StringBuilder body = new StringBuilder();
        try (InputStream in = resources.openRawResource(id)) {
            for (String line : new String(readFully(in), StandardCharsets.UTF_8).split("\n")) {
                String t = line.trim();
                if (!t.isEmpty() && !"FFD8".equals(t) && !"FFD9".equals(t)) body.append(t).append('\n');
            }
        }
        return "-----BEGIN " + type + "-----\n" + body + "-----END " + type + "-----\n";
    }

    private static SSLContext buildSslContext(String certPem, String keyPem, String caPem)
            throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate clientCert = cf.generateCertificate(bytes(certPem));
        Certificate caCert     = cf.generateCertificate(bytes(caPem));
        PrivateKey  privateKey = parsePrivateKey(keyPem);

        java.security.KeyStore keyStore = java.security.KeyStore.getInstance(
                java.security.KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, new char[0], new Certificate[]{clientCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        java.security.KeyStore trustStore = java.security.KeyStore.getInstance(
                java.security.KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("glassos-ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static final class DiscoveredKeys {
        final String cert, key, ca;
        DiscoveredKeys(String cert, String key, String ca) { this.cert = cert; this.key = key; this.ca = ca; }
    }

    private static final class CandidateCert {
        final String name;
        final X509Certificate cert;
        CandidateCert(String name, X509Certificate cert) { this.name = name; this.cert = cert; }
    }

    private static final class CandidateKey {
        final String name;
        final PrivateKey key;
        CandidateKey(String name, PrivateKey key) { this.name = name; this.key = key; }
    }
}
