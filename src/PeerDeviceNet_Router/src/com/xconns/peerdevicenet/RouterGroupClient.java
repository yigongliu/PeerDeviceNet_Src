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

import com.xconns.peerdevicenet.IRouterGroupHandler;
import com.xconns.peerdevicenet.IRouterGroupService;

public class RouterGroupClient {
	// Debugging
	private static final String TAG = "RouterGroupClient";

	// from IGroupHandler
	public interface GroupHandler {
		void onError(String errInfo);

		void onSelfJoin(DeviceInfo[] peersInfo);

		void onPeerJoin(DeviceInfo peerInfo);

		void onSelfLeave();

		void onPeerLeave(DeviceInfo peerInfo);

		void onReceive(DeviceInfo src, byte[] msg);

		void onGetPeerDevices(DeviceInfo[] devices);
	}

	private Context context = null;
	private IRouterGroupService mGroupService = null;
	private String groupId = null;
	private DeviceInfo[] peers = null;
	private GroupHandler registeredHandler = null;
	private List<Message> sentMsgBuf = new ArrayList<Message>();

	public RouterGroupClient(Context c, String grp, DeviceInfo[] p, GroupHandler h) {
		context = c;
		groupId = grp;
		peers = p;
		registeredHandler = h;
	}

	private IRouterGroupHandler mGroupHandler = new IRouterGroupHandler.Stub() {
		public void onError(String errInfo) {
			registeredHandler.onError(errInfo);
		}

		public void onSelfJoin(DeviceInfo[] peersInfo) throws RemoteException {
			registeredHandler.onSelfJoin(peersInfo);
		}

		public void onPeerJoin(DeviceInfo peerInfo) throws RemoteException {
			registeredHandler.onPeerJoin(peerInfo);
		}

		public void onSelfLeave() throws RemoteException {
			registeredHandler.onSelfLeave();
		}

		public void onPeerLeave(DeviceInfo peerInfo) throws RemoteException {
			registeredHandler.onPeerLeave(peerInfo);
		}

		public void onReceive(DeviceInfo src, byte[] msg) throws RemoteException {
			registeredHandler.onReceive(src, msg);
		}

		public void onGetPeerDevices(DeviceInfo[] devices)
				throws RemoteException {
			registeredHandler.onGetPeerDevices(devices);
		}

	};

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mGroupService = IRouterGroupService.Stub.asInterface(service);
			try {
				if (groupId != null && mGroupHandler != null)
					mGroupService.joinGroup(groupId, peers, mGroupHandler);
			} catch (RemoteException e) {
				Log.e(TAG, "failed at joinGroup: " + e.getMessage());
			}
			sendBufferedMsgs();
		}

		public void onServiceDisconnected(ComponentName className) {
			mGroupService = null;
		}
	};

	public void bindService() {
		Intent intent = new Intent("com.xconns.peerdevicenet.GroupService");
		context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	public void unbindService() {
		Log.e(TAG, "unbindService() called for "+groupId);
		if (mGroupService == null)
			return;
		try {
			mGroupService.leaveGroup(groupId, mGroupHandler);
		} catch (RemoteException e) {
			Log.e(TAG, "failed at leaveGroup: " + e.getMessage());
		}
		// Detach our existing connection.
		context.unbindService(mConnection);
	}

	void sendBufferedMsgs() {
		int sz = sentMsgBuf.size();
		if (sz > 0) {
			for (int i = 0; i < sz; i++) {
				try {
					Message m = (Message) sentMsgBuf.get(i);
					switch (m.what) {
					case Router.MsgId.SEND_MSG:
						Object[] data = (Object[])m.obj;
						mGroupService.send(groupId, (DeviceInfo)data[0], (byte[])data[1]);
						break;
					case Router.MsgId.GET_CONNECTED_PEERS:
						mGroupService.getPeerDevices(groupId);
						break;
					default:
						break;
					}
				} catch (RemoteException e) {
					Log.e(TAG + ":" + groupId,
							"failed to send msg: " + e.getMessage());
				}
			}
			sentMsgBuf.clear();
		}
	}
	
	// IRouterGroupService API
	public void send(DeviceInfo dest, byte[] msg) {
		// send my msg
		if (mGroupService == null) {
			Message m = Message.obtain(null, Router.MsgId.SEND_MSG);
			Object[] data = new Object[2];
			data[0] = dest;
			data[1] = msg;
			m.obj = data;
			sentMsgBuf.add(m);
			return;
		}
		try {
			mGroupService.send(groupId, dest, msg);
		} catch (RemoteException e) {
			Log.e(TAG + ":" + groupId, "failed to send msg: " + e.getMessage());
		}
	}

	public void getPeerDevices() {
		if (mGroupService == null) {
			Message m = Message.obtain(null, Router.MsgId.GET_CONNECTED_PEERS);
			sentMsgBuf.add(m);
			return;
		}
		try {
			mGroupService.getPeerDevices(groupId);
		} catch (RemoteException e) {
			Log.e(TAG, "failed to getPeerDevices: " + e.getMessage());
		}
	}
}
