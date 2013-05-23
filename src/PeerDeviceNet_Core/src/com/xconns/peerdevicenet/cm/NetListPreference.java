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

import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.preference.ListPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;

import com.xconns.peerdevicenet.utils.Utils;

//this is used to accomodate ListPreference behaviour since android4.1
public class NetListPreference extends ListPreference {
	final static String TAG = "NetListPreference";

	Context context = null;
	ConnectionManager cm = null;
	int sel = -1;

	public NetListPreference(Context c) {
		super(c);
		context = c;
		setPersistent(false);
	}

	public NetListPreference(Context c, AttributeSet attrs) {
		super(c, attrs);
		context = c;
		setPersistent(false);
	}

	public void setCM(ConnectionManager c) {
		cm = c;
	}

	public void setSel(int s) {
		sel = s;
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		// TODO Auto-generated method stub
		super.onPrepareDialogBuilder(builder);
		//
		builder.setSingleChoiceItems(
				cm.netListEntries.toArray(new CharSequence[0]), sel,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						CharSequence mNetName = cm.netListValues.get(item);
						if (mNetName.equals(cm.config_new_nets)) {
							Log.d(TAG, "show net config interface");
							try {
								if (Utils.ANDROID_VERSION >= 16) {
									Intent in = new Intent(
											Settings.ACTION_WIFI_SETTINGS);
									context.startActivity(in);
								} else {
									Intent in = new Intent(
											Settings.ACTION_WIRELESS_SETTINGS);
									context.startActivity(in);
								}
							} catch (ActivityNotFoundException anf) {
								Log.d(TAG,
										"no activity for : Settings.ACTION_WIRELESS_SETTINGS"
												+ anf.getMessage());
								Intent in = new Intent(
										WifiManager.ACTION_PICK_WIFI_NETWORK);
								context.startActivity(in);
							}
						} else {
							cm.activateNet(mNetName.toString());
						}
						dialog.dismiss();
					}
				});
	}
}
