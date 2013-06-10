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
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.utils.PlainSocketFactory;
import com.xconns.peerdevicenet.utils.Utils;

public class DiscoveryMemberThread extends Thread {
	public static final String TAG = "DiscoveryMemberThread";

	final int BUFFER_SIZE = 1024;
	int scanTimeout = 15000; // scan timeout - default 10 seonds
	int connTimeout = 5000; // conn timeout - default 5 seonds

	RouterService context = null;

	private NetInfo netInfo = null;
	private DeviceInfo mDeviceInfo = null;
	//private HashSet<String> foundDevices = new HashSet<String>();

	//private SecureSocketFactory sockFactory = null;
	private PlainSocketFactory sockFactory = null;

	private String leaderAddr = null;
	Transport.SearchHandler handler = null;
	private Socket socket = null;
	private ScheduledFuture<?> timerTask = null;
	private boolean canceled = false;

	public DiscoveryMemberThread(RouterService c, String leadAddr, NetInfo n,
			DeviceInfo dev, int st, int ct, Transport.SearchHandler h) {
		context = c;
		leaderAddr = leadAddr;
		netInfo = n;
		mDeviceInfo = dev;
		scanTimeout = st;
		connTimeout = ct;
		handler = h;
		//sockFactory = SecureSocketFactory.getInstance(c);
		sockFactory = PlainSocketFactory.getInstance(c);
		socket = sockFactory.newClientSocket();
	}

	public void close() {
		synchronized (this) {
			canceled = true;
			if (timerTask != null) {
				timerTask.cancel(true);
			}
		}
		new Thread(new Runnable() {
			public void run() {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public void closeFromInternal() {
		synchronized (this) {
			canceled = true;
			if (timerTask != null) {
				timerTask.cancel(true);
			}
		}
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void foundDevice(DeviceInfo dev, boolean useSSL) {
		if (!mDeviceInfo.addr.equals(dev.addr)) {
			Log.d(TAG, "find new peer from " + dev.addr);
			//if (foundDevices.size() > 0 && foundDevices.contains(dev.addr))
			//	return;
			Log.d(TAG, "found new device: " + dev.name + " : " + dev.addr
					+ " : " + dev.port);
			//foundDevices.add(dev.addr);
			handler.onSearchFoundDevice(dev, useSSL);
		} else {
			Log.d(TAG, "found myself, drop it");
		}
	}
	
	public boolean connect() {
		try {
			socket.bind(null);
			socket.connect((new InetSocketAddress(leaderAddr,
					DiscoveryLeaderThread.DISCOVERY_RENDEZVOUS_PORT)), connTimeout);
			Log.d(TAG, "Client socket - " + socket.isConnected());
		} catch (IOException e) {
			Log.e(TAG,
					"socketc fail to connect for wifi direct GM scan: "
							+ e.getMessage());
			return false;
		}
		return true;
	}

	public void run() {
		Log.d(TAG, "start discovery member scan");

		byte[] buffer, def_buffer = new byte[BUFFER_SIZE];
		int len, bytes;
		DataOutputStream mOutputStream = null;
		DataInputStream mInputStream = null;

		boolean done = false;
		synchronized (this) {
			done = canceled;
		}
		if (!done) {
			try {
				Log.d(TAG, "Opening client socket - ");
				if (!socket.isConnected()) {
					int num = 0;
					int numConnAttempts = 5;
					if (scanTimeout>0) {
						numConnAttempts = scanTimeout / 3000;
					}
					while (true) {
						num++;
						try {
							socket.bind(null);
							socket.connect((new InetSocketAddress(
									leaderAddr, DiscoveryLeaderThread.DISCOVERY_RENDEZVOUS_PORT)),
									connTimeout);
						} catch (IOException e) {
							Log.d(TAG, "Failed to connect: " + e.getMessage());
							synchronized(this) {
								done = canceled;
							}
							if (done) { throw e; }
							try {
								Thread.sleep(3000); // wait for 3 secs
							} catch (InterruptedException ie) {
							}
							if (num < numConnAttempts) {
								synchronized(this) {
									done = canceled;
								}
								if (done) { throw e; }
								try {
									socket.close();
								}
								catch(IOException ioe) {}
								socket = sockFactory.newClientSocket();
								continue;
							} else {
								throw e;
							}
						}
						// conn success
						break;
					}
					Log.d(TAG, "Client socket - " + socket.isConnected());
				}
				Log.d(TAG, "bef get Streams");
				mOutputStream = new DataOutputStream(socket.getOutputStream());
				mInputStream = new DataInputStream(socket.getInputStream());
				Log.d(TAG, "aft get Streams");
				
				synchronized (this) {
					done = canceled;
					if (!done) {
						if (scanTimeout < 0) {
							//should wait for leader to terminate
							//otherwise wait 5 minutes to cleanup socket
							scanTimeout = 300000; //5minutes
						}
						try {
							Log.d(TAG, "start finish timer");
							timerTask = context.timer.schedule(new Runnable() {
								@Override
								public void run() {
									Log.d(TAG,
											"10 sec passed, wifi direct GM stop scanning");
									close();
								}
							}, scanTimeout, TimeUnit.MILLISECONDS);
						} catch (RejectedExecutionException re) {
							Log.e(TAG, "failed to schedule search timeout: "
									+ re.getMessage());
							done = true;
						} catch (NullPointerException ne) {
							Log.e(TAG, "failed to schedule search timeout: "
									+ ne.getMessage());
							done = true;
						}
					}
				}

				if (!done) {
					//read net type from router-node and check if we are using internal wifi
					int netType = mInputStream.readInt();
					context.checkNetTypeFromGO(netType);
					
					// send my SSL usage
					Log.d(TAG, "my useSSL="+context.useSSL);
					mOutputStream.writeBoolean(context.useSSL);
					
					// send my device info
					byte[] myInfo = Utils.marshallDeviceInfo(mDeviceInfo);
					mOutputStream.writeInt(myInfo.length);
					mOutputStream.write(myInfo);

					while (true) {
						//read peer's ssl settings
						boolean useSSL = mInputStream.readBoolean();
						Log.d(TAG, "peer useSSL="+context.useSSL);
						//read peer's deviceInfo
						len = mInputStream.readInt();
						if (len <= BUFFER_SIZE)
							buffer = def_buffer;
						else
							buffer = new byte[len];
						bytes = 0;
						while (bytes < len) {
							bytes += mInputStream.read(buffer, bytes, len
									- bytes);
						}
						Log.d(TAG, "DiscoveryMember recv peer device info");
						// forward recved data to local recver
						if (bytes > 0) {
							DeviceInfo dev = Utils.unmarshallDeviceInfo(buffer,
									len);
							Log.d(TAG,
									"DiscoveryMember recv peer device info as: "
											+ dev);

							if (dev != null && dev.addr != null
									&& dev.port != null) {
								foundDevice(dev, useSSL);
							} else {
								Log.e(TAG,
										"discovery member scan find null device: "
												+ dev.name + "," + dev.addr
												+ "," + dev.port);
							}
						}
					}
				}
			} catch (IOException e) {
				Log.e(TAG,
						"socketc closed for discovery member scan: "
								+ e.getMessage());
			} finally {
				if (mOutputStream != null) {
					try {
						mOutputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (mInputStream != null) {
					try {
						mInputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				closeFromInternal();
			}
		}

		handler.onSearchComplete();
		Log.e(TAG, "discovery member scan finished");
		return;
	}
}
