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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;
import android.util.Pair;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.utils.Utils;

public class DiscoveryMulticastThread extends Thread {
	public static final String TAG = "MulticastScanThread";

	RouterService context = null;
	WifiManager wifi = null;
	Transport.SearchHandler handler = null;
	final byte[] MCAST_GROUP_ADDR = new byte[] { (byte) 224, (byte) 0,
			(byte) 121, (byte) 121 };
	final int MCAST_GROUP_PORT = 7383;
	final int BUFFER_SIZE = 1024;
	int timeout = 10000; // 10 seonds
	private ScheduledFuture<?> timerTask = null;
	private boolean canceled = false;

	private NetInfo netInfo = null;
	private DeviceInfo mDeviceInfo = null;
	//private HashMap<String, DeviceInfo> foundDevices = new HashMap<String, DeviceInfo>();

	private InetAddress groupAddress;
	private MulticastSocket bSocket;

	public DiscoveryMulticastThread(RouterService c, WifiManager w, NetInfo n,
			DeviceInfo dev, int t, Transport.SearchHandler h) {
		context = c;
		wifi = w;
		netInfo = n;
		mDeviceInfo = dev;
		timeout = t;
		handler = h;
	}

	public void stop_scan() {
		synchronized (this) {
			canceled = true;
			if (timerTask != null)
				timerTask.cancel(true);
		}
		if (bSocket != null)
			bSocket.close();
	}

	private MyTimerTask scanTask = null;

	class MyTimerTask implements Runnable {
		int count;
		int maxCount;
		byte[] addrInfo;

		public MyTimerTask(int c) {
			maxCount = c;
			count = 0;
			addrInfo = Utils.marshallDeviceInfoSSL(mDeviceInfo, context.useSSL);
		}

		@Override
		public void run() {
			Log.d(TAG, "mscan Timer task starts");
			try {
				// multicast my addr info
				DatagramPacket request = new DatagramPacket(addrInfo,
						addrInfo.length, groupAddress, MCAST_GROUP_PORT);
				bSocket.send(request);
			} catch (IOException e) {
				Log.e(TAG,
						"failed during multicast addr info: " + e.getMessage());
				stop_scan();
			} catch (Throwable t) {
				Log.e(TAG, "general exception inside mscan timer task", t);
				stop_scan();
			}
			count++;
			if (maxCount > 0 && count >= maxCount) {
				Log.d(TAG, "search timeout, stop scanning");
				stop_scan();
			}
		}
	};

	public void run() {
		Log.d(TAG, "start_multicast_scan");

		boolean done = false;
		synchronized (this) {
			done = canceled;
		}

		if (!done) {
			try {
				groupAddress = InetAddress.getByAddress(MCAST_GROUP_ADDR);
			} catch (UnknownHostException e) {
				Log.d(TAG, "cannot resolve multicast addr: " + e.getMessage());
				return;
			}

			/*
			 * WifiManager wifi = (WifiManager) activity
			 * .getSystemService(Context.WIFI_SERVICE);
			 */
			MulticastLock multicastLock = wifi
					.createMulticastLock("MulticastScan");
			multicastLock.acquire();
			Log.d(TAG, "--1");
			try {
				bSocket = new MulticastSocket(MCAST_GROUP_PORT);
				// bSocket.setTimeToLive(2);
				if(timeout > 0)
					bSocket.setSoTimeout(timeout);
				else
					bSocket.setSoTimeout(0); //wait forever
				bSocket.setReuseAddress(true);
				bSocket.joinGroup(groupAddress);

				synchronized (this) {
					done = canceled;
					// start timer to multicast addr info
					if (!done) {
						int count = -1; //run forever;
						if (timeout > 0) count = timeout / 1000;
						scanTask = new MyTimerTask(count);
						try {
						timerTask = context.timer.schedule(scanTask, 1000L,
								TimeUnit.MILLISECONDS);
						}
						catch(RejectedExecutionException re) {
							Log.d(TAG, "failed to start scan timer2: "+re.getMessage());
							done = true;
						}
						catch(NullPointerException ne) {
							Log.d(TAG, "failed to start scan timer2: "+ne.getMessage());
							done = true;							
						}
					} 
				}

				if (!done) {
					// recv peer addresses
					byte[] buf = new byte[BUFFER_SIZE];
					DatagramPacket packet = new DatagramPacket(buf, buf.length);

					// Loop and try to receive responses until the timeout
					// elapses.
					// We'll get
					// back the packet we just sent out, which will be
					// discarded.
					try {
						while (true) {
							// zero the incoming buffer for good measure.
							java.util.Arrays.fill(buf, (byte) 0); // clear
																	// buffer

							bSocket.receive(packet);
							if (packet.getData() != null
									&& packet.getLength() > 0) {
								// Log.d(TAG, "raw_data="+new
								// String(packet.getData(), 0,
								// packet.getLength()));
								Pair<DeviceInfo, Boolean> pair = Utils.unmarshallDeviceInfoSSL(
										packet.getData(), packet.getLength());
								DeviceInfo dev = pair.first;
								boolean useSSL = pair.second;
								if (dev != null && dev.addr != null
										&& dev.port != null) {
									Log.d(TAG, "recv multicast from "
											+ dev.addr);
									if (!mDeviceInfo.addr.equals(dev.addr)) {
										/*
										if (foundDevices.size() > 0
												&& foundDevices
														.containsKey(dev.addr))
											continue;
											*/
										Log.d(TAG, "found new device: "
												+ dev.name + " : " + dev.addr
												+ " : " + dev.port);
										//foundDevices.put(dev.addr, dev);
										handler.onSearchFoundDevice(dev, useSSL);
									}
								} else {
									Log.e(TAG,
											"Multicast scan receive null device: "
													+ dev.name + "," + dev.addr
													+ "," + dev.port);
								}
							} else {
								Log.e(TAG, "Multicast scan receive null packet");
							}
						}
					} catch (SocketTimeoutException e) {
						Log.e(TAG, "Multicast scan receive timed out");
					}
				}
			} catch (IOException e) {
				Log.e(TAG,
						"socketc closed for multicast scan: " + e.getMessage());
			} finally {
				stop_scan();
				multicastLock.release();
			}
		}
		handler.onSearchComplete();
		Log.e(TAG, "multicast scan finished");
		return;
	}
}
