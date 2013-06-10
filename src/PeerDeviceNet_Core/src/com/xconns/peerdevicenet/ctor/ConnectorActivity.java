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

package com.xconns.peerdevicenet.ctor;

import java.nio.charset.Charset;
import java.util.Timer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;
import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.NetInfo;
import com.xconns.peerdevicenet.Router;
import com.xconns.peerdevicenet.cm.ConnectionManager;
import com.xconns.peerdevicenet.cm.ConnectionManagerService;
import com.xconns.peerdevicenet.core.R;
import com.xconns.peerdevicenet.core.WifiDirectGroupManager;
import com.xconns.peerdevicenet.core.WifiHotspotTransport;
import com.xconns.peerdevicenet.utils.RouterConfig;
import com.xconns.peerdevicenet.utils.Utils;

public class ConnectorActivity extends Activity {
	final static String TAG = "CtorActivity";

	LinearLayout groupRole = null;
	RadioGroup mQRConn = null;
	RadioGroup mAutoConn = null;
	boolean isLeader = false;

	SharedPreferences settings = null;

	CheckBox useNFCBox = null;
	boolean useNFC = true;
	NfcAdapter mNfcAdapter = null;
	Intent nfcIntent = null;
	
	LinearLayout groupNType = null;
	CheckBox useSSLBox = null;
	boolean useSSL = false;
	RadioGroup mTypes = null;
	RadioButton wifiBtn = null;
	RadioButton wifiDirectBtn = null;
	RadioButton wifiHotspotBtn = null;
	int chosenNType = NetInfo.NoNet;
	TextView wifiInfo = null;
	TextView wifiDirectInfo = null;
	TextView wifiHotspotInfo = null;
	TextView hotspotLockedInfo = null;
	CharSequence wifiInfoText = null;
	CharSequence wifiDirectInfoText = null;
	CharSequence wifiHotspotInfoText = null;
	CharSequence checkSetting = null;

	LinearLayout groupPasswd = null;
	EditText passwdText = null;
	TextView enterBtn = null;

	LinearLayout groupQRCode = null;
	ImageView qrCodeView = null;
	final static int DECODE_QRCODE_REQ = 10101;
	QRCodeData qrData = null;

	LinearLayout groupProg = null;
	LinearLayout groupClose = null;
	TextView dismissBtn = null;
	boolean Closed = false;

	static final int WIFI_CONNECTOR_FAIL_MSG = 101;
	static final int WIFI_CONNECTOR_FAIL_DIALOG = 3;
	static final int WIFI_DIRECT_WARNING_DIALOG = 4; // consistent with ConnMgr
	boolean wifiDirectSupported = false;
	WifiDirectGroupManager grpMgr = null;

	ConnectionManagerService connMgrService = null;
	boolean mInited = false;

	NetInfo[] connNets = new NetInfo[3]; // connected networks;
	int actNetType = NetInfo.NoNet;

	String devName = null;

	Timer timer = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LayoutInflater mInflater = LayoutInflater.from(this);

		requestWindowFeature(Window.FEATURE_LEFT_ICON);

		// setContentView(R.layout.activity_ctor);

		// add a dummy listview at top for good size of Theme_Dialog
		ListView lv = new ListView(this);
		LinearLayout.LayoutParams lparam1 = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		lparam1.weight = (float) 0.001;

		// use scrollview to hold the main content
		ScrollView sv = new ScrollView(this);
		LinearLayout.LayoutParams lparam2 = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		lparam2.weight = (float) 0.05;

		View mainView = mInflater.inflate(R.layout.activity_ctor, null);
		sv.addView(mainView);

		LinearLayout ll = new LinearLayout(this);
		ll.setOrientation(LinearLayout.VERTICAL);
		ll.addView(lv, lparam1); // dummy view for activity's good size
		ll.addView(sv, lparam2);

		setContentView(ll);

		getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
				R.drawable.router_icon);

		// getWindow().setLayout(LayoutParams.FILL_PARENT,
		// LayoutParams.WRAP_CONTENT);

		//check if NFC supported or not
		if(Utils.ANDROID_VERSION >= 14) { //android beam available
			checkNFC();
		}
		
		//check if WifiDirect supported or not
		wifiDirectSupported = getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_WIFI_DIRECT);
		if (wifiDirectSupported) {
			grpMgr = new WifiDirectGroupManager(this,
					new WifiDirectGroupManager.Handler() {

						@Override
						public void onError(String msg) {
							Log.d(TAG, "Group add/del error: " + msg);
							if (wifiDirectBtn != null) {
								wifiDirectBtn.setChecked(false);
							}
							showDialog(WIFI_DIRECT_WARNING_DIALOG);
						}

						@Override
						public void onWifiDirectNotEnabled() {
							Log.d(TAG, "Please enable wifi direct first");
							if (wifiDirectBtn != null) {
								wifiDirectBtn.setChecked(false);
							}
							showDialog(WIFI_DIRECT_WARNING_DIALOG);
						}
					});
		}

		//
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		useSSL = settings.getBoolean(ConnectionManager.PREF_KEY_USE_SSL,
				RouterConfig.DEF_USE_SSL);
		devName = settings.getString(ConnectionManager.PREF_KEY_NAME, null);
		if (devName == null) {
			SharedPreferences.Editor editor = settings.edit();
			devName = android.os.Build.MODEL;
			editor.putString(ConnectionManager.PREF_KEY_NAME, devName);
			// mDeviceNamePref.setText(mDeviceName);
			editor.commit();
		}

		//
		initGroupRole();
		initGroupNType();
		initGroupQRCode();
		initGroupClose();
		initGroupProg();
		//
		showGroup(1);

		// start connection mgr service service
		Intent intent = new Intent(this, ConnectionManagerService.class);
		startService(intent); // make service live during screen rotate
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		Log.d(TAG, "onCreate() done");
	}
	
	@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
	void checkNFC() {
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		Log.d(TAG, "NFC init");
		if (mNfcAdapter != null) {
			Log.d(TAG, "NFC supported");
		}
	}

	@Override
	protected void onDestroy() {
		if (connMgrService != null) {
			Log.d(TAG, "CtorActivity destroyed");
			connMgrService.onConnectorDestroy();
			unbindService(mConnection);
			connMgrService = null;
		}

		if (grpMgr != null) {
			grpMgr.onDestroy();
		}

		// dont destroy connMgrService here
		// since connMgr will start right away
		super.onDestroy();
		Log.d(TAG, "onDestroyed");
	}

	@Override
	protected void onPause() {
		resetChosenNType();

		if (grpMgr != null) {
			grpMgr.onPause();
		}

		Closed = true;
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();

		//
		if (grpMgr != null) {
			grpMgr.onResume();
		}

		// resume caused by intent from an Android Beam
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
			processNfcIntent(getIntent());
		} else {
			Closed = false;
			//
			// getPeerDeviceNetInfo();
			//
			resumeLeader();
		}
	}
	
    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }
	
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	void processNfcIntent(Intent intent) {
		nfcIntent = intent;
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        //get encoded data
        String res = new String(msg.getRecords()[0].getPayload());
        //show progr bar
        showGroup(-1);
        //handle it
		Log.d(TAG, "Decoded raw res: " + res);
		isLeader = false;
		chosenNType = NetInfo.WiFi;
		if (res != null) {
			qrData = QRCodeData.decode(res);
			if (qrData != null) {
				Log.d(TAG, "Decoded QRCode: " + qrData.toString());
				Log.d(TAG, "chosen Ntype : " + chosenNType);
				setupWifiConn(qrData);
			}
		}
    }

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			ConnectionManagerService.LocalBinder binder = (ConnectionManagerService.LocalBinder) service;
			connMgrService = binder.getService();
			Log.d(TAG, "ConnectionManagerService connected");
			// attach to remote intent service to allow it call back
			connMgrService.setConnector(ConnectorActivity.this);
			connMgrService.setSimpleConnectionInfo(devName, useSSL);

			// in case we miss it at onResume()
			//getPeerDeviceNetInfo();
		}

		public void onServiceDisconnected(ComponentName arg0) {
			connMgrService = null;
			Log.d(TAG, "ConnectionManagerService disconnected");
		}
	};

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Closed = true;
			if (connMgrService != null) {
				connMgrService.onConnectorDestroy();
			}
			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	// start call-chain:
	// getNetworks()->getActiveNetwork()->getPeerDevices()/getDeviceInfo()
	void getPeerDeviceNetInfo() {
		if (connMgrService != null) {
			connMgrService.getNetworks();
		}
	}

	void initGroupRole() {
		groupRole = (LinearLayout) findViewById(R.id.group_role);
		//
		mQRConn = (RadioGroup) findViewById(R.id.qrconn);
		mQRConn.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId == R.id.leader) {
					doLeader();
				} else if (checkedId == R.id.member) {
					doMember();
				}
			}
		});
		//
		mAutoConn = (RadioGroup) findViewById(R.id.autoconn);
		mAutoConn
				.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
					public void onCheckedChanged(RadioGroup group, int checkedId) {
						if (checkedId == R.id.autofind) {
							Closed = true;
							if (connMgrService != null) {
								connMgrService.onConnectorDestroy();
							}
							finish();
							Intent intent = new Intent(
									Router.ACTION_CONNECTION_MANAGEMENT);
							startActivity(intent);
						}
					}
				});
	}
	
	@TargetApi(Build.VERSION_CODES.GINGERBREAD) 
	void initNfcBox() {
		if(mNfcAdapter.isEnabled()) {
			useNFCBox.setChecked(true);
			useNFC = true;
		} else {
			useNFCBox.setChecked(false);
			useNFC = false;
		}		
	}
	
	@TargetApi(Build.VERSION_CODES.GINGERBREAD) 
	void setNfc(boolean checked) {
		if(checked) {
			useNFC = true;
			if (!mNfcAdapter.isEnabled()) {
				//goto sys pref to turn on nfc
				Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                startActivity(intent);
			}
		} else {
			useNFC = false;
		}
	}
	
	void initGroupNType() {
		groupNType = (LinearLayout) findViewById(R.id.group_type);
		//
		useNFCBox = (CheckBox) findViewById(R.id.use_nfc);
		if (mNfcAdapter == null || Utils.ANDROID_VERSION < 14) {
			useNFCBox.setEnabled(false);
		} 
		else {
			initNfcBox();
			useNFCBox.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View arg0) {
					setNfc(useNFCBox.isChecked());
				}
			});
		}
		
		useSSLBox = (CheckBox) findViewById(R.id.use_ssl);

		useSSLBox.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				if (qrData != null && actNetType != NetInfo.NoNet
						&& chosenNType != NetInfo.NoNet
						&& chosenNType == actNetType) {
					netActivatedAtLeader(connNets[actNetType]);
					setUseSSL(qrData.useSSL);
				}
			}

		});

		//
		mTypes = (RadioGroup) findViewById(R.id.types);
		mTypes.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId == R.id.wifi) {
					doWifi();
				} else if (checkedId == R.id.wifi_direct) {
					doWifiDirect();
				} else if (checkedId == R.id.wifi_hotspot) {
					doWifiHotspot();
				}
			}
		});
		wifiBtn = (RadioButton) findViewById(R.id.wifi);
		wifiDirectBtn = (RadioButton) findViewById(R.id.wifi_direct);
		wifiHotspotBtn = (RadioButton) findViewById(R.id.wifi_hotspot);
		wifiInfo = (TextView) findViewById(R.id.wifi_info);
		wifiDirectInfo = (TextView) findViewById(R.id.wifi_direct_info);
		wifiHotspotInfo = (TextView) findViewById(R.id.wifi_hotspot_info);
		hotspotLockedInfo = (TextView) findViewById(R.id.hotspot_locked_info);
		wifiInfoText = getResources().getText(R.string.wifi_info);
		wifiDirectInfoText = getResources().getText(R.string.wifi_direct_info);
		wifiHotspotInfoText = getResources()
				.getText(R.string.wifi_hotspot_info);
		hotspotLockedInfo.setVisibility(View.GONE);
		checkSetting = getResources().getText(R.string.check_setting);

		//
		if (!wifiDirectSupported) {
			wifiDirectBtn.setEnabled(false);
			wifiDirectInfo.setEnabled(false);
		}
		//init passwd group
		groupPasswd = (LinearLayout) findViewById(R.id.group_passwd);;
		passwdText = (EditText) findViewById(R.id.wifi_passwd);
		enterBtn = (TextView) findViewById(R.id.button_enter);
		enterBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				String passwd = passwdText.getText().toString();
				if (connNets[NetInfo.WiFi] != null) {
					connNets[NetInfo.WiFi].pass = passwd;
					if (qrData != null && actNetType == NetInfo.WiFi
							&& chosenNType == actNetType) {
						netActivatedAtLeader(connNets[actNetType]);
						setUseSSL(qrData.useSSL);
					}
				}
			}
		});
		groupPasswd.setVisibility(View.GONE);
	}

	void initGroupQRCode() {
		groupQRCode = (LinearLayout) findViewById(R.id.group_qrcode);
		qrCodeView = (ImageView) findViewById(R.id.qrcode_image);
	}

	void initGroupClose() {
		groupClose = (LinearLayout) findViewById(R.id.group_close);
		dismissBtn = (TextView) findViewById(R.id.button_dismiss);
		dismissBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Closed = true;
				if (connMgrService != null) {
					connMgrService.onConnectorDestroy();
				}
				finish();
			}
		});
	}

	void initGroupProg() {
		groupProg = (LinearLayout) findViewById(R.id.group_prog);
	}

	void doLeader() {
		isLeader = true;
		if (mNfcAdapter != null && Utils.ANDROID_VERSION >= 14) {
			initNfcBox();
		}
		// start query network info
		getPeerDeviceNetInfo();
		showGroup(2);
	}

	void doMember() {
		isLeader = false;
		chosenNType = NetInfo.WiFi;
		// get current nets
		getPeerDeviceNetInfo();
		Intent i = new Intent("com.xconns.peerdevicenet.DECODE_QRCODE");
		startActivityForResult(i, DECODE_QRCODE_REQ);
	}

	void doWifi() {
		Log.d(TAG, "doWifi");
		chosenNType = NetInfo.WiFi;
		if (connNets[NetInfo.WiFi] != null) {
			if (actNetType != NetInfo.WiFi) {
				connMgrService.activateNetwork(connNets[NetInfo.WiFi]);
			} else {
				NetInfo net = connNets[NetInfo.WiFi];
				netActivatedAtLeader(net);
				setUseSSL(qrData.useSSL);
			}
		} else {
			configWifi();
		}
	}

	void doWifiDirect() {
		chosenNType = NetInfo.WiFiDirect;
		if (connNets[NetInfo.WiFiDirect] != null) {
			if (actNetType != NetInfo.WiFiDirect) {
				connMgrService.activateNetwork(connNets[NetInfo.WiFiDirect]);
			} else {
				NetInfo net = connNets[NetInfo.WiFiDirect];
				netActivatedAtLeader(net);
				setUseSSL(qrData.useSSL);
			}
		} else {
			Log.d(TAG, "start Wifi Direct network");
			grpMgr.createNetwork();
		}
	}

	void doWifiHotspot() {
		chosenNType = NetInfo.WiFiHotspot;
		if (connNets[NetInfo.WiFiHotspot] != null) {
			if (actNetType != NetInfo.WiFiHotspot) {
				connMgrService.activateNetwork(connNets[NetInfo.WiFiHotspot]);
			} else {
				NetInfo net = connNets[NetInfo.WiFiHotspot];
				netActivatedAtLeader(net);
				setUseSSL(qrData.useSSL);
			}
		} else {
			configWifi();
		}
	}

	void resetChosenNType() {
		//
		if (isLeader && connMgrService != null) {
			connMgrService.stopPeerSearch();
			connMgrService.onConnectorDestroy();
		}
		//
		if (isLeader) {
			showGroup(2);
		}
		// chosenNType = NetInfo.NoNet;
		
		wifiBtn.setChecked(false);
		wifiDirectBtn.setChecked(false);
		wifiHotspotBtn.setChecked(false);
		wifiInfo.setText(wifiInfoText);
		wifiDirectInfo.setText(wifiDirectInfoText);
		wifiHotspotInfo.setText(wifiHotspotInfoText);
		
	}

	void resumeLeader() {
		if (isLeader) {
			if (connMgrService != null) {
				connMgrService.setConnector(this);
			}
			// clear gui
			wifiBtn.setChecked(false);
			wifiDirectBtn.setChecked(false);
			wifiHotspotBtn.setChecked(false);
			wifiInfo.setText(wifiInfoText);
			wifiDirectInfo.setText(wifiDirectInfoText);
			wifiHotspotInfo.setText(wifiHotspotInfoText);
			// query peers
			doLeader();
			//
			/*
			 * switch (chosenNType) { case NetInfo.WiFi: if (wifiBtn != null) {
			 * wifiBtn.setChecked(true); } break; case NetInfo.WiFiDirect: if
			 * (wifiDirectBtn != null) { wifiDirectBtn.setChecked(true); }
			 * break; case NetInfo.WiFiHotspot: if (wifiHotspotBtn != null) {
			 * wifiHotspotBtn.setChecked(true); } break; }
			 */
		}
	}

	void showGroup(int grpNo) {
		groupRole.setVisibility(View.GONE);
		groupNType.setVisibility(View.GONE);
		groupPasswd.setVisibility(View.GONE);
		hotspotLockedInfo.setVisibility(View.GONE);
		groupQRCode.setVisibility(View.GONE);
		groupProg.setVisibility(View.GONE);
		switch (grpNo) {
		case -1:
			groupClose.setVisibility(View.GONE);
			groupProg.setVisibility(View.VISIBLE);
			break;
		case 1:
			groupRole.setVisibility(View.VISIBLE);
			break;
		case 2:
			groupNType.setVisibility(View.VISIBLE);
			if (connNets[NetInfo.WiFi] != null && connNets[NetInfo.WiFi].encrypt != NetInfo.NoPass) {
				groupPasswd.setVisibility(View.VISIBLE);
			}
			if(connNets[NetInfo.WiFiHotspot] != null && WifiHotspotTransport.Unknown.equals(connNets[NetInfo.WiFiHotspot].name)) {
				wifiHotspotInfo.setText(NetInfo.NetTypeName(NetInfo.WiFiHotspot) + ": " + checkSetting);
				hotspotLockedInfo.setVisibility(View.VISIBLE);
			}
			break;
		case 3:
			groupNType.setVisibility(View.VISIBLE);
			if (connNets[NetInfo.WiFi] != null && connNets[NetInfo.WiFi].encrypt != NetInfo.NoPass) {
				groupPasswd.setVisibility(View.VISIBLE);
			}
			if(connNets[NetInfo.WiFiHotspot] != null && WifiHotspotTransport.Unknown.equals(connNets[NetInfo.WiFiHotspot].name)) {
				wifiHotspotInfo.setText(NetInfo.NetTypeName(NetInfo.WiFiHotspot) + ": " + checkSetting);
				hotspotLockedInfo.setVisibility(View.VISIBLE);
			}
			groupQRCode.setVisibility(View.VISIBLE);
			break;
		}
	}

	void configWifi() {
		Log.d(TAG, "show net config interface");
		try {
			if (Utils.ANDROID_VERSION >= 16) {
				Intent in = new Intent(Settings.ACTION_WIFI_SETTINGS);
				startActivity(in);
			} else {
				Intent in = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
				startActivity(in);
			}
		} catch (ActivityNotFoundException anf) {
			Log.d(TAG, "no activity for : Settings.ACTION_WIRELESS_SETTINGS"
					+ anf.getMessage());
			Intent in = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
			startActivity(in);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK
				&& requestCode == DECODE_QRCODE_REQ) {
			String res = data.getStringExtra("DecodeResult");
			// hide my-member gui, show progr dialog
			showGroup(-1);
			//
			Log.d(TAG, "Decoded raw res: " + res);
			if (res != null) {
				qrData = QRCodeData.decode(res);
				if (qrData != null) {
					Log.d(TAG, "Decoded QRCode: " + qrData.toString());
					Log.d(TAG, "chosen Ntype : " + chosenNType);
					setupWifiConn(qrData);
				}
			}
		}
	}

	void setupWifiConn(QRCodeData qrData) {
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		/*
		 * WifiInfo winfo = wifiManager.getConnectionInfo(); boolean match =
		 * qrData.ssid.equals(winfo.getSSID()); //sometimes the above winfo is
		 * encoded strangely, further verify if (!match) {
		 * List<WifiConfiguration> existingConfigs = wifiManager
		 * .getConfiguredNetworks(); for (WifiConfiguration ec :
		 * existingConfigs) { if (ec.SSID.equals(qrData.ssid)) { match = true;
		 * break; } } } if (winfo != null && match) {
		 */
		// NetInfo connWifi = connNets[NetInfo.WiFi];
		// if(connWifi!=null&&connWifi.name!=null&&connWifi.name.equals(qrData.ssid))
		// {

		boolean match = false;
		NetInfo netWifi = connNets[NetInfo.WiFi];
		if (netWifi != null && netWifi.name != null) {
			String name1 = netWifi.name.trim();
			String name2 = qrData.ssid.trim();
			Log.d(TAG, "ssids = " + name1 + ", " + name2);
			name1 = trimQuote(name1);
			name2 = trimQuote(name2);
			Log.d(TAG, "ssids2 = " + name1 + ", " + name2);
			if (name1.equals(name2)) {
				match = true;
			}
		}
		if (match) {
			// already connect to the right wifi net
			Log.d(TAG, "already connect to right wifi");
			if (actNetType != NetInfo.WiFi) {
				connMgrService.activateNetwork(connNets[NetInfo.WiFi]);
			} else {
				setUseSSL(qrData.useSSL);
			}
		} else {
			Log.d(TAG, "start connect to wifi");
			new WifiConnector(this, wifiManager).execute(qrData);
		}
	}
	
	public static String trimQuote(String name1) {
		char[] b1 = name1.toCharArray();
		if (b1.length > 2) {
			int start = 0;
			if (b1[0] == '"')
				start = 1;
			int end = b1.length - 1;
			if (b1[end] == '"')
				end = b1.length - 2;
			name1 = new String(b1, start, end - start + 1);
		}
		return name1;
	}

	void setUseSSL(boolean useSSL) {
		Log.d(TAG, "setUseSSL: " + useSSL);

		// update shared preference
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(ConnectionManager.PREF_KEY_USE_SSL, useSSL);
		editor.commit();
		// notif router service
		if (connMgrService != null) {
			connMgrService.setSimpleConnectionInfo(null, useSSL);
		}
	}
	
	public void onGetNetworks(NetInfo[] nets) {
		if (Closed)
			return;
		Log.d(TAG, "GET_NETWORKS = " + nets);
		if (nets != null && nets.length > 0) {
			for (NetInfo net : nets) {
				connNets[net.type] = net;
			}
			// dont check & limit to leader, since user may have not
			// chosen yet.
			// simply update GUI for all, it will not show foe member
			// if (isLeader) {
			netConnectedAtLeader(nets);
			// }
			if (connMgrService != null) {
				connMgrService.getActiveNetwork();
			}
		}
	}
	
	public void onGetActiveNetwork(NetInfo net) {
		if (Closed)
			return;
		Log.d(TAG, "GET_ACTIVE_NETWORK");
		Log.d(TAG, "chosen Ntype : " + chosenNType);

		if (net != null) { 
			if (actNetType != NetInfo.NoNet &&
					connNets[actNetType] != null && net.name != null && net.name.equals(connNets[actNetType].name)) {
				return;
			}
			actNetType = net.type;
			if (isLeader && chosenNType == actNetType) {
				netActivatedAtLeader(net);
			}
			if (connMgrService != null) {
				connMgrService.getDeviceInfo();
			}
			/*
			 * Log.d(TAG, "chosen Ntype : " +
			 * chosenNType+", net.type="+net.type); if (chosenNType ==
			 * net.type) { setUseSSL(qrData.useSSL); }
			 */
		}

	}
	
	public void onNetworkConnected(NetInfo net) {
		if (Closed)
			return;
		Log.d(TAG, "NETWORK_CONNECTED");
		connNets[net.type] = net;
		NetInfo[] nets = new NetInfo[] { net };
		if (isLeader) {
			netConnectedAtLeader(nets);
		}
		Log.d(TAG, "activateNet type=" + net.type);
		if (net.type == chosenNType) {
			if (connMgrService != null)
				connMgrService.activateNetwork(net);
		}

	}
	
	public void onNetworkDisconnected(NetInfo net) {
		if (Closed)
			return;
		Log.d(TAG, "NETWORK_DISCONNECTED");
		connNets[net.type] = null;
		if (isLeader) {
			netDisconnectedAtLeader(net);
		}
		if (actNetType == net.type) {
			actNetType = NetInfo.NoNet;
		}

	}
	
	public void onNetworkActivated(NetInfo net) {
		if (Closed)
			return;
		Log.d(TAG, "ACTIVATE_NETWORK");
		if (net != null) {
			if (actNetType != NetInfo.NoNet &&
					connNets[actNetType] != null && net.name != null && net.name.equals(connNets[actNetType].name)) {
				return;
			}
			actNetType = net.type;
			if (isLeader && chosenNType == actNetType) {
				netActivatedAtLeader(net);
			}
			if (connMgrService != null) {
				connMgrService.getDeviceInfo();
			}
			Log.d(TAG, "chosen Ntype : " + chosenNType + ", net.type="
					+ net.type);
			if (chosenNType == net.type) {
				setUseSSL(qrData.useSSL);
			}
		}

	}
	
	public void onGetDeviceInfo(DeviceInfo dev) {
		
	}
	
	public void onSetConnectionInfo() {
		if (Closed)
			return;
		Log.d(TAG, "SET_CONNECTION_INFO");
		if (qrData != null && chosenNType != NetInfo.NoNet) {
			NetInfo cn = connNets[chosenNType];
			if (isLeader) {
				DeviceInfo devv = new DeviceInfo(null, cn.addr, null);
				if (connMgrService != null)
					connMgrService.startPeerSearch(devv, -1);
			} else {
				DeviceInfo devv = new DeviceInfo(null, qrData.addr,
						null);
				if (connMgrService != null)
					connMgrService.startPeerSearch(devv, -1);
			}
		}

	}
	
	public void onSearchStart(DeviceInfo leader) {
		if (Closed)
			return;
		Log.d(TAG, "SEARCH_START1");
		if (!isLeader && qrData != null && chosenNType != NetInfo.NoNet) {
			Log.d(TAG, "group member search started, exit connector");
			Closed = true;
			if (connMgrService != null) {
				connMgrService.onConnectorDestroy();
			}
			finish();
			if (nfcIntent != null) { //member connects thru NFC, bring up CnnMgr
				Intent intent = new Intent(
						Router.ACTION_CONNECTION_MANAGEMENT);
				startActivity(intent);
			}
		}

	}
	
	public void onError(String errMsg) {
		if (Closed)
			return;
		Toast.makeText(this, "Error : " + errMsg, Toast.LENGTH_LONG)
				.show();
		Log.e(TAG, errMsg);
	}
	
	public void onGetConnectionInfo(){
		
	}

	void netConnectedAtLeader(NetInfo[] nets) {
		for (NetInfo net : nets) {
			StringBuilder sb = new StringBuilder();
			sb.append("SSID: ").append(net.name);
			sb.append("; passwd: ").append(net.pass);
			sb.append("; encryption: ").append(
					NetInfo.NetEncryptionName(net.encrypt));
			// update GUI
			switch (net.type) {
			case NetInfo.WiFi:
				wifiInfo.setText(sb);
				if (connNets[NetInfo.WiFi]!=null && connNets[NetInfo.WiFi].encrypt != NetInfo.NoPass) {
					groupPasswd.setVisibility(View.VISIBLE);
				}
				break;
			case NetInfo.WiFiDirect:
				wifiDirectInfo.setText(sb);
				break;
			case NetInfo.WiFiHotspot:
				if(WifiHotspotTransport.Unknown.equals(net.name)) {
					wifiHotspotInfo.setText(NetInfo.NetTypeName(net.type) + ": " + checkSetting);
					hotspotLockedInfo.setVisibility(View.VISIBLE);
				} else {
					wifiHotspotInfo.setText(sb);					
					hotspotLockedInfo.setVisibility(View.GONE);
				}
				break;
			}
		}
	}

	void netDisconnectedAtLeader(NetInfo net) {
		// update GUI, change from real netinfo to net info doc string
		if (net.type == NetInfo.WiFi) {
			wifiInfo.setText(wifiInfoText);
			groupPasswd.setVisibility(View.GONE);
			if(net.type == chosenNType) {
				wifiBtn.setChecked(false);
				chosenNType = NetInfo.NoNet;
			}
		} else if (net.type == NetInfo.WiFiDirect) {
			wifiDirectInfo.setText(wifiDirectInfoText);
			if(net.type == chosenNType) {
				wifiDirectBtn.setChecked(false);
				chosenNType = NetInfo.NoNet;
			}
		} else if (net.type == NetInfo.WiFiHotspot) {
			wifiHotspotInfo.setText(wifiHotspotInfoText);
			hotspotLockedInfo.setVisibility(View.GONE);
			if(net.type == chosenNType) {
				wifiHotspotBtn.setChecked(false);
				chosenNType = NetInfo.NoNet;
			}
		}
	}

	void netActivatedAtLeader(NetInfo net) {
		// update GUI
		showGroup(3);
		// ssl
		boolean checked = useSSLBox.isChecked();
		//
		String pass = net.pass;
		if (pass != null && (pass.length() == 0 || pass.equals("*"))) {
			pass = null;
		}
		qrData = new QRCodeData(net.name, net.pass, net.encrypt, net.hidden,
				checked, net.addr);
		Log.d(TAG, "encode QRCode for: " + qrData.encode());
		int dim = mTypes.getWidth();
		// int dim = groupNType.getWidth();
		Log.d(TAG, "image view dim=" + dim);
		//
		try {
			Bitmap bitmap = QRCodeEncoder.encodeAsBitmap(qrData.encode(), dim);
			qrCodeView.setImageBitmap(bitmap);
		} catch (WriterException e) {
			Log.e(TAG, "Could not encode barcode", e);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Could not encode barcode", e);
		}
		//init NFC transfer
		if (mNfcAdapter != null) {
			Log.d(TAG, "init NFC transfer");
	        initNfcTransfer(qrData);
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	void initNfcTransfer(QRCodeData qrData) {
		if (mNfcAdapter != null && mNfcAdapter.isEnabled() && useNFC) {
			NdefMessage msg = new NdefMessage(
					new NdefRecord[] { new NdefRecord(
							NdefRecord.TNF_MIME_MEDIA,
							"application/com.xconns.peerdevicenet.connector"
									.getBytes(Charset.forName("US-ASCII")),
							new byte[0], qrData.encode().getBytes(
									Charset.forName("US-ASCII"))) });
			mNfcAdapter.setNdefPushMessage(msg, this);
		}
	}

	public Dialog onCreateDialog(int id) {
		switch (id) {
		case WIFI_DIRECT_WARNING_DIALOG:
			return new AlertDialog.Builder(this)
					.setTitle(R.string.wifidir_warning_title)
					.setIcon(R.drawable.router_icon)
					.setMessage(R.string.wifidir_warning)
					.setPositiveButton(R.string.ok,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {

									// dialogInterface.dismiss();
									removeDialog(WIFI_DIRECT_WARNING_DIALOG);

									// ask user to turn on wifi direct
									Log.d(TAG,
											"ask user to turn on wifi direct");
									try {
										if (Utils.ANDROID_VERSION >= 16) {
											Intent in = new Intent(
													Settings.ACTION_WIFI_SETTINGS);
											startActivity(in);
										} else {
											Intent in = new Intent(
													Settings.ACTION_WIRELESS_SETTINGS);
											startActivity(in);
										}
									} catch (ActivityNotFoundException anf) {
										Log.d(TAG,
												"no activity for : Settings.ACTION_WIRELESS_SETTINGS"
														+ anf.getMessage());
										Intent in = new Intent(
												WifiManager.ACTION_PICK_WIFI_NETWORK);
										startActivity(in);
									}
								}
							})
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									removeDialog(WIFI_DIRECT_WARNING_DIALOG);
								}
							}).create();

		case WIFI_CONNECTOR_FAIL_DIALOG:
			if (qrData == null) {
				Closed = true;
				if (connMgrService != null) {
					connMgrService.onConnectorDestroy();
				}
				finish();
				return null;
			}
			CharSequence msg1 = getResources().getText(
					R.string.wifi_conn_fail_msg1);
			CharSequence msg2 = getResources().getText(
					R.string.wifi_conn_fail_msg2);
			return new AlertDialog.Builder(this)
					.setTitle(R.string.wifi_conn_fail_title)
					.setIcon(R.drawable.router_icon)
					.setMessage(msg1 + " "+qrData.ssid + msg2)
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {
									removeDialog(WIFI_CONNECTOR_FAIL_DIALOG);
									Closed = true;
									if (connMgrService != null) {
										connMgrService.onConnectorDestroy();
									}
									finish();
								}
							}).create();
		default:
			break;
		}
		return null;

	}

}
