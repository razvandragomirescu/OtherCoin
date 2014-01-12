package com.bccapi.bitlib.crypto;

public interface BitcoinSigner {
   public byte[] makeStandardBitcoinSignature(byte[] transactionSigningHash);
}
