package com.cayennegraphics.othercoin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.ArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import com.bccapi.bitlib.crypto.InMemoryPrivateKey;
import com.bccapi.bitlib.crypto.PublicKey;
import com.bccapi.bitlib.crypto.ec.Parameters;
import com.bccapi.bitlib.crypto.ec.Point;
import com.bccapi.bitlib.model.NetworkParameters;
import com.bccapi.bitlib.util.Base58;
import com.bccapi.bitlib.util.HashUtils;
import com.cayennegraphics.othercoin.R;

import com.mobilesecuritycard.openmobileapi.service.terminals.CertgatePluginTerminal;
import com.reinersct.cyberjack.SmartCardReader;
import com.reinersct.cyberjack.SmartCardReaderService;
import com.reinersct.cyberjack.exceptions.ServiceNotBoundException;
import com.reinersct.cyberjack.listeners.ServiceBindCallback;

import android.net.Uri;
import android.nfc.*;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.RemoteException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Base64;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

public class OtherCoinActivity extends ListActivity {
	
	// special mode for Samsung Knox secure containers
	// set to true to disable NFC and microSD access and only keep Bluetooth (that is supposed to work)
	private static final boolean KNOX = false;

	// operating mode for the application
	// MODE_INTERNAL tries to talk to the OtherCoin smartcard inserted into the microSD slot or connected over USB
	// MODE_CGTOKEN talks to the OtherCoin smartcard over Bluetooth (using the CyberJack Components from Reiner SCT)
	// MODE_NFC talks to an NFC OtherCoin smartcard (currently only tested on the Yubikey Neo)
	public static final int MODE_INTERNAL = 0;
	public static final int MODE_CGTOKEN = 1;
	public static final int MODE_NFC = 2;

	// default mode is MODE_INTERNAL (microSD card)
	public static int mode = MODE_INTERNAL;

	static final String logTag = "OtherCoin";
	
	// this is where the BitcoinWallet gets its backed up keys from - we save ours here to make them easily importable from the Android Bitcoin Wallet
    public static final File EXTERNAL_WALLET_BACKUP_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);


	private NfcAdapter mNfcAdapter;
	private IsoDep tag;
	private ProgressDialog pd;
	
	// the random number generator is used to generate the key halves that the smartphone knows
	// these key halves are combined with the keys on the smartcard to create the final private keys that the app uses
	// this way neither the smartphone nor the smartcard know the actual private key and only the smartphone knows the public key (and corresponding Bitcoin address)
	private SecureRandom random;

	// this is where we store the public keys that we receive from the smartcard
	ArrayList<PK> publicKeys;
	ArrayAdapter<PK> arrayAdapter;

	private PendingIntent mPendingIntent;
	private String[][] mTechLists;

	// used for receiving SMS
	private BroadcastReceiver mReceiver;
	

	// Bluetooth (cgToken) mode stuff - we need to connect to an external service (provided by the Cyberjack Components app) to talk to the cgToken
	private SmartCardReaderService mSmartCardReaderService;
	private SmartCardReader mSmartCardReader;
	
	// the microSD smartcard terminal - exposes APDU sending/receiving to/from the smartcard
	private CertgatePluginTerminal cgTerminal;

	
	
	private static final int REQUEST_DEVICE = 0;
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int REQUEST_SCAN = 2;
	
	// context menu options
	private static final int CONTEXT_MENU_SEND = 0;
	private static final int CONTEXT_MENU_REVEAL = 1;
	private static final int CONTEXT_MENU_SHOWQR = 2;
	private static final int CONTEXT_MENU_ADDFUNDS = 3;

	// publicIdentity stores the (encrypted) public identity of the currently inserted OtherCoin card
	// this is the identity that is presented to another user during an initial handshake and will become the destination address of the funds once a transfer is initiated
	private String publicIdentity = null;
	
	// this is the public identity of the other party (same format as our publicIdentity, still encrypted)
	private String remotePartyPublicData = null;
	
	// if the remote party has sent the identity over SMS, we store the number here so that we can display it to the user and send the response to it directly
	private String remotePhoneNumber = null;
	
	// (encrypted) key transfer data, as received from the smartcard - this needs to be sent to the other party
	private String transferKeyData = null;
	
	// the current PIN, as entered by the user (we cache it so that we don't have to prompt the user every time)
	private String currentPIN = null;

	
	// callback class that gets called if/when the application successfully establishes a connection to the cgToken service (CyberJack Components)
	private class cgTokenServiceCallback implements ServiceBindCallback {
		@Override
		public void onBound() {
			try {
				// If default Reader is set, use it.
				if (mSmartCardReaderService.isDefaultReaderSet()) {

					Toast.makeText(OtherCoinActivity.this,
							"using default reader", Toast.LENGTH_LONG).show();

					// Get default reader
					Log.i(logTag, "Getting default reader");
					mSmartCardReader = mSmartCardReaderService
							.getDefaultReader();
					Log.i(logTag, "Default reader is " + mSmartCardReader);
					if (mSmartCardReader == null) {

						Log.d(logTag,
								"Bluetooth is disabled. Call Activity to enable it.");
						Intent enableIntent = new Intent(
								BluetoothAdapter.ACTION_REQUEST_ENABLE);
						startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
					} else {

						if (publicKeys.size() == 0)
							populateList();
					}

				} else { // Default reader is not set: Start Activity to choose
							// reader.

					Log.i(logTag,
							"No default reader found. Starting Device-Chooser Activity.");
					startActivityForResult(
							mSmartCardReaderService
									.getDeviceChooserActivityIntent(),
							REQUEST_DEVICE);
				}

			} catch (Exception e) {
				String defaultReaderError = "Error while getting default reader.";
				Log.e(logTag, defaultReaderError, e);
				Toast.makeText(OtherCoinActivity.this, defaultReaderError,
						Toast.LENGTH_LONG).show();
				finish();
			}
		}

		@Override
		public void onBindFailed() {
			Toast.makeText(OtherCoinActivity.this, "binding to service failed",
					Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	
	// ugly hack, always show the overflow dots in the Action Bar, even if a physical Menu button is present
	private void getOverflowMenu() {

	     try {
	        ViewConfiguration config = ViewConfiguration.get(this);
	        Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
	        if(menuKeyField != null) {
	            menuKeyField.setAccessible(true);
	            menuKeyField.setBoolean(config, false);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		// force an overflow icon in the ActionBar
		getOverflowMenu();

		
		random = new SecureRandom();
		publicKeys = new ArrayList<PK>();

		// register to receive SMS messages (see below)
		registerSMSReceiver();
		
		arrayAdapter = new ArrayAdapter<PK>(this,
				android.R.layout.simple_list_item_2, android.R.id.text1,
				publicKeys) {
			@Override
			public View getView(int position, View convertView,
					android.view.ViewGroup parent) {
				View view = super.getView(position, convertView, parent);
				TextView text1 = (TextView) view
						.findViewById(android.R.id.text1);
				TextView text2 = (TextView) view
						.findViewById(android.R.id.text2);

				text1.setText(publicKeys.get(position).getAddress());
				text1.setTextSize(17);
				
				// zero or negative balances are displayed in RED
				if (publicKeys.get(position).balance <= 0)
					text2.setTextColor(Color.RED);

				text2.setText(publicKeys.get(position).getBalance());
				return view;
			}
		};
		
		TextView tv = new TextView(this);
		((ViewGroup) getListView().getParent()).addView(tv);
		tv.setText("No keys on this card");
		tv.setTextSize(16);
		tv.setGravity(Gravity.CENTER);
		getListView().setEmptyView(tv);
		tv.setVisibility(View.VISIBLE);
		// arrayAdapter = new ArrayAdapter<PK>(this,
		// android.R.layout.simple_list_item_1, publicKeys);
		setListAdapter(arrayAdapter);

		registerForContextMenu(getListView());

		
		// prompt the user for the PIN code
		createPINDialog().show();

	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
		PK ckey = (PK) (getListAdapter().getItem(acmi.position));
		menu.setHeaderTitle(ckey.getAddress());
		menu.add(Menu.NONE, CONTEXT_MENU_SEND, Menu.NONE, "Send");
		menu.add(Menu.NONE, CONTEXT_MENU_REVEAL, Menu.NONE, "Reveal");
		menu.add(Menu.NONE, CONTEXT_MENU_SHOWQR, Menu.NONE, "Show QR Code");
		menu.add(Menu.NONE, CONTEXT_MENU_ADDFUNDS, Menu.NONE, "Add funds");

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		PK ckey = (PK) getListAdapter().getItem(info.position);/*
																 * what item was
																 * selected is
																 * ListView
																 */

		switch (item.getItemId()) {
		
		// send a key to someone else - it will be removed from the smartcard and the encrypted private key will be given to you to send to the other side
		case CONTEXT_MENU_SEND:
			requestKeyTransfer(ckey);
			break;
			
		// reveal a key - it will be deleted from the smartcard, combined with the other half (known only by the smartphone) and then imported into the Bitcoin Wallet
		case CONTEXT_MENU_REVEAL:
			requestKeyReveal(ckey);
			break;
			
		// just show the QR code containing the Bitcoin address, no Base64 encoding, nothing else - this helps if you want someone else to just pay you at that address	
		case CONTEXT_MENU_SHOWQR:
			showQR("BA", "bitcoin:"+ckey.getAddress(), "Show the code to another device to receive a payment");
			break;
			
		// add funds to this address - starts the Bitcoin Wallet to do this
		case CONTEXT_MENU_ADDFUNDS:
			
			Intent browserIntent = new Intent(
				    Intent.ACTION_VIEW,
				    Uri.parse("bitcoin:"+ckey.getAddress()));
				startActivity(browserIntent);
			break;
		}

		return (super.onOptionsItemSelected(item));
	}

	
	// initialize the smartcard - depending on the mode it calls the corresponding initialization procedure
	public void initTerminal() {
		if (mode == MODE_CGTOKEN) {
			initCGToken();
		} else if (mode == MODE_INTERNAL) {
			initInternal();
		} else if (mode == MODE_NFC) {
			initNFC();
		}
	}

	private void initNFC() {
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null) {
			Toast.makeText(this, "NFC is not available", Toast.LENGTH_LONG)
					.show();
			finish();
			return;
		}

		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		// Setup a tech list for all NfcA tags
		mTechLists = new String[][] { new String[] { NfcA.class.getName() } };
	}

	private void initCGToken() {
		// cgToken

		mSmartCardReaderService = SmartCardReaderService
				.getInstance(getApplicationContext());

		// Check if installed Version is right.
		if (!mSmartCardReaderService.isServiceAvaliable()) {
			Toast.makeText(this, "service not installed", Toast.LENGTH_LONG)
					.show();
			mSmartCardReaderService.startDownloadActivity();
			finish();
			return;
		}

		mSmartCardReaderService.bind(new cgTokenServiceCallback());

		// end of cgToken
	}

	// cgToken send and receive function
	private byte[] cgTokenSendAndReceive(byte[] apdu) {
		byte[] rapdu = null;
		try {
			rapdu = mSmartCardReader.transmit(apdu).getMessage();
		} catch (Exception re) {
			Log.e(logTag, "Remote exception " + re);
		}
		return rapdu;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_DEVICE: // Chose a bluetooth reader
			if (resultCode == RESULT_OK) {
				try {
					// Extract bluetooth reader from resources
					mSmartCardReader = mSmartCardReaderService
							.getReaderFromData(data);
					// smartCardReader.connect();
					return;
				} catch (RemoteException e) {
					Log.e(logTag, "Received remote exception.", e);
				} catch (ServiceNotBoundException e) {
					Log.e(logTag, "Service is not bound.", e);
				}
				Toast.makeText(this, "Error on connect.", Toast.LENGTH_LONG)
						.show();
				finish();
			} else {
				Log.i(logTag, "no device");
				finish();
			}
			break;
		case REQUEST_ENABLE_BT:
			if (resultCode != RESULT_OK) {
				Log.w(logTag, "Bluetooth is not enabled");
				finish();
			}
			break;
			
		// this is the result of a barcode scan (either a handshake (encrypted public identity of the other side) or an actual key)
		case REQUEST_SCAN:
			if (data==null) return;
			String type = data.getStringExtra("TYPE");
			String qrdata = data.getStringExtra("QR");
			processScan(type, qrdata, null);
			break;

		default:
			Log.i(logTag, "Received unknown requestCode");
		}
	}
	
	// process a scanned QR code or an incoming SMS (they both contain the exact same data but one comes from the camera, the other from the network)
	private void processScan(String type, String data, final String source) {

		Log.i(logTag, "PROCESS SCAN TYPE="+type+", DATA="+data+", SOURCE="+source);
		final String xdata = HexUtils.toHex(Base64.decode(data, Base64.NO_WRAP));
		
		// PI = public identity
		if ("PI".equals(type)) {
			runOnUiThread(new Runnable() {
				public void run() {
					confirmIdentityImport(xdata, source);
				}
			});
			
			
		}
		// KT = Key Transfer
		else if ("KT".equals(type)) {
			transferKeyData = xdata;
			Log.i(logTag, "TRANSFER KEY DATA " + transferKeyData);
			importTransferKeyData();
		} else {
			Log.i(logTag, "Received unknown type "+type);
		}
	}

	// cgToken connect method - called once on init
	private void cgTokenConnect() {
		try {
			if (mSmartCardReader != null) {
				Log.i(logTag, "connecting to reader");
				if (mSmartCardReader.connect()) {
					Log.i(logTag, "successfully connected");
				} else {
					Log.i(logTag, "could not connect to reader");
				}
			}
		} catch (Exception e) {
			Toast.makeText(this,
					"An error occurred while reading card:\n" + e.getMessage(),
					Toast.LENGTH_SHORT).show();
			Log.e(logTag, "Error occurred while reading card.", e);
		}
	}

	// cgToken disconnect - we only call this once on exit (if we disconnect after each session the user needs to press the Power button on the cgToken again to talk to it, this is not user friendly)
	private void cgTokenDisconnect() {
		try {
			if (mSmartCardReader != null) {
				mSmartCardReader.disconnect();
			}
		} catch (RemoteException e) {
			Log.w(logTag, "Error while closing reader", e);
		}
	}

	// general method for sending and receiving APDUs
	// depending on mode, it sends and receives the data over various channels
	private byte[] sendAPDU(byte[] apdu) {
		byte[] response = null;

		if (mode == MODE_INTERNAL) {
			try {
				response = cgTerminal.internalTransmit(apdu);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (mode == MODE_CGTOKEN) {
			response = cgTokenSendAndReceive(apdu);
		} else if (mode == MODE_NFC) {
			try {
				response = tag.transceive(apdu);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return response;
	}

	// initialize an internal microSD smartcard and its corresponding terminal
	private void initInternal() {
		cgTerminal = new CertgatePluginTerminal(OtherCoinActivity.this);
		try {
			cgTerminal.internalConnect();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	// select the OtherCoin applet on the card - just sends the corresponding APDU
	private void selectApplet() throws RuntimeException {
		byte[] apdu = HexUtils
				.toBytes("00A40400094F74686572436F696E00");
		Log.d(logTag, "SELECT APDU: " + HexUtils.toHex(apdu));
		byte[] rapdu = sendAPDU(apdu);
		String srapdu = HexUtils.toHex(rapdu);
		Log.d(logTag, "SELECT RESPONSE APDU: "
				+ srapdu);
		if (!"9000".equals(srapdu)) throw new RuntimeException("Cannot select applet");

	}

	// verify the OtherCoin smartcard PIN
	private boolean verifyPIN() {
		if (currentPIN==null) currentPIN="000000";
		byte pinlen = (byte)currentPIN.length();
		String pinAPDU = "00200000"+HexUtils.toHex(pinlen)+HexUtils.toHex(currentPIN.getBytes());

		byte[] apdu = HexUtils.toBytes(pinAPDU);
		Log.d(logTag, "VERIFY PIN APDU: " + HexUtils.toHex(apdu));
		byte[] rapdu = sendAPDU(apdu);
		String srapdu = HexUtils.toHex(rapdu);
		Log.d(logTag, "VERIFY PIN APDU: " + HexUtils.toHex(rapdu));
		if (srapdu.startsWith("63c")) {
			final byte tries = (byte)(rapdu[1]&0xF);
			runOnUiThread( 
				new Runnable() {
					public void run() {
						showPINError(tries);			
					}
				}
			);
			
			return false;
		} else return true;

	}
	
	
	// change the PIN to a new one - the APDU also needs the old PIN but we already know it (it's stored in the currentPIN field)
	private boolean changePIN(String newPIN) {
		byte pinlen = (byte)currentPIN.length();
		byte newpinlen = (byte)newPIN.length();
		String pinAPDU = "00240000"+HexUtils.toHex((byte)(pinlen+newpinlen))+HexUtils.toHex(currentPIN.getBytes())+HexUtils.toHex(newPIN.getBytes());

		byte[] apdu = HexUtils.toBytes(pinAPDU);
		Log.d(logTag, "VERIFY PIN APDU: " + HexUtils.toHex(apdu));
		byte[] rapdu = sendAPDU(apdu);
		String srapdu = HexUtils.toHex(rapdu);
		Log.d(logTag, "VERIFY PIN APDU: " + HexUtils.toHex(rapdu));
		if (srapdu.startsWith("63c")) {
			return false;
		} else {
			currentPIN = newPIN;
			return true;
		}

	}

	
	// request the encrypted transfer of a private key
	// the result is displayed as a QR code and you can then send it via SMS if the recipient is remote
	private void requestKeyTransfer(final PK theKey) {
		pd = ProgressDialog.show(this, "Please wait",
				"Requesting transfer of key for " + theKey.getAddress());

		new Thread(new Runnable() {
			public void run() {
				try {

					connectTerminal();

					selectApplet();
					if (!verifyPIN()) return;
					
					String tt = theKey.key + remotePartyPublicData;
					byte tl = (byte) (tt.length() / 2);

					byte[] apdu = HexUtils.toBytes("000A0000" + HexUtils.toHex(tl)
							+ tt);
					Log.d(logTag, "TRANSFER KEY APDU: " + HexUtils.toHex(apdu));
					byte[] rapdu = sendAPDU(apdu);
					String rsp = HexUtils.toHex(rapdu);
					Log.d(logTag, "TRANSFER KEY RESPONSE : " + rsp);
					if (rsp.endsWith("9000")) {
						String pkey = HexUtils.toHex(rapdu).substring(0,
								rsp.length() - 4);

						// prepend the Bitcoin public key being sent
						pkey = theKey.otherHalfString+theKey.key + pkey;
						showQR("KT", pkey, "Select the camera icon on the other phone and scan this code");
					}
					disconnectTerminal();

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					runOnUiThread(new Runnable() {
						public void run() {
							// repopulate the list after transferring the key (the one we've transferred will be gone)
							publicKeys.clear();
							arrayAdapter.notifyDataSetChanged();
							pd.dismiss();
							populateList();
						}
					});
				}
			}

		}).start();

	}

	
	
	// reveal a private key from the smartcard - erases it from secure storage and sends it to the smartphone
	// from this point the key is no longer usable in OtherCoin system
	// this app calls the BitcoinWallet to import the key, but you could just as well process it locally (if you are integrating OtherCoin into your own wallet)
	private void requestKeyReveal(final PK theKey) {
		pd = ProgressDialog.show(this, "Please wait",
				"Requesting private key reveal for " + theKey.getAddress());

		new Thread(new Runnable() {
			public void run() {
				try {

					connectTerminal();
					selectApplet();
					if (!verifyPIN()) return;
					String tt = theKey.key;
					byte tl = (byte) (tt.length() / 2);

					byte[] apdu = HexUtils.toBytes("00010000" + HexUtils.toHex(tl)
							+ tt);
					Log.d(logTag, "REVEAL KEY APDU: " + HexUtils.toHex(apdu));
					byte[] rapdu = sendAPDU(apdu);
					String rsp = HexUtils.toHex(rapdu);
					Log.d(logTag, "REVEAL KEY RESPONSE : " + rsp);
					if (rsp.endsWith("9000")) {
						String pkey = HexUtils.toHex(rapdu).substring(0,
								rsp.length() - 4);

						// pkey contains the hex encoded private key, we need to convert it to WIF
						byte[] bp1 = new byte[33];
						byte[] bp2 = new byte[33];
						byte[] sp1 = HexUtils.toBytes(pkey);
						byte[] sp2 = HexUtils.toBytes(get(tt));
						System.arraycopy(sp1, 0, bp1, 1, 32);
						System.arraycopy(sp2, 0, bp2, 1, 32);
						Log.i(logTag, "Our key is "+pkey+", other half is "+get(tt));
						BigInteger b1 = new BigInteger(bp1);
						BigInteger b2 = new BigInteger(bp2);
						byte[] nk = b1.add(b2).mod(Parameters.n).toByteArray();
						byte[] nnk = new byte[32];
						System.arraycopy(nk, nk.length-32, nnk, 0, 32);
						Log.i(logTag, "Resulting key is "+HexUtils.toHex(nk));
						
						
						InMemoryPrivateKey impk = new  InMemoryPrivateKey(nnk);
						
						Log.i(logTag, "Associated public key is "+HexUtils.toHex(impk.getPublicKey().getPublicKeyBytes()));
						String wifKey = impk.getBase58EncodedPrivateKey(NetworkParameters.productionNetwork);
						
						// now write this to the SD Card under /Download/bitcoin-othercoin-<address>.key
						File destination = new File (EXTERNAL_WALLET_BACKUP_DIR, "bitcoin-othercoin-"+theKey.getAddress()+".key");
						FileOutputStream fos = new FileOutputStream(destination);
						fos.write(wifKey.getBytes());
						fos.close();
						
						
						
					}
					disconnectTerminal();

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					runOnUiThread(new Runnable() {
						public void run() {
							publicKeys.clear();
							arrayAdapter.notifyDataSetChanged();
							pd.dismiss();
							populateList();
							
							Intent intent = new Intent();
							intent.setComponent(new ComponentName("de.schildbach.wallet", "de.schildbach.wallet.WalletActivity"));
							
							Toast.makeText(OtherCoinActivity.this,
									"BitcoinWallet will start, choose Back Up Keys -> Restore Private Keys to import this key",
									Toast.LENGTH_LONG).show();
							
							//Uri data = Uri.fromFile(destination);
							//String type = "x-bitcoin/private-keys";
							//intent.setDataAndType(data,  type);
							startActivity(intent);
							
						}
					});
				}
			}

		}).start();

	}


	// import the encrypted key received from the other party, then refresh the list to show it
	private void importTransferKeyData() {
		String otherHalf = transferKeyData.substring(0, 64);
		final PK theKey = new PK(transferKeyData.substring(64, 194));
		store(theKey.key, otherHalf);
		pd = ProgressDialog.show(this, "Please wait", "Importing key from remote party");

		new Thread(new Runnable() {
			public void run() {
				try {

					connectTerminal();

					selectApplet();
					if (!verifyPIN()) return;

					String tt = transferKeyData.substring(64);
					byte tl = (byte) (tt.length() / 2);

					byte[] apdu = HexUtils.toBytes("00060000" + HexUtils.toHex(tl)
							+ tt);
					Log.d(logTag, "IMPORT KEY APDU: " + HexUtils.toHex(apdu));
					byte[] rapdu = sendAPDU(apdu);
					String rsp = HexUtils.toHex(rapdu);
					Log.d(logTag, "IMPORT KEY RESPONSE : " + rsp);

					disconnectTerminal();

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					runOnUiThread(new Runnable() {
						public void run() {
							publicKeys.clear();
							arrayAdapter.notifyDataSetChanged();
							pd.dismiss();
							populateList();
						}
					});
				}
			}

		}).start();

	}

	
	// ask the smartcard to produce an encrypted handshake (contains the encrypted public identity = the signed public key of the card and a random nonce)
	private void getPublicIdentity() {
		pd = ProgressDialog.show(this, "Please wait",
				"Requesting public identity from OtherCoin card");

		new Thread(new Runnable() {
			public void run() {
				try {

					connectTerminal();
					selectApplet();
					if (!verifyPIN()) return;

					byte[] apdu = HexUtils.toBytes("0004000000");
					byte[] rapdu = sendAPDU(apdu);
					String rsp = HexUtils.toHex(rapdu);
					Log.d(logTag, "PUBLIC IDENTITY : " + rsp);
					if (rsp.endsWith("9000")) {
						String pkey = HexUtils.toHex(rapdu).substring(0,
								rsp.length() - 4);
						publicIdentity = pkey;
						
						showQR("PI", publicIdentity, "Select the camera icon on the other phone and scan this code");
					}
					disconnectTerminal();

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					runOnUiThread(new Runnable() {
						public void run() {
							arrayAdapter.notifyDataSetChanged();
							pd.dismiss();
						}
					});
				}
			}

		}).start();

	}

	// populate the list of Bitcoin addresses / public key on the main screen
	private void populateList() {
		populateList(false);
	}

	// repopulate the list _after_ adding a new key (newKey = true)
	private void populateList(final boolean newKey) {
		pd = ProgressDialog.show(this, "Please wait", "Updating list");
		publicKeys.clear();
		arrayAdapter.notifyDataSetChanged();
		new Thread(new Runnable() {
			public void run() {
				try {

					connectTerminal();
					selectApplet();
					if (!verifyPIN()) return;

					byte[] apdu, rapdu;
					if (newKey) {
						apdu = HexUtils.toBytes("0000000000");
						Log.d(logTag, "NEW KEY APDU: " + HexUtils.toHex(apdu));
						rapdu = sendAPDU(apdu);
						Log.d(logTag, "NEW KEY RESPONSE APDU: "
								+ HexUtils.toHex(rapdu));
						
						
						String newKey = HexUtils.toHex(rapdu);
						newKey = newKey.substring(0,
								newKey.length() - 4);
						
						// for each key we ask the card to create, we also generate another half that is stored locally, on the smartphone
						// this prevents the smartcard from ever knowing the actual private key that is used or the Bitcoin addresses that the user has
						InMemoryPrivateKey otherHalf = new InMemoryPrivateKey(random);
						String hexOtherHalf = HexUtils.toHex(otherHalf.getPrivateKeyBytes());
						store(newKey, hexOtherHalf);
						
					}

					for (int i = 0;; i++) {
						apdu = HexUtils.toBytes("0003000"
								+ (i == 0 ? "1" : "0") + "00");
						Log.d(logTag, "GET KEY " + i + " APDU: "
								+ HexUtils.toHex(apdu));
						rapdu = sendAPDU(apdu);
						String rsp = HexUtils.toHex(rapdu);
						Log.d(logTag, "GET KEY " + i + " RESPONSE APDU: " + rsp);
						if (!rsp.endsWith("9000"))
							break;
						String pkey = HexUtils.toHex(rapdu).substring(0,
								rsp.length() - 4);
						
						PK item = new PK(pkey);
						String otherHalfString = get(pkey);
						if (otherHalfString!="") {
							item.otherHalfString = otherHalfString;
						}
							
						
						
						
						// balance is currently fetched from blockchain.info using their API
						updateBalance(item);

						publicKeys.add(item);
					}

					disconnectTerminal();

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					
					showSelectError();
					
				} finally {
					runOnUiThread(new Runnable() {
						public void run() {
							arrayAdapter.notifyDataSetChanged();
							pd.dismiss();
						}
					});
				}
			}

		}).start();

	}
	
	
	// when the app is closed, turn off SMS receiving and close the Bluetooth connection to the cgToken (if that mode is used)
	// all other modes open and close connections as needed, but for the cgToken we keep it open to save the user from pressing the power button every time he needs something
	public void onDestroy() {
		super.onDestroy();
		unregisterSMSReceiver();
		if (mode == MODE_CGTOKEN) {
			cgTokenDisconnect();
		}
	}

	// disconnect from the terminal - how we do this depends on the mode
	public void disconnectTerminal() {
		if (mode == MODE_INTERNAL) {
			try {
				cgTerminal.internalDisconnect();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (mode == MODE_CGTOKEN) {
			// do not disconnect yet, do this in onDestroy
			//cgTokenDisconnect();
		} else if (mode == MODE_NFC) {
			try {
				tag.close();
				this.runOnUiThread(new Runnable() {
					public void run() {
						Toast.makeText(OtherCoinActivity.this,
								"You may now remove the NFC OtherCoin",
								Toast.LENGTH_LONG).show();

					}
				});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void connectTerminal() {
		if (mode == MODE_INTERNAL) {
			try {
				cgTerminal.internalConnect();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (mode == MODE_CGTOKEN) {
			cgTokenConnect();
		} else if (mode == MODE_NFC) {

			this.runOnUiThread(new Runnable() {
				public void run() {
					Toast.makeText(OtherCoinActivity.this,
							"Please touch the smartphone with the OtherCoin",
							Toast.LENGTH_LONG).show();

				}
			});
			for (;;) {
				try {
					Log.i(logTag, "Attempting to connect to tag");
					Thread.sleep(1000);
					tag.connect();

					break;
				} catch (Exception te) {
					// te.printStackTrace();
				}
			}
			Log.i(logTag, "Done, I've managed to connect to tag");
			tag.setTimeout(10000);

		}
	}

	private void updateBalance(PK item) {
		item.balance = Integer
				.parseInt(DownloadText("https://blockchain.info/q/addressbalance/"
						+ item.getAddress() + "?confirmations=0"));
	}



	@Override
	public void onResume() {
		super.onResume();
		if (mode == MODE_NFC)
			mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null,
					mTechLists);

	}

	@Override
	public void onNewIntent(Intent intent) {
		Log.d(logTag, "New intent: " + intent.getAction());
		
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
			tag = IsoDep.get((Tag) intent
					.getParcelableExtra(NfcAdapter.EXTRA_TAG));
		}
		
	}

	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items

		switch (item.getItemId()) {
		case R.id.action_request:
			getPublicIdentity();
			return true;
		case R.id.action_scan:
			doScan();
			return true;
		case R.id.refresh:
			populateList();
			return true;
		case R.id.newpin:
			createPINChangeDialog().show();
			return true;
		case R.id.new_key:
			populateList(true);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		PK item = (PK) getListAdapter().getItem(position);
		showQR("BA", "bitcoin:"+item.getAddress(), "Show the code to another device to receive a payment");
		
		
	}

	
	// download text as String from an URL - it uses certificate pinning to prevent man in the middle attacks
	private String DownloadText(String URL) {
		int BUFFER_SIZE = 2000;
		InputStream in = null;
		try {
			in = OpenHttpConnection(URL);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return "";
		}

		InputStreamReader isr = new InputStreamReader(in);
		int charRead;
		String str = "";
		char[] inputBuffer = new char[BUFFER_SIZE];
		try {
			while ((charRead = isr.read(inputBuffer)) > 0) {
				// ---convert the chars to a String---
				String readString = String
						.copyValueOf(inputBuffer, 0, charRead);
				str += readString;
				inputBuffer = new char[BUFFER_SIZE];
			}
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
		return str;
	}

	private InputStream OpenHttpConnection(String urlString) throws Exception {
		defineTrust();

		InputStream in = null;
		int response = -1;

		URL url = new URL(urlString);
		URLConnection conn = url.openConnection();

		try {
			HttpsURLConnection httpConn = (HttpsURLConnection) conn;
			// httpConn.setAllowUserInteraction(false);
			// httpConn.setInstanceFollowRedirects(true);
			// httpConn.setRequestMethod("GET");
			// httpConn.connect();

			response = httpConn.getResponseCode();
			if (response == HttpURLConnection.HTTP_OK) {
				in = httpConn.getInputStream();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new IOException("Error connecting");
		}
		return in;
	}

	private void defineTrust() {
		try {

			TrustManager tm[] = { new PubKeyManager() };
			HttpsURLConnection
					.setDefaultHostnameVerifier(new HostnameVerifier() {
						public boolean verify(String hostname,
								SSLSession session) {
							return true;
						}
					});
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, tm, null);
			HttpsURLConnection.setDefaultSSLSocketFactory(context
					.getSocketFactory());
		} catch (Exception e) { // should never happen
			e.printStackTrace();
		}
	}

	
	// create the PIN prompt
	// this is also the place where the user selects the connection mode (microSD, Bluetooth or NFC)
	private Dialog createPINDialog() {
		
		boolean hasNFC = getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
		boolean hasBluetooth = getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
		
		final ArrayList<String> om = new ArrayList<String>();
		if (KNOX) {
			// for KNOX mode there's only one connection that (supposedly) works = Bluetooth
			om.add("KNOX (Bluetooth)");
		} else {
			om.add("microSD");
			if (hasBluetooth) om.add("Bluetooth");
			if (hasNFC) om.add("NFC");
		}
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		// Get the layout inflater
		LayoutInflater inflater = getLayoutInflater();

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		builder.setTitle("Select connectivity and enter your PIN")
		
				.setView(inflater.inflate(R.layout.pin_entry, null))
				// Add action buttons
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						if (KNOX) {
							OtherCoinActivity.mode = OtherCoinActivity.MODE_CGTOKEN;
						} else {
							int theMode = ((AlertDialog) dialog)
								.getListView().getCheckedItemPosition();
							String textMode = om.get(theMode);
							if ("microSD".equals(textMode)) OtherCoinActivity.mode = OtherCoinActivity.MODE_INTERNAL;
							if ("Bluetooth".equals(textMode)) OtherCoinActivity.mode = OtherCoinActivity.MODE_CGTOKEN;
							if ("KNOX (Bluetooth)".equals(textMode)) OtherCoinActivity.mode = OtherCoinActivity.MODE_CGTOKEN;
							
							if ("NFC".equals(textMode)) OtherCoinActivity.mode = OtherCoinActivity.MODE_NFC;
						}
						Log.i(logTag, "MODE IS " + OtherCoinActivity.mode);
						
						EditText et = (EditText) ((AlertDialog)dialog).findViewById(R.id.password);
						Log.i(logTag, "PIN IS "+et.getText());
						currentPIN = et.getText().toString();
						initTerminal();

						if (OtherCoinActivity.mode != MODE_CGTOKEN)
							populateList();

					}
				})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
								finish();
							}
						}).setCancelable(false);
		
		
			builder.setSingleChoiceItems((String[])om.toArray(new String[0]), 0, null);
		
		return builder.create();
	}

private Dialog createPINChangeDialog() {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		// Get the layout inflater
		LayoutInflater inflater = getLayoutInflater();

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		builder.setTitle("Change your PIN")
		
				.setView(inflater.inflate(R.layout.pin_change, null))
				// Add action buttons
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						
						EditText np = (EditText) ((AlertDialog)dialog).findViewById(R.id.newpassword);
						EditText cp = (EditText) ((AlertDialog)dialog).findViewById(R.id.confirmpassword);
						String newPIN = np.getText().toString();
						String confirmPIN = cp.getText().toString();
						if (newPIN.equals(confirmPIN)) {
							changePIN(newPIN);
						} else {
							showPINChangeError();
							dialog.cancel();
						}
						
					}
				})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						}).setCancelable(false);
		
		
		return builder.create();
	}

	

private void confirmIdentityImport(final String identity, final String source) {
	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	
	// Inflate and set the layout for the dialog
	// Pass null as the parent view because its going in the dialog layout
	builder.setTitle("New handshake received")
	
			.setMessage("Received new handshake"+((source==null)?"":" from "+source)+", do you want to send BTC to this user?")
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					remotePartyPublicData = identity;
					remotePhoneNumber = source;
					Log.i(logTag, "REMOTE PUBLIC DATA " + remotePartyPublicData);
				}
			})
			.setNegativeButton("No", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			}).create().show();
			
}

	
	private void showPINError(final byte tries) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		builder.setTitle("Incorrect PIN")
		
				.setMessage((tries==0)?"Entered wrong pin too many times, card is now locked. The application will exit.":"Wrong PIN, "+tries+" tries remaining. Please try again.")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						if (tries!=0) createPINDialog().show();
						else finish();
					}
				});
				builder.create().show();
	}
	
	private void showPINChangeError() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		builder.setTitle("Incorrect PIN").setMessage("The two PINs do not match, please try again!")
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
						createPINChangeDialog().show();
					}
				}).create().show();
		
	}
	
	private void showSelectError() {
		runOnUiThread(new Runnable() {
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(OtherCoinActivity.this);
				
				// Inflate and set the layout for the dialog
				// Pass null as the parent view because its going in the dialog layout
				builder.setTitle("OtherCoin communication error")
				
						.setMessage("Cannot communicate with the card, please try again.")
						.setPositiveButton("OK", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								createPINDialog().show();
								}
						});
						builder.create().show();

			}
		});
	}


	
	public void showQR(final String type, final String qrtext, final String comment) {	
		final Intent i = new Intent(this, QRDisplayActivity.class);
		i.putExtra("TEXT", qrtext);
		i.putExtra("TYPE", type);
		if (remotePhoneNumber!=null) i.putExtra("PHONE", remotePhoneNumber);
		runOnUiThread(new Runnable() {
			public void run() {
				Toast.makeText(OtherCoinActivity.this, comment, Toast.LENGTH_LONG).show();
				startActivity(i);
			}
		});
		
		

	}

	public void doScan() {
		Intent i = new Intent(this, QRReaderActivity.class);
		startActivityForResult(i, REQUEST_SCAN);
	}
	
	// store a persistent value
	public void store(String key, String value) {
		SharedPreferences sp = getSharedPreferences("OTHERCOIN", 0);
		SharedPreferences.Editor ed = sp.edit();
		ed.putString(key, value);
		ed.commit();
	}

	// remove a persistent value
	public void clear(String key) {
		SharedPreferences sp = getSharedPreferences("OTHERCOIN", 0);
		SharedPreferences.Editor ed = sp.edit();
		ed.remove(key);
		ed.commit();
	}

	
	// fetch a value from persistent storage
	public String get(String key) {
		SharedPreferences sp = getSharedPreferences("OTHERCOIN", 0);
		return sp.getString(key, "");
	}
	
	// stop receiving SMS messages
	private void unregisterSMSReceiver() {
		if (mReceiver!=null) getApplicationContext().unregisterReceiver(mReceiver);
	}
	
	// start receiving SMS messages
	private void registerSMSReceiver() {
		mReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {

				Bundle bundle = intent.getExtras();
				SmsMessage[] msgs = null;
				String str = "";
				String sender = null;
				if (bundle != null) {
					// ---retrieve the SMS message received---
					Object[] pdus = (Object[]) bundle.get("pdus");
					msgs = new SmsMessage[pdus.length];
					for (int i = 0; i < msgs.length; i++) {
						msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
						String mbody = msgs[i].getMessageBody().toString();
						str += mbody;
						if (sender==null) sender = msgs[i].getOriginatingAddress();
					}
						if (str.startsWith("#OC")) {
							abortBroadcast();
							
							String[] tokens = str.split(" ");
							String command = tokens[1];


							
								String content = tokens[2];
								
								Log.i(logTag, "Received SMS command "+command+" with content "+content);
								processScan(command, content, sender);
								return;
							
						}
						

				}

			}

		};
		
		// set up the IntentFilter to receive text messages
		IntentFilter ifilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
		
		// set the priority high so that we are called _before_ the default app. If the message doesn't start with #OC we let it fall through anyway
		ifilter.setPriority(100);
		getApplicationContext().registerReceiver(mReceiver, ifilter);
	}
	

}
