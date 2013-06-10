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

package com.xconns.peerdevicenet.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.utils.PlainSocketFactory;
import com.xconns.peerdevicenet.utils.SecureSocketFactory;
import com.xconns.peerdevicenet.utils.SocketFactory;

public class TCPConnector implements Connector {
	// Debugging
	private static final String TAG = "TCPConnector";

	private SocketFactory sockFactory = null;

	//
	private RouterService mService = null;
	private AcceptThread mAcceptThread;

	public TCPConnector(RouterService srv) {
		mService = srv;
	}

	public void start() {
		if (mService.useSSL) {
			sockFactory = SecureSocketFactory.getInstance(mService);
		} else {
			sockFactory = PlainSocketFactory.getInstance(mService);
		}
		mAcceptThread = new AcceptThread(sockFactory);
		mAcceptThread.start();
	}

	public void restart() {
		if (mAcceptThread != null) {
			mAcceptThread.cancel();
			mAcceptThread = null;
		}
		start();
	}

	public void stop() {
		new Thread(new Runnable() {
			public void run() {
				if (mAcceptThread != null) {
					mAcceptThread.cancel();
					mAcceptThread = null;
				}
			}
		}).start();
	}

	public int getServicePort() {
		return mAcceptThread.getServicePort();
	}

	int getConnMonTime() {
		return mService.getConnMonitorTimeout();
	}

	public void connect(DeviceInfo peerDevice, byte[] token, int timeout) {
		Socket socket = sockFactory.newClientSocket();
		DataOutputStream mOutputStream;
		DataInputStream mInputStream;

		try {
			Log.d(TAG, "Client: open socket - ");
			socket.bind(null);
			InetSocketAddress peerAddr = new InetSocketAddress(peerDevice.addr,
					Integer.parseInt(peerDevice.port));
			socket.connect(peerAddr, timeout);
			if(socket instanceof SSLSocket) {
				((SSLSocket)socket).startHandshake();
			}

			Log.d(TAG, "Client: socket - " + socket.isConnected());
			mOutputStream = new DataOutputStream(socket.getOutputStream());
			mInputStream = new DataInputStream(socket.getInputStream());

			int to = getConnMonTime();
			Log.d(TAG, "Client: start recving from server");
			new TCPConnection(socket, mInputStream, mOutputStream,
					mService.mMyDeviceInfo, true/* accepted */, to, token,
					mService.getLocalGroupInfo(), mService.mConnRecver, mService.timer).start();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
			mService.mConnRecver
					.onError("Client: Connector failed to connect, "
							+ e.getMessage());
		}
	}

	/**
	 * This thread runs while listening for incoming connections.
	 */
	private class AcceptThread extends Thread {
		private ServerSocket serverSocket = null;
		private boolean canceled = false;

		public AcceptThread(SocketFactory sockFactory) {
			try {
				serverSocket = sockFactory.newServerSocket();
				serverSocket.setReuseAddress(true);
				serverSocket.bind(null);
				Log.d(TAG, "TCPConnector Server: Socket opened");
			} catch (IOException e) {
				Log.d(TAG, "TCPConnector Server: Socket opened failed");
				Log.e(TAG, e.getMessage());
			}
		}

		public int getServicePort() {
			return serverSocket.getLocalPort();
		}

		public void run() {
			synchronized (this) {
				if (canceled)
					return;
			}

			DataOutputStream mOutputStream;
			DataInputStream mInputStream;
			boolean err = false;
			while (!err) {
				try {
					Log.d(TAG, "Server: start waiting for new connection");
					Socket client = serverSocket.accept();
					Log.d(TAG, "Server: recv a new connection");
					mOutputStream = new DataOutputStream(
							client.getOutputStream());
					mInputStream = new DataInputStream(client.getInputStream());

					int to = getConnMonTime();
					Log.d(TAG, "Server: start a new connection");
					new TCPConnection(client, mInputStream, mOutputStream,
							mService.mMyDeviceInfo,
							false/* need accept confirm */, to, null,
							mService.getLocalGroupInfo(), mService.mConnRecver, mService.timer)
							.start();
				} catch (IOException e) {
					err = true;
					Log.e(TAG, "Server: Accept failed, " + e.getMessage());
					boolean cc = false;
					synchronized (this) {
						cc = canceled;
					}
					if (!cc)
						mService.mConnRecver.onError("Server: Accept failed, "
								+ e.getMessage());
				}
			}
		}

		public void cancel() {
			Log.d(TAG, "Server: cancel " + this);
			synchronized (this) {
				canceled = true;
			}
			// / !!!!dont set serverSocket = null; !!!!
			try {
				serverSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "Server: close() failed,  ", e);
			}
		}
	}

}
