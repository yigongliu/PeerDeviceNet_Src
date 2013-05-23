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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.xconns.peerdevicenet.core.R;

public class PreferenceLauncher extends Activity {
	final static int EULA_DIALOG = 101;
	final static int INTRO_DIALOG = 102;

	private String eulaKey = "eula";
	PackageInfo versionInfo = null;
	SharedPreferences prefs = null;
	Intent intent = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		versionInfo = getPackageInfo();

		intent = getIntent();

		if (licenseAgreed()) {
			launch(intent);
			// close myself
			finish();
		} else {
			showDialog(EULA_DIALOG);
		}
	}

	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case EULA_DIALOG:
			// Show the Eula
			String title = getString(R.string.license_agreement) + " v"
					+ versionInfo.versionName;

			// Includes the updates as well so users know what changed.
			// String message = /* mActivity.getString(R.string.updates) +
			// "\n\n" +
			// */getString(R.string.eula);
			String message = null;
			InputStream is = getResources().openRawResource(R.raw.eula);
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

			return new AlertDialog.Builder(this)
					.setTitle(title)
					.setIcon(R.drawable.router_icon)
					.setMessage(message)
					.setCancelable(false)
					.setPositiveButton(R.string.accept,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {
									// Mark this version as read.
									SharedPreferences.Editor editor = prefs
											.edit();
									editor.putInt(eulaKey,
											versionInfo.versionCode);
									editor.commit();
									//dialogInterface.dismiss();
									removeDialog(EULA_DIALOG);
									showDialog(INTRO_DIALOG);
								}
							})
					.setNegativeButton(R.string.refuse,
							new Dialog.OnClickListener() {

								public void onClick(DialogInterface dialog,
										int which) {
									// Close the activity as they have declined
									// the EULA
									removeDialog(EULA_DIALOG);
									finish();
								}

							}).create();
		case INTRO_DIALOG:
			message = null;
			is = getResources().openRawResource(R.raw.intro);
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

			return new AlertDialog.Builder(this)
					.setTitle(R.string.intro_title)
					.setIcon(R.drawable.router_icon)
					.setMessage(message)
					.setCancelable(false)
					.setPositiveButton(R.string.ok,
							new Dialog.OnClickListener() {

								public void onClick(
										DialogInterface dialogInterface, int i) {
									removeDialog(INTRO_DIALOG);
									launch(intent);
									finish();
								}
							}).create();
		}
		return null;
	}

	private PackageInfo getPackageInfo() {
		PackageInfo pi = null;
		try {
			pi = getPackageManager().getPackageInfo(getPackageName(),
					PackageManager.GET_ACTIVITIES);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		return pi;
	}

	public boolean licenseAgreed() {
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		return versionInfo.versionCode == prefs.getInt(eulaKey, -1);
	}

	void launch(Intent intent) {
		Bundle b = intent.getExtras();

		// for smaller screen, choose single-pane
		// for xlarge screen(tablet), choose dual-pane
		//boolean dualPane = true;
		
		boolean dualPane = false;
		///*
		if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE) {
			dualPane = false;
		} else if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_NORMAL) {
			dualPane = false;
		} else if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_SMALL) {
			dualPane = false;
		} else {
			dualPane = true; // only use dualPane for xlarge screen
		}
		//*/dualPane = true;

		// launch correct preference activity based on OS version
		if (!dualPane) {
			Intent i1 = new Intent(this, ConnectionManagerActivity.class);
			if (b != null)
				i1.putExtras(b);
			startActivity(i1);
		} else {
			Intent i2 = new Intent(this, ConnectionManagerDualPane.class);
			if (b != null)
				i2.putExtras(b);
			startActivity(i2);
		}

	}

}
