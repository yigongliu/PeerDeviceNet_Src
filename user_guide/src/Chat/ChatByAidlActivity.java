package com.xconns.samples.chat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
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

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.IRouterGroupHandler;
import com.xconns.peerdevicenet.IRouterGroupService;
import com.xconns.peerdevicenet.Router;

public class ChatByAidlActivity extends Activity {
	// Debugging
	private static final String TAG = "ChatByAidlActivity";

	private static final String groupId = "WifiChat";
	private IRouterGroupService mGroupService = null;
	//
	private ArrayAdapter<String> mChatArrayAdapter;
	private ListView mChatView;
	private EditText mOutMsgText;
	private Button mSendButton;
	
	int numPeer = 0;

	/**
	 * Standard initialization of this activity. Set up the UI, then wait for
	 * the user to poke it before doing anything.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
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

		Intent intent = new Intent("com.xconns.peerdevicenet.GroupService");
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDestroy() {
		// leave group
		if (mGroupService != null) {
			try {
				mGroupService.leaveGroup(groupId, mGroupHandler);
			} catch (RemoteException e) {
				//Log.e(TAG, "failed at leaveGroup: " + e.getMessage());
			}
			// Detach our existing connection.
			unbindService(mConnection);
		}
		super.onDestroy();
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mGroupService = IRouterGroupService.Stub.asInterface(service);
			Log.d(TAG, "GroupService connected");
			//join group
			try {
				mGroupService.joinGroup(groupId, null, mGroupHandler);
			} catch (RemoteException e) {
				Log.e(TAG, "failed at joinGroup: " + e.getMessage());
			}
			Log.d(TAG, "joined group: "+groupId);
		}

		public void onServiceDisconnected(ComponentName className) {
			mGroupService = null;
		}
	};

	private IRouterGroupHandler mGroupHandler = new IRouterGroupHandler.Stub() {

		public void onError(String errInfo) throws RemoteException {
			Message msg = mHandler.obtainMessage(Router.MsgId.ERROR);
			msg.obj = errInfo;
			mHandler.sendMessage(msg);
		}

		public void onSelfJoin(DeviceInfo[] devices) throws RemoteException {
			if (devices != null && devices.length > 0) {
				Message msg = mHandler
						.obtainMessage(Router.MsgId.SELF_JOIN);
				msg.obj = devices;
				mHandler.sendMessage(msg);
			}
		}

		public void onPeerJoin(DeviceInfo device) throws RemoteException {
			if (device == null) return;
			Message msg = mHandler.obtainMessage(Router.MsgId.PEER_JOIN);
			msg.obj = device;
			mHandler.sendMessage(msg);
		}

		public void onSelfLeave() throws RemoteException {
		}

		public void onPeerLeave(DeviceInfo device) throws RemoteException {
			if (device == null) return;
			Message msg = mHandler.obtainMessage(Router.MsgId.PEER_LEAVE);
			msg.obj = device;
			mHandler.sendMessage(msg);
		}

		public void onReceive(DeviceInfo src, byte[] b) throws RemoteException {
			Message msg = mHandler.obtainMessage(Router.MsgId.RECV_MSG);
			msg.obj = b;
			mHandler.sendMessage(msg);
		}

		public void onGetPeerDevices(DeviceInfo[] devices)
				throws RemoteException {
		}
	};

	/**
	 * Handler of incoming messages from service.
	 */
	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Router.MsgId.RECV_MSG:
				byte[] data = (byte[]) msg.obj;
				// append peer msg to chat area
				mChatArrayAdapter.add("Peer send : "
						+ new String(data));
				break;
			case Router.MsgId.SELF_JOIN:
				DeviceInfo[] devices = (DeviceInfo[]) msg.obj;
				for(DeviceInfo dev : devices) {
					mChatArrayAdapter.add("Peer join : " + dev.name + ":"
							+ dev.addr);
				}
				numPeer = devices.length;
				Log.d(TAG, "self_join found peers: "+numPeer);
				mSendButton.setEnabled(true);
				break;
			case Router.MsgId.PEER_JOIN:
				DeviceInfo dev = (DeviceInfo) msg.obj;
				// append peer msg to chat area
				mChatArrayAdapter.add("Peer join : " + dev.name + ":"
						+ dev.addr);
				numPeer++;
				mSendButton.setEnabled(true);
				break;
			case Router.MsgId.PEER_LEAVE:
				dev = (DeviceInfo) msg.obj;
				// append peer msg to chat area
				mChatArrayAdapter.add("Peer leave : " + dev.name + ":"
						+ dev.addr);
				numPeer--;
				if (numPeer <= 0) {
					numPeer = 0;
					mSendButton.setEnabled(false);
				}
				break;
			case Router.MsgId.ERROR:
				mChatArrayAdapter.add((String) msg.obj);
				// Toast.makeText(ChatByIntentingActivity.this,
				// "connection failed: "+msg, Toast.LENGTH_SHORT).show();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};

	private void sendMsg(String msg_data) {
		// show my msg first
		mChatArrayAdapter.add("Me: " + msg_data);
		// send my msg
		try {
			mGroupService.send(groupId, null, msg_data.getBytes());
		} catch (RemoteException re) {
			//Log.e(TAG, "failed to send msg");
		}
	}
}
