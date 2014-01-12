package com.cayennegraphics.othercoin;

import android.util.Log;

import com.bccapi.bitlib.crypto.InMemoryPrivateKey;
import com.bccapi.bitlib.crypto.PublicKey;
import com.bccapi.bitlib.crypto.ec.Parameters;
import com.bccapi.bitlib.crypto.ec.Point;
import com.bccapi.bitlib.util.Base58;
import com.bccapi.bitlib.util.HashUtils;

public class PK {
	
	public String key;
	public String otherHalfString;
	public int balance = -1;
	
	public PK(String key) {
		this.key = key;
	}
	
	public String getBalance() {
		return "Balance: "+((double)balance/100000000)+" BTC";
	}
	
	public String getAddress() {
		
		String xkey = key;
		
		if (otherHalfString!=null) {
			InMemoryPrivateKey otherHalf = new InMemoryPrivateKey(HexUtils.toBytes(otherHalfString));
			PublicKey publicOtherHalf = otherHalf.getPublicKey();
			byte[] publicBytes = publicOtherHalf.getPublicKeyBytes();
			Log.i(OtherCoinActivity.logTag, "Public key "+key+" has other half "+HexUtils.toHex(publicBytes));
			Point p = Parameters.curve.decodePoint(publicBytes);
			Point q = Parameters.curve.decodePoint(HexUtils.toBytes(key));
			Point newp = p.add(q);
			xkey = HexUtils.toHex(newp.getEncoded());
			Log.i(OtherCoinActivity.logTag, "XXXXXXXXXXXXXXXXXXX "+xkey+" XXXXXXXXXXXXXXXXXXXXX");
			
		}
		
		
		
		
		
		byte[] addr = HashUtils.addressHash(HexUtils.toBytes(xkey));
		byte[] ab = new byte[25];
		System.arraycopy(addr, 0, ab, 1, 20);
		byte[] doubleHash = HashUtils.doubleSha256(ab, 0, 21);
		System.arraycopy(doubleHash, 0, ab, 21, 4);
		String theAddress = Base58.encode(ab);
		return theAddress;
	}
	public String toString() {
		byte[] addr = HashUtils.addressHash(HexUtils.toBytes(key));
		byte[] ab = new byte[25];
		System.arraycopy(addr, 0, ab, 1, 20);
		byte[] doubleHash = HashUtils.doubleSha256(ab, 0, 21);
		System.arraycopy(doubleHash, 0, ab, 21, 4);
		String theAddress = Base58.encode(ab);
		if (balance>=0) theAddress+="/"+((double)balance/100000000)+" BTC";
		return theAddress;
	
	}
	
	

}
