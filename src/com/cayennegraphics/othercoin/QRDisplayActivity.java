package com.cayennegraphics.othercoin;

import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.bccapi.bitlib.util.HexUtils;
import com.cayennegraphics.othercoin.R;

import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class QRDisplayActivity extends Activity {

	private String qrData;
	private String type;
	private String remotePhoneNumber;
	
	int qrCodeDimension = 270;
	long maxCharsPerCode = 1024;

	ImageView imageView;

	private static final int CONTACT_PICKER_RESULT = 1001;  
	public void doLaunchContactPicker() {  
	    Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,  
	            Contacts.CONTENT_URI);  
	    startActivityForResult(contactPickerIntent, CONTACT_PICKER_RESULT);  
	}  
	
	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent i = getIntent();
		qrData = i.getStringExtra("TEXT");
		type = i.getStringExtra("TYPE");
		remotePhoneNumber = i.getStringExtra("PHONE");
		getActionBar().setDisplayHomeAsUpEnabled(true);
		// requestWindowFeature(Window.FEATURE_NO_TITLE);
		// getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		// WindowManager.LayoutParams.FLAG_FULLSCREEN);

		// getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
		// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		setContentView(R.layout.qr);

		// ImageView to display the QR code in. This should be defined in
		// your Activity's XML layout file
		imageView = (ImageView) findViewById(R.id.qrCode);
		findViewById(R.id.container).setBackgroundColor(
				Color.parseColor("#FFFFFF"));
		populate();

	}

	@Override
	protected void onResume() {
		super.onResume();

	}

	public void populate() {

		Log.i(OtherCoinActivity.logTag, "Unencoded QR data is " + qrData);
		
		String toDisplay = qrData;
		
		if  (!"BA".equals(type)) {
			byte[] qrBinary = HexUtils.toBytes(qrData);
			toDisplay = type+" "+Base64.encodeToString(qrBinary, Base64.NO_WRAP);
		}
		Log.i(OtherCoinActivity.logTag, "Encoded QR data is " + toDisplay);

		QRCodeEncoder qrCodeEncoder = new QRCodeEncoder(toDisplay, null,
				Contents.Type.TEXT, BarcodeFormat.QR_CODE.toString(),
				qrCodeDimension);

		try {
			final Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();

			imageView.post(new Runnable() {
				public void run() {
					imageView.setImageBitmap(bitmap);

				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
		return;

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.qrdisplaymenu, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items

		switch (item.getItemId()) {
		case R.id.action_sendastext:
			getRecipientAndSendSMS(type, HexUtils.toBytes(qrData), remotePhoneNumber);
						return true;
				default:
			return super.onOptionsItemSelected(item);
		}
	}
	

	private void getRecipientAndSendSMS(String type, byte[] contentBinary, String defaultNumber) {
		final String content = "#OC "+type+" "+Base64.encodeToString(contentBinary, Base64.NO_WRAP);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = getLayoutInflater();

		
		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		builder.setTitle("Send via SMS")
		
				.setMessage("Input recipient phone number below")
				.setView(inflater.inflate(R.layout.phone_entry, null))

				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						EditText ph = (EditText) ((AlertDialog)dialog).findViewById(R.id.phonenumber);
						String recipient = ph.getText().toString();
						SmsManager manager = SmsManager.getDefault();
						
						ArrayList<String> splitContent = manager.divideMessage(content);
						manager.sendMultipartTextMessage(recipient, null, splitContent, null, null);
						finish();
					}
				})
				.setNeutralButton("From contacts", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						doLaunchContactPicker();
					}
				})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
				AlertDialog td = builder.create();
			
				td.show();
				
				if (defaultNumber!=null) {
					EditText ph = (EditText) td.findViewById(R.id.phonenumber);
					ph.setText(defaultNumber);
				}
	}


	@Override  
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
	    if (resultCode == RESULT_OK) {  
	        switch (requestCode) {  
	        case CONTACT_PICKER_RESULT:  
	            Cursor cursor = null;  
	            String phone = "";  
	            try {  
	                Uri result = data.getData();  
	                Log.v(OtherCoinActivity.logTag, "Got a contact result: "  
	                        + result.toString());  
	                // get the contact id from the Uri  
	                String id = result.getLastPathSegment();  
	                // query for everything phone  
	                cursor = getContentResolver().query(Phone.CONTENT_URI,  
	                        null, Phone.CONTACT_ID + "=?", new String[] { id },  
	                        null);  
	                int phoneIdx = cursor.getColumnIndex(Phone.DATA1);  
	                // let's just get the first phone  
	                if (cursor.moveToFirst()) {  
	                    phone = cursor.getString(phoneIdx);  
	                    Log.v(OtherCoinActivity.logTag, "Got phone: " + phone);  
	                    getRecipientAndSendSMS(type, HexUtils.toBytes(qrData), phone);
	                } else {  
	                    Log.w(OtherCoinActivity.logTag, "No results");  
	                }  
	            } catch (Exception e) {  
	                Log.e(OtherCoinActivity.logTag, "Failed to get phone data", e);  
	            } finally {  
	                if (cursor != null) {  
	                    cursor.close();  
	                }  
	                  
	                if (phone.length() == 0) {  
	                    Toast.makeText(this, "No phone found for contact.",  
	                            Toast.LENGTH_LONG).show();  
	                }  
	            }  
	            break;  
	        }  
	    } else {  
	        Log.w(OtherCoinActivity.logTag, "Warning: activity result not ok");  
	    }  
	}  

}
