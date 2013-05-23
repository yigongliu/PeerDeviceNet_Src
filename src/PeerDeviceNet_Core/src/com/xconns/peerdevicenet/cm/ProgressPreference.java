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
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;

import com.xconns.peerdevicenet.core.R;

public class ProgressPreference extends Preference {
	private ProgressBar mProgress = null;
	private boolean started = false;

	public ProgressPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		setPersistent(false);
		setWidgetLayoutResource(R.layout.progress_widget);
	}

	@Override
	protected void onBindView(View view) {
		// TODO Auto-generated method stub
		super.onBindView(view);
		mProgress = (ProgressBar) view.findViewById(R.id.cmd_progress);
		if (mProgress != null) {
			if (started)
				mProgress.setVisibility(View.VISIBLE);
			else
				mProgress.setVisibility(View.INVISIBLE);
		}
	}
	
	public void startProgress() {
		started = true;
		if(mProgress != null) {
			mProgress.setVisibility(View.VISIBLE);
		}
	}

	public void stopProgress() {
		started = false;
		if(mProgress != null) {
			mProgress.setVisibility(View.INVISIBLE);
		}
	}
}
