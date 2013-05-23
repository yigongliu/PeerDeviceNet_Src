package com.xconns.samples.chat;

import com.xconns.samples.chat.R;
import com.xconns.samples.chat.R.id;
import com.xconns.samples.chat.R.layout;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RadioGroup;

public class ChatActivity extends Activity {
	// Debugging
	private static final String TAG = "ChatByIntentingActivity";
	
	private Button mStartButton;
    private RadioGroup mApiTypeRadioGroup;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.startup_menu);

        mApiTypeRadioGroup = (RadioGroup) findViewById(R.id.startup_choice);

		mStartButton = (Button) findViewById(R.id.button_start);
		mStartButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				int chosenId = mApiTypeRadioGroup.getCheckedRadioButtonId();
				switch(chosenId) {
				case R.id.conn_mgr:
					Intent intent = new Intent("com.xconns.peerdevicenet.CONNECTOR");
					startActivity(intent);
					break;
				case R.id.chat_intenting:
					intent = new Intent(ChatActivity.this, ChatByIntentingActivity.class);
					startActivity(intent);
					break;
				case R.id.chat_messenger:
					intent = new Intent(ChatActivity.this, ChatByMessengerActivity.class);
					startActivity(intent);
					break;
				case R.id.chat_aidl:
					intent = new Intent(ChatActivity.this, ChatByAidlActivity.class);
					startActivity(intent);
					break;
				}
			}
		});

	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}
