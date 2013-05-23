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

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;
import com.xconns.peerdevicenet.Router.MsgId;
import com.xconns.peerdevicenet.utils.RouterConfig;
import com.xconns.peerdevicenet.utils.Utils;

public class MessengerAPIPeer implements Peer {
	private final static String TAG = "MessengerAPIPeer";
	private CoreAPI router = null;
	private Messenger mServerMessenger = null;
	private Messenger mClientMessenger = null;

	private int sessionId = -1;

	// use handler for async processing in service
	private HandlerThread myThread = null;
	private Looper mServiceLooper = null;
	private ServiceHandler mServiceHandler = null;

	// Handler that receives messages from the thread
	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MsgId.START_SEARCH:
				Bundle data = msg.getData();
				String name = data.getString(Router.PEER_NAME);
				String addr = data.getString(Router.PEER_ADDR);
				String port = data.getString(Router.PEER_PORT);
				int timeout = data.getInt(Router.TIMEOUT);
				router.startPeerSearch(sessionId, new DeviceInfo(name, addr, port), timeout);
				break;
			case MsgId.STOP_SEARCH:
				router.stopPeerSearch(sessionId);
				break;
			case MsgId.ACCEPT_CONNECTION:
				break;
			case MsgId.DENY_CONNECTION:
				break;
			case MsgId.CONNECT:
				data = msg.getData();
				name = data.getString(Router.PEER_NAME);
				addr = data.getString(Router.PEER_ADDR);
				port = data.getString(Router.PEER_PORT);
				byte[] token = data.getByteArray(Router.AUTHENTICATION_TOKEN);
				timeout = data.getInt(Router.TIMEOUT);
				router.connectPeer(sessionId, new DeviceInfo(name, addr, port),
						token, timeout);
				break;
			case MsgId.DISCONNECT:
				data = msg.getData();
				name = data.getString(Router.PEER_NAME);
				addr = data.getString(Router.PEER_ADDR);
				port = data.getString(Router.PEER_PORT);
				router.disconnectPeer(sessionId, new DeviceInfo(name, addr,
						port));
				break;
			case MsgId.JOIN_GROUP:
				String groupId = msg.getData().getString(Router.GROUP_ID);
				router.joinGroup(groupId, null, new MyGroupHandler(groupId));
				break;
			case MsgId.LEAVE_GROUP:
				groupId = msg.getData().getString(Router.GROUP_ID);
				router.leaveGroup(groupId);
				break;
			case MsgId.SEND_MSG:
				// send msg to peer thru socket
				data = msg.getData();
				name = data.getString(Router.PEER_NAME);
				addr = data.getString(Router.PEER_ADDR);
				port = data.getString(Router.PEER_PORT);
				groupId = data.getString(Router.GROUP_ID);
				router.sendMsg(groupId, new DeviceInfo(name, addr, port),
						msg.getData());
				// now for testing, just bounce back
				// recvedMsg("sent: " + msg.getData().getString(MSG_DATA));
				break;
			case MsgId.REGISTER_RECEIVER:
				mClientMessenger = msg.replyTo;
				break;
			case MsgId.UNREGISTER_RECEIVER:
				mClientMessenger = null;
				break;
			case MsgId.SET_CONNECTION_INFO:
				data = msg.getData();
				name = data.getString(Router.DEVICE_NAME);
				int liveTime = data.getInt(Router.LIVENESS_TIMEOUT,
						-1);
				int connTime = data.getInt(Router.CONNECT_TIMEOUT,
						-1);
				int searchTime = data.getInt(Router.SEARCH_TIMEOUT,
						-1);
				boolean useSSL = data.getBoolean(Router.USE_SSL,
						RouterConfig.DEF_USE_SSL);
				router.setConnectionInfo(sessionId, name, useSSL, liveTime, connTime,
						searchTime);
				break;
			case MsgId.GET_CONNECTION_INFO:
				router.getConnectionInfo(sessionId);
				break;
			case MsgId.GET_DEVICE_INFO:
				router.getDeviceInfo(sessionId);
				break;
			case MsgId.GET_CONNECTED_PEERS:
				data = msg.getData();
				groupId = null;
				if (data != null)
					groupId = data.getString(Router.GROUP_ID);
				router.getConnectedPeers(groupId, sessionId);
				break;
			case MsgId.GET_NETWORKS:
				router.getNetworks(sessionId);
				break;
			case MsgId.GET_ACTIVE_NETWORK:
				router.getActiveNetwork(sessionId);
				break;
			case MsgId.ACTIVATE_NETWORK:
				data = msg.getData();
				int type = data.getInt(Router.NET_TYPE);
				name = data.getString(Router.NET_NAME);
				int encrypt = data.getInt(Router.NET_ENCRYPT);
				String pass = data.getString(Router.NET_PASS);
				boolean hidden = data.getBoolean(Router.NET_HIDDEN);
				byte[] info = data.getByteArray(Router.NET_INFO);
				String intfName = data.getString(Router.NET_INTF_NAME);
				addr = data.getString(Router.NET_ADDR);
				boolean mcast = data.getBoolean(Router.NET_INTF_MCAST);
				router.activateNetwork(sessionId, new NetInfo(type, name, encrypt, pass,
						hidden, info, intfName, addr, mcast));
				break;

			default:
			}
		}
	}

	public MessengerAPIPeer(CoreAPI s) {
		router = s;
		// Start up the thread running the service. Note that we create a
		// separate thread because the service normally runs in the process's
		// main thread, which we don't want to block. We also make it
		// background priority so CPU-intensive work will not disrupt our UI.
		myThread = new HandlerThread("ServiceStartArguments",
				Process.THREAD_PRIORITY_BACKGROUND);
		myThread.start();

		// Get the HandlerThread's Looper and use it for our Handler
		mServiceLooper = myThread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);

		mServerMessenger = new Messenger(mServiceHandler);

		sessionId = router.getNextSessionId();
		router.registerConnHandler(sessionId, mConnHandler);
	}

	@TargetApi(5)
	public void stop() {
		router.unregisterConnHandler(sessionId);
		myThread.quit();
	}

	// no binding allowed for this, so return null
	public IBinder getBinder() {
		return mServerMessenger.getBinder();
	}

	ConnHandler mConnHandler = new ConnHandler() {

		public void onError(String errInfo) {
			Bundle b = new Bundle();
			b.putString(Router.MSG_DATA, errInfo);
			Message m = Message.obtain(null, MsgId.ERROR);
			m.setData(b);
			recvMsg(m);
		}

		public void onConnected(DeviceInfo dev) {
			Message m = Message.obtain(null, MsgId.CONNECTED);
			m.setData(Utils.device2Bundle(dev));
			recvMsg(m);
		}

		public void onDisconnected(DeviceInfo dev) {
			Message m = Message.obtain(null, MsgId.DISCONNECTED);
			m.setData(Utils.device2Bundle(dev));
			recvMsg(m);
		}

		public void onGetDeviceInfo(DeviceInfo device) {
			Message m = Message.obtain(null, MsgId.GET_DEVICE_INFO);
			m.setData(Utils.device2Bundle(device));
			recvMsg(m);
		}

		public void onGetPeerDevices(DeviceInfo[] devices) {
			Message m = Message.obtain(null, MsgId.GET_CONNECTED_PEERS);
			m.setData(Utils.deviceArray2Bundle(devices));
			recvMsg(m);
		}

		public void onSearchStart(DeviceInfo leader) {
			Message m = Message.obtain(null, MsgId.SEARCH_START);
			m.setData(Utils.device2Bundle(leader));
			recvMsg(m);
		}

		public void onSearchFoundDevice(DeviceInfo device, boolean useSSL) {
			Message m = Message.obtain(null, MsgId.SEARCH_FOUND_DEVICE);
			Bundle b = Utils.device2Bundle(device);
			b.putBoolean(Router.USE_SSL, useSSL);
			m.setData(b);
			recvMsg(m);
		}

		public void onSearchComplete() {
			Message m = Message.obtain(null, MsgId.SEARCH_COMPLETE);
			recvMsg(m);
		}

		public void onConnecting(DeviceInfo device, byte[] token) {
			Message m = Message.obtain(null, MsgId.CONNECTING);
			Bundle b = Utils.device2Bundle(device);
			b.putByteArray(Router.AUTHENTICATION_TOKEN, token);
			m.setData(b);
			recvMsg(m);
		}

		public void onConnectionFailed(DeviceInfo device, int rejectCode) {
			Message m = Message.obtain(null, MsgId.CONNECTION_FAILED);
			Bundle b = Utils.device2Bundle(device);
			b.putInt(Router.CONN_DENY_CODE, rejectCode);
			m.setData(b);

			recvMsg(m);
		}

		@Override
		public void onGetNetworks(NetInfo[] nets) {
			Message m = Message.obtain(null, MsgId.GET_NETWORKS);
			m.setData(Utils.netArray2Bundle(nets));
			recvMsg(m);
		}

		@Override
		public void onGetActiveNetwork(NetInfo net) {
			Message m = Message.obtain(null, MsgId.GET_ACTIVE_NETWORK);
			m.setData(Utils.net2Bundle(net));
			recvMsg(m);
		}

		@Override
		public void onNetworkConnected(NetInfo net) {
			Message m = Message.obtain(null, MsgId.NETWORK_CONNECTED);
			m.setData(Utils.net2Bundle(net));
			recvMsg(m);
		}

		@Override
		public void onNetworkDisconnected(NetInfo net) {
			Message m = Message.obtain(null, MsgId.NETWORK_DISCONNECTED);
			m.setData(Utils.net2Bundle(net));
			recvMsg(m);
		}

		@Override
		public void onNetworkActivated(NetInfo net) {
			Message m = Message.obtain(null, MsgId.ACTIVATE_NETWORK);
			m.setData(Utils.net2Bundle(net));
			recvMsg(m);
		}

		@Override
		public void onSetConnectionInfo() {
			Message m = Message.obtain(null, MsgId.SET_CONNECTION_INFO);
			recvMsg(m);
		}

		@Override
		public void onGetConnectionInfo(String devName, boolean useSSL, int liveTime,
				int connTime, int searchTime) {
			Message m = Message.obtain(null, MsgId.GET_CONNECTION_INFO);
			Bundle i = new Bundle();
			m.setData(i);

			i.putString(Router.DEVICE_NAME, devName);
			i.putInt(Router.LIVENESS_TIMEOUT, liveTime);
			i.putInt(Router.CONNECT_TIMEOUT, connTime);
			i.putInt(Router.SEARCH_TIMEOUT, searchTime);
			i.putBoolean(Router.USE_SSL, useSSL);

			recvMsg(m);
		}

	};

	class MyGroupHandler implements GroupHandler {
		String groupId;

		public MyGroupHandler(String g) {
			groupId = g;
		}

		public void onError(String errInfo) {
			Bundle b = new Bundle();
			b.putString(Router.MSG_DATA, errInfo);
			b.putString(Router.GROUP_ID, groupId);
			Message m = Message.obtain(null, MsgId.ERROR);
			m.setData(b);
			recvMsg(m);
		}

		public void onSelfJoin(DeviceInfo[] devs) {
			Bundle b = Utils.deviceArray2Bundle(devs);
			b.putString(Router.GROUP_ID, groupId);
			Message m = Message.obtain(null, MsgId.SELF_JOIN);
			m.setData(b);
			recvMsg(m);
		}

		public void onPeerJoin(DeviceInfo dev) {
			Bundle b = Utils.device2Bundle(dev);
			b.putString(Router.GROUP_ID, groupId);
			Message m = Message.obtain(null, MsgId.PEER_JOIN);
			m.setData(b);
			recvMsg(m);
		}

		public void onSelfLeave() {
			Bundle b = new Bundle();
			b.putString(Router.GROUP_ID, groupId);
			Message m = Message.obtain(null, MsgId.SELF_LEAVE);
			m.setData(b);
			recvMsg(m);
		}

		public void onPeerLeave(DeviceInfo dev) {
			Bundle b = Utils.device2Bundle(dev);
			b.putString(Router.GROUP_ID, groupId);
			Message m = Message.obtain(null, MsgId.PEER_LEAVE);
			m.setData(b);
			recvMsg(m);
		}

		public void onReceive(DeviceInfo src, Bundle msg) {
			Message m = Message.obtain(null, MsgId.RECV_MSG);
			msg.putString(Router.GROUP_ID, groupId);
			msg.putString(Router.PEER_NAME, src.name);
			msg.putString(Router.PEER_ADDR, src.addr);
			msg.putString(Router.PEER_PORT, src.port);
			m.setData(msg);
			Log.d(TAG,"recv a msg");
			recvMsg(m);
		}

		public void onGetPeerDevices(DeviceInfo[] devices) {
			Bundle b = Utils.deviceArray2Bundle(devices);
			b.putString(Router.GROUP_ID, groupId);
			Message m = Message.obtain(null, MsgId.GET_CONNECTED_PEERS);
			m.setData(b);
			recvMsg(m);
		}

	};

	// pass msgs inward
	public void recvMsg(Message m) {
		if (mClientMessenger != null)
			try {
				Log.d(TAG, "forward a msg to clientMessenger");
				mClientMessenger.send(m);
			} catch (RemoteException e) {
				Log.e(TAG, "failed to forward msg to local messenger recver: "
						+ e.getMessage());
			}
	}

	public void sendMsg(Intent i) {
		// TODO Auto-generated method stub

	}
}
