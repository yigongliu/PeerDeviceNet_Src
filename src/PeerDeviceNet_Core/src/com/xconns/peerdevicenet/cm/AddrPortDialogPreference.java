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

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;

import com.xconns.peerdevicenet.core.R;
import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.utils.Utils;

public class AddrPortDialogPreference extends DialogPreference {
	private DeviceHolder holder = null;
	private EditText mAddr = null;
	private EditText mPort = null;
	
	public AddrPortDialogPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		holder = (DeviceHolder)context;
		setPersistent(false);
		setDialogLayoutResource(R.layout.text_entry_2fields);
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		
		mAddr = (EditText) view.findViewById(R.id.field1);
		mPort = (EditText) view.findViewById(R.id.field2);
		String myAddr = Utils.getLocalIpAddr((Context)holder);
		if (myAddr != null)
			mAddr.setText(myAddr);
		//mPort.setText("");
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);
		
		if (!positiveResult)
			return;
		
		String addr = mAddr.getText().toString();
		String port = mPort.getText().toString();
		DeviceInfo device = new DeviceInfo("unknown", addr, port);
		holder.addConnectDevice(device);
	}

}
