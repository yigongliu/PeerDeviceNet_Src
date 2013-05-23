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

import android.app.Dialog;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;

import com.xconns.peerdevicenet.core.R;
import com.xconns.peerdevicenet.DeviceInfo;

public class ConnectionManagerActivity extends PreferenceActivity implements
		DeviceHolder {
	// Debugging
	static final String TAG = "ConnectionManagerActivity";

	ConnectionManager cm = null;
	boolean mJumpToAddPeer = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "onCreate() called");

		cm = new ConnectionManager(this);
		cm.onCreate();
		// cm.initConnSettings();

		// compose hierarchy from the XML preferences files
		//save default screen
		PreferenceScreen originScreen = getPreferenceScreen();
		//create a new screen for conn settings prefs
		setPreferenceScreen(getPreferenceManager().createPreferenceScreen(this));
		addPreferencesFromResource(R.xml.conn_setting_prefs);
		PreferenceScreen settingsScreen = getPreferenceScreen();
		settingsScreen.setTitle(R.string.conn_settings);
		settingsScreen.setSummary(R.string.detail_params);
		//create a new screen for actions prefs
		setPreferenceScreen(getPreferenceManager().createPreferenceScreen(this));
		addPreferencesFromResource(R.xml.actions_prefs);
		PreferenceScreen actionsScreen = getPreferenceScreen();
		actionsScreen.setTitle(R.string.cleanup_shutdown);
		//load conn prefs to orgin screen
		setPreferenceScreen(originScreen);
		addPreferencesFromResource(R.xml.conn_prefs);

		//add conn_settings_prefs and actions_prefs to conn_prefs
		cm.mGenSettingsCat = (PreferenceCategory) getPreferenceScreen()
				.findPreference(ConnectionManager.PREF_KEY_CAT_GEN_SETTINGS);
		cm.mGenSettingsCat.addPreference(settingsScreen);
		cm.mGenSettingsCat.addPreference(actionsScreen);

		getPreferenceScreen().setTitle(R.string.conn_settings);

		cm.attachConnectionsGUI(getPreferenceScreen());
		cm.attachConnectionSettingsGUI(getPreferenceScreen()/*settingsScreen*/);
		cm.attachActionsGUI(getPreferenceScreen()/*actionsScreen*/);
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		Log.d(TAG, "onResume");

		//cm.updateNetworkSettings();

		cm.onResume();

		cm.sharedPref
				.registerOnSharedPreferenceChangeListener(cm.prefChangeListener);

		//get new service port
		//cm.getDeviceInfo();
		
		/*
		 * if (mJumpToAddPeer) { PreferenceScreen addPeerScreen =
		 * (PreferenceScreen) getPreferenceScreen()
		 * .findPreference(ConnectionManager.PREF_KEY_PEER_CONNS);
		 * setPreferenceScreen(addPeerScreen); // start scanning }
		 */
	}

	@Override
	protected void onPause() {
		super.onPause();
		cm.onPause();
		cm.sharedPref
				.unregisterOnSharedPreferenceChangeListener(cm.prefChangeListener);
	}

	private boolean configChange = false;

	@Override
	public Object onRetainNonConfigurationInstance() {
		configChange = true;
		return super.onRetainNonConfigurationInstance();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		cm.onDestroy(configChange);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		return cm.onCreateDialog(id);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		cm.onPrepareDialog(id, dialog);
	}

	public void addConnectDevice(DeviceInfo dev) {
		cm.addConnectDevice(dev);
	}
}
