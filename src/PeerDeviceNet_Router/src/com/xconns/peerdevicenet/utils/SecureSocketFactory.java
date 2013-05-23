/*
 * Copyright (C) 2013 Yigong Liu, XCONNS, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xconns.peerdevicenet.utils;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import android.content.Context;
import android.util.Log;

import com.xconns.peerdevicenet.router.R;

public class SecureSocketFactory implements SocketFactory {
	public final static String TAG = "SecureSocketFactory";
	private static volatile SecureSocketFactory instance = null;
	private static volatile Object lock = new Object();
	
	private Context context = null;
	private static final String keystorePassword = "SamQuah";

	// SSL related
	SSLContext sslContext = null;
	TrustManagerFactory trustManagerFactory = null;
	KeyManagerFactory keyManagerFactory = null;

	protected SecureSocketFactory(Context c) {
		context = c;
		initSSLContext();
	}
	
	public static SecureSocketFactory getInstance(Context c) {
		if (instance == null) {
			synchronized (lock) {
				if (instance == null) {
					instance = new SecureSocketFactory(c);
				}
			}
		}
		Log.d(TAG, "SecureSocketFactory instance returned");

		return instance;
	}

	private void initSSLContext() {
		Log.d(TAG, "Creating SSL socket factory");

		InputStream clientTruststoreIs = context.getResources()
				.openRawResource(R.raw.routertruststore);
		KeyStore trustStore = null;
		try {
			trustStore = KeyStore.getInstance("BKS");
			trustStore.load(clientTruststoreIs, keystorePassword.toCharArray());
			clientTruststoreIs.close();

			System.out.println("Loaded peer certificate: " + trustStore.size());
		} catch (KeyStoreException kse) {
			kse.printStackTrace();
		} catch (CertificateException ce) {
			ce.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		// initialize trust manager factory with the read truststore
		try {
			trustManagerFactory = TrustManagerFactory
					.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(trustStore);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyStoreException kse) {
			kse.printStackTrace();
		}

		// setup client certificate

		// load client certificate
		InputStream keyStoreStream = context.getResources().openRawResource(
				R.raw.router);
		KeyStore keyStore = null;

		try {
			keyStore = KeyStore.getInstance("BKS");
			keyStore.load(keyStoreStream, keystorePassword.toCharArray());
			keyStoreStream.close();

			System.out.println("Loaded my certificates: " + keyStore.size());
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException ne) {
			// TODO Auto-generated catch block
			ne.printStackTrace();
		} catch (CertificateException ce) {
			// TODO Auto-generated catch block
			ce.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// initialize key manager factory with the read client certificate
		try {
			keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
					.getDefaultAlgorithm());
			keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
		} catch (KeyStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnrecoverableKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// initialize SSL context
		try {
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(keyManagerFactory.getKeyManagers(),
					trustManagerFactory.getTrustManagers(), null);
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public SSLServerSocket newServerSocket() {
		SSLServerSocketFactory fact = (SSLServerSocketFactory) sslContext.getServerSocketFactory();
		SSLServerSocket sock = null;
		try {
			sock = (SSLServerSocket)fact.createServerSocket();
		} catch(IOException ioe) {
			Log.d(TAG, ioe.getMessage());
		}
		return sock;
	}
	
	public SSLServerSocket newServerSocket(int port) {
		SSLServerSocketFactory fact = (SSLServerSocketFactory) sslContext.getServerSocketFactory();
		SSLServerSocket sock = null;
		try {
			sock = (SSLServerSocket)fact.createServerSocket(port);
		} catch(IOException ioe) {
			Log.d(TAG, ioe.getMessage());
		}
		return sock;
	}
	
	public SSLSocket newClientSocket() {
		SSLSocketFactory fact = (SSLSocketFactory) sslContext.getSocketFactory();
		SSLSocket sock = null;
		try {
			sock = (SSLSocket)fact.createSocket();
		} catch(IOException ioe) {
			Log.d(TAG, ioe.getMessage());
		}
		return sock;
	}
}
