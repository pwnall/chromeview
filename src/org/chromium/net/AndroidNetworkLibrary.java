// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.security.KeyChain;
import android.util.Log;

import org.chromium.base.CalledByNative;
import org.chromium.base.CalledByNativeUnchecked;
import org.chromium.net.CertVerifyResultAndroid;
import org.chromium.net.CertificateMimeType;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLConnection;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Enumeration;

/**
 * This class implements net utilities required by the net component.
 */
class AndroidNetworkLibrary {

    private static final String TAG = AndroidNetworkLibrary.class.getName();

    /**
     * Stores the key pair through the CertInstaller activity.
     * @param context: current application context.
     * @param public_key: The public key bytes as DER-encoded SubjectPublicKeyInfo (X.509)
     * @param private_key: The private key as DER-encoded PrivateKeyInfo (PKCS#8).
     * @return: true on success, false on failure.
     *
     * Note that failure means that the function could not launch the CertInstaller
     * activity. Whether the keys are valid or properly installed will be indicated
     * by the CertInstaller UI itself.
     */
    @CalledByNative
    static public boolean storeKeyPair(Context context, byte[] public_key, byte[] private_key) {
        // TODO(digit): Use KeyChain official extra values to pass the public and private
        // keys when they're available. The "KEY" and "PKEY" hard-coded constants were taken
        // from the platform sources, since there are no official KeyChain.EXTRA_XXX definitions
        // for them. b/5859651
        try {
            Intent intent = KeyChain.createInstallIntent();
            intent.putExtra("PKEY", private_key);
            intent.putExtra("KEY", public_key);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "could not store key pair: " + e);
        }
        return false;
    }

    /**
      * Adds a cryptographic file (User certificate, a CA certificate or
      * PKCS#12 keychain) through the system's CertInstaller activity.
      *
      * @param context: current application context.
      * @param cert_type: cryptographic file type. E.g. CertificateMimeType.X509_USER_CERT
      * @param data: certificate/keychain data bytes.
      * @return true on success, false on failure.
      *
      * Note that failure only indicates that the function couldn't launch the
      * CertInstaller activity, not that the certificate/keychain was properly
      * installed to the keystore.
      */
    @CalledByNative
    static public boolean storeCertificate(Context context, int cert_type, byte[] data) {
        try {
            Intent intent = KeyChain.createInstallIntent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            switch (cert_type) {
              case CertificateMimeType.X509_USER_CERT:
              case CertificateMimeType.X509_CA_CERT:
                intent.putExtra(KeyChain.EXTRA_CERTIFICATE, data);
                break;

              case CertificateMimeType.PKCS12_ARCHIVE:
                intent.putExtra(KeyChain.EXTRA_PKCS12, data);
                break;

              default:
                Log.w(TAG, "invalid certificate type: " + cert_type);
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "could not store crypto file: " + e);
        }
        return false;
    }

    /**
     * @return the mime type (if any) that is associated with the file
     *         extension. Returns null if no corresponding mime type exists.
     */
    @CalledByNative
    static public String getMimeTypeFromExtension(String extension) {
        return URLConnection.guessContentTypeFromName("foo." + extension);
    }

    /**
     * @return true if it can determine that only loopback addresses are
     *         configured. i.e. if only 127.0.0.1 and ::1 are routable. Also
     *         returns false if it cannot determine this.
     */
    @CalledByNative
    static public boolean haveOnlyLoopbackAddresses() {
        Enumeration<NetworkInterface> list = null;
        try {
            list = NetworkInterface.getNetworkInterfaces();
            if (list == null) return false;
        } catch (Exception e) {
            Log.w(TAG, "could not get network interfaces: " + e);
            return false;
        }

        while (list.hasMoreElements()) {
            NetworkInterface netIf = list.nextElement();
            try {
                if (netIf.isUp() && !netIf.isLoopback()) return false;
            } catch (SocketException e) {
                continue;
            }
        }
        return true;
    }

    /**
     * @return the network interfaces list (if any) string. The items in
     *         the list string are delimited by a semicolon ";", each item
     *         is a network interface name and address pair and formatted
     *         as "name,address". e.g.
     *           eth0,10.0.0.2;eth0,fe80::5054:ff:fe12:3456
     *         represents a network list string which containts two items.
     */
    @CalledByNative
    static public String getNetworkList() {
        Enumeration<NetworkInterface> list = null;
        try {
            list = NetworkInterface.getNetworkInterfaces();
            if (list == null) return "";
        } catch (SocketException e) {
            Log.w(TAG, "Unable to get network interfaces: " + e);
            return "";
        }

        StringBuilder result = new StringBuilder();
        while (list.hasMoreElements()) {
            NetworkInterface netIf = list.nextElement();
            try {
                // Skip loopback interfaces, and ones which are down.
                if (!netIf.isUp() || netIf.isLoopback())
                    continue;
                Enumeration<InetAddress> addressList = netIf.getInetAddresses();
                while (addressList.hasMoreElements()) {
                    InetAddress address = addressList.nextElement();
                    // Skip loopback addresses configured on non-loopback interfaces.
                    if (address.isLoopbackAddress())
                        continue;
                    StringBuilder addressString = new StringBuilder();
                    addressString.append(netIf.getName());
                    addressString.append(",");

                    String ipAddress = address.getHostAddress();
                    if (address instanceof Inet6Address && ipAddress.contains("%")) {
                        ipAddress = ipAddress.substring(0, ipAddress.lastIndexOf("%"));
                    }
                    addressString.append(ipAddress);

                    if (result.length() != 0)
                        result.append(";");
                    result.append(addressString.toString());
                }
            } catch (SocketException e) {
                continue;
            }
        }
        return result.toString();
    }

    /**
     * Validate the server's certificate chain is trusted.
     *
     * @param certChain The ASN.1 DER encoded bytes for certificates.
     * @param authType The key exchange algorithm name (e.g. RSA)
     * @return Android certificate verification result code.
     */
    @CalledByNative
    public static int verifyServerCertificates(byte[][] certChain, String authType) {
        try {
            return X509Util.verifyServerCertificates(certChain, authType);
        } catch (KeyStoreException e) {
            return CertVerifyResultAndroid.VERIFY_FAILED;
        } catch (NoSuchAlgorithmException e) {
            return CertVerifyResultAndroid.VERIFY_FAILED;
        }
    }

    /**
     * Adds a test root certificate to the local trust store.
     * @param rootCert DER encoded bytes of the certificate.
     */
    @CalledByNativeUnchecked
    public static void addTestRootCertificate(byte[] rootCert) throws CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        X509Util.addTestRootCertificate(rootCert);
    }

    /**
     * Removes all test root certificates added by |addTestRootCertificate| calls from the local
     * trust store.
     */
    @CalledByNativeUnchecked
    public static void clearTestRootCertificates() throws NoSuchAlgorithmException,
            CertificateException, KeyStoreException {
        X509Util.clearTestRootCertificates();
    }
}
