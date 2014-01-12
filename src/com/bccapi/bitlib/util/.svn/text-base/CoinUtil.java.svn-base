/**
 * Copyright 2011 bccapi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bccapi.bitlib.util;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Utility for turning an amount of satoshis into a user friendly string. 1
 * satoshi == 0.000000001 BTC
 */
public class CoinUtil {

   private static final BigDecimal ONE_BTC_IN_SATOSHIS = new BigDecimal(100000000);

   /**
    * Number of satoshis in a Bitcoin.
    */
   public static final BigInteger BTC = new BigInteger("100000000", 10);

   /**
    * Get the given value in satoshis as a string on the form "10.12345".
    * <p>
    * This method only returns necessary decimal points to tell the exact value.
    * If you wish to have all 8 digits use
    * {@link CoinUtil#fullValueString(long)}
    * 
    * @param value
    *           The number of satoshis
    * @return The given value in satoshis as a string on the form "10.12345".
    */
   public static String valueString(long value) {
      BigDecimal d = BigDecimal.valueOf(value);
      d = d.divide(ONE_BTC_IN_SATOSHIS);
      return d.toPlainString();
   }

   /**
    * Get the given value in satoshis as a string on the form "10.12345000".
    * <p>
    * This method always returns a string with 8 decimal points. If you only
    * wish to have the necessary digits use {@link CoinUtil#valueString(long)}
    * 
    * @param value
    *           The number of satoshis
    * @return The given value in satoshis as a string on the form "10.12345000".
    */
   public static String fullValueString(long value) {
      BigDecimal d = BigDecimal.valueOf(value);
      d = d.movePointLeft(8);
      return d.toPlainString();
   }

}
