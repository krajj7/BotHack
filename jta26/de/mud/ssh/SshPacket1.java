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
package de.mud.ssh;

import java.math.BigInteger;

/**
 * @author Marcus Meissner
 * @version $Id: SshPacket1.java 499 2005-09-29 08:24:54Z leo $
 */
public class SshPacket1 extends SshPacket {
  private final static boolean debug = false;

  //SSH_RECEIVE_PACKET
  private byte[] packet_length_array = new byte[4];
  private int packet_length = 0;
  private byte[] padding = null;
  private byte[] crc_array = new byte[4];
  private byte[] block = null;
  private byte[] encryptedBlock = null;							// encrypted part (Padding + Type + Data + Check)
  private byte[] decryptedBlock = null;							// decrypted part (Padding + Type + Data + Check)

  private SshCrypto crypto = null;

  public SshPacket1(SshCrypto _crypto) {
    /* receiving packet */
    position = 0;
    phase_packet = PHASE_packet_length;
    crypto = _crypto;
  }

  public SshPacket1(byte newType) {
    setType(newType);
  }

  /**
   * Return the mp-int at the position offset in the data
   * First 2 bytes are the number of bits in the integer, msb first
   * (for example, the value 0x00012345 would have 17 bits).  The
   * value zero has zero bits.  It is permissible that the number of
   * bits be larger than the real number of bits.
   * The number of bits is followed by (bits + 7) / 8 bytes of binary
   * data, msb first, giving the value of the integer.
   */

  public byte[] getMpInt() {
    return getBytes((getInt16() + 7) / 8);
  }

  public void putMpInt(BigInteger bi) {
    byte[] mpbytes = bi.toByteArray(), xbytes;
    int i;
    for (i = 0; (i < mpbytes.length) && (mpbytes[i] == 0); i++) /* EMPTY */ ;
    xbytes = new byte[mpbytes.length - i];
    System.arraycopy(mpbytes, i, xbytes, 0, mpbytes.length - i);
    putInt16(xbytes.length * 8);
    putBytes(xbytes);
  }

  byte[] getPayLoad(SshCrypto crypto) {
    byte[] data = getData();

    //packet length
    if (data != null)
      packet_length = data.length + 5;
    else
      packet_length = 5;
    packet_length_array[3] = (byte) (packet_length & 0xff);
    packet_length_array[2] = (byte) ((packet_length >> 8) & 0xff);
    packet_length_array[1] = (byte) ((packet_length >> 16) & 0xff);
    packet_length_array[0] = (byte) ((packet_length >> 24) & 0xff);

    //padding
    padding = new byte[(8 - (packet_length % 8))];
    if (crypto == null) {
      for (int i = 0; i < padding.length; i++)
        padding[i] = 0;
    } else {
      for (int i = 0; i < padding.length; i++)
        padding[i] = SshMisc.getNotZeroRandomByte();
    }

    //Compute the crc of [ Padding, Packet type, Data ]

    block = new byte[packet_length + padding.length];
    System.arraycopy(padding, 0, block, 0, padding.length);
    int offset = padding.length;
    block[offset++] = getType();
    if (packet_length > 5) {
      System.arraycopy(data, 0, block, offset, data.length);
      offset += data.length;
    }

    long crc = SshMisc.crc32(block, offset);
    crc_array[3] = (byte) (crc & 0xff);
    crc_array[2] = (byte) ((crc >> 8) & 0xff);
    crc_array[1] = (byte) ((crc >> 16) & 0xff);
    crc_array[0] = (byte) ((crc >> 24) & 0xff);
    System.arraycopy(crc_array, 0, block, offset, 4);

    //encrypt
    if (crypto != null)
      block = crypto.encrypt(block);
    byte[] full = new byte[block.length + 4];
    System.arraycopy(packet_length_array, 0, full, 0, 4);
    System.arraycopy(block, 0, full, 4, block.length);
    return full;
  };

  private int position = 0;
  private int phase_packet = 0;
  private final int PHASE_packet_length = 0;
  private final int PHASE_block = 1;

  public byte[] addPayload(byte[] buff) {
    int boffset = 0;
    byte newbuf[] = null;

    while (boffset < buff.length) {
      switch (phase_packet) {

        // 4 bytes
        // Packet length: 32 bit unsigned integer
        // gives the length of the packet, not including the length field
        // and padding.  maximum is 262144 bytes.

        case PHASE_packet_length:
          packet_length_array[position++] = buff[boffset++];
          if (position >= 4) {
            packet_length =
              (packet_length_array[3] & 0xff) +
              ((packet_length_array[2] & 0xff) << 8) +
              ((packet_length_array[1] & 0xff) << 16) +
              ((packet_length_array[0] & 0xff) << 24);
            position = 0;
            phase_packet++;
            block = new byte[8 * (packet_length / 8 + 1)];
          }
          break; //switch (phase_packet)

          //8*(packet_length/8 +1) bytes

        case PHASE_block:

          if (block.length > position) {
            if (boffset < buff.length) {
              int amount = buff.length - boffset;
              if (amount > block.length - position)
                amount = block.length - position;
              System.arraycopy(buff, boffset, block, position, amount);
              boffset += amount;
              position += amount;
            }
          }

          if (position == block.length) { //the block is complete
            if (buff.length > boffset) {  //there is more than 1 packet in buff
              newbuf = new byte[buff.length - boffset];
              System.arraycopy(buff, boffset, newbuf, 0, buff.length - boffset);
            }
            int blockOffset = 0;
            //padding
            int padding_length = (int) (8 - (packet_length % 8));
            padding = new byte[padding_length];

            if (crypto != null)
              decryptedBlock = crypto.decrypt(block);
            else
              decryptedBlock = block;

            if (decryptedBlock.length != padding_length + packet_length)
              System.out.println("???");

            for (int i = 0; i < padding.length; i++)
              padding[i] = decryptedBlock[blockOffset++];

            //packet type
            setType(decryptedBlock[blockOffset++]);

            byte[] data;
            //data
            if (packet_length > 5) {
              data = new byte[packet_length - 5];
              System.arraycopy(decryptedBlock, blockOffset, data, 0, packet_length - 5);
              blockOffset += packet_length - 5;
            } else
              data = null;
            putData(data);
            //crc
            for (int i = 0; i < crc_array.length; i++)
              crc_array[i] = decryptedBlock[blockOffset++];
            if (!checkCrc())
              System.err.println("SshPacket1: CRC wrong in received packet!");

            return newbuf;
          }
          break;
      }
    }
    return null;
  };

  private boolean checkCrc() {
    byte[] crc_arrayCheck = new byte[4];
    long crcCheck;

    crcCheck = SshMisc.crc32(decryptedBlock, decryptedBlock.length - 4);
    crc_arrayCheck[3] = (byte) (crcCheck & 0xff);
    crc_arrayCheck[2] = (byte) ((crcCheck >> 8) & 0xff);
    crc_arrayCheck[1] = (byte) ((crcCheck >> 16) & 0xff);
    crc_arrayCheck[0] = (byte) ((crcCheck >> 24) & 0xff);

    if (debug) {
      System.err.println(crc_arrayCheck[3] + " == " + crc_array[3]);
      System.err.println(crc_arrayCheck[2] + " == " + crc_array[2]);
      System.err.println(crc_arrayCheck[1] + " == " + crc_array[1]);
      System.err.println(crc_arrayCheck[0] + " == " + crc_array[0]);
    }
    if (crc_arrayCheck[3] != crc_array[3]) return false;
    if (crc_arrayCheck[2] != crc_array[2]) return false;
    if (crc_arrayCheck[1] != crc_array[1]) return false;
    if (crc_arrayCheck[0] != crc_array[0]) return false;
    return true;
  }
} //class
