/**
 * Parts of this code was extracted from the BitcoinJ library from
 * http://code.google.com/p/bitcoinj/.
 */
package com.bccapi.bitlib.util;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * This class contains tools for encoding and decoding the special base58 format
 * used by Bitcoin.
 */
public class Base58 {
   private static final int TYPICAL_BITCOIN_ADDRESS_SIZE = 34;
   private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
   private static final BigInteger BASE58 = BigInteger.valueOf(58);

   /**
    * Encode an array of bytes to a string on Bitcoin's base58 format
    */
   public static String encode(byte[] data) {
      BigInteger b = new BigInteger(1, data);
      StringBuilder sb = new StringBuilder(TYPICAL_BITCOIN_ADDRESS_SIZE);
      while (b.compareTo(BigInteger.ZERO) > 0) {
         BigInteger[] result = b.divideAndRemainder(BASE58);
         b = result[0];
         sb.append(ALPHABET[result[1].intValue()]);
      }
      // Convert any leading zeros
      for (byte anInput : data) {
         if (anInput == 0) {
            sb.append(ALPHABET[0]);
         } else {
            break;
         }
      }
      return sb.reverse().toString();
   }

   /**
    * Decode a Bitcoin base58 encoded string to an array of bytes.
    */
   public static byte[] decode(String encoded) {
      if (encoded.length() == 0) {
         return null;
      }
      BigInteger decoded = toBigInteger(encoded);
      if (decoded == null) {
         return null;
      }
      byte[] bytes = decoded.toByteArray();
      boolean stripSignByte = bytes.length > 1 && bytes[0] == 0 && bytes[1] < 0;
      int leadingZeros = 0;
      for (int i = 0; i < encoded.length() && encoded.charAt(i) == ALPHABET[0]; i++) {
         leadingZeros++;
      }
      byte[] tmp = new byte[bytes.length - (stripSignByte ? 1 : 0) + leadingZeros];
      System.arraycopy(bytes, stripSignByte ? 1 : 0, tmp, leadingZeros, tmp.length - leadingZeros);
      return tmp;
   }

   private static BigInteger toBigInteger(String input) {
      BigInteger bi = BigInteger.valueOf(0);
      for (int i = input.length() - 1; i >= 0; i--) {
         int alphaIndex = indexOf(input.charAt(i));
         if (alphaIndex == -1) {
            return null;
         }
         bi = bi.add(BigInteger.valueOf(alphaIndex).multiply(BASE58.pow(input.length() - 1 - i)));
      }
      return bi;
   }

   private static int indexOf(char c) {
      for (int i = 0; i < ALPHABET.length; i++) {
         if (ALPHABET[i] == c) {
            return i;
         }
      }
      return -1;
   }

   /**
    * Decode a Bitcoin base58 encoded string to an array of bytes and verify the
    * 4-byte checksum at the end. The checksum is removed from the result.
    * 
    * @param encoded
    *           The base58 encoded string to decode and verify.
    * @return The decoded and verified input as an array of bytes, or null if
    *         decoding failed or the checksum was incorrect.
    */
   public static byte[] decodeChecked(String encoded) {
      byte[] tmp = decode(encoded);
      if (tmp == null) {
         return null;
      }
      if (tmp.length < 4) {
         return null;
      }
      byte[] checksum = new byte[4];
      System.arraycopy(tmp, tmp.length - 4, checksum, 0, 4);
      byte[] bytes = new byte[tmp.length - 4];
      System.arraycopy(tmp, 0, bytes, 0, tmp.length - 4);
      tmp = HashUtils.doubleSha256(bytes);
      byte[] hash = new byte[4];
      System.arraycopy(tmp, 0, hash, 0, 4);
      if (!Arrays.equals(hash, checksum))
         return null;
      return bytes;
   }
}
