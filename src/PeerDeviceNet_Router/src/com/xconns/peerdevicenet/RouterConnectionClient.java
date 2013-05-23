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

package com.xconns.peerdevicenet;

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.xconns.peerdevicenet.IRouterConnectionHandler;
import com.xconns.peerdevicenet.IRouterConnectionService;

public class RouterConnectionClient {
	// Debugging
	private static final String TAG = "RouterConnectionClient";

	// from IConnectionHandler
	public interface ConnectionHandler {
		void onError(String errInfo);

		void onGetNetworks(NetInfo[] nets);
		void onGetActiveNetwork(NetInfo net);
		void onNetworkConnected(NetInfo net);
		void onNetworkDisconnected(NetInfo net);
		void onNetworkActivated(NetInfo net);
		
		void onSearchStart(DeviceInfo groupLeader);
		void onSearchFoundDevice(DeviceInfo device, boolean useSSL);
		void onSearchComplete();
		
		void onConnecting(DeviceInfo device, byte[] token);
		void onConnectionFailed(DeviceInfo device, int rejectCode);
		
		void onConnected(DeviceInfo peerInfo);

		void onDisconnected(DeviceInfo peerInfo);

		void onSetConnectionInfo();
		
		void onGetConnectionInfo(String devName, boolean useSSL, int liveTime, int connTime, int searchTime);

		void onGetDeviceInfo(DeviceInfo device);

		void onGetPeerDevices(DeviceInfo[] devices);
	}

	private Context context = null;
	private int sessionId = -1;
	private IRouterConnectionService mConnService = null;
	private ConnectionHandler registeredHandler = null;
	private List<Message> sentMsgBuf = new ArrayList<Message>();

	public RouterConnectionClient(Context c, ConnectionHandler h) {
		context = c;
		registeredHandler = h;
	}

	private IRouterConnectionHandler mConnHandler = new IRouterConnectionHandler.Stub() {
		public void onError(String errInfo) {
			registeredHandler.onError(errInfo);
		}

		public void onGetNetworks(NetInfo[] nets) {
			Log.d(TAG, "onGetNetworks callback");
			registeredHandler.onGetNetworks(nets);
		}
		public void onGetActiveNetwork(NetInfo net) {
			Log.d(TAG, "onGetActiveNetwork callback");
			registeredHandler.onGetActiveNetwork(net);
		}
		public void onNetworkConnected(NetInfo net) {
			Log.d(TAG, "onNetworkConnected callback");
			registeredHandler.onNetworkConnected(net);
		}
		public void onNetworkDisconnected(NetInfo net) {
			Log.d(TAG, "onNetworkDisconnected callback");
			registeredHandler.onNetworkDisconnected(net);
		}
		public void onNetworkActivated(NetInfo net) {
			Log.d(TAG, "onNetworkActivated callback");
			registeredHandler.onNetworkActivated(net);
		}

		@Override
		public void onSearchStart(DeviceInfo groupLeader)
				throws RemoteException {
			Log.d(TAG, "onSearchStart callback");
			Log.d(TAG, "onSearchStart: "+groupLeader);
			registeredHandler.onSearchStart(groupLeader);
		}
		
		public void onSearchFoundDevice(DeviceInfo device, boolean useSSL) {
			registeredHandler.onSearchFoundDevice(device, useSSL);
		}
		public void onSearchComplete() {
			registeredHandler.onSearchComplete();
		}
		public void onConnecting(DeviceInfo device, byte[] token) {
			registeredHandler.onConnecting(device, token);
		}
		public void onConnectionFailed(DeviceInfo device, int rejectCode) {
			registeredHandler.onConnectionFailed(device, rejectCode);
		}
		
		public void onConnected(DeviceInfo peerInfo) {
			registeredHandler.onConnected(peerInfo);
		}

		public void onDisconnected(DeviceInfo peerInfo) {
			registeredHandler.onDisconnected(peerInfo);
		}

		public void onGetDeviceInfo(DeviceInfo device) throws RemoteException {
			registeredHandler.onGetDeviceInfo(device);
		}

		public void onGetPeerDevices(DeviceInfo[] devices)
				throws RemoteException {
			registeredHandler.onGetPeerDevices(devices);
		}

		@Override
		public void onSetConnectionInfo() throws RemoteException {
			registeredHandler.onSetConnectionInfo();
		}

		@Override
		public void onGetConnectionInfo(String devName, boolean useSSL, int liveTime,
				int connTime, int searchTime)
				throws RemoteException {
			registeredHandler.onGetConnectionInfo(devName, useSSL, liveTime, connTime, searchTime);
		}

	};

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.d(TAG, "Conn Service onServiceConnected() called");
			mConnService = IRouterConnectionService.Stub.asInterface(service);
			try {
				if (registeredHandler != null)
					sessionId = mConnService.startSession(mConnHandler);
				else
					sessionId = mConnService.startSession(null);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed at registerConnectionHandler: "
								+ e.getMessage());
			}
			Log.d(TAG, "Conn service finish setup, connHandler registered");
			sendBufferedMsgs();
		}

		public void onServiceDisconnected(ComponentName className) {
			mConnService = null;
		}
	};

	public void bindService() {
		Intent intent = new Intent("com.xconns.peerdevicenet.ConnectionService");
		context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	public void unbindService() {
		Log.d(TAG, "unbindService");
		if (mConnService != null) {
			try {
				Log.d(TAG, "connService.stopSession");
				mConnService.stopSession(sessionId);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed at unregisterConnectionHandler: "
								+ e.getMessage());
			}
		}
		// Detach our existing connection.
		Log.d(TAG, "context.unbindService(mConnection)");
		context.unbindService(mConnection);
	}
	
	static final class ConnInfo {
		String name;
		int liveTime;
		int connTime;
		int searchTime;
		boolean useSSL;
		public ConnInfo(String n, boolean us, int l, int c, int s) {
			name = n; liveTime = l; connTime = c; searchTime = s; 
			useSSL = us;
		}
	}

	void sendBufferedMsgs() {
		int sz = sentMsgBuf.size();
		if (sz > 0) {
			Log.d(TAG, "send buffered cmds");
			for (int i = 0; i < sz; i++) {
				Message m = sentMsgBuf.get(i);
				try {
					switch (m.what) {
					case Router.MsgId.START_SEARCH:
						mConnService.startPeerSearch(sessionId, (DeviceInfo)m.obj, m.arg1);
						break;
					case Router.MsgId.STOP_SEARCH:
						mConnService.stopPeerSearch(sessionId);
						break;
					case Router.MsgId.ACCEPT_CONNECTION:
						mConnService.acceptConnection(sessionId, (DeviceInfo) m.obj);
						break;
					case Router.MsgId.DENY_CONNECTION:
						mConnService.denyConnection(sessionId, (DeviceInfo) m.obj, m.arg1);
						break;
					case Router.MsgId.CONNECT:
						Object[] dd = (Object[]) m.obj;
						mConnService.connect(sessionId, (DeviceInfo)dd[0], (byte[])dd[1], m.arg1);
						break;
					case Router.MsgId.DISCONNECT:
						mConnService.disconnect(sessionId, (DeviceInfo) m.obj);
						break;
					case Router.MsgId.SET_CONNECTION_INFO:
						ConnInfo ci = (ConnInfo) m.obj;
						mConnService.setConnectionInfo(sessionId, ci.name, ci.useSSL, ci.liveTime, ci.connTime, ci.searchTime);
						break;
					case Router.MsgId.GET_CONNECTION_INFO:
						mConnService.getConnectionInfo(sessionId);
						break;
					case Router.MsgId.GET_DEVICE_INFO:
						mConnService.getDeviceInfo(sessionId);
						break;
					case Router.MsgId.GET_CONNECTED_PEERS:
						mConnService.getPeerDevices(sessionId);
						break;
					case Router.MsgId.GET_NETWORKS:
						mConnService.getNetworks(sessionId);
						break;
					case Router.MsgId.GET_ACTIVE_NETWORK:
						mConnService.getActiveNetwork(sessionId);
						break;
					case Router.MsgId.ACTIVATE_NETWORK:
						mConnService.activateNetwork(sessionId, (NetInfo) m.obj);
						break;
					default:
						break;
					}
				} catch (RemoteException re) {
					Log.e(TAG,
							"failed to call RouterConnectionService: "
									+ re.getMessage());
				}
			}
			sentMsgBuf.clear();
		}
	}
	
	public void startPeerSearch(DeviceInfo groupLeader, int timeout) {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.START_SEARCH;
			m.obj = groupLeader;
			m.arg1 = timeout;
			sentMsgBuf.add(m);
			return;
		}
		try {
			mConnService.startPeerSearch(sessionId, groupLeader, timeout);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to startPeerSearch: " + e.getMessage());
		}		
	}
	
	public void stopPeerSearch() {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.STOP_SEARCH;
			sentMsgBuf.add(m);
			return;
		}
		try {
			mConnService.stopPeerSearch(sessionId);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to stopPeerSearch: " + e.getMessage());
		}		
	}

	public void acceptConnection(DeviceInfo peer) {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.ACCEPT_CONNECTION;
			m.obj = peer;
			sentMsgBuf.add(m);
			return;
		}
		try {
			mConnService.acceptConnection(sessionId, peer);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to accept_connect: " + e.getMessage());
		}
	}
	
	public void denyConnection(DeviceInfo peer, int rejectCode) {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.DENY_CONNECTION;
			m.arg1 = rejectCode;
			m.obj = peer;
			sentMsgBuf.add(m);
			return;
		}
		try {
			mConnService.denyConnection(sessionId, peer, rejectCode);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to accept_connect: " + e.getMessage());
		}
	}

	// IRouterConnectionService API
	public void connect(DeviceInfo peerInfo, byte[] token, int timeout) {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.CONNECT;
			m.arg1 = timeout;
			m.obj = new Object[]{peerInfo, token};
			sentMsgBuf.add(m);
			return;
		}
		try {
			mConnService.connect(sessionId, peerInfo, token, timeout);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to connect: " + e.getMessage());
		}
	}

	public void disconnect(DeviceInfo peerInfo) {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.DISCONNECT;
			m.obj = peerInfo;
			sentMsgBuf.add(m);
			return;
		}
		try {
			mConnService.disconnect(sessionId, peerInfo);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to disconnect: " + e.getMessage());
		}
	}

	public void setConnectionInfo(String devName, boolean useSSL, int liveTime, int connTime, int searchTime) {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.SET_CONNECTION_INFO;
			m.obj = new ConnInfo(devName, useSSL, liveTime, connTime, searchTime);
			sentMsgBuf.add(m);
			return;
		}
		try {
			mConnService.setConnectionInfo(sessionId, devName, useSSL, liveTime, connTime, searchTime);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to setConnectionInfo: " + e.getMessage());
		}
	}

	public void getConnectionInfo() {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.GET_CONNECTION_INFO;
			sentMsgBuf.add(m);
			return;
		}
		try {
			Log.d(TAG, "start getConnectionInfo()");
			mConnService.getConnectionInfo(sessionId);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to getConnectionInfo: " + e.getMessage());
		}
	}

	public void getDeviceInfo() {
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.GET_DEVICE_INFO;
			sentMsgBuf.add(m);
			return;
		}
		try {
			Log.d(TAG, "start getDeviceInfo()");
			mConnService.getDeviceInfo(sessionId);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to getDeviceInfo: " + e.getMessage());
		}
	}

	public void getPeerDevices() {
		Log.d(TAG, "bef wait for Conn service");
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.GET_CONNECTED_PEERS;
			sentMsgBuf.add(m);
			return;
		}
		try {
			Log.d(TAG, "start getPeerDevices()");
			mConnService.getPeerDevices(sessionId);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to getPeerDevices: " + e.getMessage());
		}
	}
	
	public void getNetworks() {
		Log.d(TAG, "getNetworks bef wait for Conn service");
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.GET_NETWORKS;
			sentMsgBuf.add(m);
			return;
		}
		try {
			Log.d(TAG, "start getNetworks()");
			mConnService.getNetworks(sessionId);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to getNetworks: " + e.getMessage());
		}
		
	}
	public void getActiveNetwork() {
		Log.d(TAG, "getActiveNetwork bef wait for Conn service");
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.GET_ACTIVE_NETWORK;
			sentMsgBuf.add(m);
			return;
		}
		try {
			Log.d(TAG, "start getActiveNetwork()");
			mConnService.getActiveNetwork(sessionId);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to getActiveNetwork: " + e.getMessage());
		}
		
	}
	public void activateNetwork(NetInfo net) {
		Log.d(TAG, "activateNetwork bef wait for Conn service");
		if (mConnService == null) {
			Message m = Message.obtain();
			m.what = Router.MsgId.ACTIVATE_NETWORK;
			m.obj = net;
			sentMsgBuf.add(m);
			return;
		}
		try {
			Log.d(TAG, "start activateNetwork()");
			mConnService.activateNetwork(sessionId, net);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to activateNetwork: " + e.getMessage());
		}		
	}

}
