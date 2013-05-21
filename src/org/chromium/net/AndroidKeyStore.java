// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.net;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.interfaces.DSAKey;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECParameterSpec;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;
import org.chromium.net.PrivateKeyType;;

@JNINamespace("net::android")
public class AndroidKeyStore {

    private static final String TAG = "AndroidKeyStore";

    ////////////////////////////////////////////////////////////////////
    //
    // Message signing support.

    /**
     * Returns the public modulus of a given RSA private key as a byte
     * buffer.
     * This can be used by native code to convert the modulus into
     * an OpenSSL BIGNUM object. Required to craft a custom native RSA
     * object where RSA_size() works as expected.
     *
     * @param key A PrivateKey instance, must implement RSAKey.
     * @return A byte buffer corresponding to the modulus. This is
     * big-endian representation of a BigInteger.
     */
    @CalledByNative
    public static byte[] getRSAKeyModulus(PrivateKey key) {
        if (key instanceof RSAKey) {
            return ((RSAKey) key).getModulus().toByteArray();
        } else {
            Log.w(TAG, "Not a RSAKey instance!");
            return null;
        }
    }

    /**
     * Returns the 'Q' parameter of a given DSA private key as a byte
     * buffer.
     * This can be used by native code to convert it into an OpenSSL BIGNUM
     * object where DSA_size() works as expected.
     *
     * @param key A PrivateKey instance. Must implement DSAKey.
     * @return A byte buffer corresponding to the Q parameter. This is
     * a big-endian representation of a BigInteger.
     */
    @CalledByNative
    public static byte[] getDSAKeyParamQ(PrivateKey key) {
        if (key instanceof DSAKey) {
            DSAParams params = ((DSAKey) key).getParams();
            return params.getQ().toByteArray();
        } else {
            Log.w(TAG, "Not a DSAKey instance!");
            return null;
        }
    }

    /**
     * Returns the 'order' parameter of a given ECDSA private key as a
     * a byte buffer.
     * @param key A PrivateKey instance. Must implement ECKey.
     * @return A byte buffer corresponding to the 'order' parameter.
     * This is a big-endian representation of a BigInteger.
     */
    @CalledByNative
    public static byte[] getECKeyOrder(PrivateKey key) {
        if (key instanceof ECKey) {
            ECParameterSpec params = ((ECKey) key).getParams();
            return params.getOrder().toByteArray();
        } else {
            Log.w(TAG, "Not an ECKey instance!");
            return null;
        }
    }

    /**
     * Returns the encoded data corresponding to a given PrivateKey.
     * Note that this will fail for platform keys on Android 4.0.4
     * and higher. It can be used on 4.0.3 and older platforms to
     * route around the platform bug described below.
     * @param key A PrivateKey instance
     * @return encoded key as PKCS#8 byte array, can be null.
     */
    @CalledByNative
    public static byte[] getPrivateKeyEncodedBytes(PrivateKey key) {
        return key.getEncoded();
    }

    /**
     * Sign a given message with a given PrivateKey object. This method
     * shall only be used to implement signing in the context of SSL
     * client certificate support.
     *
     * The message will actually be a hash, computed and padded by OpenSSL,
     * itself, depending on the type of the key. The result should match
     * exactly what the vanilla implementations of the following OpenSSL
     * function calls do:
     *
     *  - For a RSA private key, this should be equivalent to calling
     *    RSA_sign(NDI_md5_sha1,....), i.e. it must generate a raw RSA
     *    signature. The message must a combined, 36-byte MD5+SHA1 message
     *    digest padded to the length of the modulus using PKCS#1 padding.
     *
     *  - For a DSA and ECDSA private keys, this should be equivalent to
     *    calling DSA_sign(0,...) and ECDSA_sign(0,...) respectively. The
     *    message must be a 20-byte SHA1 hash and the function shall
     *    compute a direct DSA/ECDSA signature for it.
     *
     * @param privateKey The PrivateKey handle.
     * @param message The message to sign.
     * @return signature as a byte buffer.
     *
     * Important: Due to a platform bug, this function will always fail on
     *            Android < 4.2 for RSA PrivateKey objects. See the
     *            getOpenSSLHandleForPrivateKey() below for work-around.
     */
    @CalledByNative
    public static byte[] rawSignDigestWithPrivateKey(PrivateKey privateKey,
                                                     byte[] message) {
        // Get the Signature for this key.
        Signature signature = null;
        // Hint: Algorithm names come from:
        // http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html
        try {
            if (privateKey instanceof RSAPrivateKey) {
                // IMPORTANT: Due to a platform bug, this will throw NoSuchAlgorithmException
                // on Android 4.0.x and 4.1.x. Fixed in 4.2 and higher.
                // See https://android-review.googlesource.com/#/c/40352/
                signature = Signature.getInstance("NONEwithRSA");
            } else if (privateKey instanceof DSAPrivateKey) {
                signature = Signature.getInstance("NONEwithDSA");
            } else if (privateKey instanceof ECPrivateKey) {
                signature = Signature.getInstance("NONEwithECDSA");
            }
        } catch (NoSuchAlgorithmException e) {
            ;
        }

        if (signature == null) {
            Log.e(TAG, "Unsupported private key algorithm: " + privateKey.getAlgorithm());
            return null;
        }

        // Sign the message.
        try {
            signature.initSign(privateKey);
            signature.update(message);
            return signature.sign();
        } catch (Exception e) {
            Log.e(TAG, "Exception while signing message with " + privateKey.getAlgorithm() +
                        " private key: " + e);
            return null;
        }
    }

    /**
     * Return the type of a given PrivateKey object. This is an integer
     * that maps to one of the values defined by org.chromium.net.PrivateKeyType,
     * which is itself auto-generated from net/android/private_key_type_list.h
     * @param privateKey The PrivateKey handle
     * @return key type, or PrivateKeyType.INVALID if unknown.
     */
    @CalledByNative
    public static int getPrivateKeyType(PrivateKey privateKey) {
        if (privateKey instanceof RSAPrivateKey)
            return PrivateKeyType.RSA;
        if (privateKey instanceof DSAPrivateKey)
            return PrivateKeyType.DSA;
        if (privateKey instanceof ECPrivateKey)
            return PrivateKeyType.ECDSA;
        else
            return PrivateKeyType.INVALID;
    }

    /**
     * Return the system EVP_PKEY handle corresponding to a given PrivateKey
     * object, obtained through reflection.
     *
     * This shall only be used when the "NONEwithRSA" signature is not
     * available, as described in rawSignDigestWithPrivateKey(). I.e.
     * never use this on Android 4.2 or higher.
     *
     * This can only work in Android 4.0.4 and higher, for older versions
     * of the platform (e.g. 4.0.3), there is no system OpenSSL EVP_PKEY,
     * but the private key contents can be retrieved directly with
     * the getEncoded() method.
     *
     * This assumes that the target device uses a vanilla AOSP
     * implementation of its java.security classes, which is also
     * based on OpenSSL (fortunately, no OEM has apperently changed to
     * a different implementation, according to the Android team).
     *
     * Note that the object returned was created with the platform version
     * of OpenSSL, and _not_ the one that comes with Chromium. Whether the
     * object can be used safely with the Chromium OpenSSL library depends
     * on differences between their actual ABI / implementation details.
     *
     * To better understand what's going on below, please refer to the
     * following source files in the Android 4.0.4 and 4.1 source trees:
     * libcore/luni/src/main/java/org/apache/harmony/xnet/provider/jsse/OpenSSLRSAPrivateKey.java
     * libcore/luni/src/main/native/org_apache_harmony_xnet_provider_jsse_NativeCrypto.cpp
     *
     * @param privateKey The PrivateKey handle.
     * @return The EVP_PKEY handle, as a 32-bit integer (0 if not available)
     */
    @CalledByNative
    public static int getOpenSSLHandleForPrivateKey(PrivateKey privateKey) {
        // Sanity checks
        if (privateKey == null) {
            Log.e(TAG, "privateKey == null");
            return 0;
        }
        if (!(privateKey instanceof RSAPrivateKey)) {
            Log.e(TAG, "does not implement RSAPrivateKey");
            return 0;
        }
        // First, check that this is a proper instance of OpenSSLRSAPrivateKey
        // or one of its sub-classes.
        Class<?> superClass;
        try {
            superClass = Class.forName(
                    "org.apache.harmony.xnet.provider.jsse.OpenSSLRSAPrivateKey");
        } catch (Exception e) {
            // This may happen if the target device has a completely different
            // implementation of the java.security APIs, compared to vanilla
            // Android. Highly unlikely, but still possible.
            Log.e(TAG, "Cannot find system OpenSSLRSAPrivateKey class: " + e);
            return 0;
        }
        if (!superClass.isInstance(privateKey)) {
            // This may happen if the PrivateKey was not created by the "AndroidOpenSSL"
            // provider, which should be the default. That could happen if an OEM decided
            // to implement a different default provider. Also highly unlikely.
            Log.e(TAG, "Private key is not an OpenSSLRSAPrivateKey instance, its class name is:" +
                       privateKey.getClass().getCanonicalName());
            return 0;
        }

        try {
            // Use reflection to invoke the 'getOpenSSLKey()' method on
            // the private key. This returns another Java object that wraps
            // a native EVP_PKEY. Note that the method is final, so calling
            // the superclass implementation is ok.
            Method getKey = superClass.getDeclaredMethod("getOpenSSLKey");
            getKey.setAccessible(true);
            Object opensslKey = null;
            try {
                opensslKey = getKey.invoke(privateKey);
            } finally {
                getKey.setAccessible(false);
            }
            if (opensslKey == null) {
                // Bail when detecting OEM "enhancement".
                Log.e(TAG, "getOpenSSLKey() returned null");
                return 0;
            }

            // Use reflection to invoke the 'getPkeyContext' method on the
            // result of the getOpenSSLKey(). This is an 32-bit integer
            // which is the address of an EVP_PKEY object.
            Method getPkeyContext;
            try {
                getPkeyContext = opensslKey.getClass().getDeclaredMethod("getPkeyContext");
            } catch (Exception e) {
                // Bail here too, something really not working as expected.
                Log.e(TAG, "No getPkeyContext() method on OpenSSLKey member:" + e);
                return 0;
            }
            getPkeyContext.setAccessible(true);
            int evp_pkey = 0;
            try {
                evp_pkey = (Integer) getPkeyContext.invoke(opensslKey);
            } finally {
                getPkeyContext.setAccessible(false);
            }
            if (evp_pkey == 0) {
                // The PrivateKey is probably rotten for some reason.
                Log.e(TAG, "getPkeyContext() returned null");
            }
            return evp_pkey;

        } catch (Exception e) {
            Log.e(TAG, "Exception while trying to retrieve system EVP_PKEY handle: " + e);
            return 0;
        }
    }
}
