/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.xconns.peerdevicenet.apps;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;

import com.xconns.peerdevicenet.DeviceInfo;
import com.xconns.peerdevicenet.IRouterGroupHandler;
import com.xconns.peerdevicenet.IRouterGroupService;
import com.xconns.peerdevicenet.Router;


// a simple msg for rotation info. 
class RotateMsg {
	// msg ids
	public final static int INIT_ORIENT_REQ = 1; //inital query of peers' orientation
	public final static int INIT_ORIENT_RSP = 2; //responses of peers' orientation
	public final static int DELTA_ROTATION = 3;  //changes of orientation
	
	public int msgId; 
	public float rx, ry; //rotation along x & y axis
	public RotateMsg() {
	}
	public RotateMsg(int id, float x, float y) {
		msgId = id;
		rx = x;
		ry = y;
	}
	
	//the following using android Parcel to marshaling data, could use JSON etc.
	public byte[] marshall() {
		final Parcel parcel = Parcel.obtain();
		byte[] data = null;
		parcel.writeInt(msgId);
		parcel.writeFloat(rx);
		parcel.writeFloat(ry);
		data = parcel.marshall();
		parcel.recycle();
		return data;
	}

	public void unmarshall(byte[] data, int len) {
		final Parcel parcel = Parcel.obtain();
		parcel.unmarshall(data, 0, len);
		parcel.setDataPosition(0);
		RotateMsg m = new RotateMsg();
		msgId = parcel.readInt();
		rx = parcel.readFloat();
		ry = parcel.readFloat();
		parcel.recycle();
	}

}


/**
 * Wrapper activity demonstrating the use of {@link GLSurfaceView}, a view that
 * uses OpenGL drawing into a dedicated surface.
 * 
 * Shows: + How to redraw in response to user input.
 */
@SuppressLint("NewApi")
public class TouchRotateActivity extends Activity {
	private static final String TAG = "TouchRotateActivity";

	private static final String groupId = "RotateWith123!@#Peers";
	private IRouterGroupService mGroupService = null;
	

	// opengl canvas
	private TouchSurfaceView mGLSurfaceView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/*
		setContentView(R.layout.main);

		LinearLayout mainView = (LinearLayout) findViewById(R.id.container);
		*/
		
		// Create our Preview view and set it as the content of our
		// Activity
		mGLSurfaceView = new TouchSurfaceView(this);
		//mainView.addView(mGLSurfaceView);
		setContentView(mGLSurfaceView);
		mGLSurfaceView.requestFocus();
		mGLSurfaceView.setFocusableInTouchMode(true);
		
		/*
		//add a button to allow device connect to peers, if it is not connected
		Button connBtn = (Button) findViewById(R.id.conn_btn);
		connBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(
						"com.xconns.peerdevicenet.CONNECTION_MANAGEMENT");
				startActivity(intent);
			}
		});
		*/
	}

	@Override
	protected void onResume() {
		super.onResume();
		mGLSurfaceView.onResume();
		//bind to group service
		Intent intent = new Intent("com.xconns.peerdevicenet.GroupService");
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		// Ideally a game should implement onResume() and onPause()
		// to take appropriate action when the activity looses focus
	}

	@Override
	protected void onPause() {
		if (mGroupService != null) {
			try {
				// leave group
				mGroupService.leaveGroup(groupId, mGroupHandler);
			} catch (RemoteException e) {
				// Log.e(TAG, "failed at leaveGroup: " + e.getMessage());
			}
		}
		// unbind service
		unbindService(mConnection);
		// Ideally a game should implement onResume() and onPause()
		// to take appropriate action when the activity looses focus
		super.onPause();
		mGLSurfaceView.onPause();
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mGroupService = IRouterGroupService.Stub.asInterface(service);
			Log.d(TAG, "GroupService connected");
			// join group
			try {
				mGroupService.joinGroup(groupId, null, mGroupHandler);
			} catch (RemoteException e) {
				Log.e(TAG, "failed at joinGroup: " + e.getMessage());
			}
			Log.d(TAG, "joined group: " + groupId);
		}

		public void onServiceDisconnected(ComponentName className) {
			mGroupService = null;
		}
	};

	//send rotate info to peers
	public void sendRotateMsgToPeers(RotateMsg m) {
		if (mGroupService != null) {
			try {
				mGroupService.send(groupId, null, m.marshall());
			} 
			catch (RemoteException re) {
				Log.d(TAG, "fail to send rotate info");
			}
		}
	}
	
	//process rotate info from peers
	void procRotateMsgFromPeer(DeviceInfo dev, RotateMsg m) {
		//process init orientation req
		if (m.msgId == RotateMsg.INIT_ORIENT_REQ) {
			RotateMsg m1 = mGLSurfaceView.getCurrentOrientation();
			try {
				mGroupService.send(groupId, dev, m1.marshall());
			}
			catch(RemoteException re) {
				Log.d(TAG, "fail to send initial orientation");
			}
			return;
		}
		//handle init orientation resp and delta rotation
		mGLSurfaceView.procRotateMsgFromPeer(m);
	}

	private IRouterGroupHandler mGroupHandler = new IRouterGroupHandler.Stub() {

		public void onError(String errInfo) throws RemoteException {
			Log.d(TAG, "group comm error : " + errInfo);
		}

		public void onSelfJoin(DeviceInfo[] devices) throws RemoteException {
			if (devices != null && devices.length > 0) {
				//i have peers, sync my inital orientation with them
				RotateMsg m = new RotateMsg(RotateMsg.INIT_ORIENT_REQ, 0, 0); //req init orientation
				mGroupService.send(groupId, null, m.marshall());
			}
		}

		public void onPeerJoin(DeviceInfo device) throws RemoteException {
		}

		public void onSelfLeave() throws RemoteException {
		}

		public void onPeerLeave(DeviceInfo device) throws RemoteException {
		}

		public void onReceive(DeviceInfo src, byte[] b) throws RemoteException {
			Log.d(TAG, "recv rotate info from "+src.toString());
			Message msg = mHandler.obtainMessage(Router.MsgId.RECV_MSG);
			msg.obj = new Object[]{src, b};
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
				Object[] data = (Object[]) msg.obj;
				DeviceInfo dev = (DeviceInfo) data[0];
				byte[] rawbytes = (byte[]) data[1];
				RotateMsg m = new RotateMsg();
				m.unmarshall(rawbytes, rawbytes.length);
				procRotateMsgFromPeer(dev, m);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	};

}


/**
 * Implement a simple rotation control.
 * 
 */
class TouchSurfaceView extends GLSurfaceView {
	
	TouchRotateActivity rotAct = null;

	public TouchSurfaceView(TouchRotateActivity context) {
		super(context);
		
		// We want an 8888 pixel format because that's required for
		// a translucent window.
		setEGLConfigChooser(8, 8, 8, 8, 0, 0);
		// Use a surface format with an Alpha channel:
		getHolder().setFormat(PixelFormat.TRANSLUCENT);
		//
		rotAct = context;
		mRenderer = new CubeRenderer();
		setRenderer(mRenderer);
		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}

	@Override
	public boolean onTrackballEvent(MotionEvent e) {
		float dx = e.getX() * TRACKBALL_SCALE_FACTOR;
		float dy = e.getY() * TRACKBALL_SCALE_FACTOR;
		rotAct.sendRotateMsgToPeers(new RotateMsg(RotateMsg.DELTA_ROTATION, dx, dy));
		mRenderer.mAngleX += dx;
		mRenderer.mAngleY += dy;
		requestRender();
		return true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		float x = e.getX();
		float y = e.getY();
		switch (e.getAction()) {
		case MotionEvent.ACTION_MOVE:
			float dx = (x - mPreviousX) * TOUCH_SCALE_FACTOR;
			float dy = (y - mPreviousY) * TOUCH_SCALE_FACTOR;
			rotAct.sendRotateMsgToPeers(new RotateMsg(RotateMsg.DELTA_ROTATION, dx, dy));
			mRenderer.mAngleX += dx;
			mRenderer.mAngleY += dy;
			requestRender();
		}
		mPreviousX = x;
		mPreviousY = y;
		return true;
	}
	
	//the following two methods are added for handling msgs from peers
	public void procRotateMsgFromPeer(RotateMsg m) {
		if (m.msgId == RotateMsg.INIT_ORIENT_RSP) {
			mRenderer.mAngleX = m.rx;
			mRenderer.mAngleY = m.ry;
		}
		else if (m.msgId == RotateMsg.DELTA_ROTATION) {
			mRenderer.mAngleX += m.rx;
			mRenderer.mAngleY += m.ry;
		}
		requestRender();		
	}
	
	public RotateMsg getCurrentOrientation() {
		return new RotateMsg(RotateMsg.INIT_ORIENT_RSP, mRenderer.mAngleX, mRenderer.mAngleY);
	}

	/**
	 * Render a cube.
	 */
	
	private class CubeRenderer implements GLSurfaceView.Renderer {
		public CubeRenderer() {
			mCube = new Cube();
		}

		public void onDrawFrame(GL10 gl) {

			/*
			 * Now we're ready to draw some 3D objects
			 */

			gl.glMatrixMode(GL10.GL_MODELVIEW);
			/*
			 * Usually, the first thing one might want to do is to clear the
			 * screen. The most efficient way of doing this is to use glClear().
			 */

			gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
			//
			gl.glLoadIdentity();
			gl.glTranslatef(0, 0, -4.0f);
			gl.glRotatef(mAngleX, 0, 1, 0);
			gl.glRotatef(mAngleY, 1, 0, 0);

			gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
			gl.glEnableClientState(GL10.GL_COLOR_ARRAY);

			mCube.draw(gl);
		}

		public void onSurfaceChanged(GL10 gl, int width, int height) {
			gl.glViewport(0, 0, width, height);

			/*
			 * Set our projection matrix. This doesn't have to be done each time
			 * we draw, but usually a new projection needs to be set when the
			 * viewport is resized.
			 */

			float ratio = (float) width / height;
			gl.glMatrixMode(GL10.GL_PROJECTION);
			gl.glLoadIdentity();
			gl.glFrustumf(-ratio, ratio, -1, 1, 1, 10);
		}

		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			/*
			 * By default, OpenGL enables features that improve quality but
			 * reduce performance. One might want to tweak that especially on
			 * software renderer.
			 */
			gl.glDisable(GL10.GL_DITHER);
            gl.glDisable(GL10.GL_DEPTH_TEST);
            gl.glDisable(GL10.GL_LIGHTING);

			/*
			 * Some one-time OpenGL initialization can be made here probably
			 * based on features of this particular context
			 */
			gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);

			//gl.glClearColor(1, 1, 1, 1);
			gl.glClearColor(0, 0, 0, 0);
			gl.glEnable(GL10.GL_CULL_FACE);
			gl.glShadeModel(GL10.GL_SMOOTH);
			gl.glEnable(GL10.GL_DEPTH_TEST);
		}

		private Cube mCube;
		public float mAngleX;
		public float mAngleY;
	}

	private final float TOUCH_SCALE_FACTOR = 180.0f / 320;
	private final float TRACKBALL_SCALE_FACTOR = 36.0f;
	private CubeRenderer mRenderer;
	private float mPreviousX;
	private float mPreviousY;
}

/**
 * A vertex shaded cube.
 */
class Cube
{
    public Cube()
    {
        //int one = 0x10000;
        int one = 0x10000;
        int vertices[] = {
                -one, -one, -one,
                one, -one, -one,
                one,  one, -one,
                -one,  one, -one,
                -one, -one,  one,
                one, -one,  one,
                one,  one,  one,
                -one,  one,  one,
        };

        float colors[] = {
                0,    0,    0,  0.75f,
                1,    0,    0,  0.75f,
                1,  1,    0,  0.75f,
                0,  1,    0,  0.75f,
                0,    0,  1,  0.75f,
                1,    0,  1,  0.75f,
                1,  1,  1,  0.75f,
                0,  1,  1,  0.75f,
        };

        byte indices[] = {
                0, 4, 5,    0, 5, 1,
                1, 5, 6,    1, 6, 2,
                2, 6, 7,    2, 7, 3,
                3, 7, 4,    3, 4, 0,
                4, 7, 6,    4, 6, 5,
                3, 0, 1,    3, 1, 2
        };

        // Buffers to be passed to gl*Pointer() functions
        // must be direct, i.e., they must be placed on the
        // native heap where the garbage collector cannot
        // move them.
        //
        // Buffers with multi-byte datatypes (e.g., short, int, float)
        // must have their byte order set to native order

        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length*4);
        vbb.order(ByteOrder.nativeOrder());
        mVertexBuffer = vbb.asIntBuffer();
        mVertexBuffer.put(vertices);
        mVertexBuffer.position(0); 

        ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length*4*4);
        cbb.order(ByteOrder.nativeOrder());
        mColorBuffer = cbb.asFloatBuffer();
        mColorBuffer.put(colors);
        mColorBuffer.position(0);

        mIndexBuffer = ByteBuffer.allocateDirect(indices.length);
        mIndexBuffer.put(indices);
        mIndexBuffer.position(0);
    }

    public void draw(GL10 gl)
    {
        gl.glFrontFace(gl.GL_CW);
        gl.glVertexPointer(3, gl.GL_FIXED, 0, mVertexBuffer);
        gl.glColorPointer(4, gl.GL_FLOAT, 0, mColorBuffer);
        gl.glDrawElements(gl.GL_TRIANGLES, 36, gl.GL_UNSIGNED_BYTE, mIndexBuffer);
    }

    private IntBuffer   mVertexBuffer;
    private FloatBuffer   mColorBuffer;
    private ByteBuffer  mIndexBuffer;
}

/*
class Cube {
	public Cube() {
		int one = 0x10000;
		int vertices[] = { -one, -one, -one, one, -one, -one, one, one, -one,
				-one, one, -one, -one, -one, one, one, -one, one, one, one,
				one, -one, one, one, };

		int colors[] = { 0, 0, 0, one, one, 0, 0, one, one, one, 0, one, 0,
				one, 0, one, 0, 0, one, one, one, 0, one, one, one, one, one,
				one, 0, one, one, one, };

		byte indices[] = { 0, 4, 5, 0, 5, 1, 1, 5, 6, 1, 6, 2, 2, 6, 7, 2, 7,
				3, 3, 7, 4, 3, 4, 0, 4, 7, 6, 4, 6, 5, 3, 0, 1, 3, 1, 2 };

		// Buffers to be passed to gl*Pointer() functions
		// must be direct, i.e., they must be placed on the
		// native heap where the garbage collector cannot
		// move them.
		//
		// Buffers with multi-byte datatypes (e.g., short, int, float)
		// must have their byte order set to native order

		ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
		vbb.order(ByteOrder.nativeOrder());
		mVertexBuffer = vbb.asIntBuffer();
		mVertexBuffer.put(vertices);
		mVertexBuffer.position(0);

		ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length * 4);
		cbb.order(ByteOrder.nativeOrder());
		mColorBuffer = cbb.asIntBuffer();
		mColorBuffer.put(colors);
		mColorBuffer.position(0);

		mIndexBuffer = ByteBuffer.allocateDirect(indices.length);
		mIndexBuffer.put(indices);
		mIndexBuffer.position(0);
	}

	public void draw(GL10 gl) {
		gl.glFrontFace(gl.GL_CW);
		gl.glVertexPointer(3, gl.GL_FIXED, 0, mVertexBuffer);
		gl.glColorPointer(4, gl.GL_FIXED, 0, mColorBuffer);
		gl.glDrawElements(gl.GL_TRIANGLES, 36, gl.GL_UNSIGNED_BYTE,
				mIndexBuffer);
	}

	private IntBuffer mVertexBuffer;
	private IntBuffer mColorBuffer;
	private ByteBuffer mIndexBuffer;
}
*/
