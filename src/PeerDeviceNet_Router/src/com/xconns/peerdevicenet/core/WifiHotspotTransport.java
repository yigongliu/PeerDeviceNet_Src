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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.utils.Utils;
import com.xconns.peerdevicenet.utils.Utils.IntfAddr;

public class WifiHotspotTransport implements Transport {
	public final static String TAG = "WifiHotspotTransport";

	public final static String Unknown = "unKnownXXXHotspot2@7&info";

	WifiManager mWifiManager = null;
	WifiApManager wifiApMgr = null;
	WifiConfiguration wc = null;

	RouterService routerService = null;
	Transport.Handler handler = null;
	final IntentFilter intentFilter = new IntentFilter();

	boolean isWifiHotspotEnabled = false;
	
	DiscoveryLeaderThread scannerGO = null;

	
	NetInfo netInfo = null;
	IntfAddr intfAddr = null;
	
	private String hotspotPrefix = "192.168.43.";
	private ScheduledFuture<?> timerTask = null;

	public WifiHotspotTransport(RouterService c) {
		routerService = c;
	}

	public void onCreate(Handler h) {
		handler = h;

		mWifiManager = (WifiManager) routerService
				.getSystemService(Context.WIFI_SERVICE);

		intentFilter.addAction(WifiApManager.WIFI_AP_STATE_CHANGED_ACTION);
	}

	public void onDestroy() {
		reset(true);
	}

	public void onPause() {
		// unregister recver for wifi conn change
		routerService.unregisterReceiver(myWifiRecver);
	}

	public void onResume() {
		// register recver for wifi conn change
		routerService.registerReceiver(myWifiRecver, intentFilter);
	}

	public boolean isEnabled() {
		return isWifiHotspotEnabled;
	}

	public boolean isNetworkConnected() {
		// TODO Auto-generated method stub
		return netInfo != null;
	}

	public int getType() {
		return NetInfo.WiFiHotspot;
	}

	public NetInfo getNetworkInfo() {
		return netInfo;
	}

	public IntfAddr getIntfAddr() {
		return intfAddr;
	}

	public void configureNetwork() {
		routerService.startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
	}

	public void createNetwork() {
		// meaningful only for wifi-direct
	}

	public void removeNetwork() {
		// meaningful only for wifi-direct
	}

	public void startSearch(DeviceInfo myDevInfo, DeviceInfo grpLeader, int scanTimeout,
			Transport.SearchHandler h) {
		if (netInfo != null) {
			
			if (scannerGO != null) {
				scannerGO.start_scan(myDevInfo, scanTimeout, h);
				Log.d(TAG, "start wifi hotspot scanner");
			}
			Log.d(TAG, "onSearchStart: "+grpLeader);
			h.onSearchStart(grpLeader);
		}
	}

	public void stopSearch() {
		if (netInfo != null) {
			
			if (scannerGO != null) {
				scannerGO.stop_scan();
			}
		}
	}
	

	void reset(boolean fromGUI) {
		// stop scan
		stopSearch();

		if (scannerGO != null) {
			if (fromGUI)
				scannerGO.close();
			else
				scannerGO.closeFromInternal();
			scannerGO = null;
		}
		
		if (timerTask != null)
			timerTask.cancel(true);
		
		NetInfo ni = netInfo;

		// reset data
		netInfo = null;
		intfAddr = null;

		if (ni != null) {
			handler.onNetworkDisconnected(ni);
		}
	}
	
	class MyTimerCB implements Runnable {
		@Override
		public void run() {
			IntfAddr ia = Utils.getFirstWifiHotspotIntfAddr();
			if (ia != null) {
				Log.d(TAG, "wifi ap 1st intfAddr = "+ia.toString());
				updateNetworkSettings(wc, ia);
			} else {
				timerTask = routerService.timer.schedule(new MyTimerCB(), 3000, TimeUnit.MILLISECONDS);
			}
		}
	}

	private BroadcastReceiver myWifiRecver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (WifiApManager.WIFI_AP_STATE_CHANGED_ACTION.equals(action)) {
				Log.d(TAG, "recv WIFI_AP_STATE_CHANGED_ACTION");
				int rawWifiApState = intent.getIntExtra(
						WifiApManager.EXTRA_WIFI_AP_STATE,
						WifiApManager.WIFI_AP_STATE_UNKNOWN);
				int wifiApState = WifiApManager.getWifiApState(rawWifiApState);
				Log.d(TAG, "WIFI_AP_STATE_CHANGED_ACTION, raw state=" + rawWifiApState + ", state="+wifiApState);
				if (WifiApManager.WIFI_AP_STATE_ENABLED == wifiApState) {
					Log.d(TAG, "wifi ap is enabled");
					isWifiHotspotEnabled = true;
					handler.onTransportEnabled(NetInfo.WiFiHotspot, isWifiHotspotEnabled);
					wifiApMgr = new WifiApManager(mWifiManager);
					wc = wifiApMgr.getWifiApConfiguration();
					IntfAddr ia = Utils.getFirstWifiHotspotIntfAddr();
					if (ia != null) {
						Log.d(TAG, "wifi ap 1st intfAddr = "+ia.toString());
						updateNetworkSettings(wc, ia);
					} else {
						try {
							Log.d(TAG, "wifi hotspot ip is not set yet, wait 3 secs to get it");
							timerTask = routerService.timer.schedule(new MyTimerCB(), 3000, TimeUnit.MILLISECONDS);
						} catch (RejectedExecutionException re) {
							Log.e(TAG, "failed to schedule reget wifi hotspot ip: "
									+ re.getMessage());
						} catch (NullPointerException ne) {
							Log.e(TAG, "failed to schedule reget wifi hotspot ip: "
									+ ne.getMessage());
						}
					}
				}
				else if (WifiApManager.WIFI_AP_STATE_DISABLED == wifiApState
						|| WifiApManager.WIFI_AP_STATE_DISABLING == wifiApState) {
					Log.d(TAG, "wifi ap is disabling");
					isWifiHotspotEnabled = false;
					handler.onTransportEnabled(NetInfo.WiFiHotspot, isWifiHotspotEnabled);
					reset(false);
				}

			}

		}

	};

	void updateNetworkSettings(WifiConfiguration wc, IntfAddr intfAddr) {
		String ssid = Unknown;
		String passwd = "";
		int encrypt = NetInfo.NoPass;
		if (wc != null && wc.SSID != null) {
			ssid = wc.SSID;
			//if(wc.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN)) {
			if (wc.preSharedKey != null) {
				encrypt = NetInfo.WPA;
				passwd = wc.preSharedKey;
			} 
			//else if(wc.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.SHARED)) {
	    	else if(wc.wepKeys != null && wc.wepKeys[0] != null) {
				encrypt = NetInfo.WEP;
				passwd = wc.wepKeys[0];
			}
			if (wc.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE) || passwd == null) {
				encrypt = NetInfo.NoPass;
			}
			Log.d(TAG, "wifi ap ssid="+ssid+", pass="+passwd);
		}
		
		netInfo = new NetInfo(NetInfo.WiFiHotspot, ssid, encrypt, passwd, wc.hiddenSSID, null,
				intfAddr.intfName, intfAddr.addr, intfAddr.mcast);
		Log.d(TAG, "got netInfo: " + netInfo.toString());
		
		Log.d(TAG, "start hotspot scanner");
		if (scannerGO != null) {
			scannerGO.closeFromInternal();
		}
		scannerGO = new DiscoveryLeaderThread(routerService, netInfo,
				intfAddr.addr, routerService.searchTimeout, routerService.connTimeout);
		scannerGO.start();

		handler.onNetworkConnected(netInfo);
	}
}
