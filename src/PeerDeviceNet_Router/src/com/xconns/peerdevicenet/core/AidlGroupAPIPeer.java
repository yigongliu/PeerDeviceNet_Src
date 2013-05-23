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
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.IRouterGroupHandler;
import com.xconns.peerdevicenet.IRouterGroupService;
import com.xconns.peerdevicenet.Router;

public class AidlGroupAPIPeer implements Peer {
	final static String TAG = "AidlGroupAPIPeer";
	CoreAPI router = null;

	// final ConcurrentHashMap<String, IRouterGroupHandler> mCallbacks = new
	// ConcurrentHashMap<String, IRouterGroupHandler>();

	public AidlGroupAPIPeer(CoreAPI s) {
		router = s;
	}

	// implements Peer methods
	public void stop() {
		// mCallbacks.clear();
	}

	public IBinder getBinder() {
		return mBinder;
	}

	public void sendMsg(Intent i) { // pass intenting msgs outward
		// do nothing
	}

	class MyGroupHandler implements GroupHandler {
		IRouterGroupHandler handler = null;

		public MyGroupHandler(IRouterGroupHandler h) {
			handler = h;
		}

		/* (non-Javadoc)
		 * @see com.xconns.peerdevicenet.GroupHandler#onError(java.lang.String)
		 */
		public void onError(String errInfo) {
			try {
				handler.onError(errInfo);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterGroupHandler::onError(): "
								+ e.getMessage());
			}
		}

		public void onSelfJoin(DeviceInfo[] peersInfo) {
			try {
				handler.onSelfJoin(peersInfo);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterGroupHandler::onSelfJoin(): "
								+ e.getMessage());
			}
		}

		public void onPeerJoin(DeviceInfo peerInfo) {
			try {
				handler.onPeerJoin(peerInfo);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterGroupHandler::onPeerJoin(): "
								+ e.getMessage());
			}
		}

		public void onSelfLeave() {
			try {
				handler.onSelfLeave();
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterGroupHandler::onSelfLeave(): "
								+ e.getMessage());
			}
		}

		public void onPeerLeave(DeviceInfo peerInfo) {
			try {
				handler.onPeerLeave(peerInfo);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterGroupHandler::onPeerLeave(): "
								+ e.getMessage());
			}
		}

		public void onReceive(DeviceInfo src, Bundle msg) {
			try {
				handler.onReceive(src, msg.getByteArray(Router.MSG_DATA));
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterGroupHandler::onReceive(): "
								+ e.getMessage());
			}
		}

		public void onGetPeerDevices(DeviceInfo[] devices) {
			try {
				handler.onGetPeerDevices(devices);
			} catch (RemoteException e) {
				Log.e(TAG,
						"failed to call IRouterGroupHandler::onGetPeerDevices(): "
								+ e.getMessage());
			}
		}

	}

	//
	private final IRouterGroupService.Stub mBinder = new IRouterGroupService.Stub() {

		public void joinGroup(String groupId, DeviceInfo[] peers, IRouterGroupHandler h)
				throws RemoteException {
			if (groupId != null && h != null)
				router.joinGroup(groupId, peers, new MyGroupHandler(h));
		}

		public void leaveGroup(String groupId, IRouterGroupHandler h)
				throws RemoteException {
			if (groupId != null)
				router.leaveGroup(groupId);
		}

		public void send(String groupId, DeviceInfo dest, byte[] data) throws RemoteException {
			Bundle msg = new Bundle();
			msg.putByteArray(Router.MSG_DATA, data);
			if (groupId != null)
				msg.putString(Router.GROUP_ID, groupId);
			if (dest != null)
				msg.putString(Router.PEER_ADDR, dest.addr);
			router.sendMsg(groupId, dest, msg);
		}

		public void getPeerDevices(String groupId) throws RemoteException {
			router.getConnectedPeers(groupId, -1);
		}
	};
}
