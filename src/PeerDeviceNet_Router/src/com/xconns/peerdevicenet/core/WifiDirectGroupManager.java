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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.util.Log;

@SuppressLint("NewApi")
public class WifiDirectGroupManager implements ChannelListener {
	
	public interface Handler {
		void onError(String msg);
		void onWifiDirectNotEnabled();
	}
	
	final static String TAG = "WifiDirectGroupManager";
	
	Handler handler = null;

	// data used to setup wifi-direct net
	WifiP2pManager manager = null;
	Channel channel = null;
	BroadcastReceiver receiver = null;
	final IntentFilter intentFilter = new IntentFilter();
	// info about wifi-direct net
	WifiP2pGroup groupInfo = null;
	//
	boolean isWifiDirectEnabled = false;
	boolean retryChannel = false;
	//
	Context context = null;

	public WifiDirectGroupManager(Context c, Handler h) {
		context = c;
		handler = h;
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		manager = (WifiP2pManager) context
				.getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(context, context.getMainLooper(), this);
		receiver = new WiFiDirectGroupManagerReceiver(this);
	}

	public void onResume() {
		context.registerReceiver(receiver, intentFilter);
	}

	public void onPause() {
		context.unregisterReceiver(receiver);
	}

	public void onDestroy() {
		reset();
	}

	void reset() {
		groupInfo = null;
	}

	// called by WifiDirect broadcaster
	void onEnabledStatus(boolean status) {
		isWifiDirectEnabled = status;
	}

	public void createNetwork() {
		if (!isWifiDirectEnabled) {
			handler.onWifiDirectNotEnabled();
		} else {
			manager.createGroup(channel, new ActionListener() {

				@Override
				public void onFailure(int reason) {
					handler.onError("failed to create group, error code: " + reason);
					Log.d(TAG, "failed to create group, error code: " + reason);
				}

				@Override
				public void onSuccess() {
					// TODO Auto-generated method stub
				}

			});
		}
	}

	public void removeNetwork() {
		if (!isWifiDirectEnabled) {
			handler.onWifiDirectNotEnabled();
		} else {
			manager.removeGroup(channel, new ActionListener() {

				@Override
				public void onFailure(int reason) {
					handler.onError("failed to remove group, error code: " + reason);
					Log.d(TAG, "failed to remove group, error code: " + reason);
				}

				@Override
				public void onSuccess() {
					// TODO Auto-generated method stub

				}

			});
		}
	}

	@Override
	public void onChannelDisconnected() {
		if (manager != null && !retryChannel) {
			Log.d(TAG, "Channel lost. Trying again");
			reset();
			retryChannel = true;
			channel = manager
					.initialize(context, context.getMainLooper(), this);
		} else {
			Log.d(TAG,
					"Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.");
		}
	}

}

class WiFiDirectGroupManagerReceiver extends BroadcastReceiver {

	private WifiDirectGroupManager groupManager = null;

	/**
	 * @param manager
	 *            WifiP2pManager system service
	 * @param channel
	 *            Wifi p2p channel
	 * @param activity
	 *            activity associated with the receiver
	 */
	public WiFiDirectGroupManagerReceiver(WifiDirectGroupManager wifiD) {
		super();
		this.groupManager = wifiD;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
	 * android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

			// UI update to indicate wifi p2p status.
			int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
			if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
				// Wifi Direct mode is enabled
				groupManager.onEnabledStatus(true);
				Log.d(WifiDirectGroupManager.TAG, "P2P state enabled");
			} else {
				groupManager.onEnabledStatus(false);
				groupManager.reset();
				Log.d(WifiDirectGroupManager.TAG, "P2P state disabled");
			}
			Log.d(WifiDirectGroupManager.TAG, "P2P state changed - " + state);
		}
	}
}
