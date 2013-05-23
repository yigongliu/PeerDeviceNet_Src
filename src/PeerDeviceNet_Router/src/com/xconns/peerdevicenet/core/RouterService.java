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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;
import com.xconns.peerdevicenet.Router.MsgId;
import com.xconns.peerdevicenet.utils.RouterConfig;
import com.xconns.peerdevicenet.utils.Utils;

import com.xconns.peerdevicenet.router.R;

/*
 * the following interfaces define the contracts between components of Router services
 */
interface CoreAPI {
	int getNextSessionId();

	// net api
	void getNetworks(int sessionId);

	void getActiveNetwork(int sessionId);

	void activateNetwork(int sessionId, NetInfo net);

	// scan api
	void startPeerSearch(int sessionId, DeviceInfo grpLeader, int timeout);

	void stopPeerSearch(int sessionId);

	// conn api
	void connectPeer(int sessionId, DeviceInfo peer, byte[] token, int timeout);

	void disconnectPeer(int sessionId, DeviceInfo peer);

	void acceptConnection(int sessionId, DeviceInfo peer);

	void denyConnection(int sessionId, DeviceInfo peer, int rejectCode);

	void setConnectionInfo(int sessionId, String devName, boolean useSSL, int liveTime,
			int connTime, int searchTime);

	void getConnectionInfo(int sessionId);

	void getDeviceInfo(int sessionId);

	void registerConnHandler(int sessionId, ConnHandler h);

	void unregisterConnHandler(int sessionId);

	// group api
	void joinGroup(String groupId, DeviceInfo[] peers, GroupHandler h);

	void leaveGroup(String groupId);

	void sendMsg(String groupId, DeviceInfo peer, Bundle msg);

	// shared
	void getConnectedPeers(String group, int sessionId);
}

interface ConnHandler extends Transport.SearchHandler {
	void onError(String errInfo);

	void onGetNetworks(NetInfo[] nets);

	void onGetActiveNetwork(NetInfo net);

	void onNetworkConnected(NetInfo net);

	void onNetworkDisconnected(NetInfo net);

	void onNetworkActivated(NetInfo net);
	
	void onSearchStart(DeviceInfo grpLeader);

	void onSearchFoundDevice(DeviceInfo device, boolean useSSL);

	void onSearchComplete();

	void onConnecting(DeviceInfo device, byte[] token);

	void onConnectionFailed(DeviceInfo device, int rejectCode);

	void onConnected(DeviceInfo peerInfo);

	void onDisconnected(DeviceInfo peerInfo);

	void onSetConnectionInfo();

	void onGetConnectionInfo(String devName, boolean useSSL, int liveTime, int connTime,
			int searchTime);

	void onGetDeviceInfo(DeviceInfo device);

	void onGetPeerDevices(DeviceInfo[] devices);
}

interface GroupHandler {
	void onError(String errInfo);

	void onSelfJoin(DeviceInfo[] peersInfo);

	void onPeerJoin(DeviceInfo peerInfo);

	void onSelfLeave();

	void onPeerLeave(DeviceInfo peerInfo);

	void onReceive(DeviceInfo src, Bundle msg);

	void onGetPeerDevices(DeviceInfo[] devices);
}

// interface for local msging peers (intenting, messenger, aidl)
interface Peer {
	public void stop();

	public IBinder getBinder();

	public void sendMsg(Intent i); // pass intenting msgs to remote
}

// the following 3 interfaces is interface for remote connections
interface ConnectionRecver {
	public void onRecvData(byte[] data, int len, Connection conn);

	public void onConnecting(Connection conn, byte[] token);

	public void onConnectionFailed(Connection conn, int rejectCode);

	public void onConnected(Connection conn);

	public void onDisconnected(Connection conn);

	public void onError(String errMsg);
}

interface Connection {
	public final static int CONNECTING = 1;
	public final static int CONNECTED = 2;
	public final static int DISCONNECTED = 3;

	public void close();

	public void accept();

	public void deny(int rejectCode);

	public DeviceInfo getPeerDevice();

	public String[] getPeerGroups();

	public void addPeerGroup(String groupId);

	public void delPeerGroup(String groupId);

	public int sendData(byte[] data);
}

interface Connector {
	public void start(); // start listening

	public void stop(); // stop connection, close IO streams

	public void connect(DeviceInfo peer, byte[] token, int timeout);

}

public class RouterService extends Service implements CoreAPI {

	// Debugging
	private static final String TAG = "RouterService";

	// api peer intf
	private AidlConnAPIPeer idlConnPeer = null;
	private AidlGroupAPIPeer idlGroupPeer = null;
	private IntentingAPIPeer mIntentingAPIPeer = null;
	private MessengerAPIPeer messengerPeer = null;

	// internal data
	int connLivenessTimeout = RouterConfig.DEF_CONN_LIVENESS_TIMEOUT;
	int connTimeout = RouterConfig.DEF_CONN_TIMEOUT;
	int searchTimeout = RouterConfig.DEF_SEARCH_TIMEOUT;
	boolean useSSL = RouterConfig.DEF_USE_SSL;
	DeviceInfo mMyDeviceInfo = null;

	WifiLock myWifiLock = null;
	boolean useIntWifi = false;
	TransportManager linkMgr = null;
	int actNetType = NetInfo.NoNet;
	TCPConnector mTCPConn = null;
	boolean scanStarted = false;

	// map of remote connections indexed by peer addr
	ConcurrentHashMap<String, Connection> mRemoteConnTable = new ConcurrentHashMap<String, Connection>();
	// map of remote connections indexed by group id
	ConcurrentHashMap<String, ArrayList<Connection>> mGroupConnTable = new ConcurrentHashMap<String, ArrayList<Connection>>();
	// map of local api peers indexed by group id
	ConcurrentHashMap<String, GroupHandler> mLocalGroupTable = new ConcurrentHashMap<String, GroupHandler>();
	// local conn handlers
	ConcurrentHashMap<Integer, ConnHandler> mConnHandlerTable = new ConcurrentHashMap<Integer, ConnHandler>();

	// for timers (1 threads)
	ScheduledThreadPoolExecutor timer = null;

	@TargetApi(5)
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		Log.d(TAG, "RouterService onCreate()");

		timer = new ScheduledThreadPoolExecutor(1);

		linkMgr = new TransportManager(this, linkHandler);
		linkMgr.onResume();

		mMyDeviceInfo = new DeviceInfo();

		//loc wifi
		myWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL, "mywifilock");
		myWifiLock.acquire();

		// init tcp connector
		mTCPConn = new TCPConnector(this);

		// start remote intent service here
		// Router service will send ACTION_ROUTER_STARTUP which other services will listen
		Intent startupSignal = new Intent(Router.ACTION_ROUTER_STARTUP);
		startService(startupSignal);

		// add notification and start service at foreground
		Notification notification = new Notification(R.drawable.router_icon,
				getText(R.string.router_notif_ticker),
				System.currentTimeMillis());
		Intent notificationIntent = new Intent(
				Router.ACTION_CONNECTION_MANAGEMENT);
		// notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
		// Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		notification.setLatestEventInfo(this,
				getText(R.string.router_notif_title),
				getText(R.string.router_notif_message), pendingIntent);
		// using id of ticker text as notif id
		startForeground(R.string.router_notif_ticker, notification);

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "RouterService onStartCommand called");

		if (intent != null) {
			String action = intent.getAction();
			if (action != null && action.length() > 0) {
				if (Router.ACTION_ROUTER_RESET.equals(action)) {
					resetRouter();
				} else if (!startUpActions(action)) {
					if (mIntentingAPIPeer == null) {
						mIntentingAPIPeer = new IntentingAPIPeer(this, this);
					}
					mIntentingAPIPeer.sendMsg(intent);
				}
			}
		}
		// If we get killed, after returning from here, restart
		return START_STICKY;
	}

	boolean startUpActions(String action) {
		if (action.equals(Router.ACTION_SERVICE)
				|| action.equals(Router.ACTION_CONNECTION_SERVICE)
				|| action.equals(Router.ACTION_GROUP_SERVICE)
				|| action.equals(Router.ACTION_MESSENGER_SERVICE))
			return true;
		else
			return false;
	}

	@Override
	public IBinder onBind(Intent intent) {
		String action = intent.getAction();
		Log.d(TAG, "RouterService onBind() called with action=" + action);
		if (action == null)
			return null;
		if (action.equals(Router.ACTION_CONNECTION_SERVICE)) {
			if (idlConnPeer == null)
				idlConnPeer = new AidlConnAPIPeer(this);
			Log.d(TAG, "conn service created & returned");
			return idlConnPeer.getBinder();
		} else if (action.equals(Router.ACTION_GROUP_SERVICE)) {
			if (idlGroupPeer == null)
				idlGroupPeer = new AidlGroupAPIPeer(this);
			Log.d(TAG, "group service created & returned");
			return idlGroupPeer.getBinder();
		} else if (action.equals(Router.ACTION_MESSENGER_SERVICE)) {
			if (messengerPeer == null)
				messengerPeer = new MessengerAPIPeer(this);
			Log.d(TAG, "messenger created & returned");
			return messengerPeer.getBinder();
		}
		return null;
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();

		myWifiLock.release();
		//
		timer.shutdown();
		timer.shutdownNow();

		//
		linkMgr.onPause();
		linkMgr.onDestroy();

		// clean up internal switch data
		Log.d(TAG, "RouterService destroyed");
		if (idlConnPeer != null)
			idlConnPeer.stop();
		if (idlGroupPeer != null)
			idlGroupPeer.stop();
		if (messengerPeer != null)
			messengerPeer.stop();
		if (mIntentingAPIPeer != null)
			mIntentingAPIPeer.stop();

		// close remote connections
		for (Connection c : mRemoteConnTable.values())
			c.close();
		// stop connector
		mTCPConn.stop();
	}

	void resetRouter() {
		// close remote connections
		for (Connection c : mRemoteConnTable.values())
			c.close();
		mRemoteConnTable.clear();

		// stop connector
		//mTCPConn.stop();

		// restart connector
		if (mMyDeviceInfo.addr != null) {
			mTCPConn.restart();
			mMyDeviceInfo.port = Integer.toString(mTCPConn.getServicePort());
		}

		// how to restart linkMgr
	}

	String getLocalGroupInfo() {
		StringBuilder sb = new StringBuilder();
		if (mLocalGroupTable.size() > 0) {
			for (String key : mLocalGroupTable.keySet()) {
				sb.append(key).append(';');
			}
			sb.deleteCharAt(sb.length() - 1);
			return sb.toString();
		} else
			return null;
	}

	int getConnMonitorTimeout() {
		Log.d(TAG, "Peer liveness timeout = " + connLivenessTimeout
				+ " seconds");
		return connLivenessTimeout;
	}

	void restartTCPConnector(String addr) {
		Log.d(TAG, "restartTCPConnector");
		/*
		 * if we connect to a net and have a addr, start Connector otherwise
		 * wait for SetConnectionInfo msg from ConnMgr
		 */
		if (addr != null) {
			mMyDeviceInfo.addr = addr;
			mTCPConn.restart();
			mMyDeviceInfo.port = Integer.toString(mTCPConn.getServicePort());
			Log.d(TAG, "restartTCPConnector: device: " + mMyDeviceInfo.name
					+ ", " + mMyDeviceInfo.addr + ", " + mMyDeviceInfo.port);
		}
	}

	public void startPeerSearch(int sessionId, DeviceInfo grpLeader, int timeout) {
		if (scanStarted)
			stopPeerSearch(sessionId);
		scanStarted = true;
		ConnHandler h = mConnHandlerTable.get(sessionId);
		Log.d(TAG, "start peerSearch with device: " + mMyDeviceInfo + ","
				+ mMyDeviceInfo.port);
		if (grpLeader == null)
			Log.d(TAG, "leader is null");
		else 
			Log.d(TAG, "leader addr="+grpLeader.addr+", leader="+grpLeader);
		Log.d(TAG, "scan timeout="+timeout);
		linkMgr.startSearch(mMyDeviceInfo, grpLeader, timeout, h);
		Log.d(TAG, "scan started");
	}

	public void stopPeerSearch(int sessionId) {
		if (scanStarted) {
			linkMgr.stopSearch();
			scanStarted = false;
		}
	}

	public void connectPeer(int sessionId, DeviceInfo peer, byte[] token,
			int timeout) {
		Log.d(TAG, "start connect to: " + peer.addr);
		mTCPConn.connect(peer, token, timeout);
	}

	public void disconnectPeer(int sessionId, DeviceInfo peer) {
		Log.d(TAG, "start disconnect from: " + peer.addr);
		Connection conn = mRemoteConnTable.get(peer.addr);
		if (conn != null) {
			mRemoteConnTable.remove(peer.addr);
			
			Log.d(TAG, "close conn: " + peer.addr);
			conn.close();
		}
	}

	public void acceptConnection(int sessionId, DeviceInfo peer) {
		Log.d(TAG, "accept conn from: " + peer.addr);
		Connection conn = mRemoteConnTable.get(peer.addr);
		if (conn != null) {
			conn.accept();
		}
	}

	public void denyConnection(int sessionId, DeviceInfo peer, int rejectCode) {
		Log.d(TAG, "deny conn from: " + peer.addr);
		Connection conn = mRemoteConnTable.get(peer.addr);
		if (conn != null) {
			mRemoteConnTable.remove(peer.addr);
			conn.deny(rejectCode);
		}
	}

	public void joinGroup(String groupId, DeviceInfo[] peers,
			GroupHandler ghandler) {
		Log.d(TAG, "join group : " + groupId);
		// register peer
		mLocalGroupTable.put(groupId, ghandler);
		//
		Bundle b = new Bundle();
		b.putInt(Router.MSG_ID, MsgId.JOIN_GROUP);
		b.putString(Router.GROUP_ID, groupId);
		b.putString(Router.DEVICE_ADDR, mMyDeviceInfo.addr);
		sendMsg(groupId, null, b);

		// send Self_Join msgs with all connected peer devices
		ArrayList<Connection> conns = mGroupConnTable.get(groupId);
		if (conns != null && conns.size() > 0) {
			Log.d(TAG,
					"join group : has existing peers "
							+ Integer.toString(conns.size()));
			int numPeers = conns.size();
			DeviceInfo[] devices = new DeviceInfo[numPeers];
			int num = 0;
			for (Connection c : conns) {
				devices[num++] = c.getPeerDevice();
			}
			ghandler.onSelfJoin(devices);
		} else {
			Log.d(TAG, "join group : has NO existing peers ");
			ghandler.onSelfJoin(null);
		}
	}

	public void leaveGroup(String groupId) {
		Log.d(TAG, "leave group : " + groupId);
		GroupHandler ghandler = mLocalGroupTable.get(groupId);
		if (ghandler == null) {
			Log.d(TAG, "leaveGroup() failed to find groupId: " + groupId);
			return;
		}
		// unregister peer
		mLocalGroupTable.remove(groupId);
		//
		Bundle b = new Bundle();
		b.putInt(Router.MSG_ID, MsgId.LEAVE_GROUP);
		b.putString(Router.GROUP_ID, groupId);
		b.putString(Router.DEVICE_ADDR, mMyDeviceInfo.addr);
		sendMsg(groupId, null, b);

		// sendMyLeft
		ghandler.onSelfLeave();
	}

	public void sendMsg(String groupId, DeviceInfo peer, Bundle msg) {
		Log.d(TAG, "send a new msg");
		byte[] data = Utils.marshallBundle(msg);

		// point-to-point send
		if (peer != null && peer.addr != null) {
			Connection conn = mRemoteConnTable.get(peer.addr);
			if (conn != null) {
				Log.d(TAG, "send msg to " + peer.addr);
				conn.sendData(data);
			} else {
				Log.d(TAG, "failed to find connection for addr: " + peer.addr);
			}
			return;
		}

		// multicast on groupId
		int mid = msg.getInt(Router.MSG_ID);
		// group join/leave msgs should be broadcasted
		if (groupId != null && mid != MsgId.JOIN_GROUP
				&& mid != MsgId.LEAVE_GROUP) {
			ArrayList<Connection> conns = mGroupConnTable.get(groupId);
			if (conns != null) {
				for (Connection c : conns)
					c.sendData(data);
			}
			return;
		}

		// broadcast
		Log.d(TAG, "do a broadcast (for join/leave_group)");
		Iterator<Connection> iter = mRemoteConnTable.values().iterator();
		while (iter.hasNext())
			iter.next().sendData(data);
	}
	
	public void checkNetTypeFromGO(int netType) {
		if ((netType == NetInfo.WiFiDirect || netType == NetInfo.WiFiHotspot) && 
				!useIntWifi) {
			useIntWifi = true;
		}
	}

	public void setConnectionInfo(int sessionId, String devName, boolean uSSL, int liveTime,
			int connTime, int searchTime) {
		Log.d(TAG, "setConnectionInfo: " + devName);
		boolean restart = false;
		if (devName != null && !devName.equals(mMyDeviceInfo.name)) {
			mMyDeviceInfo.name = devName;
			restart = true;
		}
		if (liveTime > 0)
			connLivenessTimeout = liveTime;
		if (connTime > 0)
			connTimeout = connTime;
		if (searchTime > 0)
			searchTimeout = searchTime;
		if (useSSL != uSSL) {
			useSSL = uSSL;
			restart = true;
		}
		if (restart) {
			// before activate new net, disconn all old devices
			for (Connection c : mRemoteConnTable.values()) {
				onDeviceDisconnected(c);
				c.close();
			}
			mRemoteConnTable.clear();
			mGroupConnTable.clear();

			mTCPConn.restart();
			mMyDeviceInfo.port = Integer.toString(mTCPConn.getServicePort());
		}
		ConnHandler h = mConnHandlerTable.get(sessionId);
		h.onSetConnectionInfo();
	}

	public void getConnectionInfo(int sessionId) {
		Log.d(TAG, "getConnectionInfo");
		ConnHandler h = mConnHandlerTable.get(sessionId);
		h.onGetConnectionInfo(mMyDeviceInfo.name, useSSL, connLivenessTimeout,
				connTimeout, searchTimeout);
	}

	public void getDeviceInfo(int sessionId) {
		Log.d(TAG, "getDeviceInfo");
		ConnHandler h = mConnHandlerTable.get(sessionId);
		h.onGetDeviceInfo(mMyDeviceInfo);
	}
	
	public void getConnectedPeers(String groupId, int sessionId) {
		DeviceInfo[] devices = null;
		if (groupId == null) {
			int numPeers = mRemoteConnTable.size();
			if (numPeers > 0) {
				devices = new DeviceInfo[numPeers];
				DeviceInfo dev;
				int num = 0;
				for (Connection c : mRemoteConnTable.values()) {
					dev = c.getPeerDevice();
					devices[num++] = dev;
				}
			}
			ConnHandler h = mConnHandlerTable.get(sessionId);
			h.onGetPeerDevices(devices);
		} else {
			ArrayList<Connection> conns = mGroupConnTable.get(groupId);
			if (conns != null && conns.size() > 0) {
				int numPeers = conns.size();
				devices = new DeviceInfo[numPeers];
				DeviceInfo dev;
				int num = 0;
				for (Connection c : conns) {
					dev = c.getPeerDevice();
					devices[num++] = dev;
				}
			}
			GroupHandler ghandler = mLocalGroupTable.get(groupId);
			if (ghandler != null)
				ghandler.onGetPeerDevices(devices);
		}
	}

	public void registerConnHandler(int sessionId, ConnHandler chandler) {
		Log.d(TAG, "register a connHandler");
		mConnHandlerTable.put(sessionId, chandler);
	}

	public void unregisterConnHandler(int sessionId) {
		Log.d(TAG, "unregister a connHandler");
		mConnHandlerTable.remove(sessionId);
	}

	@Override
	public void getNetworks(int sessionId) {
		Log.d(TAG, "getNetworks");
		ConnHandler h = mConnHandlerTable.get(sessionId);
		NetInfo[] nets = linkMgr.getAllNetworks();
		Log.d(TAG, "getNetworks() found " + (nets != null ? nets.length : 0)
				+ " networks");
		h.onGetNetworks(nets);
	}

	@Override
	public void getActiveNetwork(int sessionId) {
		Log.d(TAG, "getActiveNetwork");
		ConnHandler h = mConnHandlerTable.get(sessionId);
		NetInfo net = linkMgr.getActiveNetwork();
		h.onGetActiveNetwork(net);
	}

	@Override
	public void activateNetwork(int sessionId, NetInfo net) {
		Log.d(TAG, "activateNetwork");
		if (net == null) {
			return;
		}
		// before activate new net, disconn all old devices
		for (Connection c : mRemoteConnTable.values()) {
			onDeviceDisconnected(c);
			c.close();
		}
		mRemoteConnTable.clear();
		mGroupConnTable.clear();
		//
		useIntWifi = false;
		if (net.type == NetInfo.WiFiDirect || net.type == NetInfo.WiFiHotspot) {
			useIntWifi = true;
		}
		//
		ConnHandler h = mConnHandlerTable.get(sessionId);
		linkMgr.setActiveNetwork(net.type);
		net = linkMgr.getActiveNetwork(); // make sure addr info is correct
		if (actNetType != net.type) {
			actNetType = net.type;
			restartTCPConnector(net.addr);
		}
		h.onNetworkActivated(net);
	}

	Transport.Handler linkHandler = new Transport.Handler() {

		@Override
		public void onError(int netType, String errInfo) {
			Log.e(TAG, NetInfo.NetTypeName(netType) + ": " + errInfo);
		}

		@Override
		public void onTransportEnabled(int netType, boolean enabled) {
			Log.d(TAG, NetInfo.NetTypeName(netType) + ": "
					+ (enabled ? "enabled" : "disabled"));
		}

		@Override
		public void onNetworkConnected(NetInfo net) {
			String netType = NetInfo.NetTypeName(net.type);
			Log.d(TAG, netType + "-" + net.name + ": connected");
			if (mConnHandlerTable.size() > 0) {
				Iterator<ConnHandler> iter = mConnHandlerTable.values()
						.iterator();
				while (iter.hasNext()) {
					Log.d(TAG, "send netConnected to 1 local connMgrs");
					iter.next().onNetworkConnected(net);
				}
			}
		}

		@Override
		public void onNetworkDisconnected(NetInfo net) {
			Log.d(TAG, "RouterServer::onNetworkDisconnected called");
			if (net == null) {
				Log.d(TAG, "RouterServer::onNetworkDisconnected got null net");
				return;
			}
			String netType = NetInfo.NetTypeName(net.type);
			Log.d(TAG, netType + "-" + net.name + ": disconnected");
			if (actNetType == net.type) {
				// act net disconn, disconn all old devices
				for (Connection conn : mRemoteConnTable.values()) {
					Log.d(TAG, "remove device: "
							+ conn.getPeerDevice().toString());
					onDeviceDisconnected(conn);
					conn.close();
				}
				mRemoteConnTable.clear();
				mGroupConnTable.clear();

				mMyDeviceInfo.addr = null;
				mMyDeviceInfo.port = null;
				mTCPConn.stop();
				linkMgr.setActiveNetwork(NetInfo.NoNet);
				actNetType = NetInfo.NoNet;
			}
			if (mConnHandlerTable.size() > 0) {
				Log.d(TAG, "send netDisConnected to local connMgrs");
				Iterator<ConnHandler> iter = mConnHandlerTable.values()
						.iterator();
				while (iter.hasNext()) {
					iter.next().onNetworkDisconnected(net);
				}
			}
		}

	};
	
	public void onDeviceDisconnected(Connection conn) {
		DeviceInfo peer = conn.getPeerDevice();
		if (peer.addr == null) {
			return;
		}
		mRemoteConnTable.remove(peer.addr);
		// send peer leave msgs for peer groups
		for (String groupId : conn.getPeerGroups()) {
			if (groupId != null) {
				ArrayList<Connection> l = mGroupConnTable.get(groupId);
				if (l != null)
					l.remove(conn);
				GroupHandler gh = mLocalGroupTable.get(groupId);
				if (gh != null) {
					gh.onPeerLeave(peer);
				}
			}
		}
		// send disconn msg
		Iterator<ConnHandler> iter = mConnHandlerTable.values().iterator();
		while (iter.hasNext())
			iter.next().onDisconnected(peer);
	}


	ConnectionRecver mConnRecver = new ConnectionRecver() {

		public void onRecvData(byte[] data, int len, Connection srcConn) {
			Bundle b = Utils.unmarshallBundle(data, len);
			String groupId = b.getString(Router.GROUP_ID);
			if (groupId == null || groupId.length() == 0) {
				Log.e(TAG, "Incoming msg miss groupId");
				return;
			}
			GroupHandler ghandler = mLocalGroupTable.get(groupId);
			int msgId = b.getInt(Router.MSG_ID);
			switch (msgId) {
			case MsgId.JOIN_GROUP:
				Log.d(TAG, "recv joinGroup: " + groupId);
				String addr = b.getString(Router.DEVICE_ADDR);
				if (addr == null) {
					return;
				}
				Connection conn = mRemoteConnTable.get(addr);
				if (groupId != null && conn != null) {
					ArrayList<Connection> l = mGroupConnTable.get(groupId);
					if (l == null) {
						l = new ArrayList<Connection>();
						mGroupConnTable.put(groupId, l);
						l.add(conn);
						conn.addPeerGroup(groupId);
					} else {
						if (!l.contains(conn)) {
							l.add(conn);
							conn.addPeerGroup(groupId);
						}
					}
				}
				// send peer_joined
				DeviceInfo dev = conn.getPeerDevice();
				if (ghandler != null) {
					ghandler.onPeerJoin(dev);
				}
				break;
			case MsgId.LEAVE_GROUP:
				Log.d(TAG, "recv leaveGroup: " + groupId);
				groupId = b.getString(Router.GROUP_ID);
				addr = b.getString(Router.DEVICE_ADDR);
				if (addr == null) {
					return;
				}
				conn = mRemoteConnTable.get(addr);
				if (groupId != null && conn != null) {
					ArrayList<Connection> l = mGroupConnTable.get(groupId);
					if (l != null) {
						l.remove(conn);
						if (l.size() == 0)
							mGroupConnTable.remove(groupId);
					}
				}
				conn.delPeerGroup(groupId);
				// send peer_leave
				dev = conn.getPeerDevice();
				if (ghandler != null) {
					ghandler.onPeerLeave(dev);
				}
				break;
			default: // app msgs
				Log.d(TAG, "recv msgData for Group: " + groupId);
				// if no local peer in this group, just return
				if (ghandler != null) {
					ghandler.onReceive(srcConn.getPeerDevice(), b);
				} else {
					Log.d(TAG, "No group handler, drop msgData for Group: "
							+ groupId);
				}
				break;
			}

		}

		public void onConnecting(Connection conn, byte[] token) {
			DeviceInfo peer = conn.getPeerDevice();
			Log.d(TAG, "peer " + peer.addr + " send connecting");
			if (peer.addr == null) {
				return;
			}
			if (mConnHandlerTable.size() > 0) {
				Log.d(TAG, "send connecting to local connMgrs");
				mRemoteConnTable.put(peer.addr, conn);
				Iterator<ConnHandler> iter = mConnHandlerTable.values()
						.iterator();
				while (iter.hasNext())
					iter.next().onConnecting(peer, token);
			} else {
				Log.d(TAG, "No ConnMgr active, deny conn from: " + peer.addr);
				conn.deny(Router.ConnFailureCode.FAIL_CONNMGR_INACTIVE);
			}
		}

		public void onConnectionFailed(Connection conn, int rejectCode) {
			DeviceInfo peer = conn.getPeerDevice();
			Log.d(TAG, "Peer denied my connection to : " + peer.addr
					+ ", reject_code: " + rejectCode);
			if (peer.addr != null) {
				if (rejectCode != Router.ConnFailureCode.FAIL_CONN_EXIST)
					mRemoteConnTable.remove(peer.addr);
				Iterator<ConnHandler> iter = mConnHandlerTable.values()
						.iterator();
				while (iter.hasNext())
					iter.next().onConnectionFailed(peer, rejectCode);
			}
		}

		public void onConnected(Connection conn) {
			DeviceInfo peer = conn.getPeerDevice();
			Log.d(TAG, "connected: " + peer.addr);
			if (peer.addr == null) {
				return;
			}
			mRemoteConnTable.put(peer.addr, conn);
			Iterator<ConnHandler> iter = mConnHandlerTable.values().iterator();
			while (iter.hasNext())
				iter.next().onConnected(peer);
			Log.d(TAG, "sent CONNECTED: " + peer.addr);
			// send peer join msgs for peer groups
			for (String groupId : conn.getPeerGroups()) {
				if (groupId != null) {
					ArrayList<Connection> l = mGroupConnTable.get(groupId);
					if (l == null) {
						l = new ArrayList<Connection>();
						mGroupConnTable.put(groupId, l);
					}
					l.add(conn);
					GroupHandler gh = mLocalGroupTable.get(groupId);
					if (gh != null) {
						gh.onPeerJoin(peer);
					}
				}
			}
		}

		public void onDisconnected(Connection conn) {
			onDeviceDisconnected(conn);
		}

		public void onError(String errMsg) {
			Iterator<GroupHandler> iter = mLocalGroupTable.values().iterator();
			while (iter.hasNext())
				iter.next().onError(errMsg);
			Iterator<ConnHandler> iter2 = mConnHandlerTable.values().iterator();
			while (iter2.hasNext())
				iter2.next().onError(errMsg);
		}
	};

	int sessionId = 0;
	Object sessionLock = new Object();

	public int getNextSessionId() {
		synchronized (sessionLock) {
			return ++sessionId;
		}
	}

}

class AsyncJob extends AsyncTask<Runnable, Void, Void> {
	static AsyncJob instance = null;

	public static AsyncJob instance() {
		if (instance == null)
			instance = new AsyncJob();
		return instance;
	}

	@Override
	protected Void doInBackground(Runnable... params) {
		for (Runnable job : params)
			job.run();
		return null;
	}

}
