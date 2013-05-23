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

import java.util.List;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;

import com.xconns.peerdevicenet.core.R;
import com.xconns.peerdevicenet.DeviceInfo;

@TargetApi(11)
public class ConnectionManagerDualPane extends PreferenceActivity implements
		DeviceHolder {
	static final String TAG = "ConnectionManagerDualPane";

	boolean mJumpToAddPeer = false;
	ConnectionManager mConnMgr = null;

	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.prefs_headers, target);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// init data first
		mConnMgr = new ConnectionManager(this);
		mConnMgr.onCreate();

		// super will call onBuilderHeaders() to load frames
		super.onCreate(savedInstanceState);

	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");

		mConnMgr.onResume();
		mConnMgr.sharedPref
				.registerOnSharedPreferenceChangeListener(mConnMgr.prefChangeListener);

	}

	@Override
	protected void onPause() {
		super.onPause();
		mConnMgr.onPause();
		mConnMgr.sharedPref
				.unregisterOnSharedPreferenceChangeListener(mConnMgr.prefChangeListener);
	}

	private boolean configChange = false;

	@Override
	public Object onRetainNonConfigurationInstance() {
		configChange = true;
		return super.onRetainNonConfigurationInstance();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mConnMgr.onDestroy(configChange);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		return mConnMgr.onCreateDialog(id);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		mConnMgr.onPrepareDialog(id, dialog);
	}

	public void addConnectDevice(DeviceInfo dev) {
		mConnMgr.addConnectDevice(dev);
	}

}
