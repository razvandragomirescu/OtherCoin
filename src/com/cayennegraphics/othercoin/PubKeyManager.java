package com.cayennegraphics.othercoin;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.util.Log;

// Many thanks to Nikolay Elenkov for feedback.
// Shamelessly based upon Moxie's example code (AOSP/Google did not offer code)
// http://www.thoughtcrime.org/blog/authenticity-is-broken-in-ssl-but-your-app-ha/
public final class PubKeyManager implements X509TrustManager {

	// DER encoded public key
	private static String PUB_KEY = "30820122300d06092a864886f70d01010105000382010f003082010a02820101009bdc3fea14e470b0ce1079c771148b2a46fb38efdc5c11e118d7cd1cc310523d828bfb5cc1d1f4d51a8722757985a3628a8e2ce8fa72118a0fe1f0cc04212e362fbc4bbf944c6f553c1a3a6c9c1a24623fef223f26b19f96924d04041c26efff9eeeba8b285a2dbe09f91e1a64b3e5190d066ebe188b6d17a0685f0a0a32563f58c60726109fe00ffb69a5d8bf3a36361ae5fc8b6b6806dd1d007972267e28d9fd84fd4917d656efb8f32b7d4990b0dc3e57258f8ff3e359dfb4b8bf42b35e685a9802cbba20b938319aa6c661fe74a400a8d21f121f0c5200d8ef2e73aaaae9d0409a092c13e0c6e8f6594de4be3a7f617246b26f5787fbc2775bbb06ac6dab0203010001";


	public void checkServerTrusted(X509Certificate[] chain, String authType)
			throws CertificateException {


		
		
		assert (chain != null);
		if (chain == null) {
			throw new IllegalArgumentException(
					"checkServerTrusted: X509Certificate array is null");
		}

		assert (chain.length > 0);
		if (!(chain.length > 0)) {
			throw new IllegalArgumentException(
					"checkServerTrusted: X509Certificate is empty");
		}

		//assert (null != authType && authType.equalsIgnoreCase("RSA"));
		//if (!(null != authType && authType.equalsIgnoreCase("RSA"))) {
		//	throw new CertificateException(
		//			"checkServerTrusted: AuthType is not RSA, it is "+authType);
		//}
		
		RSAPublicKey pubkey = (RSAPublicKey) chain[0].getPublicKey();
		String encoded = new BigInteger(1 /* positive */, pubkey.getEncoded())
				.toString(16);
		//Log.i("PkiApplet", "Encoded key is "+encoded);

		// Pin it!
		final boolean expected = PUB_KEY.equalsIgnoreCase(encoded);
		assert(expected);
		if (!expected) {
			throw new CertificateException(
					"checkServerTrusted: Expected public key: " + PUB_KEY
							+ ", got public key:" + encoded);
		}
	}

	public void checkClientTrusted(X509Certificate[] xcs, String string) {

	}

	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}
}
