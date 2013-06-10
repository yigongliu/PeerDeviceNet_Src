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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;
import com.xconns.peerdevicenet.Router.ConnFailureCode;
import com.xconns.peerdevicenet.core.R;
import com.xconns.peerdevicenet.core.WifiDirectGroupManager;
import com.xconns.peerdevicenet.core.WifiHotspotTransport;
import com.xconns.peerdevicenet.utils.RouterConfig;
import com.xconns.peerdevicenet.utils.Utils;

interface DeviceHolder {
	void addConnectDevice(DeviceInfo dev);
}

public class ConnectionManager {
	final static String TAG = "ConnectionManager";

	// global defaults

	static final int CONNECTION_CONFIRM_DIALOG = 1;
	static final int CONNECTION_PIN_DIALOG = 2;
	static final int CONNECTION_FAIL_DIALOG = 3;
	static final int WIFI_DIRECT_WARNING_DIALOG = 4;
	static final int PEER_DIFF_SSL_DIALOG = 5;
	static final int LICENSE_DIALOG = 10;
	static final int TUTORIAL_DIALOG = 11;

	// preference keys
	public static final String PREF_KEY_SHUTDOWN = "shutdown";
	public static final String PREF_KEY_CLEANUP = "cleanup";
	public static final String PREF_KEY_ROT_CUBE = "rot_cube";
	public static final String PREF_KEY_PAINT = "paint";
	public static final String PREF_KEY_NAME = "DEVICE_NAME";
	public static final String PREF_KEY_NET = "NET_NAME";
	public static final String PREF_KEY_ADDR = "DEVICE_ADDR";
	public static final String PREF_KEY_PORT = "DEVICE_PORT";
	public static final String PREF_KEY_MAX_SESSIONS = "max_sessions";
	public static final String PREF_KEY_CONN_PIN = "conn_pin";
	public static final String PREF_KEY_AUTO_SCAN = "auto_scan";
	public static final String PREF_KEY_AUTO_CONN = "auto_conn";
	public static final String PREF_KEY_AUTO_ACCEPT = "auto_accept";
	public static final String PREF_KEY_SEARCH_TIMEOUT = "search_timeout";
	public static final String PREF_KEY_CONN_TIMEOUT = "conn_timeout";
	public static final String PREF_KEY_PEER_LIVE_TIMEOUT = "live_timeout";

	public static final String PREF_KEY_USE_SSL = "use_ssl";
	public static final String PREF_KEY_CONNECTOR = "connector";
	public static final String PREF_KEY_SCAN_NEARBY = "scan_nearby";
	public static final String PREF_KEY_CAT_GEN_SETTINGS = "gen_settings";
	public static final String PREF_KEY_CAT_CONN_DEVICES = "conn_devices";
	
	public static final String PREF_KEY_CAT_WIFIDIR_HOTSPOT = "wifidir_hotspot_cat";
	public static final String PREF_KEY_WIFIDIR_HOTSPOT = "wifidir_hotspot";

	public static final String PREF_KEY_LICENSE = "license";
	public static final String PREF_KEY_TUTORIAL = "tutorial";

	PreferenceActivity mContext = null;
	
	//remote intenting related
	// default session preference values
    public final static String DEF_NUM_SESSIONS = "10";
	// intent actions for remote intent service
    public static final String ACTION_MAX_SESSIONS_NUM_CHANGED = "ACTION_MAX_SESSIONS_NUM_CHANGED";
    public static final String ACTION_ACTIVE_NETWORK_DISCONNECT = "ACTION_ACTIVE_NETWORK_DISCONNECT";
    public static final String ACTION_ACTIVE_NETWORK_CONNECT = "ACTION_ACTIVE_NETWORK_CONNECT";
    public static final String ACTION_USE_SSL = "ACTION_USE_SSL";

	// -- conn GUI widgets --

	ArrayAdapter<String> askingPeers = null;
	ArrayList<String> confirmedPeers = new ArrayList<String>();
	AlertDialog connConfirmDialog = null;
	PreferenceCategory mGenSettingsCat = null;
	PreferenceCategory mConnDeviceCat = null;

	SharedPreferences sharedPref = null;

	WifiManager mWifiManager;

	DeviceInfo mDevice = new DeviceInfo();

	Preference mShutdownPref = null;
	Preference mCleanupPref = null;
	Preference mRotCubePref = null;
	Preference mPaintPref = null;
	
	Preference mLicensePref = null;
	Preference mTutorialPref = null;

	EditTextPreference mDeviceNamePref = null;

	NetListPreference mNetPref = null;
	String mNetName = null;
	HashMap<String, NetInfo> mNets = new HashMap<String, NetInfo>();
	ArrayList<CharSequence> netListEntries = new ArrayList<CharSequence>();
	ArrayList<CharSequence> netListValues = new ArrayList<CharSequence>();

	ProgressPreference mConnectorPref = null;
	boolean connectorSearch = false;
	ProgressPreference mScanNearbyPref = null;

	// --- conn_settings GUI widgets ---
	PreferenceCategory mWifiDirHotspotCat = null;
	CheckBoxPreference mWifiDirHotspotPref = null;
	boolean wifiDirectSupported = false;
	WifiDirectGroupManager grpMgr = null;
	NetInfo mCurrWifiDirectNet = null;
	
	//
	Preference mDeviceAddrPref = null;

	Preference mDevicePortPref = null;

	ListPreference mSearchTimeoutPref = null;
	String mSearchTimeout = null;

	ListPreference mLiveTimeoutPref = null;
	String mLiveTimeout = null;

	ListPreference mConnTimeoutPref = null;
	String mConnTimeout = null;

	ListPreference mMaxSessionsPref = null;
	String mMaxSessions = null;

	CheckBoxPreference mConnPINPref = null;
	boolean mConnPIN = false;

	CheckBoxPreference mAutoScanPref = null;
	boolean mAutoScan = false;

	CheckBoxPreference mAutoConnPref = null;
	boolean mAutoConn = false;

	CheckBoxPreference mAutoAcceptPref = null;
	boolean mAutoAccept = false;

	CheckBoxPreference mUseSSLPref = null;
	boolean useSSL = true;


	int max_sessions = 10;

	int scanStarted = 0;
	boolean scanToggleOn = false;
	int live_timeout = 4000;
	int scan_timeout = 30000; // 30 seconds
	int conn_timeout = 5000; // 5 seconds
	

	ConnectionManagerService connMgrService = null;
	boolean mInited = false;

	AlertDialog connFailDialog = null;
	AlertDialog sslDiffDialog = null;
	int connFailCode = -1;
	DeviceInfo connFailDevice = null;
	DeviceInfo sslDiffDevice = null;
	boolean peerSSL = true;
	
	CharSequence wifi_title = null;
	CharSequence wifi_hotspot_title = null;
	CharSequence wifi_direct_title = null;
	CharSequence config_nets = null;
	CharSequence config_new_nets = null;
	CharSequence reject_title = null;
	CharSequence reject_postfix = null;
	CharSequence reject_connmgr_inactive = null;
	CharSequence reject_pin_mismatch = null;
	CharSequence reject_by_peer = null;
	CharSequence reject_by_self_conn = null;
	CharSequence seconds = null;
	CharSequence sessions = null;
	CharSequence netName = null;
	CharSequence passphrase = null;
	CharSequence checkSetting = null;
	CharSequence peer_ssl_diff_msg = null;

	public ConnectionManager(PreferenceActivity c) {
		mContext = c;
		wifiDirectSupported = mContext.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_WIFI_DIRECT);
	}

	public void onCreate() {
		Log.d(TAG, "onCreate");

		mInited = true;
		mWifiManager = (WifiManager) mContext
				.getSystemService(Context.WIFI_SERVICE);

		if (wifiDirectSupported) {
			grpMgr = new WifiDirectGroupManager(mContext, new WifiDirectGroupManager.Handler() {

				@Override
				public void onError(String msg) {
					Log.d(TAG, "Group add/del error: "+msg);
					mContext.showDialog(WIFI_DIRECT_WARNING_DIALOG);
				}

				@Override
				public void onWifiDirectNotEnabled() {
					Log.d(TAG, "Please enable wifi direct first");
					mContext.showDialog(WIFI_DIRECT_WARNING_DIALOG);
				}
				
			});
		}

		sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
		PreferenceManager.setDefaultValues(mContext, R.xml.conn_prefs, true);
		PreferenceManager.setDefaultValues(mContext, R.xml.conn_setting_prefs,
				true);

		initNameAddrPort();
		initConnSettings();

		askingPeers = new ArrayAdapter<String>(mContext,
				android.R.layout.select_dialog_multichoice);

		wifi_title = mContext.getResources().getText(R.string.wifi_name);
		wifi_hotspot_title = mContext.getResources().getText(
				R.string.wifi_hotspot_name);
		wifi_direct_title = mContext.getResources().getText(
				R.string.wifi_direct_name);
		config_nets = mContext.getResources().getText(R.string.config_nets);
		config_new_nets = mContext.getResources().getText(
				R.string.config_new_nets);
		reject_title = mContext.getResources().getText(
				R.string.peer_reject_title);
		reject_postfix = mContext.getResources().getText(
				R.string.peer_reject_postfix);
		reject_connmgr_inactive = mContext.getResources().getText(
				R.string.peer_reject_connmgr_inactive);
		reject_pin_mismatch = mContext.getResources().getText(
				R.string.peer_reject_pin_mismatch);
		reject_by_peer = mContext.getResources().getText(
				R.string.peer_reject_by_user);
		reject_by_self_conn = mContext.getResources().getText(
				R.string.cant_connect_self);
		peer_ssl_diff_msg = mContext.getResources().getText(
				R.string.peer_ssl_diff_msg);
		seconds = mContext.getResources().getText(R.string.seconds);
		sessions = mContext.getResources().getText(R.string.sessions);
		netName = mContext.getResources().getText(R.string.netname);
		passphrase = mContext.getResources().getText(R.string.passphrase);
		checkSetting = mContext.getResources().getText(R.string.check_setting);

		// start/bind-to connection mgr service service
		Intent intent = new Intent(mContext, ConnectionManagerService.class);
		mContext.startService(intent); // make service live during screen rotate
		mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

	}

	public void attachConnectionsGUI(PreferenceScreen prefScreen) {
		mDeviceNamePref = (EditTextPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_NAME);
		mDeviceNamePref.setSummary(mDevice.name);

		mNetPref = (NetListPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_NET);
		mNetPref.setCM(this);
		// mNetName = sharedPref.getString(ConnectionManager.PREF_KEY_NET,
		// null);
		mNetPref.setSummary(config_new_nets);
		if (netListEntries.size() < 1) {
			netListEntries.add(config_new_nets);
			netListValues.add(config_new_nets);
		}

		mUseSSLPref = (CheckBoxPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_USE_SSL);
		mUseSSLPref.setChecked(useSSL);

		mConnectorPref = (ProgressPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_CONNECTOR);
		mConnectorPref
		.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				boolean startNew = (connMgrService != null && 
						!connMgrService.connectorSessionActive());
				//stop search
				stop_scan();
				if (startNew) {
					Intent i = new Intent(Router.ACTION_CONNECTOR);
					mContext.startActivity(i);
				}
				return true;
			}
		});

		mScanNearbyPref = (ProgressPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_SCAN_NEARBY);
		if (mDevice.addr == null)
			toggleConnectionButtons(false);

		mScanNearbyPref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						toggle_scan();
						return true;
					}
				});

		mConnDeviceCat = (PreferenceCategory) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_CAT_CONN_DEVICES);

	}

	public void attachConnectionSettingsGUI(PreferenceScreen prefScreen) {
		mWifiDirHotspotCat = (PreferenceCategory) prefScreen
		.findPreference(ConnectionManager.PREF_KEY_CAT_WIFIDIR_HOTSPOT);
		mWifiDirHotspotPref = (CheckBoxPreference) prefScreen
		.findPreference(ConnectionManager.PREF_KEY_WIFIDIR_HOTSPOT);
		mWifiDirHotspotPref.setChecked(false);		
		mWifiDirHotspotPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				Log.d(TAG, "mWifiDirHotspotPref clicked");
				if(mCurrWifiDirectNet == null) {
					grpMgr.createNetwork();
				} else {
					grpMgr.removeNetwork();
				}
				mWifiDirHotspotPref.setChecked(false);
				return true;
			}
		});
		if (!wifiDirectSupported) {
			prefScreen.removePreference(mWifiDirHotspotCat);
			mWifiDirHotspotCat.setEnabled(false);
			Log.d(TAG, "wifi direct not supported XXXXX");
		} else {
			Log.d(TAG, "wifi direct supported !!!!!!");		
			if (mCurrWifiDirectNet != null) {
				mWifiDirHotspotPref.setTitle(netName.toString()+": "+(mCurrWifiDirectNet.name!=null?mCurrWifiDirectNet.name:""));
				mWifiDirHotspotPref.setSummary(passphrase.toString()+": "+(mCurrWifiDirectNet.pass!=null?mCurrWifiDirectNet.pass:""));
				mWifiDirHotspotPref.setChecked(true);
			}
		}

		mDeviceAddrPref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_ADDR);
		mDeviceAddrPref.setSummary((mDevice.addr != null) ? mDevice.addr : "");

		mDevicePortPref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_PORT);
		mDevicePortPref.setSummary((mDevice.port != null) ? mDevice.port : "");

		mSearchTimeoutPref = (ListPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_SEARCH_TIMEOUT);
		mSearchTimeoutPref.setSummary(mSearchTimeout + seconds);

		mMaxSessionsPref = (ListPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_MAX_SESSIONS);
		mMaxSessionsPref.setSummary(mMaxSessions + sessions);

		mLiveTimeoutPref = (ListPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_PEER_LIVE_TIMEOUT);
		mLiveTimeoutPref.setSummary(mLiveTimeout + seconds);

		mConnTimeoutPref = (ListPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_CONN_TIMEOUT);
		mConnTimeoutPref.setSummary(mConnTimeout + seconds);

		mConnPINPref = (CheckBoxPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_CONN_PIN);
		mConnPIN = mConnPINPref.isChecked();

		mAutoScanPref = (CheckBoxPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_AUTO_SCAN);
		mAutoScan = mAutoScanPref.isChecked();

		mAutoConnPref = (CheckBoxPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_AUTO_CONN);
		mAutoConn = mAutoConnPref.isChecked();

		mAutoAcceptPref = (CheckBoxPreference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_AUTO_ACCEPT);
		mAutoAccept = mAutoAcceptPref.isChecked();

	}

	public void attachActionsGUI(PreferenceScreen prefScreen) {
		mRotCubePref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_ROT_CUBE);
		mRotCubePref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						Log.d(TAG, "Rot cube called");
						Intent intent = new Intent(
								mContext,
								com.xconns.peerdevicenet.apps.TouchRotateActivity.class);
						mContext.startActivity(intent);
						return true;
					}
				});

		mPaintPref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_PAINT);
		mPaintPref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						Log.d(TAG, "Paint called");
						Intent intent = new Intent(
								mContext,
								com.xconns.peerdevicenet.apps.FingerPaint.class);
						mContext.startActivity(intent);
						return true;
					}
				});

		mShutdownPref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_SHUTDOWN);
		mShutdownPref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						Log.d(TAG, "shutdown called");
						shutdownAll();
						return true;
					}
				});

		mCleanupPref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_CLEANUP);
		mCleanupPref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						Log.d(TAG, "cleanup called");
						Intent intent = new Intent(Router.ACTION_ROUTER_RESET);
						mContext.startService(intent);
						return true;
					}
				});

		mLicensePref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_LICENSE);
		mLicensePref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						Log.d(TAG, "show license info");
						mContext.showDialog(LICENSE_DIALOG);
						return true;
					}
				});

		mTutorialPref = (Preference) prefScreen
				.findPreference(ConnectionManager.PREF_KEY_TUTORIAL);
		mTutorialPref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						Log.d(TAG, "show tutorial");
						mContext.showDialog(TUTORIAL_DIALOG);
						return true;
					}
				});

	}

	void initConnSettings() {
		mSearchTimeout = sharedPref.getString(
				ConnectionManager.PREF_KEY_SEARCH_TIMEOUT, "30");
		scan_timeout = Integer.parseInt(mSearchTimeout) * 1000;

		mMaxSessions = sharedPref.getString(
				ConnectionManager.PREF_KEY_MAX_SESSIONS,
				DEF_NUM_SESSIONS);
		max_sessions = Integer.parseInt(mMaxSessions);

		mLiveTimeout = sharedPref.getString(
				ConnectionManager.PREF_KEY_PEER_LIVE_TIMEOUT, "4");
		live_timeout = Integer.parseInt(mLiveTimeout) * 1000;

		mConnTimeout = sharedPref.getString(
				ConnectionManager.PREF_KEY_CONN_TIMEOUT, "10");
		conn_timeout = Integer.parseInt(mConnTimeout) * 1000;

		mConnPIN = sharedPref.getBoolean(ConnectionManager.PREF_KEY_CONN_PIN,
				false);
		mAutoScan = sharedPref.getBoolean(ConnectionManager.PREF_KEY_AUTO_SCAN,
				true);
		mAutoConn = sharedPref.getBoolean(ConnectionManager.PREF_KEY_AUTO_CONN,
				true);
		mAutoAccept = sharedPref.getBoolean(
				ConnectionManager.PREF_KEY_AUTO_ACCEPT, false);
		useSSL = sharedPref.getBoolean(
				ConnectionManager.PREF_KEY_USE_SSL, RouterConfig.DEF_USE_SSL);
	}


	void shutdownAll() {
		Log.d(TAG, "shutdownAll");
		Intent intent = new Intent(Router.ACTION_ROUTER_RESET);
		mContext.startService(intent);
		/*
		intent = new Intent(mContext, ConnectionManagerService.class);
		mContext.stopService(intent);
		*/
		onDestroy(false);
		/*
		intent = new Intent(mContext,
				com.xconns.peerdevicenet.rmtintent.RemoteIntentService.class);
		mContext.stopService(intent);
		*/
		intent = new Intent(Router.ACTION_ROUTER_SHUTDOWN);
		mContext.startService(intent);		
		//
		intent = new Intent(mContext,
				com.xconns.peerdevicenet.core.RouterService.class);
		mContext.stopService(intent);
		mContext.finish();
	}

	void toggleConnectionButtons(boolean f) {
		if (f == false) {
			if (mScanNearbyPref != null)
				mScanNearbyPref.setEnabled(false);
			//if (mManualAddPref != null)
				//mManualAddPref.setEnabled(false);
		} else {
			if (mScanNearbyPref != null)
				mScanNearbyPref.setEnabled(true);
			//if (mManualAddPref != null)
				//mManualAddPref.setEnabled(true);
		}
	}
	
	void toggle_scan() {
		if (!scanToggleOn || scanStarted <= 0) {
			if(connMgrService != null && !connMgrService.connectorSessionActive()) {
				start_scan();
			}
		} else {
			stop_scan();
		}
	}
	
	void start_scan() {
		Log.d(TAG, "start_scan");
		scanToggleOn = true;
		Log.d(TAG, "start_scan started="+scanStarted);
		if(scanStarted <= 0) 
			scanStarted = 1;
		else 
			scanStarted++;
		if (mConnPIN) {
			mContext.showDialog(CONNECTION_PIN_DIALOG);
		} else {
			if (connMgrService != null) {
				connMgrService.setConnPIN(connMgrService.DEFAULT_PIN);
				connMgrService.startPeerSearch(null);
				if (mScanNearbyPref != null) {
					mScanNearbyPref.startProgress();
				}
			}
		}
	}
	
	void stop_scan() {
		Log.d(TAG, "stop_scan");
		scanToggleOn = false;
		
		if (connMgrService != null && (scanStarted > 0 || connMgrService.connectorSessionActive())) {
			connMgrService.stopPeerSearch();
			
			if (mScanNearbyPref != null) {
				mScanNearbyPref.stopProgress();
			}
			
			if(mConnectorPref != null) {
				mConnectorPref.stopProgress();
			}
		}
	}

	void onNetworkConnected(NetInfo net) {
		if (net == null || mNets.containsKey(net.name))
			return;
		Log.d(TAG, "recv NetworkConnected: " + net.toString());
		// update gui - add net to list
		mNets.put(net.name, net);
		netListValues.add(net.name);
		if (net.type == NetInfo.WiFiHotspot && WifiHotspotTransport.Unknown.equals(net.name)) {
			netListEntries.add(NetInfo.NetTypeName(net.type) + ": " + checkSetting);
		} else {
			netListEntries.add(NetInfo.NetTypeName(net.type) + ": " + net.name);
		}
		for (int i = 0; i < netListValues.size(); i++)
			Log.d(TAG, "onNetworkConnected, net " + netListEntries.get(i));
		if (mNetPref != null) {
			mNetPref.setEntries(netListEntries.toArray(new CharSequence[0]));
			mNetPref.setEntryValues(netListValues.toArray(new CharSequence[0]));
		}
		if (net.type == NetInfo.WiFiDirect) {
			mCurrWifiDirectNet = net;
			if (wifiDirectSupported && mWifiDirHotspotPref != null) {
				mWifiDirHotspotPref.setTitle(netName.toString()+": "+(net.name!=null?net.name:""));
				mWifiDirHotspotPref.setSummary(passphrase.toString()+": "+(net.pass!=null?net.pass:""));
				mWifiDirHotspotPref.setChecked(true);
			}
		}
		// validate, choose a net?
		NetInfo an = connMgrService.getActNet();
		if (an == null/*
				|| (an.type == NetInfo.WiFi && net.type == NetInfo.WiFiDirect)
				|| (an.type == NetInfo.WiFi && net.type == NetInfo.WiFiHotspot)*/) {
			connMgrService.activateNetwork(net);
			// only update gui when onNetworkActivated
		}
	}

	void onNetworkDisconnected(NetInfo net) {
		if (net == null)
			return;
		Log.d(TAG, "recv NetworkDisconnected: " + net.toString());
		// if no more connection left, show warning dialog
		// update gui - remove net from list, if net is active, choose another
		// one
		mNets.remove(net.name);
		netListValues.remove(net.name);
		if (net.type == NetInfo.WiFiHotspot && WifiHotspotTransport.Unknown.equals(net.name)) {
			netListEntries.remove(NetInfo.NetTypeName(net.type) + ": " + checkSetting);
		} else {
			netListEntries.remove(NetInfo.NetTypeName(net.type) + ": " + net.name);
		}
		/*
		for (int i = 0; i < netListValues.size(); i++)
			Log.d(TAG, "onNetworkDisconnected, net " + netListEntries.get(i));
			*/
		if (mNetPref != null) {
			mNetPref.setEntries(netListEntries.toArray(new CharSequence[0]));
			mNetPref.setEntryValues(netListValues.toArray(new CharSequence[0]));
		}
		if (net.type == NetInfo.WiFiDirect) {
			mCurrWifiDirectNet = null;
			if (wifiDirectSupported && mWifiDirHotspotPref != null) {
				mWifiDirHotspotPref.setTitle(R.string.title_wifidir_hotspot);
				mWifiDirHotspotPref.setSummary(R.string.summary_wifidir_hotspot);
				mWifiDirHotspotPref.setChecked(false);
			}
		}
		NetInfo an = connMgrService.getActNet();
		if (an != null && an.type == net.type) { 
			// active net is disconnected
			
			//remove all peer devices
			removeAllPeerDevices();
			
			//turn off scan
			if (scanStarted > 0) {
				stop_scan();
			}
			
			// if data in transfer, tell RemoteIntentService to show warning dialog
			Intent intent = new Intent(ACTION_ACTIVE_NETWORK_DISCONNECT);
			mContext.startService(intent);			
			
			// activate another connected network
			NetInfo[] nets = connMgrService.getNets();
			if (nets != null && nets.length > 0) {
				for (NetInfo n : nets) {
					if (n.type != an.type) {
						connMgrService.activateNetwork(n);
						return;
					}
				}
			}
			connMgrService.activateNetwork(null);
		}
	}

	void onNetworkActivated(NetInfo net) {
		Log.d(TAG, "recv NetworkActivated: "
				+ (net != null ? net.toString() : "null"));
		if (net == null) {
			// reset device info, since no net conn
			mDevice.addr = null;
			mDevice.port = null;
			showDeviceInfo(mDevice);
		}
		//notify remote intnent service
		if (net != null) {			
			Intent intent = new Intent(ACTION_ACTIVE_NETWORK_CONNECT);
			intent.putExtra(Router.NET_INFO, net);
			mContext.startService(intent);			
		}
		// update gui - highlight the active net
		showActiveNet(net);
		// query my new device info in new net
		if (net != null && connMgrService != null) {
			connMgrService.connClient.getPeerDevices();
			connMgrService.getDeviceInfo();
		}
	}
	
	// start scan if configured
	void startScanAfterGetDevInfo(NetInfo net) {
		if (!connMgrService.connectorSessionActive()) {
			connectorSearch = false;
			if (mConnectorPref != null) {
				mConnectorPref.stopProgress();
			}
			if (mAutoScan && net.addr != null && net.addr.length() > 0
					&& net.mcast && (scanStarted <= 0)) {
				Log.d(TAG, "spin1 from NetActivated");
				start_scan();
			}
		} else {
			Log.d(TAG, "spin2 from NetActivated");
			connectorSearch = true;
			if (mConnectorPref != null) {
				mConnectorPref.startProgress();
			}
		}
	}

	void onGetActiveNetwork(NetInfo an) {
		Log.d(TAG, "recv OnGetActiveNetwork: "
				+ (an != null ? an.toString() : "null"));
		if (an != null) {
			/*
			// validate if more proper nets exist
			NetInfo[] nets = connMgrService.getNets();
			for (NetInfo net : nets) {
				if (an.type == NetInfo.WiFi && net.type == NetInfo.WiFiDirect) {
					connMgrService.activateNetwork(net);
					return;
				}
			}
			*/
			// update gui - highlight the active net
			showActiveNet(an);
			if (connMgrService != null) {
				connMgrService.connClient.getPeerDevices();
				connMgrService.getDeviceInfo();
			}
		} else {
			NetInfo[] nets = connMgrService.getNets();
			if (nets != null && nets.length > 0) {
				for (NetInfo net : nets) {
					if (net.type == NetInfo.WiFiDirect) {
						connMgrService.activateNetwork(net);
						return;
					}
				}
				connMgrService.activateNetwork(nets[0]);
			}
		}
	}

	void onGetNetworks(NetInfo[] nets) {
		Log.d(TAG, "recv OnGetNetworks.");
		if (nets != null && nets.length > 0) {
			Log.d(TAG, "recv OnGetNetworks, got net, retrv actNet");
			// update gui - fill the list of nets; now just printing
			for (NetInfo net : nets)
				if (!mNets.containsKey(net.name)) {
					Log.d(TAG, "onGetNetworks: " + net.toString());
					mNets.put(net.name, net);
					netListValues.add(net.name);
					if (net.type == NetInfo.WiFiHotspot && WifiHotspotTransport.Unknown.equals(net.name)) {
						netListEntries.add(NetInfo.NetTypeName(net.type) + ": " + checkSetting);
					} else {
						netListEntries.add(NetInfo.NetTypeName(net.type) + ": " + net.name);
					}


					if (mNetPref != null) {
						mNetPref.setEntries(netListEntries
								.toArray(new CharSequence[0]));
						mNetPref.setEntryValues(netListValues
								.toArray(new CharSequence[0]));
					}
					
					if (net.type == NetInfo.WiFiDirect) {
						mCurrWifiDirectNet = net;
						if (wifiDirectSupported && mWifiDirHotspotPref != null) {
							mWifiDirHotspotPref.setTitle(netName.toString()+": "+(net.name!=null?net.name:""));
							mWifiDirHotspotPref.setSummary(passphrase.toString()+": "+(net.pass!=null?net.pass:""));
							mWifiDirHotspotPref.setChecked(true);
						}
					}
				}
			for (int i = 0; i < netListValues.size(); i++)
				Log.d(TAG, "onNetworkConnected, net " + netListEntries.get(i));

			connMgrService.getActiveNetwork();
		}/* else if (connMgrService != null && !connMgrService.connectorSessionActive()){ //no network, start connector
			Intent i = new Intent(Router.ACTION_CONNECTOR);
			mContext.startActivity(i);
		}*/
	}

	void showActiveNet(NetInfo net) {
		if (mNetPref == null) return; //GUI not shown yet
		if (net != null) {
			mNetPref.setValue(net.name);
			if (net.type == NetInfo.WiFiHotspot && WifiHotspotTransport.Unknown.equals(net.name)) {
				mNetPref.setSummary(checkSetting);
			} else {
				mNetPref.setSummary(net.name);
			}
			switch (net.type) {
			case NetInfo.WiFi:
				mNetPref.setTitle(wifi_title);
				break;
			case NetInfo.WiFiHotspot:
				mNetPref.setTitle(wifi_hotspot_title);
				break;
			case NetInfo.WiFiDirect:
				mNetPref.setTitle(wifi_direct_title);
				break;
			default:
				break;
			}
			toggleConnectionButtons(true);
			//update netPrefList index
			for(int i=0; i<netListValues.size();i++) {
				CharSequence cs = netListValues.get(i);
				if (net.name.equals(cs)) {
					mNetPref.setSel(i);
				}
			}
		} else {
			mNetPref.setTitle(config_nets);
			mNetPref.setSummary(config_new_nets);
			toggleConnectionButtons(false);
			mNetPref.setSel(-1);
		}
	}
	
	public void activateNet(String mNetName) {
		NetInfo net = mNets.get(mNetName);
		if (net != null && connMgrService != null) {
			//remove all peer devices; comment because it should be done from routerService
			//removeAllPeerDevices();
			
			//turn off scan
			stop_scan();
			
			connMgrService.activateNetwork(net);
		}
	}

	// start call-chain:
	// getNetworks()->getActiveNetwork()->getPeerDevices()/getDeviceInfo()
	void getPeerDeviceNetInfo() {
		if (connMgrService != null) {
			connMgrService.getNetworks();
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			ConnectionManagerService.LocalBinder binder = (ConnectionManagerService.LocalBinder) service;
			connMgrService = binder.getService();
			Log.d(TAG, "ConnectionManagerService connected");
			// attach to remote intent service to allow it call back
			connMgrService.setConnMgr(ConnectionManager.this);
			connMgrService.setConnParams(mAutoConn, mAutoAccept);
			connMgrService.setConnectionInfo(live_timeout, conn_timeout, scan_timeout, useSSL);
			connMgrService.saveDeviceInfo(mDevice);

			// in case we miss it at onResume()
			getPeerDeviceNetInfo();
		}

		public void onServiceDisconnected(ComponentName arg0) {
			connMgrService = null;
			Log.d(TAG, "ConnectionManagerService disconnected");
		}
	};

	public void onDestroy(boolean configChange) {
		Log.d(TAG, "ConnectionManager onDestroy");

		if (connMgrService != null) {
			Log.d(TAG, "ConnectionManager destroyed");
			connMgrService.onConnMgrDestroy(configChange);
			mContext.unbindService(mConnection);
			connMgrService = null;
		}
		if (!configChange) {
			if (grpMgr != null) {
				grpMgr.onDestroy();
			}

			Log.d(TAG, "stop ConnectionManagerService");
			Intent intent = new Intent(mContext, ConnectionManagerService.class);
			mContext.stopService(intent);
		}
	}

	public void onResume() {
		Log.d(TAG, "onResume");
		if (grpMgr != null) {
			grpMgr.onResume();
		}
		getPeerDeviceNetInfo();
		/*
		if (scanStarted > 0 && mScanNearbyPref != null) {
			Log.d(TAG, "spin1 from onResume");
			mScanNearbyPref.startProgress();
		}
		*/
	}

	public void onPause() {
		Log.d(TAG, "onPause");
		if (scanStarted > 0) {
			stop_scan();
		}
		if (grpMgr != null) {
			grpMgr.onPause();
		}
	}

	Dialog onCreateDialog(int id) {
		switch (id) {
		case CONNECTION_CONFIRM_DIALOG:
			connConfirmDialog = new AlertDialog.Builder(mContext)
					.setIcon(R.drawable.router_icon)
					.setTitle(R.string.conn_confirm)
					.setCancelable(false)
					.setMultiChoiceItems(new CharSequence[0], null,
							connConfirmHandler)
					.setPositiveButton(R.string.accept,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									handleConnConfirm();
									mContext
											.removeDialog(CONNECTION_CONFIRM_DIALOG);
									resetConnSession();
									connConfirmDialog = null;
								}
							})
					.setNegativeButton(R.string.reject_all,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									handleConnCancel();
									mContext
											.removeDialog(CONNECTION_CONFIRM_DIALOG);
									resetConnSession();
									connConfirmDialog = null;
								}
							}).create();
			return connConfirmDialog;

		case CONNECTION_FAIL_DIALOG:
			connFailDialog = new AlertDialog.Builder(mContext)
					.setTitle(R.string.peer_reject_title)
					.setIcon(R.drawable.router_icon)
					.setMessage(R.string.peer_reject_connmgr_inactive)
					.setPositiveButton(R.string.ok,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {
									// dialogInterface.dismiss();
									mContext
											.removeDialog(CONNECTION_FAIL_DIALOG);
								}
							}).create();
			return connFailDialog;

		case PEER_DIFF_SSL_DIALOG:
			sslDiffDialog = new AlertDialog.Builder(mContext)
					.setTitle(R.string.peer_ssl_diff_title)
					.setIcon(R.drawable.router_icon)
					.setMessage(R.string.peer_ssl_diff_msg)
					.setPositiveButton(R.string.ok,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {
									// dialogInterface.dismiss();
									mContext
											.removeDialog(PEER_DIFF_SSL_DIALOG);
								}
							}).create();
			return sslDiffDialog;

		case CONNECTION_PIN_DIALOG:
			LayoutInflater factory = LayoutInflater.from(mContext);
			final EditText pinEntry = (EditText) factory.inflate(
					R.layout.pin_entry, null);
			return new AlertDialog.Builder(mContext)
					.setIcon(R.drawable.router_icon)
					.setTitle(R.string.pin_entry)
					.setCancelable(false)
					.setView(pinEntry)
					.setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									String pin = pinEntry.getText().toString();
									connMgrService.setConnPIN(pin);
									connMgrService.startPeerSearch(null);
									mScanNearbyPref.startProgress();
									mContext
											.removeDialog(CONNECTION_PIN_DIALOG);
								}
							}).create();

		case WIFI_DIRECT_WARNING_DIALOG:
			return new AlertDialog.Builder(mContext)
					.setTitle(R.string.wifidir_warning_title)
					.setIcon(R.drawable.router_icon)
					.setMessage(R.string.wifidir_warning)
					.setPositiveButton(R.string.ok,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {

									// dialogInterface.dismiss();
									mContext
											.removeDialog(WIFI_DIRECT_WARNING_DIALOG);
									
									//ask user to turn on wifi direct
									Log.d(TAG, "ask user to turn on wifi direct");
									try {
										if (Utils.ANDROID_VERSION >= 16) {
											Intent in = new Intent(
													Settings.ACTION_WIFI_SETTINGS);
											mContext.startActivity(in);
										} else {
											Intent in = new Intent(
													Settings.ACTION_WIRELESS_SETTINGS);
											mContext.startActivity(in);
										}
									} catch (ActivityNotFoundException anf) {
										Log.d(TAG,
												"no activity for : Settings.ACTION_WIRELESS_SETTINGS"
														+ anf.getMessage());
										Intent in = new Intent(
												WifiManager.ACTION_PICK_WIFI_NETWORK);
										mContext.startActivity(in);
									}
								}
					})
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									mContext
											.removeDialog(WIFI_DIRECT_WARNING_DIALOG);
								}
							}).create();

		case LICENSE_DIALOG:
			// Show the Eula
			PackageInfo pi = null;
			try {
				pi = mContext.getPackageManager().getPackageInfo(
						mContext.getPackageName(),
						PackageManager.GET_ACTIVITIES);
			} catch (PackageManager.NameNotFoundException e) {
				e.printStackTrace();
			}
			String title = mContext.getString(R.string.license_agreement) + " v"
					+ pi.versionName;

			// Includes the updates as well so users know what changed.
			// String message = /* mActivity.getString(R.string.updates) +
			// "\n\n" +
			// */getString(R.string.eula);
			String message = null;
			InputStream is = mContext.getResources().openRawResource(R.raw.eula);
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			int i;
			try {
				i = is.read();
				while (i != -1) {
					bo.write(i);
					i = is.read();
				}
				is.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
			message = bo.toString();

			return new AlertDialog.Builder(mContext)
					.setTitle(title)
					.setIcon(R.drawable.router_icon)
					.setMessage(message)
					.setCancelable(false)
					.setPositiveButton(R.string.ok,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {
									mContext.removeDialog(LICENSE_DIALOG);
								}
							}).create();
		case TUTORIAL_DIALOG:
			message = null;
			is = mContext.getResources().openRawResource(R.raw.intro);
			bo = new ByteArrayOutputStream();
			try {
				i = is.read();
				while (i != -1) {
					bo.write(i);
					i = is.read();
				}
				is.close();
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
			message = bo.toString();

			return new AlertDialog.Builder(mContext)
					.setTitle(R.string.intro_title)
					.setIcon(R.drawable.router_icon)
					.setMessage(message)
					.setCancelable(false)
					.setPositiveButton(R.string.ok,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {
									mContext.removeDialog(TUTORIAL_DIALOG);
								}
							}).create();
		default:
			break;
		}
		return null;
	}

	void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case CONNECTION_CONFIRM_DIALOG:
			connConfirmDialog.getListView().setAdapter(askingPeers);
			break;
		case PEER_DIFF_SSL_DIALOG:
			StringBuilder sb = new StringBuilder();
			if (sslDiffDevice != null) {
				sb.append(peer_ssl_diff_msg)
						.append(sslDiffDevice.name);
			}
			sslDiffDialog.setMessage(sb.toString());
			break;
		case CONNECTION_FAIL_DIALOG:
			sb = new StringBuilder();
			if (connFailDevice != null) {
				sb.append(connFailDevice.name).append(" (")
						.append(connFailDevice.addr).append(") ");
			}
			switch (connFailCode) {
			case ConnFailureCode.FAIL_CONNMGR_INACTIVE:
				connFailDialog.setTitle(reject_title);
				sb.insert(0, " ").insert(0, reject_postfix).insert(0, ". ")
						.insert(0, reject_connmgr_inactive);
				connFailDialog.setMessage(sb.toString());
				break;
			case ConnFailureCode.FAIL_PIN_MISMATCH:
				connFailDialog.setTitle(reject_title);
				sb.insert(0, " ").insert(0, reject_postfix).insert(0, ". ")
						.insert(0, reject_pin_mismatch);
				connFailDialog.setMessage(sb.toString());
				break;
			case ConnFailureCode.FAIL_CONN_SELF:
				connFailDialog.setTitle(reject_title);
				sb.insert(0, " ").insert(0, reject_postfix).insert(0, ". ")
						.insert(0, reject_by_self_conn);
				connFailDialog.setMessage(sb.toString());
				break;
			case ConnFailureCode.FAIL_REJECT_BY_USER:
				connFailDialog.setTitle(reject_title);
				sb.insert(0, " ").insert(0, reject_postfix).insert(0, ". ")
						.insert(0, reject_by_peer);
				connFailDialog.setMessage(sb.toString());
				break;
			/*
			 * don't report the following 2 case
			 * ConnFailureCode.FAIL_CONN_EXIST: break; case
			 * ConnFailureCode.FAIL_UNKNOWN_PEER: break;
			 */
			default:
				Log.d(TAG, "invalid conn fail code = " + connFailCode);
			}
			break;
		default:
			break;
		}
	}

	public void initNameAddrPort() {
		// only need to init name here now
		// addr & port are decided at lower router layer

		mDevice.name = sharedPref.getString(PREF_KEY_NAME, null);

		if (mDevice.name == null) {
			SharedPreferences.Editor editor = sharedPref.edit();
			mDevice.name = android.os.Build.MODEL;
			editor.putString(PREF_KEY_NAME, mDevice.name);
			// mDeviceNamePref.setText(mDeviceName);
			editor.commit();
		}
	}

	OnMultiChoiceClickListener connConfirmHandler = new DialogInterface.OnMultiChoiceClickListener() {
		public void onClick(DialogInterface dialog, int which, boolean isChecked) {
			String item = askingPeers.getItem(which);
			if (isChecked) {
				confirmedPeers.add(item);
			} else {
				confirmedPeers.remove(item);
			}
		}
	};

	void handleConnConfirm() {
		for (String peer : confirmedPeers) {
			int sep_pos1 = peer.indexOf('(');
			int sep_pos2 = peer.indexOf(')');
			String peer_addr = peer.substring(sep_pos1 + 1, sep_pos2);
			Log.d(TAG, "accept peer's connection attempt from: " + peer_addr);
			connMgrService.acceptPeer(peer_addr);
		}
		int num = askingPeers.getCount();
		for (int i = 0; i < num; i++) {
			String peer = askingPeers.getItem(i);
			if (!confirmedPeers.contains(peer)) {
				int sep_pos1 = peer.indexOf('(');
				int sep_pos2 = peer.indexOf(')');
				String peer_addr = peer.substring(sep_pos1 + 1, sep_pos2);
				Log.d(TAG, "deny peer's connection attempt from: " + peer_addr);
				connMgrService.denyPeer(peer_addr);
			}
		}
		resetConnSession();
	}

	void handleConnCancel() {
		int num = askingPeers.getCount();
		for (int i = 0; i < num; i++) {
			String peer = askingPeers.getItem(i);
			int sep_pos1 = peer.indexOf('(');
			int sep_pos2 = peer.indexOf(')');
			String peer_addr = peer.substring(sep_pos1 + 1, sep_pos2);
			Log.d(TAG, "deny peer's connection attempt from: " + peer_addr);
			connMgrService.denyPeer(peer_addr);
		}
		resetConnSession();
	}

	void resetConnSession() {
		askingPeers.clear();
		confirmedPeers.clear();
	}

	void onConnectPeerDevice(DeviceInfo dev) {
		if (mConnDeviceCat == null)
			return; // settings pane on top
		CheckBoxPreference pref0 = (CheckBoxPreference) mConnDeviceCat
				.findPreference(dev.addr);
		if (pref0 == null) {
			pref0 = connPeerDevice(dev);
		}
		pref0.setChecked(true);
		pref0.setOnPreferenceClickListener(disconnDeviceListener);
	}

	void onDisconnectPeerDevice(DeviceInfo dev) {
		if (mConnDeviceCat == null)
			return; // settings pane on top
		// remove from existing peers
		CheckBoxPreference pref1 = (CheckBoxPreference) mConnDeviceCat
				.findPreference(dev.addr);
		if (pref1 != null) {
			/*
			 * pref1.setChecked(false);
			 * pref1.setOnPreferenceClickListener(connDeviceListener);
			 */
			mConnDeviceCat.removePreference(pref1);
		}
	}
	
	void removeAllPeerDevices() {
		if (mConnDeviceCat == null)
			return; // settings pane on top
		mConnDeviceCat.removeAll();
	}

	void showDeviceInfo(DeviceInfo dev) {
		Log.d(TAG, "enter showDeviceInfo");
		if (mDeviceNamePref != null) {
			if (dev.name != null)
				mDeviceNamePref.setSummary(dev.name);
			else
				mDeviceNamePref.setSummary("");
		}
		if (mDeviceAddrPref != null) {
			if (dev.addr != null) {
				mDeviceAddrPref.setSummary(dev.addr);
				toggleConnectionButtons(true);
			} else {
				mDeviceAddrPref.setSummary("");
				toggleConnectionButtons(false);
			}
		}
		if (mDevicePortPref != null) {
			if (dev.port != null)
				mDevicePortPref.setSummary(dev.port);
			else
				mDevicePortPref.setSummary("");
		}
		Log.d(TAG, "exit showDeviceInfo");
	}

	void onGetDeviceInfo(DeviceInfo dev) {
		Log.d(TAG, "recv onGetDeviceInfo: " + dev.toString());
		mDevice.addr = dev.addr;
		mDevice.port = dev.port;
		showDeviceInfo(mDevice);
		//start scan if configured
		if (connMgrService != null) {
			NetInfo an = connMgrService.getActNet();
			if (an != null) {
				startScanAfterGetDevInfo(an);
			}
		}
	}
	
	void onGetConnectionInfo(ConnInfo ci) {
		if (ci !=null)
			useSSL = ci.useSSL;
		if (mUseSSLPref !=null)
			mUseSSLPref.setChecked(useSSL);
	}

	CheckBoxPreference connPeerDevice(DeviceInfo dev) {
		CheckBoxPreference pref = new CheckBoxPreference(mContext);
		pref.setKey(dev.addr);
		pref.setTitle(dev.name);
		pref.setSummary(dev.addr);
		pref.setChecked(true);
		pref.setOnPreferenceClickListener(disconnDeviceListener);
		mConnDeviceCat.addPreference(pref);
		return pref;
	}

	OnPreferenceClickListener disconnDeviceListener = new OnPreferenceClickListener() {
		public boolean onPreferenceClick(Preference preference) {
			CheckBoxPreference pref = (CheckBoxPreference) preference;
			pref.setChecked(false); // only change check status from connection
									// events
			String addr = preference.getSummary().toString();
			DeviceInfo dev = connMgrService.discoveredDevices.get(addr);
			if (dev != null) {
				connMgrService.disconnectPeer(dev);
			}
			return true;
		}
	};

	public void addConnectDevice(DeviceInfo dev) {
		connMgrService.discoveredDevices.put(dev.addr, dev);
		connMgrService.connectPeer(dev, null, conn_timeout);
	}

	void addNearbyDevice(DeviceInfo dev) {
		if (mConnDeviceCat == null)
			return; // settings pane on top
		CheckBoxPreference pref = (CheckBoxPreference) mConnDeviceCat
				.findPreference(dev.addr);
		if (pref == null) {
			pref = new CheckBoxPreference(mContext);
			mConnDeviceCat.addPreference(pref);
		}
		pref.setKey(dev.addr);
		pref.setTitle(dev.name);
		pref.setSummary(dev.addr);
		pref.setChecked(false);
		pref.setOnPreferenceClickListener(connDeviceListener);
	}

	OnPreferenceClickListener connDeviceListener = new OnPreferenceClickListener() {
		public boolean onPreferenceClick(Preference preference) {
			CheckBoxPreference pref = (CheckBoxPreference) preference;
			pref.setChecked(false); // only change check status from connection
									// events
			String addr = preference.getSummary().toString();
			DeviceInfo dev = connMgrService.discoveredDevices.get(addr);
			if (dev != null) {
				connMgrService.connectPeer(dev,
						connMgrService.securityToken.getBytes(), conn_timeout);
			}
			return true;
		}
	};

	OnSharedPreferenceChangeListener prefChangeListener = new OnSharedPreferenceChangeListener() {
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			// Let's do something when my counter preference value changes
			if (key.equals(PREF_KEY_NAME)) {
				updateName();
			} /*
			 * else if (key.equals(PREF_KEY_PORT)) { updatePort(); }
			 */
			else if (key.equals(PREF_KEY_CONN_PIN)) {
				mConnPIN = mConnPINPref.isChecked();
				} else if (key.equals(PREF_KEY_AUTO_SCAN)) {
					mAutoScan = mAutoScanPref.isChecked();
				} else if (key.equals(PREF_KEY_AUTO_CONN)) {
					mAutoConn = mAutoConnPref.isChecked();
					connMgrService.setConnParams(mAutoConn, mAutoAccept);
			} else if (key.equals(PREF_KEY_AUTO_ACCEPT)) {
				mAutoAccept = mAutoAcceptPref.isChecked();
				connMgrService.setConnParams(mAutoConn, mAutoAccept);
			} else if (key.equals(PREF_KEY_USE_SSL)) {
				useSSL = mUseSSLPref.isChecked();
				connMgrService.setConnectionInfo(live_timeout, conn_timeout, scan_timeout, useSSL);	
				//notify rmt intent service ssl settings changed
				Intent intent = new Intent(ACTION_USE_SSL);
				intent.putExtra(Router.USE_SSL, useSSL);
				mContext.startService(intent);		
				//
				if (scanStarted > 0) {
					stop_scan();
				}
			} else if (key.equals(PREF_KEY_SEARCH_TIMEOUT)) {
				mSearchTimeout = sharedPref.getString(PREF_KEY_SEARCH_TIMEOUT,
						"30");
				scan_timeout = Integer.parseInt(mSearchTimeout) * 1000;
				mSearchTimeoutPref.setSummary(mSearchTimeout + seconds);
				connMgrService.setConnectionInfo(live_timeout, conn_timeout, scan_timeout, useSSL);
			} else if (key.equals(PREF_KEY_PEER_LIVE_TIMEOUT)) {
				mLiveTimeout = sharedPref.getString(PREF_KEY_PEER_LIVE_TIMEOUT,
						"4");
				mLiveTimeoutPref.setSummary(mLiveTimeout + seconds);
				live_timeout = Integer.parseInt(mLiveTimeout) * 1000;
				connMgrService.setConnectionInfo(live_timeout, conn_timeout, scan_timeout, useSSL);				
			} else if (key.equals(PREF_KEY_CONN_TIMEOUT)) {
				mConnTimeout = sharedPref
						.getString(PREF_KEY_CONN_TIMEOUT, "10");
				conn_timeout = Integer.parseInt(mConnTimeout) * 1000;
				mConnTimeoutPref.setSummary(mConnTimeout + seconds);
				connMgrService.setConnectionInfo(live_timeout, conn_timeout, scan_timeout, useSSL);
			} else if (key.equals(PREF_KEY_MAX_SESSIONS)) {
				mMaxSessions = sharedPref.getString(PREF_KEY_MAX_SESSIONS,
						DEF_NUM_SESSIONS);
				max_sessions = Integer.parseInt(mMaxSessions);
				mMaxSessionsPref.setSummary(mMaxSessions + sessions);
				// notif remote intent service to change pool size
				Intent intent = new Intent(ACTION_MAX_SESSIONS_NUM_CHANGED);
				intent.putExtra("max_sessions_num", max_sessions);
				mContext.startService(intent);
			}
		}
	};

	void getDeviceInfo() {
		if (connMgrService != null) {
			Log.d(TAG, "send getDeviceInfo");
			connMgrService.getDeviceInfo();
		}
	}

	void updateName() {
		String name = mDeviceNamePref.getText();
		if (name != null && !name.equals(mDevice.name)) {
			mDevice.name = name;
			mDeviceNamePref.setSummary(mDevice.name);
			SharedPreferences.Editor editor = sharedPref.edit();
			editor.putString(PREF_KEY_NAME, mDevice.name);
			editor.commit();
			Log.d(TAG, "set name: " + mDevice.name);
			// notify core service device info changed
			if (connMgrService != null)
				connMgrService.saveDeviceInfo(mDevice);
		}
	}

	void showConnConfirmDialog(DeviceInfo dev) {
		if (connConfirmDialog == null) {
			askingPeers.clear();
			confirmedPeers.clear();
			askingPeers.add(dev.name + " (" + dev.addr + ")");
		} else {
			String str = dev.name + " (" + dev.addr + ")";
			askingPeers.add(str);
			askingPeers.notifyDataSetChanged();
		}
		mContext
		.showDialog(CONNECTION_CONFIRM_DIALOG);
	}

	/**
	 * Handler of incoming messages from service.
	 */
	void onSearchFoundDiffSSLFlag(DeviceInfo sslDiffDevice, boolean peerSSL) {
		Log.d(TAG, "handle SEARCH_FOUND_DEVICE_DIFF_SSL_FLAG");
		mContext.showDialog(PEER_DIFF_SSL_DIALOG);
		Log.d(TAG, "search found peer use diff SSL setting: "
				+ sslDiffDevice.addr + ", useSSL=" + peerSSL);
	}
	
	void onSearchComplete() {
		if (connectorSearch) {
			if (mConnectorPref != null) {
				mConnectorPref.stopProgress();
			}
			connectorSearch = false;
		} else {
			scanStarted--;
			if (scanStarted <= 0 && mScanNearbyPref != null) {
				mScanNearbyPref.stopProgress();
			}
		}
	}
	
	void onConnectionFailed(DeviceInfo connFailDevice, int connFailCode){
		if (connFailDevice != null) {
			onDisconnectPeerDevice(connFailDevice);
		}
		// dont report FAIL_UNKNOWN_PEER, since this device is trying
		// hijacking
		// dont report FAIL_CONN_EXIST, no need to bother user with
		// this
		if (connFailCode == Router.ConnFailureCode.FAIL_CONNMGR_INACTIVE
				|| connFailCode == Router.ConnFailureCode.FAIL_PIN_MISMATCH
				|| connFailCode == Router.ConnFailureCode.FAIL_CONN_SELF
				|| connFailCode == Router.ConnFailureCode.FAIL_REJECT_BY_USER) {

			mContext.showDialog(CONNECTION_FAIL_DIALOG);
		}
		Log.d(TAG, "failed connection attempt to: "
				+ connFailDevice.addr + ", " + connFailCode);

	}
	
	void onError(String errMsg) {
		Toast.makeText(mContext, "Error : " + errMsg, Toast.LENGTH_LONG)
				.show();
		Log.e(TAG, errMsg);
	}
}
