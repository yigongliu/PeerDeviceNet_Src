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
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.utils.Utils;

public class TCPConnection extends Thread implements Connection {
	// Debugging
	private static final String TAG = "TCPConnection";

	boolean mAccepted = false;
	boolean mAcceptConfirmed = false;
	Object mAcceptLock = new Object();
	byte[] mToken = null;
	int rejectCode = 0;

	DeviceInfo mPeerDeviceInfo = null;
	DeviceInfo mMyDeviceInfo = null;
	List<String> mPeerGroups = new ArrayList<String>();
	String mMyGroupInfo = null;
	DataOutputStream mOutputStream = null;
	DataInputStream mInputStream = null;
	Socket sock = null;
	// conn monitor
	public final static int DEF_PEER_LIVENESS_TIMEOUT = 4000; // 4 secs
	int connLivenessTimeout = 0;
	long lastRecvTime = 0; // last time to recv anything from peer
	int unanswered_hb = 0;
	private ScheduledThreadPoolExecutor timerPool;
	Object connMonLock = new Object();
	final static int HB_REQ = -10110; // special int to mark heartbeat
	final static int HB_RSP = -10111;

	// local msg recver
	ConnectionRecver recver;
	//
	private ScheduledFuture<?> timerTask = null;

	public TCPConnection(Socket s, DataInputStream in, DataOutputStream out,
			DeviceInfo myInfo, boolean a, int to, byte[] token,
			String groupInfo, ConnectionRecver r, ScheduledThreadPoolExecutor timer) {
		sock = s;
		mInputStream = in;
		mOutputStream = out;
		mMyDeviceInfo = myInfo;
		mAccepted = a;
		if (mAccepted) {
			mAcceptConfirmed = true;
		}
		timerPool = timer;
		connLivenessTimeout = to;
		mToken = token;
		mMyGroupInfo = groupInfo;
		recver = r;
	}

	public DeviceInfo getPeerDevice() {
		return mPeerDeviceInfo;
	}

	public void accept() {
		synchronized (mAcceptLock) {
			mAccepted = true;
			mAcceptConfirmed = true;
			mAcceptLock.notifyAll();
			rejectCode = 0;
		}
	}

	public void deny(int c) {
		synchronized (mAcceptLock) {
			mAccepted = false;
			mAcceptConfirmed = true;
			rejectCode = c;
			mAcceptLock.notifyAll();
		}
	}

	public String[] getPeerGroups() {
		return mPeerGroups.toArray(new String[0]);
	}

	public void addPeerGroup(String groupId) {
		if (!mPeerGroups.contains(groupId))
			mPeerGroups.add(groupId);
	}

	public void delPeerGroup(String groupId) {
		mPeerGroups.remove(groupId);
	}

	void sendHeartbeat(long curTime, int token) {
		// send heartbeat
		synchronized (connMonLock) {
			Log.d(TAG, "ConnMonTask sends heartbeat: " + token);
			try {
				mOutputStream.writeInt(token);
				if (token == HB_REQ) {
					unanswered_hb++;
				}
			} catch (IOException e) {
				Log.e(TAG, "Exception during TCPConnection send heartbeat", e);
				close();
			}
		}
	}

	class ConnMonTask implements Runnable {
		@Override
		public void run() {
			Log.d(TAG, "ConnMonTask runs");
			long curTime = System.currentTimeMillis();
			long diff = 0;
			synchronized (connMonLock) {
				diff = curTime - lastRecvTime;
			}
			if (diff >= 3 * connLivenessTimeout) {
				// miss resp from peer for 3 consecutive heartbeats
				Log.d(TAG,
						"peer ("
								+ mPeerDeviceInfo.toString()
								+ ") determined dead since it does not respond for 3 heartbeats");
				close();
				return;
			}
			if (diff > connLivenessTimeout) {
				sendHeartbeat(curTime, HB_REQ);
			}
		}
	}

	private ConnMonTask connMonTask = null;

	public void close() {
		Log.d(TAG, "close() called");
		if (timerTask != null) {
			Log.d(TAG, "ConnMonTimer stop");
			timerTask.cancel(true);
		}
		new Thread(new Runnable() {
			public void run() {
					if (mInputStream != null) {
						try {
							mInputStream.close();
						} catch (IOException e) {
							Log.e(TAG, "close input stream failed", e);
						}
						// mInputStream = null;
					}
					if (mOutputStream != null) {
						try {
							mOutputStream.close();
						} catch (IOException e) {
							Log.e(TAG, "close output stream failed", e);
						}
						// mOutputStream = null;
					}
					if (sock != null) {
						try {
							sock.close();
						} catch (IOException e) {
							Log.e(TAG, "close socket failed", e);
						}
						// mOutputStream = null;
					}
				}
			
		}).start();
	}

	public int sendData(byte[] data) {
		try {
			synchronized (connMonLock) {
				mOutputStream.writeInt(data.length);
				mOutputStream.write(data);
			}
			return data.length;
		} catch (IOException e) {
			Log.e(TAG, "Exception during TCPConnection.sendData", e);
			close();
		}
		return -1;
	}

	public void run() {
		Log.d(TAG, "BEGIN TCPConnection::run()");

		byte[] buffer, def_buffer = new byte[1024];
		int len, bytes;
		
		// init handshake - exchange device/group info
		// send my info first
		byte[] myInfo = Utils.marshallDeviceInfo(mMyDeviceInfo);
		try {
			// send my info first
			Log.d(TAG,
					"TCPConnection start handeshaking, send my device info: "
							+ mMyDeviceInfo);
			mOutputStream.writeInt(myInfo.length);
			mOutputStream.write(myInfo);

			// recv peer device info
			// Read from the mInputStream
			Log.d(TAG, "TCPConnection recv peer device info");
			len = mInputStream.readInt();
			if (len <= 1024)
				buffer = def_buffer;
			else
				buffer = new byte[len];
			bytes = 0;
			while (bytes < len) {
				bytes += mInputStream.read(buffer, bytes, len - bytes);
			}
			// forward recved data to local recver
			if (bytes > 0) {
				mPeerDeviceInfo = Utils.unmarshallDeviceInfo(buffer, len);
				Log.d(TAG, "TCPConnection recv peer device info as: "
						+ mPeerDeviceInfo);
			}

			if (mAccepted) {
				// send security token
				Log.d(TAG, "TCPConnection send security token");
				if (mToken == null)
					mOutputStream.writeInt(0);
				else {
					mOutputStream.writeInt(mToken.length);
					mOutputStream.write(mToken);
				}
				rejectCode = mInputStream.readInt();
				if (rejectCode > 0) {
					Log.d(TAG, "peer reject my connection, code: " + rejectCode);
					recver.onConnectionFailed(this, rejectCode);
					return;
				}
			} else {
				// recv security token
				Log.d(TAG, "TCPConnection recv security token");
				len = mInputStream.readInt();
				if (len > 0) {
					mToken = new byte[len];
					bytes = 0;
					while (bytes < len) {
						bytes += mInputStream.read(mToken, bytes, len - bytes);
					}
				}

				recver.onConnecting(this, mToken);

				// wait for connection confirmation
				synchronized (mAcceptLock) {
					while (!mAcceptConfirmed) {
						try {
							mAcceptLock.wait();
						} catch (InterruptedException ie) {

						}
					}
				}

				if (!mAccepted) {
					mOutputStream.writeInt(rejectCode);
					close();
					// recver.onConnectionDenied(this);
					Log.d(TAG, "connection denied");
					return;
				} else {
					mOutputStream.writeInt(0); // send 0 to mark accept
				}
			}

			// send group info
			Log.d(TAG, "TCPConnection send my group info");
			if (mMyGroupInfo == null) {
				mOutputStream.writeInt(0);
			} else {
				mOutputStream.writeInt(mMyGroupInfo.length());
				mOutputStream.write(mMyGroupInfo.getBytes());
			}

			// recv peer group info
			// Read from the mInputStream
			Log.d(TAG, "TCPConnection recv peer group info");
			len = mInputStream.readInt();
			if (len > 0) {
				if (len <= 1024)
					buffer = def_buffer;
				else
					buffer = new byte[len];
				bytes = 0;
				while (bytes < len) {
					bytes += mInputStream.read(buffer, bytes, len - bytes);
				}
				// forward recved data to local recver
				if (bytes > 0) {
					String peerGroupInfo = new String(buffer, 0, len);
					mPeerGroups.addAll(Arrays.asList(peerGroupInfo.split(";")));
				}
			}
			Log.d(TAG, "TCPConnection finish handshaking");
		} catch (IOException e) {
			Log.e(TAG, "failed to recv peer device info ", e);
			close();
			return;
		}

		recver.onConnected(this);

		// start monitoring conn
		lastRecvTime = System.currentTimeMillis();
		connMonTask = new ConnMonTask();
		timerTask = timerPool.scheduleAtFixedRate(connMonTask, 1000L, connLivenessTimeout, TimeUnit.MILLISECONDS); // delay 1 sec
																	// to start,
																	// repeat
																	// every 4
																	// secs

		// Keep listening to the mInputStream while connected
		while (true) {
			try {
				// Read from the mInputStream
				len = mInputStream.readInt();
				synchronized (connMonLock) {
					lastRecvTime = System.currentTimeMillis();
					unanswered_hb--;
					if (unanswered_hb < 0)
						unanswered_hb = 0;
				}
				if (len == HB_REQ) {
					sendHeartbeat(lastRecvTime, HB_RSP);
					continue;
				}
				if (len <= 1024)
					buffer = def_buffer;
				else
					buffer = new byte[len];
				bytes = 0;
				while (bytes < len) {
					bytes += mInputStream.read(buffer, bytes, len - bytes);
					synchronized (connMonLock) {
						lastRecvTime = System.currentTimeMillis();
						unanswered_hb--;
						if (unanswered_hb < 0)
							unanswered_hb = 0;
					}
				}
				// forward recved data to local recver
				if (bytes > 0) {
					try {
						recver.onRecvData(buffer, bytes, this);
					} catch (Exception ee) {
						Log.e(TAG,
								"recver.onRecvData() throws exception when recv data : ",
								ee);
					}
				}
			} catch (IOException e) {
				Log.e(TAG,
						"mInputStream.read interrupted, peer disconnected: ", e);
				break;
			} catch (Exception e1) { // some other exception is thrown during
										// canceling, so catch all
				Log.e(TAG,
						"mInputStream.read interrupted, peer disconnected: ",
						e1);
				break;
			}
		}

		close();

		recver.onDisconnected(this);

		Log.d(TAG, "END TCPConnection::run()");
	}
}
