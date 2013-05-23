package com.xconns.samples.chat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.xconns.peerdevicenet.Router;

public class ChatByIntentingActivity extends Activity {
	// Debugging
	private static final String TAG = "ChatByIntentingActivity";

	//
	private static final String groupId = "WifiChat";
	private ArrayAdapter<String> mChatArrayAdapter;
	private ListView mChatView;
	private EditText mOutMsgText;
	private Button mSendButton;

	int numPeer = 0;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Initialize the array adapter for the conversation thread
		mChatArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
		mChatView = (ListView) findViewById(R.id.chat_msgs);
		mChatView.setAdapter(mChatArrayAdapter);

		// Initialize the compose field with a listener for the return key
		mOutMsgText = (EditText) findViewById(R.id.msg_out);
		mOutMsgText
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					public boolean onEditorAction(TextView view, int actionId,
							KeyEvent event) {
						// If the action is a key-up event on the return key,
						// send the message
						if (actionId == EditorInfo.IME_NULL
								&& event.getAction() == KeyEvent.ACTION_UP) {
							String message = view.getText().toString();
							view.setText("");
							sendMsg(message);
						}
						return true;
					}
				});

		// Initialize the send button with a listener that for click events
		mSendButton = (Button) findViewById(R.id.button_send);
		mSendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String message = mOutMsgText.getText().toString();
				sendMsg(message);
				mOutMsgText.setText("");
			}
		});
		mSendButton.setEnabled(false);

		// register broadcast recvers
		IntentFilter filter = new IntentFilter();
		filter.addAction(Router.ACTION_RECV_MSG);
		filter.addAction(Router.ACTION_SELF_JOIN);
		filter.addAction(Router.ACTION_PEER_JOIN);
		filter.addAction(Router.ACTION_PEER_LEAVE);
		filter.addAction(Router.ACTION_ERROR);
		registerReceiver(mReceiver, filter);

		// start router service
		// join "WifiChat" group
		Intent intent = new Intent(Router.ACTION_JOIN_GROUP);
		intent.putExtra(Router.GROUP_ID, groupId);
		startService(intent);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Leave "WifiChat" group
		Intent intent = new Intent(Router.ACTION_LEAVE_GROUP);
		intent.putExtra(Router.GROUP_ID, groupId);
		startService(intent);
		// unregister recvers
		unregisterReceiver(mReceiver);
	}

	private void sendMsg(String msg) {
		// show my msg first
		mChatArrayAdapter.add("Me: " + msg);
		// send my msg
		Intent intent = new Intent(Router.ACTION_SEND_MSG);
		intent.putExtra(Router.GROUP_ID, groupId);
		intent.putExtra(Router.MSG_DATA, msg.getBytes());
		startService(intent);
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (Router.ACTION_SELF_JOIN.equals(action)) {
				// append peer msg to chat area
				String[] pnames = intent.getStringArrayExtra(Router.PEER_NAMES);
				String[] paddrs = intent.getStringArrayExtra(Router.PEER_ADDRS);
				if (pnames != null && pnames.length > 0 && paddrs != null
						&& paddrs.length > 0) {
					numPeer = pnames.length;
					Log.d(TAG, "self_join found peers: " + numPeer);
					for (int i = 0; i < numPeer; i++) {
						mChatArrayAdapter.add("Peer join : " + pnames[i] + ": "
								+ paddrs[i]);
					}
					mSendButton.setEnabled(true);
				}
			} else if (Router.ACTION_PEER_JOIN.equals(action)) {
				// append peer msg to chat area
				String pname = intent.getStringExtra(Router.PEER_NAME);
				String paddr = intent.getStringExtra(Router.PEER_ADDR);
				if (pname != null && paddr != null) {
					mChatArrayAdapter
							.add("Peer join : " + pname + ": " + paddr);
					numPeer++;
					mSendButton.setEnabled(true);
				}
			} else if (Router.ACTION_PEER_LEAVE.equals(action)) {
				// append peer msg to chat area
				String pname = intent.getStringExtra(Router.PEER_NAME);
				String paddr = intent.getStringExtra(Router.PEER_ADDR);
				if (pname != null && paddr != null) {
					mChatArrayAdapter.add("Peer leave : " + pname + ": "
							+ paddr);
					numPeer--;
					if (numPeer <= 0) {
						numPeer = 0;
						mSendButton.setEnabled(false);
					}
				}
			} else if (Router.ACTION_RECV_MSG.equals(action)) {
				// append peer msg to chat area
				byte[] msg = intent.getByteArrayExtra(Router.MSG_DATA);
				mChatArrayAdapter.add("Peer send : " + new String(msg));
			} else if (Router.ACTION_ERROR.equals(action)) {
				String msg = intent.getStringExtra(Router.MSG_DATA);
				mChatArrayAdapter.add(msg);
				// Toast.makeText(ChatByIntentingActivity.this,
				// "connection failed: "+msg, Toast.LENGTH_SHORT).show();
			}
		}
	};

}