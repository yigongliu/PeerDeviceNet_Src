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

package com.xconns.peerdevicenet.cm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;
import com.xconns.peerdevicenet.RouterConnectionClient;
import com.xconns.peerdevicenet.core.RouterService;
import com.xconns.peerdevicenet.ctor.ConnectorActivity;

class ConnInfo {
	public String devName;
	public boolean useSSL;
	public int liveTime;
	public int connTime;
	public int searchTime;

	public ConnInfo(String d, boolean u, int l, int c, int s) {
		devName = d;
		useSSL = u;
		liveTime = l;
		connTime = c;
		searchTime = s;
	}
}

public class ConnectionManagerService extends Service {
	final static String TAG = "ConnectionManagerService";

	final String DEFAULT_PIN = "1234321";
	String securityToken = DEFAULT_PIN;

	// -- data related to networks --
	NetInfo actNet = null;
	HashMap<String, NetInfo> connNets = new HashMap<String, NetInfo>();

	// -- data related to device connection setup --
	Map<String, DeviceInfo> discoveredDevices = new HashMap<String, DeviceInfo>();
	Map<String, DeviceInfo> rejectedPeers = new HashMap<String, DeviceInfo>();
	Set<String> connectingPeers = new HashSet<String>();
	Set<String> connectedPeers = new HashSet<String>();
	Map<String, DeviceInfo> askingPeers = new HashMap<String, DeviceInfo>();
	DeviceInfo searchLeader = null;

	boolean mAutoAccept = false;
	boolean mAutoConn = false;
	int liveTimeout = 4000; // def 4 secs to check link liveness
	int searchTimeout = 30000; // def 30 seconds
	int connTimeout = 5000; // 5 seconds
	boolean useSSL = true;
	DeviceInfo mDevice = new DeviceInfo();//reset by connMgr

	public int scanStarted = 0;
	public int scanLeftOver = 0;

	// interface to router core service
	RouterConnectionClient connClient = null;
	// boolean connMgrVisible = false;
	ConnectionManager connMgr = null;
	//
	volatile ConnectorActivity ctor = null;

	final IBinder mBinder = new LocalBinder();

	public class LocalBinder extends Binder {
		public ConnectionManagerService getService() {
			// Return this instance of RemoteIntentService so clients can call
			// public methods
			return ConnectionManagerService.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG, "onBind called");
		return mBinder;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();

		// start service so that it will keep running at background
		Intent intent = new Intent(this, RouterService.class);
		startService(intent);

		// bind to aidl api of router service, listening to incoming connections
		connClient = new RouterConnectionClient(this, connHandler);
		connClient.bindService();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "ConnMgrServ destroyed, unbind connHnadler");
		connClient.unbindService();
	}

	public void setConnector(ConnectorActivity c) {
		ctor = c;
	}

	public void setConnMgr(ConnectionManager cm) {
		connMgr = cm;
		mDevice = cm.mDevice;
		if (discoveredDevices.size() > 0) {
			for (DeviceInfo d : discoveredDevices.values()) {
				cm.addNearbyDevice(d);
			}
		}
		if (askingPeers.size() > 0) {
			for (DeviceInfo d : askingPeers.values()) {
				cm.showConnConfirmDialog(d);
			}
		}
	}

	public void setConnParams(boolean autoConn, boolean autoAccept) {
		mAutoConn = autoConn;
		mAutoAccept = autoAccept;
	}

	public void setConnectionInfo(int live_to, int conn_to, int scan_to,
			boolean uSSL) {
		searchTimeout = scan_to;
		connTimeout = conn_to;
		liveTimeout = live_to;
		useSSL = uSSL;
		connClient.setConnectionInfo((mDevice != null) ? mDevice.name : "",
				useSSL, liveTimeout, connTimeout, searchTimeout);
	}

	public void setSimpleConnectionInfo(String devName, boolean uSSL) {
		useSSL = uSSL;
		connClient.setConnectionInfo(devName, useSSL, liveTimeout, connTimeout,
				searchTimeout);
	}

	public void setDeviceInfo(DeviceInfo dev) {
		mDevice = dev;
	}

	public void setConnPIN(String pin) {
		securityToken = pin;
	}

	/*
	 * public void onConnMgrResume() { connMgrVisible = true; }
	 * 
	 * public void onConnMgrPause() { connMgrVisible = false; }
	 */

	public void onConnMgrDestroy(boolean configChange) {
		connMgr = null;
		if (configChange) {
			scanLeftOver = scanStarted;
			scanStarted = 0;
		}
	}

	public void onConnectorDestroy() {
		ctor = null;
	}

	public boolean connectorSessionActive() {
		Log.d(TAG, "connectorSessionActive, ctor=" + ctor + ", leader="
				+ searchLeader);
		return ctor != null || searchLeader != null;
	}

	void saveDeviceInfo(DeviceInfo dev) {
		if (dev != null && dev.name != null && !dev.name.equals(mDevice.name)) {
			mDevice = dev;
			connClient.setConnectionInfo(mDevice.name, useSSL, liveTimeout,
					connTimeout, searchTimeout);
		}
	}

	public void getDeviceInfo() {
		connClient.getDeviceInfo();
	}

	void connectPeer(DeviceInfo dev, byte[] token, int timeout) {
		if (dev != null) {
			Log.d(TAG, "ConnMgrService connect peer : " + dev.addr);
			connClient.connect(dev, token, timeout);
		}
	}

	void disconnectPeer(DeviceInfo dev) {
		if (dev != null) {
			Log.d(TAG, "ConnMgrService disconnect peer : " + dev.addr);
			connClient.disconnect(dev);
		}
	}

	void acceptPeer(String addr) {
		DeviceInfo dev = askingPeers.get(addr);
		if (dev != null) {
			askingPeers.remove(addr);
			connectingPeers.add(dev.addr);
			connClient.acceptConnection(dev);
		}
	}

	void denyPeer(String addr) {
		DeviceInfo dev = askingPeers.get(addr);
		if (dev != null) {
			askingPeers.remove(addr);
			discoveredDevices.remove(addr);
			connMgr.onDisconnectPeerDevice(dev);
			connClient.denyConnection(dev,
					Router.ConnFailureCode.FAIL_REJECT_BY_USER);
		}
	}

	public void startPeerSearch(DeviceInfo dev, int t) {
		Log.d(TAG, "startPeerSearch: " + t);
		scanStarted++;
		connClient.startPeerSearch(dev, t);
	}

	public void startPeerSearch(DeviceInfo dev) {
		Log.d(TAG, "startPeerSearch: " + searchTimeout);
		scanStarted++;
		connClient.startPeerSearch(dev, searchTimeout);
	}

	public void stopPeerSearch() {
		Log.d(TAG,"stopPeerSearch");
		connClient.stopPeerSearch();
	}

	void getConnectionInfo() {
		if (connClient != null) {
			connClient.getConnectionInfo();
		}
	}

	public void getNetworks() {
		if (connNets != null && connNets.size() > 0) {
			if (connMgr != null) {
				connMgr.onGetNetworks(connNets.values().toArray(new NetInfo[0]));
			}
			if (ctor != null) {
				ctor.onGetNetworks(connNets.values().toArray(new NetInfo[0]));
			}
		} else {
			if (connClient != null) {
				connClient.getNetworks();
			}
		}
	}

	NetInfo[] getNets() {
		return connNets.values().toArray(new NetInfo[0]);
	}

	NetInfo getActNet() {
		return actNet;
	}

	public void getActiveNetwork() {
		NetInfo an = null;
		an = actNet;
		if (an != null) {
			if (connMgr != null) {
				connMgr.onGetActiveNetwork(an);
			}
			if (ctor != null) {
				ctor.onGetActiveNetwork(an);
			}
		} else {
			connClient.getActiveNetwork();
		}
	}

	public void activateNetwork(NetInfo net) {
		NetInfo an = null;
		an = actNet;
		Log.d(TAG, "activateNetwork: ");
		if (net == null) {
			actNet = null;
			connMgr.onNetworkActivated(null);
			return;
		}
		if (an == null || an.type != net.type) {
			// actNet will be updated when onNetworkActivated
			connClient.activateNetwork(net);
		} else {
			if (connMgr != null) {
				connMgr.onNetworkActivated(net);
			}
			if (ctor != null) {
				ctor.onNetworkActivated(net);
			}
		}
	}

	RouterConnectionClient.ConnectionHandler connHandler = new RouterConnectionClient.ConnectionHandler() {
		public void onError(String errInfo) {
			Message msg = mHandler.obtainMessage(Router.MsgId.ERROR);
			msg.obj = errInfo;
			mHandler.sendMessage(msg);
		}

		public void onConnected(DeviceInfo dev) {
			Message msg = mHandler.obtainMessage(Router.MsgId.CONNECTED);
			msg.obj = dev;
			mHandler.sendMessage(msg);
		}

		public void onDisconnected(DeviceInfo dev) {
			Message msg = mHandler.obtainMessage(Router.MsgId.DISCONNECTED);
			msg.obj = dev;
			mHandler.sendMessage(msg);
		}

		public void onGetDeviceInfo(DeviceInfo device) {
			Message msg = mHandler.obtainMessage(Router.MsgId.GET_DEVICE_INFO);
			msg.obj = device;
			mHandler.sendMessage(msg);
		}

		public void onGetPeerDevices(DeviceInfo[] devices) {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.GET_CONNECTED_PEERS);
			msg.obj = devices;
			mHandler.sendMessage(msg);
		}

		public void onConnecting(DeviceInfo device, byte[] token) {
			Message msg = mHandler.obtainMessage(Router.MsgId.CONNECTING);
			msg.obj = new Object[] { device, token };
			mHandler.sendMessage(msg);
		}

		public void onConnectionFailed(DeviceInfo device, int rejectCode) {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.CONNECTION_FAILED);
			msg.obj = new Object[] { device, rejectCode };
			mHandler.sendMessage(msg);
		}

		@Override
		public void onSearchStart(DeviceInfo groupLeader) {
			Message msg = mHandler.obtainMessage(Router.MsgId.SEARCH_START);
			msg.obj = groupLeader;
			mHandler.sendMessage(msg);
		}

		public void onSearchFoundDevice(DeviceInfo dev, boolean uSSL) {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.SEARCH_FOUND_DEVICE);
			msg.obj = new Object[] { dev, useSSL };
			mHandler.sendMessage(msg);
		}

		public void onSearchComplete() {
			Message msg = mHandler.obtainMessage(Router.MsgId.SEARCH_COMPLETE);
			mHandler.sendMessage(msg);
		}

		@Override
		public void onGetNetworks(NetInfo[] nets) {
			Message msg = mHandler.obtainMessage(Router.MsgId.GET_NETWORKS);
			msg.obj = nets;
			mHandler.sendMessage(msg);
		}

		@Override
		public void onGetActiveNetwork(NetInfo net) {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.GET_ACTIVE_NETWORK);
			msg.obj = net;
			mHandler.sendMessage(msg);
		}

		@Override
		public void onNetworkConnected(NetInfo net) {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.NETWORK_CONNECTED);
			msg.obj = net;
			mHandler.sendMessage(msg);
		}

		@Override
		public void onNetworkDisconnected(NetInfo net) {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.NETWORK_DISCONNECTED);
			msg.obj = net;
			mHandler.sendMessage(msg);
		}

		@Override
		public void onNetworkActivated(NetInfo net) {
			Message msg = mHandler.obtainMessage(Router.MsgId.ACTIVATE_NETWORK);
			msg.obj = net;
			mHandler.sendMessage(msg);
		}

		@Override
		public void onSetConnectionInfo() {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.SET_CONNECTION_INFO);
			mHandler.sendMessage(msg);
		}

		@Override
		public void onGetConnectionInfo(String devName, boolean uSSL,
				int liveTime, int connTime, int searchTime) {
			Message msg = mHandler
					.obtainMessage(Router.MsgId.GET_CONNECTION_INFO);
			ConnInfo ci = new ConnInfo(devName, uSSL, liveTime, connTime,
					searchTime);
			msg.obj = ci;
			mHandler.sendMessage(msg);
		}

	};

	/**
	 * Handler of incoming messages from service.
	 */
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			DeviceInfo dev = null;
			Object[] params = null;
			NetInfo net = null;
			switch (msg.what) {
			// handle msgs for scan
			case Router.MsgId.SEARCH_FOUND_DEVICE:
				params = (Object[]) msg.obj;
				dev = (DeviceInfo) params[0];
				boolean uSSL = (Boolean) params[1];
				Log.d(TAG, "onSearchFoundDevice: " + dev);
				if (discoveredDevices.containsKey(dev.addr)) {
					Log.d(TAG, "already discovered, drop it");
					return;
				}
				if (useSSL != uSSL) {
					if (connMgr != null) {
						connMgr.onSearchFoundDiffSSLFlag(dev, uSSL);
					} else {
						//
						Log.d(TAG,
								"No ConectionManager active, dropped [Peer has diff SSL settings] msg");
					}
					return;
				}
				Log.d(TAG, "---a1");
				// see if discovered devices should be connected auto
				int netType = -1;
				if (actNet != null)
					netType = actNet.type;
				discoveredDevices.put(dev.addr, dev);
				Log.d(TAG, "---a2");
				if ((mAutoConn || connectorSessionActive() // searchLeader !=
															// null
						|| netType == NetInfo.WiFiDirect || netType == NetInfo.WiFiHotspot)
						&& !connectingPeers.contains(dev.addr)
						&& !askingPeers.containsKey(dev.addr)
						&& !connectedPeers.contains(dev.addr)
				/*
				 * && (mDevice.addr.compareTo(dev.addr) < 0 || rejectedPeers
				 * .containsKey(dev.addr))
				 */) {
					Log.d(TAG, "connect to client: " + dev.addr);
					connectingPeers.add(dev.addr);
					connClient.connect(dev, securityToken.getBytes(),
							connTimeout);
					if (rejectedPeers.containsKey(dev.addr)) {
						rejectedPeers.remove(dev.addr);
					}
				}

				Log.d(TAG, "----- a3");
				if (connMgr != null) {
					connMgr.addNearbyDevice(dev);
				} else {
					// although user moved away from ConnMgr activity now
					// we need their permission for this, so fire a
					// notificiation
					Log.d(TAG,
							"No ConectionManager active, dropped SearchFoundDevice msg");
				}
				Log.d(TAG, "---- a4");

				break;
			case Router.MsgId.SEARCH_COMPLETE:
				if (scanLeftOver > 0) {
					scanLeftOver--;
					return;
				}
				searchLeader = null;
				if (connMgr != null) {
					scanStarted--;
					connMgr.onSearchComplete();
				} else {
					// although user moved away from ConnMgr activity now
					// we need their permission for this, so fire a
					// notificiation
					Log.d(TAG,
							"No ConectionManager active, dropped SearchComplete msg");
				}

				break;
			case Router.MsgId.SEARCH_START:
				DeviceInfo groupLeader = (DeviceInfo) msg.obj;
				Log.d(TAG, "onSearchStart: " + groupLeader);
				searchLeader = groupLeader;
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						//connMgr.onSearchStart(groupLeader); no work here
					}
					if (ctor != null) {
						ctor.onSearchStart(groupLeader);
					}
				}
				break;
			// handle msgs for connections
			case Router.MsgId.CONNECTED:
				dev = (DeviceInfo) msg.obj;
				connectingPeers.remove(dev.addr);
				connectedPeers.add(dev.addr);
				DeviceInfo device = discoveredDevices.get(dev.addr);
				if (device == null) {
					// should we prompt users? now simply add it for now
					discoveredDevices.put(dev.addr, dev);
					device = dev;
				} else {
					// local device may miss some info
					if (dev.name != null)
						device.name = dev.name;
					if (dev.port != null)
						device.port = dev.port;
				}
				if (connMgr != null) {
					connMgr.onConnectPeerDevice(device);
				} else {
					// user moved away from ConnMgrActivity; send a notification
				}
				Log.d(TAG, "a device connected");
				break;
			case Router.MsgId.DISCONNECTED:
				dev = (DeviceInfo) msg.obj;
				device = discoveredDevices.get(dev.addr);
				connectedPeers.remove(dev.addr);
				discoveredDevices.remove(dev.addr);
				rejectedPeers.remove(dev.addr);
				if (device == null)
					return;
				if (connMgr != null) {
					connMgr.onDisconnectPeerDevice(dev);
				} else {
					// user move away from ConnMgrActivity, send a notificiation
				}
				Log.d(TAG, "a device disconnected");
				break;
			case Router.MsgId.GET_CONNECTED_PEERS:
				DeviceInfo[] devices = (DeviceInfo[]) msg.obj;
				if (devices == null) {
					return;
				}
				if (connMgr != null) {
					if (devices != null && devices.length > 0)
						for (int i = 0; i < devices.length; i++) {
							discoveredDevices.put(devices[i].addr,
									devices[i]);
							connMgr.onConnectPeerDevice(devices[i]);
						}
				} else {
					// user moved away from ConnMgr activity now
					// drop it
					Log.d(TAG,
							"No ConectionManager active, dropped GetPeerDevices msg");
				}
				break;
			case Router.MsgId.CONNECTING:
				params = (Object[]) msg.obj;
				device = (DeviceInfo) params[0];
				byte[] token = (byte[]) params[1];
				Log.d(TAG, "peer " + device.addr + " sends connecting to me:"
						+ mDevice.toString());

				Log.d(TAG, "onConnecting searchLeader=" + searchLeader);
				// check if trying to conn to self
				if (device.addr != null && device.addr.equals(mDevice.addr)) {
					Log.d(TAG, "CONN_TO_SELF: deny self connection");
					connClient.denyConnection(device,
							Router.ConnFailureCode.FAIL_CONN_SELF);
					return;
				}

				Log.d(TAG, "----- 1");
				// check if conn attempt already started
				if (connectingPeers.contains(device.addr)
						&& mDevice.addr.compareTo(device.addr) < 0) {
					Log.d(TAG, "----- 1.5");
					Log.d(TAG,
							"CONN_EXIST: deny peer's connection attempt from: "
									+ device.addr); // rejectedPeers.put(device.addr,
													// device);
					connClient.denyConnection(device,
							Router.ConnFailureCode.FAIL_CONN_EXIST);
					return;
				}

				Log.d(TAG, "----- 2");

				// see if connecting req can be handled automatically
				// auto accept for wifi-direct
				netType = -1;
				if (actNet != null)
					netType = actNet.type;
				if ((mAutoAccept || connectorSessionActive() // searchLeader !=
																// null
						|| netType == NetInfo.WiFiDirect || netType == NetInfo.WiFiHotspot)
						&& token != null) {
					String tokStr = new String(token, 0, token.length);
					if (!(securityToken.equals(tokStr))) {
						Log.d(TAG,
								"PIN_MISMATCH: deny peer's connection attempt from: "
										+ device.addr + ", token: " + tokStr);
						connClient.denyConnection(device,
								Router.ConnFailureCode.FAIL_PIN_MISMATCH);
						return;
					}
					if (!(discoveredDevices.containsKey(device.addr))) {
						Log.d(TAG,
								"UNKNOWN_PEER: deny peer's connection attempt from: "
										+ device.addr + ", token: " + tokStr);
						rejectedPeers.put(device.addr, device);
						connClient.denyConnection(device,
								Router.ConnFailureCode.FAIL_UNKNOWN_PEER);
						return;
					}

					if (!connectingPeers.contains(device.addr))
						connectingPeers.add(device.addr);
					Log.d(TAG, "accept peer's connection attempt from: "
							+ device.addr);
					connClient.acceptConnection(device);
					return;
				}
				Log.d(TAG, "----- 3");

				// up to here, we need user interaction for connecting
				if (connMgr != null
				/* && Utils.isConnMgrAtTop(ConnectionManagerService.this) */) {
					askingPeers.put(device.addr, device);
					connMgr.showConnConfirmDialog(device);
					Log.d(TAG, "----- 4");

				} else {
					// user moved away from ConnMgr activity now
					// should fire a notificiation
					Log.d(TAG,
							"No ConectionManager active, deny incoming peer connecting msg");
					connClient.denyConnection(device,
							Router.ConnFailureCode.FAIL_CONNMGR_INACTIVE);
				}
				break;
			case Router.MsgId.CONNECTION_FAILED:
				params = (Object[]) msg.obj;
				device = (DeviceInfo) params[0];
				int rejectCode = ((Integer) params[1]);
				if (rejectCode == Router.ConnFailureCode.FAIL_CONN_EXIST) {
					// in case peer found conn exist and failed this conn
					// attempt;
					// do not clean up as following, otherwise lose data
					return;
				}
				connectingPeers.remove(device.addr);
				connectedPeers.remove(device.addr); // in case peer terminate
													// early
				if (rejectCode == Router.ConnFailureCode.FAIL_REJECT_BY_USER
						|| rejectCode == Router.ConnFailureCode.FAIL_PIN_MISMATCH) {
					discoveredDevices.remove(device.addr);
				}
				if (connMgr != null) {
					connMgr.onConnectionFailed(device, rejectCode);
				} else {
					// although user moved away from ConnMgr activity now
					// we need their permission for this, so fire a
					// notificiation
					Log.d(TAG,
							"No ConectionManager active, dropped ConnectionFailed msg");
				}

				break;
			case Router.MsgId.GET_DEVICE_INFO:
				device = (DeviceInfo) msg.obj;
				Log.d(TAG, "onGetDeviceInfo: " + device.toString());
				if (connMgr != null) {
					connMgr.onGetDeviceInfo(device);
				}
				if (ctor != null) {
					ctor.onGetDeviceInfo(device);
				}
				/*
				 * else { // the req for GetDeviceInfo should come from
				 * ConnMgrActivity; // since it is gone, drop it. Log.d(TAG,
				 * "No ConectionManager active, dropped GetDeviceInfo"); }
				 */
				break;

			case Router.MsgId.ERROR:
				String errInfo = (String) msg.obj;
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						connMgr.onError(errInfo);
					}
					if (ctor != null) {
						ctor.onError(errInfo);
					}
				} else {
					// although user moved away from ConnMgr activity now
					// we need their permission for this, so fire a
					// notificiation
					Log.d(TAG, "No ConectionManager active, dropped Error msg: "+errInfo);
				}
				break;
			case Router.MsgId.GET_NETWORKS:
				NetInfo[] nets = (NetInfo[]) msg.obj;
				Log.d(TAG, "onGetNetworks: "
						+ (nets != null ? nets.length : "null"));
				connNets.clear();
				for (NetInfo n : nets) {
					connNets.put(n.name, n);
				}
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						connMgr.onGetNetworks(nets);
						Log.d(TAG, "onGetNetworks: proc1");
					}
					if (ctor != null) {
						ctor.onGetNetworks(nets);
						Log.d(TAG, "onGetNetworks: proc2");
					}
				}
				Log.d(TAG, "onGetNetworks finished");

				break;
			case Router.MsgId.GET_ACTIVE_NETWORK:
				net = (NetInfo) msg.obj;
				Log.d(TAG, "onGetActiveNetwork");
				actNet = net;
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						connMgr.onGetActiveNetwork(net);
						Log.d(TAG, "onGetActiveNetwork, proc1");
					}
					if (ctor != null) {
						ctor.onGetActiveNetwork(net);
						Log.d(TAG, "onGetActiveNetwork, proc2");
					}
				}

				break;
			case Router.MsgId.ACTIVATE_NETWORK:
				net = (NetInfo) msg.obj;
				Log.d(TAG, "onNetworkActivated: " + net.toString());
				actNet = net;
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						connMgr.onNetworkActivated(net);
					}
					if (ctor != null) {
						ctor.onNetworkActivated(net);
					}
				}
				break;
			case Router.MsgId.NETWORK_CONNECTED:
				net = (NetInfo) msg.obj;
				Log.d(TAG, "onNetworkConnected: "/* +net.toString() */);
				if (connNets.containsKey(net.name))
					return;
				connNets.put(net.name, net);
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						connMgr.onNetworkConnected(net);
					}
					if (ctor != null) {
						ctor.onNetworkConnected(net);
					}
				}
				break;
			case Router.MsgId.NETWORK_DISCONNECTED:
				net = (NetInfo) msg.obj;
				Log.d(TAG, "onNetworkDisconnected: " + net.toString());
				if (!connNets.containsKey(net.name))
					return;
				connNets.remove(net.name);
				discoveredDevices.clear();
				connectingPeers.clear();
				connectedPeers.clear();
				rejectedPeers.clear();
				askingPeers.clear();
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						connMgr.onNetworkDisconnected(net);
					}
					if (ctor != null) {
						ctor.onNetworkDisconnected(net);
					}
				}

				break;
			case Router.MsgId.SET_CONNECTION_INFO:
				Log.d(TAG, "finish SetConnectionInfo()");
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						getConnectionInfo();
					}
					if (ctor != null) {
						Log.d(TAG, "send SetConnectionInfo() to ctorAct");
						ctor.onSetConnectionInfo();
					}
				}

				break;
			case Router.MsgId.GET_CONNECTION_INFO:
				ConnInfo ci = (ConnInfo) msg.obj;
				Log.d(TAG, "onGetConnectionInfo()");
				useSSL = ci.useSSL;
				if (connMgr != null || ctor != null) {
					if (connMgr != null) {
						connMgr.onGetConnectionInfo(ci);
					}
					if (ctor != null) {
						ctor.onGetConnectionInfo();
					}
				}

				break;
			default:
				Log.d(TAG, "unhandled msg: " + Router.MsgName(msg.what));
				super.handleMessage(msg);
			}
		}
	};

}
