package com.bccapi.bitlib.model;

import com.bccapi.bitlib.util.ByteReader;
import com.bccapi.bitlib.util.ByteReader.InsufficientBytesException;

public class ScriptInputStandard extends ScriptInput {

   private byte[] _signature;
   private byte[] _publicKeyBytes;

   public ScriptInputStandard(byte[] signature, byte[] publicKeyBytes) {
      super(scriptEncodeChunks(new byte[][] { signature, publicKeyBytes }));
      _signature = signature;
      _publicKeyBytes = publicKeyBytes;
   }

   protected ScriptInputStandard(byte[][] chunks, byte[] scriptBytes) {
      super(scriptBytes);
      _signature = chunks[0];
      _publicKeyBytes = chunks[1];
   }

   protected static boolean isScriptInputStandard(byte[][] chunks) throws ScriptParsingException {
      try {
         if (chunks.length != 2) {
            return false;
         }

         // Verify that first chunk contains two DER encoded BigIntegers
         ByteReader reader = new ByteReader(chunks[0]);

         // Read tag, must be 0x30
         if ((((int) reader.get()) & 0xFF) != 0x30) {
            return false;
         }

         // Read total length as a byte, standard inputs never get longer than
         // this
         int length = ((int) reader.get()) & 0xFF;

         // Read first type, must be 0x02
         if ((((int) reader.get()) & 0xFF) != 0x02) {
            return false;
         }

         // Read first length
         int length1 = ((int) reader.get()) & 0xFF;
         reader.skip(length1);

         // Read second type, must be 0x02
         if ((((int) reader.get()) & 0xFF) != 0x02) {
            return false;
         }

         // Read second length
         int length2 = ((int) reader.get()) & 0xFF;
         reader.skip(length2);

         // Validate that the lengths add up to the total
         if (2 + length1 + 2 + length2 != length) {
            return false;
         }

         // Make sure that we have a hash type at the end
         if (reader.available() != 1) {
            return false;
         }

         // XXX we may want to add more checks to verify public key length in
         // second chunk
         return true;
      } catch (InsufficientBytesException e) {
         throw new ScriptParsingException("Unable to parse " + ScriptInputStandard.class.getSimpleName());
      }
   }

   /**
    * Get the signature of this input.
    */
   public byte[] getSignature() {
      return _signature;
   }

   /**
    * The hash type.
    * <p>
    * Look for SIGHASH_ALL, SIGHASH_NONE, SIGHASH_SINGLE, SIGHASH_ANYONECANPAY
    * in the reference client
    */
   public int getHashType() {
      // hash type is the last byte of the signature
      return ((int) (_signature[_signature.length - 1])) & 0xFF;
   }

   /**
    * Get the public key bytes of this input.
    * 
    * @return The public key bytes of this input.
    */
   public byte[] getPublicKeyBytes() {
      return _publicKeyBytes;
   }

}
