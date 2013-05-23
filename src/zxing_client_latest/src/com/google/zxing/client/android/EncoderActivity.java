/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import java.util.EnumMap;
import java.util.Map;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/**
 * Example Encoder Activity.
 * 
 * @author Justin Wetherell (phishman3579@gmail.com)
 */
public final class EncoderActivity extends Activity {

	private static final String TAG = EncoderActivity.class.getSimpleName();
	EditText contents = null;
	Button encode = null;
	int smallerDimension = 0;
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.encoder);

		// This assumes the view is full screen, which is a good assumption
		WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();
		smallerDimension = width < height ? width : height;
		smallerDimension = smallerDimension * 7 / 8;

		contents = (EditText) findViewById(R.id.contents_text_view);
		encode = (Button) findViewById(R.id.encode_btn);
		encode.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {

				try {
					Bitmap bitmap = QRCodeEncoder.encodeAsBitmap(contents.getText().toString(), smallerDimension);
					ImageView view = (ImageView) findViewById(R.id.image_view);
					view.setImageBitmap(bitmap);
				} catch (WriterException e) {
					Log.e(TAG, "Could not encode barcode", e);
				} catch (IllegalArgumentException e) {
					Log.e(TAG, "Could not encode barcode", e);
				}
			}
		});
	}
	
	final static class QRCodeEncoder {

	    private static final int WHITE = 0xFFFFFFFF;
	    private static final int BLACK = 0xFF000000;

	    public static final Bitmap encodeAsBitmap(String contents, int dimension) throws WriterException {
	        Map<EncodeHintType, Object> hints = null;
	        String encoding = guessAppropriateEncoding(contents);
	        if (encoding != null) {
	            hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
	            hints.put(EncodeHintType.CHARACTER_SET, encoding);
	        }
	        MultiFormatWriter writer = new MultiFormatWriter();
	        BitMatrix result = writer.encode(contents, BarcodeFormat.QR_CODE, dimension, dimension, hints);
	        int width = result.getWidth();
	        int height = result.getHeight();
	        int[] pixels = new int[width * height];
	        // All are 0, or black, by default
	        for (int y = 0; y < height; y++) {
	            int offset = y * width;
	            for (int x = 0; x < width; x++) {
	                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
	            }
	        }

	        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
	        return bitmap;
	    }

	    private static String guessAppropriateEncoding(CharSequence contents) {
	        // Very crude at the moment
	        for (int i = 0; i < contents.length(); i++) {
	            if (contents.charAt(i) > 0xFF) {
	                return "UTF-8";
	            }
	        }
	        return null;
	    }
	}
}
