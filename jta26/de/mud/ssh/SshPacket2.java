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


import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Marcus Meissner
 * @version $Id: SshPacket2.java 499 2005-09-29 08:24:54Z leo $
 */
public class SshPacket2 extends SshPacket {

  private final static boolean debug = true;

  //SSH_RECEIVE_PACKET
  private byte[] packet_length_array = new byte[5];				// 4 bytes
  private int packet_length = 0;	// 32-bit sign int
  private int padlen = 0;		// packet length 1 byte unsigned
  private byte[] crc_array = new byte[4];	// 32-bit crc


  private int position 			= 0;
  private int phase_packet 		= 0;
  private final int PHASE_packet_length = 0;
  private final int PHASE_block 	= 1;

  private SshCrypto crypto = null;

  public SshPacket2(SshCrypto _crypto) {
    /* receiving packet */
    position = 0;
    phase_packet = PHASE_packet_length;
    crypto = _crypto;
  }

  public SshPacket2(byte newType) {
    setType(newType);
  }

  /**
   * Return the mp-int at the position offset in the data
   * First 4 bytes are the number of bytes in the integer, msb first
   * (for example, the value 0x00012345 would have 17 bits).  The
   * value zero has zero bits.  It is permissible that the number of
   * bits be larger than the real number of bits.
   * The number of bits is followed by (bits + 7) / 8 bytes of binary
   * data, msb first, giving the value of the integer.
   */
	
  public BigInteger getMpInt() { 
    return new BigInteger(1,getBytes(getInt32()));
  }

  public void putMpInt(BigInteger bi) {
    byte[] mpbytes = bi.toByteArray(), xbytes;
    int i;

    for (i = 0; (i < mpbytes.length) && ( mpbytes[i] == 0 ) ; i++) /* EMPTY */ ;
    xbytes = new byte[mpbytes.length - i];
    System.arraycopy(mpbytes,i,xbytes,0,mpbytes.length - i);

    putInt32(mpbytes.length - i);
    putBytes(xbytes);
  }

  public byte[] getPayLoad(SshCrypto xcrypt, long seqnr)
  throws IOException {
    byte[] data = getData();

    int blocksize = 8;

    // crypted data is: 
    // packet length [ payloadlen + padlen + type + data ]
    packet_length = 4 + 1 + 1;
    if (data!=null)
      packet_length += data.length;

    // pad it up to full blocksize.
    // If not crypto, zeroes, otherwise random. 
    // (zeros because we do not want to tell the attacker the state of our
    //  random generator)
    int padlen = blocksize - (packet_length % blocksize);
    if (padlen < 4) padlen += blocksize;

    byte[] padding = new byte[padlen];
    System.out.println("packet length is "+packet_length+", padlen is "+padlen);
    if (xcrypt == null)
      for(int i=0; i<padlen; i++) padding[i] = 0;
    else
      for(int i=0; i<padlen; i++) padding[i] = SshMisc.getNotZeroRandomByte();

    // [ packetlength, padlength, padding, packet type, data ]
    byte[] block = new byte[ packet_length + padlen ];

    int xlen = padlen + packet_length - 4;
    block[3] = (byte) (xlen & 0xff);
    block[2] = (byte) ((xlen>>8) & 0xff);
    block[1] = (byte) ((xlen>>16) & 0xff);
    block[0] = (byte) ((xlen>>24) & 0xff);

    block[4] = (byte)padlen;
    block[5] = getType();
    System.arraycopy(data,0,block,6,data.length);
    System.arraycopy(padding,0,block,6+data.length,padlen);

    byte[] md5sum;
    if (xcrypt != null) {
      MessageDigest md5 = null;
      try {
        md5 = MessageDigest.getInstance("MD5");
      } catch (NoSuchAlgorithmException e) {
        System.err.println("SshPacket2: unable to load message digest algorithm: "+e);
      }
      byte[] seqint = new byte[4];

      seqint[0] = (byte)((seqnr >> 24) & 0xff);
      seqint[1] = (byte)((seqnr >> 16) & 0xff);
      seqint[2] = (byte)((seqnr >>  8) & 0xff);
      seqint[3] = (byte)((seqnr      ) & 0xff);
      md5.update(seqint,0,4);
      md5.update(block,0,block.length);
      md5sum = md5.digest();
    } else {
      md5sum = new byte[0];
    }

    if (xcrypt != null)
      block = xcrypt.encrypt(block);

    byte[] sendblock = new byte[block.length + md5sum.length];
    System.arraycopy(block,0,sendblock,0,block.length);
    System.arraycopy(md5sum,0,sendblock,block.length,md5sum.length);
    return sendblock;
  };

  private byte block[];

  public byte[] addPayload(byte buff[]) {
    int boffset = 0;
    byte b;
    byte[] newbuf = null;
    int hmaclen = 0;

    if (crypto!=null) hmaclen = 16;

    System.out.println("addPayload2 "+buff.length);

    /*
     * Note: The whole packet is encrypted, except for the MAC.
     *
     * (So I have to rewrite it again).
     */

    while(boffset < buff.length) {
      switch (phase_packet) {
      // 4 bytes
      // Packet length: 32 bit unsigned integer
      // gives the length of the packet, not including the length field
      // and padding.  maximum is 262144 bytes.

      case PHASE_packet_length:
	packet_length_array[position++] = buff[boffset++];
	if (position==5) {
	  packet_length =
	     (packet_length_array[3]&0xff)	+
	    ((packet_length_array[2]&0xff)<<8)	+
	    ((packet_length_array[1]&0xff)<<16)	+
	    ((packet_length_array[0]&0xff)<<24);
	  padlen = packet_length_array[4];
	  position=0;
	  System.out.println("SSH2: packet length "+packet_length);
	  System.out.println("SSH2: padlen "+padlen);
	  packet_length += hmaclen; /* len(md5) */
	  block = new byte[packet_length-1]; /* padlen already done */
	  phase_packet++;
	}
	break; //switch (phase_packet)

      //8*(packet_length/8 +1) bytes

      case PHASE_block  :
	if (position < block.length) {
	  int amount = buff.length - boffset;
	  if (amount > 0) {
	    if (amount > block.length - position)
	      amount = block.length - position;
	    System.arraycopy(buff,boffset,block,position,amount);
	    boffset	+= amount;
	    position	+= amount;
	  }
	}
	if (position==block.length) { //the block is complete
	  if (buff.length>boffset) {
	    newbuf = new byte[buff.length-boffset];
	    System.arraycopy(buff,boffset,newbuf,0,buff.length-boffset);
	  }
	  byte[] decryptedBlock = new byte[block.length - hmaclen];
	  byte[] data;
	  packet_length -= hmaclen;

	  System.arraycopy(block,0,decryptedBlock,0,block.length-hmaclen);

	  if (crypto != null)
	    decryptedBlock = crypto.decrypt(decryptedBlock);

	  for (int i = 0; i < decryptedBlock.length; i++)
		  System.out.print(" "+decryptedBlock[i]);
	  System.out.println("");

	  setType(decryptedBlock[0]);
	  System.err.println("Packet type: "+getType());
	  System.err.println("Packet len: "+packet_length);

	  //data
	  if(packet_length > padlen+1+1)  {
	    data = new byte[packet_length-1-padlen-1];
	    System.arraycopy(decryptedBlock,1,data,0,data.length);
	    putData(data);
	  } else {
	    putData(null);
	  }
	  /* MAC! */
	  return newbuf;
	}
	break;
      } 
    } 
    return null;
  };
  /*

  private boolean checkCrc(){
    byte[] crc_arrayCheck = new byte[4];
    long crcCheck;

    crcCheck = SshMisc.crc32(decryptedBlock, decryptedBlock.length-4);
    crc_arrayCheck[3] = (byte) (crcCheck & 0xff);
    crc_arrayCheck[2] = (byte) ((crcCheck>>8) & 0xff);
    crc_arrayCheck[1] = (byte) ((crcCheck>>16) & 0xff);
    crc_arrayCheck[0] = (byte) ((crcCheck>>24) & 0xff);

    if(debug) {
      System.err.println(crc_arrayCheck[3]+" == "+crc_array[3]);
      System.err.println(crc_arrayCheck[2]+" == "+crc_array[2]);
      System.err.println(crc_arrayCheck[1]+" == "+crc_array[1]);
      System.err.println(crc_arrayCheck[0]+" == "+crc_array[0]);
    }
    if	(crc_arrayCheck[3] != crc_array[3]) return false;
    if	(crc_arrayCheck[2] != crc_array[2]) return false;
    if	(crc_arrayCheck[1] != crc_array[1]) return false;
    if	(crc_arrayCheck[0] != crc_array[0]) return false;
    return true;
  }
  */
}
