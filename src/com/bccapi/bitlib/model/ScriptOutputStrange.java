package com.bccapi.bitlib.model;

/**
 * This class is used for output scripts that we do not understand
 */
public class ScriptOutputStrange extends ScriptOutput {

   protected ScriptOutputStrange(byte[][] chunks, byte[] scriptBytes) {
      super(scriptBytes);
   }

   @Override
   public Address getAddress(NetworkParameters network) {
      // We cannot determine the address from scripts we do not understand
      return null;
   }

}
