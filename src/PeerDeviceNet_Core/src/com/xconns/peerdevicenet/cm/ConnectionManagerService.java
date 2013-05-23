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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
	Object actNetLock = new Object();
	HashMap<String, NetInfo> connNets = new HashMap<String, NetInfo>();

	// -- data related to device connection setup --
	Map<String, DeviceInfo> discoveredDevices = new ConcurrentHashMap<String, DeviceInfo>();
	Map<String, DeviceInfo> rejectedPeers = new ConcurrentHashMap<String, DeviceInfo>();
	Set<String> connectingPeers = new HashSet<String>();
	Set<String> connectedPeers = new HashSet<String>();
	Map<String, DeviceInfo> askingPeers = new ConcurrentHashMap<String, DeviceInfo>();
	volatile DeviceInfo searchLeader = null;

	boolean mAutoAccept = false;
	boolean mAutoConn = false;
	int liveTimeout = 4000; // def 4 secs to check link liveness
	int searchTimeout = 30000; // def 30 seconds
	int connTimeout = 5000; // 5 seconds
	boolean useSSL = true;
	DeviceInfo mDevice;

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
				NetInfo[] nets = connNets.values().toArray(new NetInfo[0]);
				Message msg = ctor.mHandler
						.obtainMessage(Router.MsgId.GET_NETWORKS);
				msg.obj = nets;
				ctor.mHandler.sendMessage(msg);
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
		synchronized (actNetLock) {
			return actNet;
		}
	}

	public void getActiveNetwork() {
		NetInfo an = null;
		synchronized (actNetLock) {
			an = actNet;
		}
		if (an != null) {
			if (connMgr != null) {
				connMgr.onGetActiveNetwork(an);
			}
			if (ctor != null) {
				Message msg = ctor.mHandler
						.obtainMessage(Router.MsgId.GET_ACTIVE_NETWORK);
				msg.obj = an;
				ctor.mHandler.sendMessage(msg);
			}
		} else {
			connClient.getActiveNetwork();
		}
	}

	public void activateNetwork(NetInfo net) {
		NetInfo an = null;
		synchronized (actNetLock) {
			an = actNet;
		}
		Log.d(TAG, "activateNetwork: ");
		if (net == null) {
			synchronized (actNetLock) {
				actNet = null;
			}
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
				Message msg = ctor.mHandler
						.obtainMessage(Router.MsgId.ACTIVATE_NETWORK);
				msg.obj = net;
				ctor.mHandler.sendMessage(msg);
			}
		}
	}

	RouterConnectionClient.ConnectionHandler connHandler = new RouterConnectionClient.ConnectionHandler() {
		public void onError(String errInfo) {
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.ERROR);
					msg.obj = errInfo;
					connMgr.mHandler.sendMessage(msg);
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.ERROR);
					msg.obj = errInfo;
					ctor.mHandler.sendMessage(msg);
				}
			} else {
				// although user moved away from ConnMgr activity now
				// we need their permission for this, so fire a notificiation
				Log.d(TAG, "No ConectionManager active, dropped Error msg");
			}
		}

		public void onConnected(DeviceInfo dev) {
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
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.CONNECTED);
				msg.obj = device;
				connMgr.mHandler.sendMessage(msg);
			} else {
				// user moved away from ConnMgrActivity; send a notification
			}
			Log.d(TAG, "a device connected");
		}

		public void onDisconnected(DeviceInfo dev) {
			DeviceInfo device = discoveredDevices.get(dev.addr);
			connectedPeers.remove(dev.addr);
			discoveredDevices.remove(dev.addr);
			rejectedPeers.remove(dev.addr);
			if (device == null)
				return;
			if (connMgr != null) {
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.DISCONNECTED);
				msg.obj = dev;
				connMgr.mHandler.sendMessage(msg);
			} else {
				// user move away from ConnMgrActivity, send a notificiation
			}
			Log.d(TAG, "a device disconnected");
		}

		public void onGetDeviceInfo(DeviceInfo device) {
			Log.d(TAG, "onGetDeviceInfo: " + device.toString());
			if (connMgr != null) {
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.GET_DEVICE_INFO);
				msg.obj = device;
				connMgr.mHandler.sendMessage(msg);
			}
			if (ctor != null) {
				Message msg = ctor.mHandler
						.obtainMessage(Router.MsgId.GET_DEVICE_INFO);
				msg.obj = device;
				ctor.mHandler.sendMessage(msg);
			}
			/*
			 * else { // the req for GetDeviceInfo should come from
			 * ConnMgrActivity; // since it is gone, drop it. Log.d(TAG,
			 * "No ConectionManager active, dropped GetDeviceInfo"); }
			 */
		}

		public void onGetPeerDevices(DeviceInfo[] devices) {
			if (devices == null) {
				return;
			}
			if (connMgr != null) {
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.GET_CONNECTED_PEERS);
				ArrayList<DeviceInfo> devs = new ArrayList<DeviceInfo>();
				for (DeviceInfo d : devices) {
					if (!askingPeers.containsKey(d.addr)) {
						devs.add(d);
					}
				}
				msg.obj = devs.toArray(new DeviceInfo[0]);
				connMgr.mHandler.sendMessage(msg);
			} else {
				// user moved away from ConnMgr activity now
				// drop it
				Log.d(TAG,
						"No ConectionManager active, dropped GetPeerDevices msg");
			}
		}

		public void onConnecting(DeviceInfo device, byte[] token) {
			Log.d(TAG, "peer " + device.addr + " sends connecting");

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
				Log.d(TAG, "CONN_EXIST: deny peer's connection attempt from: "
						+ device.addr); // rejectedPeers.put(device.addr,
										// device);
				connClient.denyConnection(device,
						Router.ConnFailureCode.FAIL_CONN_EXIST);
				return;
			}

			Log.d(TAG, "----- 2");

			// see if connecting req can be handled automatically
			// auto accept for wifi-direct
			int netType = -1;
			synchronized (actNetLock) {
				if (actNet != null)
					netType = actNet.type;
			}
			if ((mAutoAccept || connectorSessionActive() // searchLeader != null
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
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.CONNECTING);
				msg.obj = device;
				connMgr.mHandler.sendMessage(msg);
				Log.d(TAG, "----- 4");

			} else {
				// user moved away from ConnMgr activity now
				// should fire a notificiation
				Log.d(TAG,
						"No ConectionManager active, deny incoming peer connecting msg");
				connClient.denyConnection(device,
						Router.ConnFailureCode.FAIL_CONNMGR_INACTIVE);
			}
		}

		public void onConnectionFailed(DeviceInfo device, int rejectCode) {
			if (rejectCode == Router.ConnFailureCode.FAIL_CONN_EXIST) {
				// in case peer found conn exist and failed this conn attempt;
				// do not clean up as following, otherwise lose data
				return;
			}
			connectingPeers.remove(device.addr);
			connectedPeers.remove(device.addr); // in case peer terminate early
			if (rejectCode == Router.ConnFailureCode.FAIL_REJECT_BY_USER
					|| rejectCode == Router.ConnFailureCode.FAIL_PIN_MISMATCH) {
				discoveredDevices.remove(device.addr);
			}
			if (connMgr != null) {
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.CONNECTION_FAILED);
				msg.arg1 = rejectCode;
				msg.obj = device;
				connMgr.mHandler.sendMessage(msg);
			} else {
				// although user moved away from ConnMgr activity now
				// we need their permission for this, so fire a notificiation
				Log.d(TAG,
						"No ConectionManager active, dropped ConnectionFailed msg");
			}
		}

		@Override
		public void onSearchStart(DeviceInfo groupLeader) {
			Log.d(TAG, "onSearchStart: " + groupLeader);
			searchLeader = groupLeader;
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.SEARCH_START);
					msg.obj = groupLeader;
					connMgr.mHandler.sendMessage(msg);
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.SEARCH_START);
					msg.obj = groupLeader;
					ctor.mHandler.sendMessage(msg);
				}
			}
		}

		public void onSearchFoundDevice(DeviceInfo dev, boolean uSSL) {
			Log.d(TAG, "onSearchFoundDevice: " + dev);
			if (discoveredDevices.containsKey(dev.addr)) {
				return;
			}
			if (useSSL != uSSL) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(ConnectionManager.SEARCH_FOUND_DEVICE_DIFF_SSL_FLAG);
					msg.arg1 = uSSL ? 1 : 0;
					msg.obj = dev;
					connMgr.mHandler.sendMessage(msg);
				} else {
					//
					Log.d(TAG,
							"No ConectionManager active, dropped [Peer has diff SSL settings] msg");
				}
				return;
			}
			// see if discovered devices should be connected auto
			int netType = -1;
			synchronized (actNetLock) {
				if (actNet != null)
					netType = actNet.type;
			}
			discoveredDevices.put(dev.addr, dev);
			if ((mAutoConn || connectorSessionActive() // searchLeader !=
														// null
					|| netType == NetInfo.WiFiDirect || netType == NetInfo.WiFiHotspot)
					&& !connectingPeers.contains(dev.addr)
					&& !askingPeers.containsKey(dev.addr)
					&& !connectedPeers.contains(dev.addr)
					/*
					&& (mDevice.addr.compareTo(dev.addr) < 0 || rejectedPeers
							.containsKey(dev.addr))*/) {
				connectingPeers.add(dev.addr);
				connClient.connect(dev, securityToken.getBytes(), connTimeout);
				if (rejectedPeers.containsKey(dev.addr)) {
					rejectedPeers.remove(dev.addr);
				}
			}
			

			if (connMgr != null) {
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.SEARCH_FOUND_DEVICE);
				msg.obj = dev;
				connMgr.mHandler.sendMessage(msg);
			} else {
				// although user moved away from ConnMgr activity now
				// we need their permission for this, so fire a notificiation
				Log.d(TAG,
						"No ConectionManager active, dropped SearchFoundDevice msg");
			}
		}

		public void onSearchComplete() {
			if (scanLeftOver > 0) {
				scanLeftOver--;
				return;
			}
			searchLeader = null;
			if (connMgr != null) {
				scanStarted--;
				Message msg = connMgr.mHandler
						.obtainMessage(Router.MsgId.SEARCH_COMPLETE);
				connMgr.mHandler.sendMessage(msg);
			} else {
				// although user moved away from ConnMgr activity now
				// we need their permission for this, so fire a notificiation
				Log.d(TAG,
						"No ConectionManager active, dropped SearchComplete msg");
			}
		}

		@Override
		public void onGetNetworks(NetInfo[] nets) {
			Log.d(TAG, "onGetNetworks: "
					+ (nets != null ? nets.length : "null"));
			connNets.clear();
			for (NetInfo net : nets) {
				connNets.put(net.name, net);
			}
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.GET_NETWORKS);
					msg.obj = nets;
					connMgr.mHandler.sendMessage(msg);
					Log.d(TAG, "onGetNetworks: proc1");
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.GET_NETWORKS);
					msg.obj = nets;
					ctor.mHandler.sendMessage(msg);
					Log.d(TAG, "onGetNetworks: proc2");
				}
			}
			Log.d(TAG, "onGetNetworks finished");
		}

		@Override
		public void onGetActiveNetwork(NetInfo net) {
			Log.d(TAG, "onGetActiveNetwork");
			synchronized (actNetLock) {
				actNet = net;
			}
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.GET_ACTIVE_NETWORK);
					msg.obj = net;
					connMgr.mHandler.sendMessage(msg);
					Log.d(TAG, "onGetActiveNetwork, proc1");
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.GET_ACTIVE_NETWORK);
					msg.obj = net;
					ctor.mHandler.sendMessage(msg);
					Log.d(TAG, "onGetActiveNetwork, proc2");
				}
			}
		}

		@Override
		public void onNetworkConnected(NetInfo net) {
			Log.d(TAG, "onNetworkConnected: "/* +net.toString() */);
			if (connNets.containsKey(net.name))
				return;
			connNets.put(net.name, net);
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.NETWORK_CONNECTED);
					msg.obj = net;
					connMgr.mHandler.sendMessage(msg);
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.NETWORK_CONNECTED);
					msg.obj = net;
					ctor.mHandler.sendMessage(msg);
				}
			}
		}

		@Override
		public void onNetworkDisconnected(NetInfo net) {
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
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.NETWORK_DISCONNECTED);
					msg.obj = net;
					connMgr.mHandler.sendMessage(msg);
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.NETWORK_DISCONNECTED);
					msg.obj = net;
					ctor.mHandler.sendMessage(msg);
				}
			}
		}

		@Override
		public void onNetworkActivated(NetInfo net) {
			Log.d(TAG, "onNetworkActivated: " + net.toString());
			synchronized (actNetLock) {
				actNet = net;
			}
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.ACTIVATE_NETWORK);
					msg.obj = net;
					connMgr.mHandler.sendMessage(msg);
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.ACTIVATE_NETWORK);
					msg.obj = net;
					ctor.mHandler.sendMessage(msg);
				}
			}
		}

		@Override
		public void onSetConnectionInfo() {
			Log.d(TAG, "finish SetConnectionInfo()");
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Log.d(TAG, "send SetConnectionInfo() to connMgr");
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.SET_CONNECTION_INFO);
					connMgr.mHandler.sendMessage(msg);
				}
				if (ctor != null) {
					Log.d(TAG, "send SetConnectionInfo() to ctorAct");
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.SET_CONNECTION_INFO);
					ctor.mHandler.sendMessage(msg);
				}
			}
		}

		@Override
		public void onGetConnectionInfo(String devName, boolean uSSL,
				int liveTime, int connTime, int searchTime) {
			Log.d(TAG, "onGetConnectionInfo()");
			useSSL = uSSL;
			if (connMgr != null || ctor != null) {
				if (connMgr != null) {
					Message msg = connMgr.mHandler
							.obtainMessage(Router.MsgId.GET_CONNECTION_INFO);
					msg.obj = new ConnInfo(devName, useSSL, liveTime, connTime,
							searchTime);
					connMgr.mHandler.sendMessage(msg);
				}
				if (ctor != null) {
					Message msg = ctor.mHandler
							.obtainMessage(Router.MsgId.GET_CONNECTION_INFO);
					msg.obj = new ConnInfo(devName, useSSL, liveTime, connTime,
							searchTime);
					ctor.mHandler.sendMessage(msg);
				}
			}
		}

	};
	
}
