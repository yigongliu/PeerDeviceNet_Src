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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.util.Log;
import android.util.Pair;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.utils.PlainSocketFactory;
import com.xconns.peerdevicenet.utils.Utils;

public class DiscoveryLeaderThread extends Thread {
	public static final String TAG = "DiscoveryLeaderThread";

	public static final int DISCOVERY_RENDEZVOUS_PORT = 7385;
	final int BUFFER_SIZE = 1024;
	volatile int scanTimeout = 15000; // scan timeout - default 15 seonds
	int connTimeout = 5000; // conn timeout - default 5 seonds

	private RouterService context = null;
	private NetInfo netInfo = null;
	private String myAddr;
	private ConcurrentHashMap<String, Pair<DeviceInfo, Boolean>> foundDevices = new ConcurrentHashMap<String, Pair<DeviceInfo, Boolean>>();
	private ConcurrentHashMap<String, GMConnection> foundPeers = new ConcurrentHashMap<String, GMConnection>();

	// private SecureSocketFactory sockFactory = null;
	private PlainSocketFactory sockFactory = null;

	Transport.SearchHandler handler = null;
	ServerSocket serverSocket = null;
	private Socket socket = null;
	private ScheduledFuture<?> timerTask = null;
	boolean canceled = false;

	public DiscoveryLeaderThread(RouterService c, NetInfo n, String addr,
			int st, int ct) {
		context = c;
		netInfo = n;
		myAddr = addr;
		scanTimeout = st;
		connTimeout = ct;
		// sockFactory = SecureSocketFactory.getInstance(c);
		sockFactory = PlainSocketFactory.getInstance(c);

		//
		try {
			serverSocket = sockFactory.newServerSocket();
			serverSocket.setReuseAddress(true);
			serverSocket.bind(new InetSocketAddress(DISCOVERY_RENDEZVOUS_PORT));
			Log.d(TAG, "GOPeerScanner Server: Socket opened");
		} catch (IOException e) {
			Log.d(TAG, "GOPeerScanner Server: Socket opened failed");
			Log.e(TAG, e.getMessage());
		}
	}

	//could be called from onDestroy() - GUI thread, has to close socket in separate thread
	public void close() {
		Log.d(TAG, "DiscoveryLeaderThread close" + this);
		synchronized (this) {
			canceled = true;
			if (timerTask != null) {
				timerTask.cancel(true);
			}
		}
		stop_scan();
		// / !!!!dont set serverSocket = null; !!!!
		new Thread(new Runnable() {
			public void run() {
				try {
					serverSocket.close();
				} catch (IOException e) {
					Log.e(TAG, "Server: close() failed,  ", e);
				}
				//
				for (GMConnection conn : foundPeers.values()) {
					conn.closeConn();
				}
			}
		}).start();
	}

	//close not from GUI thread, so we can close socket in place
	public void closeFromInternal() {
		Log.d(TAG, "DiscoveryLeaderThread close internal " + this);
		synchronized (this) {
			canceled = true;
			if (timerTask != null) {
				timerTask.cancel(true);
			}
		}
		stop_scan();
		try {
			serverSocket.close();
		} catch (IOException e) {
			Log.e(TAG, "Server: close() failed,  ", e);
		}
		Log.d(TAG, "serverSocket closed");
		//
		for (GMConnection conn : foundPeers.values()) {
			conn.closeConn();
		}
	}

	// for GO, start_scan means expose its ip to peers
	public void start_scan(DeviceInfo mDeviceInfo, int ownScanTimeout,
			Transport.SearchHandler h) {
		if (!myAddr.equals(mDeviceInfo.addr)) {
			Log.d(TAG, "invalid device info");
			return;
		}
		Log.d(TAG, "start discovery leader scan");
		
		if (ownScanTimeout < 0) {
			//running inside connect by QR code so change peer conn timeout
			scanTimeout = ownScanTimeout;
		}

		synchronized (this) {
			handler = h;
		}

		// report all found peers
		if (foundDevices.size() > 0) {
			for (Pair<DeviceInfo, Boolean> dev : foundDevices.values()) {
				handler.onSearchFoundDevice(dev.first, dev.second);
			}
		}
		//
		foundDevices.put(mDeviceInfo.addr, new Pair<DeviceInfo, Boolean>(
				mDeviceInfo, context.useSSL));
		// broadcast my dev info to all peers
		broadcastDevToGM(mDeviceInfo, context.useSSL);
		// timer to stop
		Log.d(TAG, "scan timeout=" + ownScanTimeout);
		if (ownScanTimeout > 0) {
			boolean timerStarted = true;
			try {
				timerTask = context.timer.schedule(new Runnable() {
					@Override
					public void run() {
						Log.d(TAG,
								"discovery leader scan timeout, stop scanning1111");
						stop_scan();
					}
				}, ownScanTimeout, TimeUnit.MILLISECONDS);
			} catch (RejectedExecutionException re) {
				Log.d(TAG, "failed to start leader scan timer1: " + re.getMessage());
				timerStarted = false;
			} catch (NullPointerException ne) {
				Log.d(TAG, "failed to start leader scan timer1: " + ne.getMessage());
				timerStarted = false;
			}
			if (!timerStarted) {
				stop_scan();
			}
		}
	}

	// for GO, stop_scan means hide its ip to peers
	public void stop_scan() {
		if (timerTask != null)
			timerTask.cancel(true);
		foundDevices.remove(myAddr);
		if (handler != null) {
			handler.onSearchComplete();
			synchronized (this) {
				handler = null;
			}
		}
		if (scanTimeout < 0) { //scan by QR code
			for (GMConnection conn : foundPeers.values()) {
				conn.closeConn();
			}
		}
		Log.e(TAG, "discovery leader scan finished");
	}

	void foundDevice(DeviceInfo dev, boolean useSSL) {
		Log.d(TAG, "find new peer from " + dev.addr);

		//if (foundDevices.size() > 0 && foundDevices.containsKey(dev.addr))
		//	return;
		Log.d(TAG, "found new device: " + dev.name + " : " + dev.addr + " : "
				+ dev.port);
		foundDevices.put(dev.addr, new Pair<DeviceInfo, Boolean>(dev, useSSL));
		// broadcast new peer devinfo to all conned peers
		broadcastDevToGM(dev, useSSL);
		// if GO is scanning, report dev
		if (foundDevices.size() > 0 && foundDevices.containsKey(myAddr)) {
			synchronized (this) {
				if (handler != null)
					handler.onSearchFoundDevice(dev, useSSL);
			}
		}
	}

	void broadcastDevToGM(DeviceInfo dev, boolean useSSL) {
		Log.d(TAG, "useSSL=" + context.useSSL);
		for (GMConnection conn : foundPeers.values()) {
			conn.sendDevInfo(dev, useSSL);
		}
	}

	public void run() {
		synchronized (this) {
			if (canceled)
				return;
		}
		Log.d(TAG, "GO scanner thread start waiting for peers");
		boolean err = false;
		while (!err) {
			try {
				socket = serverSocket.accept();
				Log.d(TAG, "one new peer connects");
			} catch (IOException e0) {
				//wait 2secs and try again
				try {
					Thread.sleep(2000);
				} catch(InterruptedException e1) {}
				try {
					socket = serverSocket.accept();
					Log.d(TAG, "one new peer connects");
				} catch (IOException e2) {
					err = true;
					Log.e(TAG, "Server: Accept failed, " + e2.getMessage());
					synchronized (this) {
						if (!canceled) {
							if (handler != null)
								handler.onError("DiscoveryLeaderThread Server: Accept failed, "
										+ e2.getMessage());
						}
					}
				}
			}
			if (!err) {
				new GMConnection(this, socket).start();
				Log.d(TAG, "GMConnection start called");
			}
		}
		closeFromInternal();
	}

	class GMConnection extends Thread {
		DiscoveryLeaderThread scanner = null;
		volatile DeviceInfo myDevInfo = null;
		Socket socket = null;
		ScheduledFuture<?> timerTask = null;
		DataOutputStream mOutputStream = null;
		DataInputStream mInputStream = null;
		boolean canceled = false;

		public GMConnection(DiscoveryLeaderThread ss, Socket s) {
			scanner = ss;
			socket = s;
			/*
			 * Log.d(TAG, "bef get Streams"); try { mOutputStream = new
			 * DataOutputStream(socket.getOutputStream()); mInputStream = new
			 * DataInputStream(socket.getInputStream()); } catch (IOException e)
			 * { } Log.d(TAG, "aft get Streams");
			 */
		}

		public void closeConn() {
			synchronized (this) {
				canceled = true;
				if (timerTask != null) {
					timerTask.cancel(true);
				}
			}
			if (myDevInfo != null) {
				scanner.foundDevices.remove(myDevInfo.addr);
				scanner.foundPeers.remove(myDevInfo.addr);
			}
			/*
			 * if (mOutputStream != null) { try { mOutputStream.close(); } catch
			 * (IOException e) { e.printStackTrace(); } } if (mInputStream !=
			 * null) { try { mInputStream.close(); } catch (IOException e) {
			 * e.printStackTrace(); } }
			 */
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		public void sendDevInfo(DeviceInfo dev, boolean useSSL) {
			try {
				mOutputStream.writeBoolean(useSSL);
				byte[] myInfo = Utils.marshallDeviceInfo(dev);
				mOutputStream.writeInt(myInfo.length);
				mOutputStream.write(myInfo);
			} catch (IOException e) {
				Log.e(TAG,
						"fail to send, socketc closed for discovery member scan: "
								+ e.getMessage());
				closeConn();
			}
		}

		public void run() {
			Log.d(TAG, "open discovery member scan conn");

			Log.d(TAG, "bef get Streams");
			try {
				OutputStream os = socket.getOutputStream();
				InputStream is = socket.getInputStream();
				Log.d(TAG, "when get raw Streams");
				mOutputStream = new DataOutputStream(os);
				mInputStream = new DataInputStream(is);
			} catch (IOException e) {
				Log.d(TAG, "failed to open in/out Stream");
				closeConn();
				return;
			}
			Log.d(TAG, "aft get Streams");

			synchronized (this) {
				if (canceled) {
					return;
				}
				// init stop timer
				if (scanTimeout > 0) {
					try {
						Log.d(TAG, "start timer for scan conn to peer, scanTime="+scanTimeout);
						timerTask = scanner.context.timer.schedule(
								new Runnable() {
									@Override
									public void run() {
										Log.d(TAG,
												"timeout, close discovery scan conn to peer222");
										closeConn();
									}
								}, scanTimeout, TimeUnit.MILLISECONDS);
					} catch (RejectedExecutionException re) {
						Log.d(TAG,
								"failed to schedule scan timer: "
										+ re.getMessage());
						return;
					} catch (NullPointerException ne) {
						Log.d(TAG,
								"failed to schedule scan timer: "
										+ ne.getMessage());
						return;
					}
				}
			}

			Log.d(TAG, "start recv peer device info");

			byte[] buffer, def_buffer = new byte[BUFFER_SIZE];
			int len, bytes;
			boolean useSSL = false;

			try {
				// first send my net type
				mOutputStream.writeInt(context.actNetType);
				// read peer's ssl usage
				useSSL = mInputStream.readBoolean();
				Log.d(TAG, "read useSSL="+useSSL);
				// then recv peer device info and forward to GO and other GMs
				len = mInputStream.readInt();
				if (len <= BUFFER_SIZE)
					buffer = def_buffer;
				else
					buffer = new byte[len];
				bytes = 0;
				while (bytes < len) {
					bytes += mInputStream.read(buffer, bytes, len - bytes);
				}
				Log.d(TAG, "DiscoveryLeader recv peer device info");
				// forward recved data
				if (bytes > 0) {
					DeviceInfo dev = Utils.unmarshallDeviceInfo(buffer, len);
					Log.d(TAG, "DiscoveryLeader recv peer device info as: "
							+ dev.toString());

					if (dev != null && dev.addr != null && dev.port != null) {
						myDevInfo = dev;
						// add myself to scanner's peer connect map so that new
						// peers can
						// send dev info to me
						scanner.foundPeers.put(myDevInfo.addr, this);
						// sent my device info to peers
						scanner.foundDevice(myDevInfo, useSSL);
					} else {
						Log.e(TAG,
								"discovery leader find null device: "
										+ dev.toString());
						closeConn();
						return;
					}
				} else {
					Log.e(TAG,
							"discovery leader find 0 length deviceInfo, failed");
					closeConn();
					return;
				}

				// send registered GMs device info to this peer
				for (Pair<DeviceInfo, Boolean> dev : scanner.foundDevices
						.values()) {
					mOutputStream.writeBoolean(dev.second);
					// send my device info
					byte[] myInfo = Utils.marshallDeviceInfo(dev.first);
					mOutputStream.writeInt(myInfo.length);
					mOutputStream.write(myInfo);
				}

			} catch (IOException e) {
				Log.e(TAG,
						"socketc closed for discovery member scan: "
								+ e.getMessage());
				closeConn();
				return;
			} catch (NullPointerException npe) {
				Log.e(TAG,
						"socketc closed for discovery member scan: "
								+ npe.getMessage());
				closeConn();
				return;
			}

			Log.e(TAG, "finish broadcasting dev info with a new peer");

			while (true) {
				try {
					byte b = mInputStream.readByte();
				} catch (IOException e) {
					Log.e(TAG,
							"socketc closed for discovery member scan: "
									+ e.getMessage());
					closeConn();
					break;
				} catch (NullPointerException npe) {
					Log.e(TAG,
							"socketc closed for discovery member scan: "
									+ npe.getMessage());
					closeConn();
					return;
				}
			}
			return;
		}
	}
}
