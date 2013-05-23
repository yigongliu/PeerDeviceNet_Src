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

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.xconns.peerdevicenet.core.R;

@TargetApi(11)
public class ConnectionsPreferenceFragment extends PreferenceFragment {
	ConnectionManagerDualPane mActivity = null;
	ConnectionManager cm = null;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onActivityCreated(savedInstanceState);

		cm.attachConnectionsGUI(getPreferenceScreen());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.conn_prefs);

		// Tell the framework to try to keep this fragment around
		// during a configuration change.
		// setRetainInstance(true);
	}

	@Override
	public void onAttach(Activity activity) {
		// TODO Auto-generated method stub
		super.onAttach(activity);
		mActivity = (ConnectionManagerDualPane) activity;
		cm = mActivity.mConnMgr;
	}

	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		//cm.updateNetworkSettings();
		
		cm.onResume();

	}

}
