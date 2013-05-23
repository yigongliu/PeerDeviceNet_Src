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

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.IRouterConnectionHandler;
import com.xconns.peerdevicenet.IRouterConnectionService;
import com.xconns.peerdevicenet.NetInfo;

public class AidlConnAPIPeer implements Peer {
	final static String TAG = "AidlConnAPIPeer";
	CoreAPI router = null;

	public AidlConnAPIPeer(CoreAPI s) {
		router = s;
	}

	// implements Peer methods
	public void stop() {
	}

	public IBinder getBinder() {
		return mBinder;
	}

	public void sendMsg(Intent i) { // pass intenting msgs outward
		// do nothing
	}

	class MyConnHandler implements ConnHandler {
		IRouterConnectionHandler handler = null;

		public MyConnHandler(IRouterConnectionHandler h) {
			handler = h;
		}

		public void onError(String errInfo) {
			try {
				handler.onError(errInfo);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onError(): "
								+ e.getMessage());
			}
		}

		public void onConnected(DeviceInfo peerInfo) {
			try {
				handler.onConnected(peerInfo);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onConnected(): "
								+ e.getMessage());
			}
		}

		public void onDisconnected(DeviceInfo peerInfo) {
			try {
				handler.onDisconnected(peerInfo);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onDisconnected(): "
								+ e.getMessage());
			}
		}

		public void onGetDeviceInfo(DeviceInfo device) {
			try {
				handler.onGetDeviceInfo(device);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onGetDeviceInfo(): "
								+ e.getMessage());
			}
		}

		public void onGetPeerDevices(DeviceInfo[] devices) {
			try {
				handler.onGetPeerDevices(devices);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onGetPeerDevices(): "
								+ e.getMessage());
			}
		}
		
		public void onSearchStart(DeviceInfo grpLeader) {
			try {
				Log.d(TAG, "onSearchStart: "+grpLeader);
				handler.onSearchStart(grpLeader);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onSearchStart(): "
								+ e.getMessage());
			}
		}

		public void onSearchFoundDevice(DeviceInfo device, boolean useSSL) {
			try {
				handler.onSearchFoundDevice(device, useSSL);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onSearchFoundDevice(): "
								+ e.getMessage());
			}
		}

		public void onSearchComplete() {
			try {
				handler.onSearchComplete();
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onSearchComplete(): "
								+ e.getMessage());
			}
		}

		public void onConnecting(DeviceInfo device, byte[] token) {
			try {
				handler.onConnecting(device, token);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onConnecting(): "
								+ e.getMessage());
			}
		}

		public void onConnectionFailed(DeviceInfo device, int rejectCode) {
			try {
				handler.onConnectionFailed(device, rejectCode);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onConnectionDenied(): "
								+ e.getMessage());
			}
		}

		@Override
		public void onGetNetworks(NetInfo[] nets) {
			try {
				Log.d(TAG, "onGetNetworks called");
				handler.onGetNetworks(nets);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onGetNetworks(): "
								+ e.getMessage());
			}
		}

		@Override
		public void onGetActiveNetwork(NetInfo net) {
			try {
				Log.d(TAG, "onGetActiveNetwork called");
				handler.onGetActiveNetwork(net);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onGetActiveNetwork(): "
								+ e.getMessage());
			}
		}

		@Override
		public void onNetworkConnected(NetInfo net) {
			try {
				Log.d(TAG, "onNetworkConnected called");
				handler.onNetworkConnected(net);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onNetworkConnected(): "
								+ e.getMessage());
			}
		}

		@Override
		public void onNetworkDisconnected(NetInfo net) {
			try {
				Log.d(TAG, "onNetworkDisc�onnected called");
				handler.onNetworkDisconnected(net);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onNetworkDisconnected(): "
								+ e.getMessage());
			}
		}

		@Override
		public void onNetworkActivated(NetInfo net) {
			try {
				Log.d(TAG, "onNetworkActivated called");
				handler.onNetworkActivated(net);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onNetworkActivated(): "
								+ e.getMessage());
			}
		}


		@Override
		public void onSetConnectionInfo() {
			try {
				handler.onSetConnectionInfo();
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onSetConnectionInfo(): "
								+ e.getMessage());
			}			
		}

		@Override
		public void onGetConnectionInfo(String devName, boolean useSSL, int liveTime,
				int connTime, int searchTime) {			
			try {
				handler.onGetConnectionInfo(devName, useSSL, liveTime,
						connTime, searchTime);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterConnectionHandler::onGetConnectionInfo(): "
								+ e.getMessage());
			}			
		}

	}

	//
	private final IRouterConnectionService.Stub mBinder = new IRouterConnectionService.Stub() {
		public void connect(int sessionId, DeviceInfo device, byte[] token, int timeout) throws RemoteException {
			router.connectPeer(sessionId, device, token, timeout);
		}

		public void disconnect(int sessionId, DeviceInfo device) throws RemoteException {
			router.disconnectPeer(sessionId, device);
		}

		public void getDeviceInfo(int sessionId) throws RemoteException {
			router.getDeviceInfo(sessionId);
		}

		public void getPeerDevices(int sessionId) throws RemoteException {
			router.getConnectedPeers(null, sessionId);
		}

		public void shutdown() throws RemoteException {
			// TODO Auto-generated method stub
			
		}

		public void startPeerSearch(int sessionId, DeviceInfo grpLeader, int timeout)
				throws RemoteException {
			router.startPeerSearch(sessionId, grpLeader, timeout);
		}

		public void stopPeerSearch(int sessionId) throws RemoteException {
			router.stopPeerSearch(sessionId);
		}

		public void acceptConnection(int sessionId, DeviceInfo peer)
				throws RemoteException {
			router.acceptConnection(sessionId, peer);
		}

		public void denyConnection(int sessionId, DeviceInfo peer, int rejectCode)
				throws RemoteException {
			router.denyConnection(sessionId, peer, rejectCode);
		}

		public int startSession(IRouterConnectionHandler h)
				throws RemoteException {
			int sessionId = router.getNextSessionId();
			//h could be null: when client just want to send msgs, not recv them
			if (h != null) {
				MyConnHandler mh = new MyConnHandler(h);
				router.registerConnHandler(sessionId, mh);
			}
			return sessionId;
		}

		public void stopSession(int sessionId) throws RemoteException {
			Log.d(TAG, "stopSession called");
			router.unregisterConnHandler(sessionId);
		}

		@Override
		public void getNetworks(int sessionId) throws RemoteException {
			Log.d(TAG, "getNetworks called");
			router.getNetworks(sessionId);
		}

		@Override
		public void getActiveNetwork(int sessionId) throws RemoteException {
			Log.d(TAG, "getActiveNetwork called");
			router.getActiveNetwork(sessionId);
		}

		@Override
		public void activateNetwork(int sessionId, NetInfo net)
				throws RemoteException {
			Log.d(TAG, "activateNetwork called");
			router.activateNetwork(sessionId, net);
		}

		@Override
		public void setConnectionInfo(int sessionId, String devName, boolean useSSL,
				int liveTime, int connTime, int searchTime) throws RemoteException {
			Log.d(TAG, "setConnectionInfo called");
			router.setConnectionInfo(sessionId, devName, useSSL, liveTime, connTime, searchTime);
		}

		@Override
		public void getConnectionInfo(int sessionId) throws RemoteException {
			router.getConnectionInfo(sessionId);
		}
	};
	
}
