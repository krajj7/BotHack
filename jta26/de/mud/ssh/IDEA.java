/*
 * This file is part of "JTA - Telnet/SSH for the JAVA(tm) platform".
 *
 * (c) Matthias L. Jugel, Marcus Meißner 1996-2005. All Rights Reserved.
 *
 * Please visit http://javatelnet.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 *
 */

/*
 * Information about the base of this code:
 * Xuejia Lai: On the Design and Security of Block Ciphers, ETH
 * Series in Information Processing, vol. 1, Hartung-Gorre
 * Verlag, Konstanz, Switzerland, 1992.  Another source was Bruce
 * Schneier: Applied Cryptography, John Wiley & Sons, 1994.
 *
 * The IDEA mathematical formula may be covered by one or more of the
 * following patents: PCT/CH91/00117, EP 0 482 154 B1, US Pat. 5,214,703.
 */

package de.mud.ssh;

/**
 * @author Marcus Meissner
 * @version $Id: IDEA.java 499 2005-09-29 08:24:54Z leo $
 */
public final class IDEA extends Cipher {

  protected int[] key_schedule = new int[52];
  protected int IV0 = 0;
  protected int IV1 = 0;

  public synchronized void encrypt(byte[] src, int srcOff, byte[] dest, int destOff, int len) {
    int[] out = new int[2];
    int iv0 = IV0;
    int iv1 = IV1;
    int end = srcOff + len;

    for (int si = srcOff, di = destOff; si < end; si += 8, di += 8) {
      encrypt(iv0, iv1, out);
      iv0 = out[0];
      iv1 = out[1];
      iv0 ^= ((src[si + 3] & 0xff) | ((src[si + 2] & 0xff) << 8) |
        ((src[si + 1] & 0xff) << 16) | ((src[si] & 0xff) << 24));
      iv1 ^= ((src[si + 7] & 0xff) | ((src[si + 6] & 0xff) << 8) |
        ((src[si + 5] & 0xff) << 16) | ((src[si + 4] & 0xff) << 24));

      if (di + 8 <= end) {
        dest[di + 3] = (byte) (iv0 & 0xff);
        dest[di + 2] = (byte) ((iv0 >>> 8) & 0xff);
        dest[di + 1] = (byte) ((iv0 >>> 16) & 0xff);
        dest[di] = (byte) ((iv0 >>> 24) & 0xff);
        dest[di + 7] = (byte) (iv1 & 0xff);
        dest[di + 6] = (byte) ((iv1 >>> 8) & 0xff);
        dest[di + 5] = (byte) ((iv1 >>> 16) & 0xff);
        dest[di + 4] = (byte) ((iv1 >>> 24) & 0xff);
      } else {
        switch (end - di) {
          case 7:
            dest[di + 6] = (byte) ((iv1 >>> 8) & 0xff);
          case 6:
            dest[di + 5] = (byte) ((iv1 >>> 16) & 0xff);
          case 5:
            dest[di + 4] = (byte) ((iv1 >>> 24) & 0xff);
          case 4:
            dest[di + 3] = (byte) (iv0 & 0xff);
          case 3:
            dest[di + 2] = (byte) ((iv0 >>> 8) & 0xff);
          case 2:
            dest[di + 1] = (byte) ((iv0 >>> 16) & 0xff);
          case 1:
            dest[di] = (byte) ((iv0 >>> 24) & 0xff);
        }
      }
    }
    IV0 = iv0;
    IV1 = iv1;
  }

  public synchronized void decrypt(byte[] src, int srcOff, byte[] dest, int destOff, int len) {
    int[] out = new int[2];
    int iv0 = IV0;
    int iv1 = IV1;
    int plain0, plain1;
    int end = srcOff + len;

    for (int si = srcOff, di = destOff; si < end; si += 8, di += 8) {
      decrypt(iv0, iv1, out);
      iv0 = ((src[si + 3] & 0xff) | ((src[si + 2] & 0xff) << 8) |
        ((src[si + 1] & 0xff) << 16) | ((src[si] & 0xff) << 24));
      iv1 = ((src[si + 7] & 0xff) | ((src[si + 6] & 0xff) << 8) |
        ((src[si + 5] & 0xff) << 16) | ((src[si + 4] & 0xff) << 24));
      plain0 = out[0] ^ iv0;
      plain1 = out[1] ^ iv1;

      if (di + 8 <= end) {
        dest[di + 3] = (byte) (plain0 & 0xff);
        dest[di + 2] = (byte) ((plain0 >>> 8) & 0xff);
        dest[di + 1] = (byte) ((plain0 >>> 16) & 0xff);
        dest[di] = (byte) ((plain0 >>> 24) & 0xff);
        dest[di + 7] = (byte) (plain1 & 0xff);
        dest[di + 6] = (byte) ((plain1 >>> 8) & 0xff);
        dest[di + 5] = (byte) ((plain1 >>> 16) & 0xff);
        dest[di + 4] = (byte) ((plain1 >>> 24) & 0xff);
      } else {
        switch (end - di) {
          case 7:
            dest[di + 6] = (byte) ((plain1 >>> 8) & 0xff);
          case 6:
            dest[di + 5] = (byte) ((plain1 >>> 16) & 0xff);
          case 5:
            dest[di + 4] = (byte) ((plain1 >>> 24) & 0xff);
          case 4:
            dest[di + 3] = (byte) (plain0 & 0xff);
          case 3:
            dest[di + 2] = (byte) ((plain0 >>> 8) & 0xff);
          case 2:
            dest[di + 1] = (byte) ((plain0 >>> 16) & 0xff);
          case 1:
            dest[di] = (byte) ((plain0 >>> 24) & 0xff);
        }
      }
    }
    IV0 = iv0;
    IV1 = iv1;
  }

  public void setKey(byte[] key) {
    int i, ki = 0, j = 0;
    for (i = 0; i < 8; i++)
      key_schedule[i] = ((key[2 * i] & 0xff) << 8) | (key[(2 * i) + 1] & 0xff);

    for (i = 8, j = 0; i < 52; i++) {
      j++;
      key_schedule[ki + j + 7] = ((key_schedule[ki + (j & 7)] << 9) |
        (key_schedule[ki + ((j + 1) & 7)] >>> 7)) & 0xffff;
      ki += j & 8;
      j &= 7;
    }
  }

  public final void encrypt(int l, int r, int[] out) {
    int t1 = 0, t2 = 0, x1, x2, x3, x4, ki = 0;

    x1 = (l >>> 16);
    x2 = (l & 0xffff);
    x3 = (r >>> 16);
    x4 = (r & 0xffff);

    for (int round = 0; round < 8; round++) {
      x1 = mulop(x1 & 0xffff, key_schedule[ki++]);
      x2 = (x2 + key_schedule[ki++]);
      x3 = (x3 + key_schedule[ki++]);
      x4 = mulop(x4 & 0xffff, key_schedule[ki++]);

      t1 = (x1 ^ x3);
      t2 = (x2 ^ x4);
      t1 = mulop(t1 & 0xffff, key_schedule[ki++]);
      t2 = (t1 + t2);
      t2 = mulop(t2 & 0xffff, key_schedule[ki++]);
      t1 = (t1 + t2);

      x1 = (x1 ^ t2);
      x4 = (x4 ^ t1);
      t1 = (t1 ^ x2);
      x2 = (t2 ^ x3);
      x3 = t1;
    }

    t2 = x2;
    x1 = mulop(x1 & 0xffff, key_schedule[ki++]);
    x2 = (t1 + key_schedule[ki++]);
    x3 = ((t2 + key_schedule[ki++]) & 0xffff);
    x4 = mulop(x4 & 0xffff, key_schedule[ki]);


    out[0] = (x1 << 16) | (x2 & 0xffff);
    out[1] = (x3 << 16) | (x4 & 0xffff);
  }

  public final void decrypt(int l, int r, int[] out) {
    encrypt(l, r, out);
  }

  public static final int mulop(int a, int b) {
    int ab = a * b;
    if (ab != 0) {
      int lo = ab & 0xffff;
      int hi = (ab >>> 16) & 0xffff;
      return ((lo - hi) + ((lo < hi) ? 1 : 0));
    }
    if (a == 0)
      return (1 - b);
    return (1 - a);
  }

  /* !!! REMOVE DEBUG !!!

  public static void main(String[] argv) {
    byte[] key = {
      (byte) 0xd3, (byte) 0x96, (byte) 0xcf, (byte) 0x07, (byte) 0xfa, (byte) 0xa2, (byte) 0x64,
      (byte) 0xfe, (byte) 0xf3, (byte) 0xa2, (byte) 0x06, (byte) 0x07, (byte) 0x1a, (byte) 0xb6,
      (byte) 0x13, (byte) 0xf6
    };

    byte[] txt = {
      (byte) 0x2e, (byte) 0xbe, (byte) 0xc5, (byte) 0xac, (byte) 0x02, (byte) 0xa1, (byte) 0xd5,
      (byte) 0x7f, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x1f, (byte) 0x43,
      (byte) 0x6f, (byte) 0x72, (byte) 0x72, (byte) 0x75, (byte) 0x70, (byte) 0x74, (byte) 0x65,
      (byte) 0x64, (byte) 0x20, (byte) 0x63, (byte) 0x68, (byte) 0x65, (byte) 0x63, (byte) 0x6b,
      (byte) 0x20, (byte) 0x62, (byte) 0x79, (byte) 0x74, (byte) 0x65, (byte) 0x73, (byte) 0x20,
      (byte) 0x6f, (byte) 0x6e, (byte) 0x20, (byte) 0x69, (byte) 0x6e, (byte) 0x70, (byte) 0x75,
      (byte) 0x74, (byte) 0x2e, (byte) 0x91, (byte) 0x9a, (byte) 0x57, (byte) 0xdd
    };

    byte[] enc;
    byte[] dec;

    System.out.println("key: " + printHex(key));
    System.out.println("txt: " + printHex(txt));

    IDEA cipher = new IDEA();
    cipher.setKey(key);

    for (int i = 0; i < 52; i++) {
      if ((i & 0x7) == 0)
        System.out.println("");
      System.out.print(" " + cipher.key_schedule[i]);
    }

    enc = cipher.encrypt(txt);
    System.out.println("enc: " + printHex(enc));

    cipher = new IDEA();
    cipher.setKey(key);
    dec = cipher.decrypt(enc);

    System.out.println("dec: " + printHex(dec));
  }

  static String printHex(byte[] buf) {
    byte[] out = new byte[buf.length + 1];
    out[0] = 0;
    System.arraycopy(buf, 0, out, 1, buf.length);
    BigInteger big = new BigInteger(out);
    return big.toString(16);
  }
*/

}

/*
 * This file is part of "JTA - Telnet/SSH for the JAVA(tm) platform".
 *
 * (c) Matthias L. Jugel, Marcus Meißner 1996-2005. All Rights Reserved.
 *
 * Please visit http://javatelnet.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * --LICENSE NOTICE--
 *
 */