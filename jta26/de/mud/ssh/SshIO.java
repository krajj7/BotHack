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
import java.security.SecureRandom;

/**
 * Secure Shell IO
 * @author Marcus Meissner
 * @version $Id: SshIO.java 506 2005-10-25 10:07:21Z marcus $
 */
public abstract class SshIO {

  private static MessageDigest md5;

  static {
    try {
      md5 = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      System.err.println("SshIO: unable to load message digest algorithm: "+e);
      e.printStackTrace();
    }
  }

  /**
   * variables for the connection
   */
  private String idstr = ""; //("SSH-<protocolmajor>.<protocolminor>-<version>\n")
  private String idstr_sent = "SSH/JTA (c) Marcus Meissner, Matthias L. Jugel\n";

  /**
   * Debug level. This results in additional diagnostic messages on the
   * java console.
   */
  private static int debug = 0;

  /**
   * State variable for Ssh negotiation reader
   */
  private SshCrypto crypto = null;

  String cipher_type = "IDEA";

  private int remotemajor, remoteminor;
  private int mymajor, myminor;
  private int useprotocol;

  private String login = "", password = "";
  //nobody is to access those fields  : better to use pivate, nobody knows :-)

  public String dataToSend = null;

  public String hashHostKey = null;  // equals to the applet parameter if any

  byte lastPacketSentType;


  // phase : handleBytes
  private int phase = 0;
  private final int PHASE_INIT = 0;
  private final int PHASE_SSH_RECEIVE_PACKET = 1;


  // SSH v2 RSA
  BigInteger rsa_e, rsa_n;

  //handlePacket
  //messages
  //  The supported packet types and the corresponding message numbers are
  //	given in the following table.  Messages with _MSG_ in their name may
  //	be sent by either side.  Messages with _CMSG_ are only sent by the
  //  client, and messages with _SMSG_ only by the server.
  //
  private final byte SSH_MSG_DISCONNECT = 1;
  private final byte SSH_SMSG_PUBLIC_KEY = 2;
  private final byte SSH_CMSG_SESSION_KEY = 3;
  private final byte SSH_CMSG_USER = 4;
  private final byte SSH_CMSG_AUTH_PASSWORD = 9;
  private final byte SSH_CMSG_REQUEST_PTY = 10;
  private final byte SSH_CMSG_WINDOW_SIZE = 11;
  private final byte SSH_CMSG_EXEC_SHELL = 12;
  private final byte SSH_SMSG_SUCCESS = 14;
  private final byte SSH_SMSG_FAILURE = 15;
  private final byte SSH_CMSG_STDIN_DATA = 16;
  private final byte SSH_SMSG_STDOUT_DATA = 17;
  private final byte SSH_SMSG_STDERR_DATA = 18;
  private final byte SSH_SMSG_EXITSTATUS = 20;
  private final byte SSH_MSG_IGNORE = 32;
  private final byte SSH_CMSG_EXIT_CONFIRMATION = 33;
  private final byte SSH_MSG_DEBUG = 36;


  /* SSH v2 stuff */

  private final byte SSH2_MSG_DISCONNECT = 1;
  private final byte SSH2_MSG_IGNORE = 2;
  private final byte SSH2_MSG_SERVICE_REQUEST = 5;
  private final byte SSH2_MSG_SERVICE_ACCEPT = 6;

  private final byte SSH2_MSG_KEXINIT = 20;
  private final byte SSH2_MSG_NEWKEYS = 21;

  private final byte SSH2_MSG_KEXDH_INIT = 30;
  private final byte SSH2_MSG_KEXDH_REPLY = 31;

  private String kexalgs, hostkeyalgs, encalgs2c, encalgc2s, macalgs2c, macalgc2s, compalgc2s, compalgs2c, langc2s, langs2;

  private int outgoingseq = 0, incomingseq = 0;

  //
  // encryption types
  //
  private int SSH_CIPHER_NONE = 0;	 // No encryption
  private int SSH_CIPHER_IDEA = 1;  // IDEA in CFB mode		(patented)
  private int SSH_CIPHER_DES = 2;  // DES in CBC mode
  private int SSH_CIPHER_3DES = 3;  // Triple-DES in CBC mode
  private int SSH_CIPHER_TSS = 4;  // An experimental stream cipher

  private int SSH_CIPHER_RC4 = 5;  // RC4			(patented)

  private int SSH_CIPHER_BLOWFISH = 6;	// Bruce Scheiers blowfish (public d)


  //
  // authentication methods
  //
  private final int SSH_AUTH_RHOSTS = 1;   //.rhosts or /etc/hosts.equiv
  private final int SSH_AUTH_RSA = 2;   //pure RSA authentication
  private final int SSH_AUTH_PASSWORD = 3;   //password authentication, implemented !
  private final int SSH_AUTH_RHOSTS_RSA = 4;   //.rhosts with RSA host authentication


  private boolean cansenddata = false;

  /**
   * Initialise SshIO
   */
  public SshIO() {
    crypto = null;
  }

  public void setLogin(String user) {
    if (user == null) user = "";
    login = user;
  }

  public void setPassword(String password) {
    if (password == null) password = "";
    this.password = password;
  }

  SshPacket currentpacket;

  protected abstract void write(byte[] buf) throws IOException;

  public abstract String getTerminalType();

  byte[] one = new byte[1];

  private void write(byte b) throws IOException {
    one[0] = b;
    write(one);
  }

  public void disconnect() {
    // System.err.println("In Disconnect");
    idstr = "";
    login = "";
    password = "";
    phase = 0;
    crypto = null;
  }

  public void setWindowSize(int columns,int rows)
    throws IOException {
    if (phase == PHASE_INIT) {
      System.err.println("sshio:setWindowSize(), sizing in init phase not supported.\n");
    }
    if (debug>1) System.err.println("SSHIO:setWindowSize("+columns+","+rows+")");
    Send_SSH_CMSG_WINDOW_SIZE(columns,rows);
  }

  synchronized public void sendData(String str) throws IOException {
    if (debug > 1) System.out.println("SshIO.send(" + str + ")");
    if (dataToSend == null)
      dataToSend = str;
    else
      dataToSend += str;
    if (cansenddata) {
      Send_SSH_CMSG_STDIN_DATA(dataToSend);
      dataToSend = null;
    }
  }

  /**
   * Read data from the remote host. Blocks until data is available.
   *
   * Returns an array of bytes that will be displayed.
   *
   */
  public byte[] handleSSH(byte buff[])
    throws IOException {
    byte[] rest;
    String result;

    if (debug > 1)
      System.out.println("SshIO.getPacket(" + buff + "," + buff.length + ")");


    if (phase == PHASE_INIT) {
      byte b;  		// of course, byte is a signed entity (-128 -> 127)
      int boffset = 0;	// offset into the buffer received

      while (boffset < buff.length) {
        b = buff[boffset++];
        // both sides MUST send an identification string of the form
        // "SSH-protoversion-softwareversion comments",
        // followed by newline character(ascii 10 = '\n' or '\r')
        idstr += (char) b;
        if (b == '\n') {
          if (!idstr.substring(0, 4).equals("SSH-")) {
              // we need to ignore lines of data that precede the idstr
              if (debug > 0)
                System.out.print("Received data line: " + idstr);
              idstr = "";
              continue;
          }
          phase++;
          remotemajor = Integer.parseInt(idstr.substring(4, 5));
          String minorverstr = idstr.substring(6, 8);
          if (!Character.isDigit(minorverstr.charAt(1)))
            minorverstr = minorverstr.substring(0, 1);
          remoteminor = Integer.parseInt(minorverstr);

          System.out.println("remotemajor " + remotemajor);
          System.out.println("remoteminor " + remoteminor);

          if (remotemajor == 2) {
            mymajor = 2;
            myminor = 0;
            useprotocol = 2;
          } else {
            if (false && (remoteminor == 99)) {
              mymajor = 2;
              myminor = 0;
              useprotocol = 2;
            } else {
              mymajor = 1;
              myminor = 5;
              useprotocol = 1;
            }
          }
          // this is how we tell the remote server what protocol we use.
          idstr_sent = "SSH-" + mymajor + "." + myminor + "-" + idstr_sent;
          write(idstr_sent.getBytes());

          if (useprotocol == 2)
            currentpacket = new SshPacket2(null);
          else
            currentpacket = new SshPacket1(null);
        }
      }
      if (boffset == buff.length)
        return "".getBytes();
      return "Must not have left over data after PHASE_INIT!\n".getBytes();
    }

    result = "";
    rest = currentpacket.addPayload(buff);
    if (currentpacket.isFinished()) {
      if (useprotocol == 1) {
        result = result + handlePacket1((SshPacket1) currentpacket);
        currentpacket = new SshPacket1(crypto);
      } else {
        result = result + handlePacket2((SshPacket2) currentpacket);
        currentpacket = new SshPacket2(crypto);
      }
    }
    while (rest != null) {
      rest = currentpacket.addPayload(rest);
      if (currentpacket.isFinished()) {
        // the packet is finished, otherwise we would not have got a rest
        if (useprotocol == 1) {
          result = result + handlePacket1((SshPacket1) currentpacket);
          currentpacket = new SshPacket1(crypto);
        } else {
          result = result + handlePacket2((SshPacket2) currentpacket);
          currentpacket = new SshPacket2(crypto);
        }
      }
    }
    return result.getBytes();
  }

  /**
   * Handle SSH protocol Version 2
   *
   * @param p the packet we will process here.
   * @return a array of bytes
   */
  private String handlePacket2(SshPacket2 p)
    throws IOException {
    switch (p.getType()) {
      case SSH2_MSG_IGNORE:
        System.out.println("SSH2: SSH2_MSG_IGNORE");
        break;
      case SSH2_MSG_DISCONNECT:
        int discreason = p.getInt32();
        String discreason1 = p.getString();
        /*String discreason2 = p.getString();*/
        System.out.println("SSH2: SSH2_MSG_DISCONNECT(" + discreason + "," + discreason1 + "," + /*discreason2+*/")");

        return "\nSSH2 disconnect: " + discreason1 + "\n";

      case SSH2_MSG_NEWKEYS:
        {
          System.out.println("SSH2: SSH2_MSG_NEWKEYS");
          sendPacket2(new SshPacket2(SSH2_MSG_NEWKEYS));

          byte[] session_key = new byte[16];

          crypto = new SshCrypto(cipher_type, session_key);

          SshPacket2 pn = new SshPacket2(SSH2_MSG_SERVICE_REQUEST);
          pn.putString("ssh-userauth");
          sendPacket2(pn);
          break;
        }
      case SSH2_MSG_SERVICE_ACCEPT:
        {
          System.out.println("Service Accept: " + p.getString());
          break;
        }
      case SSH2_MSG_KEXINIT:
        {
          byte[] fupp;
          System.out.println("SSH2: SSH2_MSG_KEXINIT");
          byte kexcookie[] = p.getBytes(16); // unused.

          String kexalgs = p.getString();
          System.out.println("- " + kexalgs);
          String hostkeyalgs = p.getString();
          System.out.println("- " + hostkeyalgs);
          String encalgc2s = p.getString();
          System.out.println("- " + encalgc2s);
          String encalgs2c = p.getString();
          System.out.println("- " + encalgs2c);
          String macalgc2s = p.getString();
          System.out.println("- " + macalgc2s);
          String macalgs2c = p.getString();
          System.out.println("- " + macalgs2c);
          String compalgc2s = p.getString();
          System.out.println("- " + compalgc2s);
          String compalgs2c = p.getString();
          System.out.println("- " + compalgs2c);
          String langc2s = p.getString();
          System.out.println("- " + langc2s);
          String langs2c = p.getString();
          System.out.println("- " + langs2c);
          fupp = p.getBytes(1);
          System.out.println("- first_kex_follows: " + fupp[0]);
          /* int32 reserved (0) */

          SshPacket2 pn = new SshPacket2(SSH2_MSG_KEXINIT);
          byte[] kexsend = new byte[16];
          String ciphername;
          pn.putBytes(kexsend);
          pn.putString("diffie-hellman-group1-sha1");
          pn.putString("ssh-rsa");

          /* FIXME: check if it really is in the encalgc2s */
          cipher_type = "NONE";
          ciphername = "none";

          /* FIXME: dito for HMAC */

          pn.putString("none");
          pn.putString("none");
          pn.putString("hmac-md5");
          pn.putString("hmac-md5");
          pn.putString("none");
          pn.putString("none");
          pn.putString("");
          pn.putString("");
          pn.putByte((byte) 0);
          pn.putInt32(0);
          sendPacket2(pn);

          pn = new SshPacket2(SSH2_MSG_KEXDH_INIT);
          pn.putMpInt(BigInteger.valueOf(0xdeadbeef));
          sendPacket2(pn);
          break;
        }
      case SSH2_MSG_KEXDH_REPLY:
        {
          String result;

          System.out.println("SSH2_MSG_KEXDH_REPLY");
          int bloblen = p.getInt32();
          System.out.println("bloblen is " + bloblen);
          /* the blob has a substructure:
           * 	String type
           * 	if RSA:
           * 		bignum1
           * 		bignum2
           * 	if DSA:
           * 		bignum1,2,3,4
           */
          String keytype = p.getString();
          System.out.println("KEXDH: " + keytype);
          if (keytype.equals("ssh-rsa")) {
            rsa_e = p.getMpInt();
            rsa_n = p.getMpInt();
            result = "\n\rSSH-RSA (" + rsa_n + "," + rsa_e + ")\n\r";
          } else {
            return "\n\rUnsupported kexdh algorithm " + keytype + "!\n\r";
          }
          BigInteger dhserverpub = p.getMpInt();
          result += "DH Server Pub: " + dhserverpub + "\n\r";

          /* signature is a new blob, length is Int32. */
          /*
           * RSA:
           * 	String 		type (ssh-rsa)
           * 	Int32/byte[]	signed signature
           */
          int siglen = p.getInt32();
          String sigstr = p.getString();
          result += "Signature: ktype is " + sigstr + "\r\n";
          byte sigdata[] = p.getBytes(p.getInt32());

          return result;
        }
      default:
        return "SSH2: handlePacket2 Unknown type " + p.getType();
    }
    return "";
  }


  private String handlePacket1(SshPacket1 p)
    throws IOException { //the message to handle is data and its length is

    byte b;  		// of course, byte is a signed entity (-128 -> 127)

    //we have to deal with data....

    if (debug > 0)
      System.out.println("1 packet to handle, type " + p.getType());


    switch (p.getType()) {
      case SSH_MSG_IGNORE:
        return "";

      case SSH_MSG_DISCONNECT:
        String str = p.getString();
        disconnect();
        return str;

      case SSH_SMSG_PUBLIC_KEY:
        byte[] anti_spoofing_cookie;			//8 bytes
        byte[] server_key_bits;				//32-bit int
        byte[] server_key_public_exponent;		//mp-int
        byte[] server_key_public_modulus;			//mp-int
        byte[] host_key_bits;				//32-bit int
        byte[] host_key_public_exponent;			//mp-int
        byte[] host_key_public_modulus;			//mp-int
        byte[] protocol_flags;				//32-bit int
        byte[] supported_ciphers_mask;			//32-bit int
        byte[] supported_authentications_mask;		//32-bit int

        anti_spoofing_cookie = p.getBytes(8);
        server_key_bits = p.getBytes(4);
        server_key_public_exponent = p.getMpInt();
        server_key_public_modulus = p.getMpInt();
        host_key_bits = p.getBytes(4);
        host_key_public_exponent = p.getMpInt();
        host_key_public_modulus = p.getMpInt();
        protocol_flags = p.getBytes(4);
        supported_ciphers_mask = p.getBytes(4);
        supported_authentications_mask = p.getBytes(4);

        // We have completely received the PUBLIC_KEY
        // We prepare the answer ...

        String ret = Send_SSH_CMSG_SESSION_KEY(
          anti_spoofing_cookie, server_key_public_modulus,
          host_key_public_modulus, supported_ciphers_mask,
          server_key_public_exponent, host_key_public_exponent
        );
        if (ret != null)
          return ret;

        // we check if MD5(server_key_public_exponent) is equals to the
        // applet parameter if any .
        if (hashHostKey != null && hashHostKey.compareTo("") != 0) {
          // we compute hashHostKeyBis the hash value in hexa of
          // host_key_public_modulus
          byte[] Md5_hostKey = md5.digest(host_key_public_modulus);
          String hashHostKeyBis = "";
          for (int i = 0; i < Md5_hostKey.length; i++) {
            String hex = "";
            int[] v = new int[2];
            v[0] = (Md5_hostKey[i] & 240) >> 4;
            v[1] = (Md5_hostKey[i] & 15);
            for (int j = 0; j < 1; j++)
              switch (v[j]) {
                case 10:
                  hex += "a";
                  break;
                case 11:
                  hex += "b";
                  break;
                case 12:
                  hex += "c";
                  break;
                case 13:
                  hex += "d";
                  break;
                case 14:
                  hex += "e";
                  break;
                case 15:
                  hex += "f";
                  break;
                default :
                  hex += String.valueOf(v[j]);
                  break;
              }
            hashHostKeyBis = hashHostKeyBis + hex;
          }
          //we compare the 2 values
          if (hashHostKeyBis.compareTo(hashHostKey) != 0) {
            login = password = "";
            return "\nHash value of the host key not correct \r\n"
              + "login & password have been reset \r\n"
              + "- erase the 'hashHostKey' parameter in the Html\r\n"
              + "(it is used for auhentificating the server and "
              + "prevent you from connecting \r\n"
              + "to any other)\r\n";
          }
        }
        break;

      case SSH_SMSG_SUCCESS:
        if (debug > 0)
          System.out.println("SSH_SMSG_SUCCESS (last packet was " + lastPacketSentType + ")");
        if (lastPacketSentType == SSH_CMSG_SESSION_KEY) {
          //we have succefully sent the session key !! (at last :-) )
          Send_SSH_CMSG_USER();
          break;
        }

        if (lastPacketSentType == SSH_CMSG_USER) {
          // authentication is NOT needed for this user
          Send_SSH_CMSG_REQUEST_PTY(); //request a pseudo-terminal
          return "\nEmpty password login.\r\n";
        }

        if (lastPacketSentType == SSH_CMSG_AUTH_PASSWORD) {// password correct !!!
          //yahoo
          if (debug > 0)
            System.out.println("login succesful");

          //now we have to start the interactive session ...
          Send_SSH_CMSG_REQUEST_PTY(); //request a pseudo-terminal
          return "\nLogin & password accepted\r\n";
        }

        if (lastPacketSentType == SSH_CMSG_REQUEST_PTY) {// pty accepted !!
          /* we can send data with a pty accepted ... no need for a shell. */
          cansenddata = true;
          if (dataToSend != null) {
            Send_SSH_CMSG_STDIN_DATA(dataToSend);
            dataToSend = null;
          }
          Send_SSH_CMSG_EXEC_SHELL(); //we start a shell
          break;
        }
        if (lastPacketSentType == SSH_CMSG_EXEC_SHELL) {// shell is running ...
          /* empty */
        }

        break;

      case SSH_SMSG_FAILURE:
        if (debug > 1) System.err.println("SSH_SMSG_FAILURE");
        if (lastPacketSentType == SSH_CMSG_AUTH_PASSWORD) {// password incorrect ???
          System.out.println("failed to log in");
          Send_SSH_MSG_DISCONNECT("Failed to log in.");
          disconnect();
          return "\nLogin & password not accepted\r\n";
        }
        if (lastPacketSentType == SSH_CMSG_USER) {
          // authentication is needed for the given user
          // (in most cases that's true)
          Send_SSH_CMSG_AUTH_PASSWORD();
          break;
        }

        if (lastPacketSentType == SSH_CMSG_REQUEST_PTY) {// pty not accepted !!
          break;
        }
        break;

      case SSH_SMSG_STDOUT_DATA: //receive some data from the server
        return p.getString();

      case SSH_SMSG_STDERR_DATA: //receive some error data from the server
        //	if(debug > 1)
        str = "Error : " + p.getString();
        System.out.println("SshIO.handlePacket : " + "STDERR_DATA " + str);
        return str;

      case SSH_SMSG_EXITSTATUS: //sent by the server to indicate that
        // the client program has terminated.
        //32-bit int   exit status of the command
        int value = p.getInt32();
        Send_SSH_CMSG_EXIT_CONFIRMATION();
        System.out.println("SshIO : Exit status " + value);
        disconnect();
        break;

      case SSH_MSG_DEBUG:
        str = p.getString();
        if (debug > 0) {
          System.out.println("SshIO.handlePacket : " + " DEBUG " + str);

          // bad bad bad bad bad. We should not do actions in DEBUG messages,
          // but apparently some SSH demons does not send SSH_SMSG_FAILURE for
          // just USER CMS.
/*
      if(lastPacketSentType==SSH_CMSG_USER) {
        Send_SSH_CMSG_AUTH_PASSWORD();
        break;
      }
*/
          return str;
        }
        return "";

      default:
        System.err.print("SshIO.handlePacket1: Packet Type unknown: " + p.getType());
        break;

    }//	switch(b)
    return "";
  } // handlePacket

  private void sendPacket1(SshPacket1 packet) throws IOException {
    write(packet.getPayLoad(crypto));
    lastPacketSentType = packet.getType();
  }

  private void sendPacket2(SshPacket2 packet) throws IOException {
    write(packet.getPayLoad(crypto, outgoingseq));
    outgoingseq++;
    lastPacketSentType = packet.getType();
  }

  //
  // Send_SSH_CMSG_SESSION_KEY
  // Create :
  // the session_id,
  // the session_key,
  // the Xored session_key,
  // the double_encrypted session key
  // send SSH_CMSG_SESSION_KEY
  // Turn the encryption on (initialise the block cipher)
  //

  private String Send_SSH_CMSG_SESSION_KEY(byte[] anti_spoofing_cookie,
                                           byte[] server_key_public_modulus,
                                           byte[] host_key_public_modulus,
                                           byte[] supported_ciphers_mask,
                                           byte[] server_key_public_exponent,
                                           byte[] host_key_public_exponent)
    throws IOException {

    String str;
    int boffset;

    byte cipher_types;		//encryption types
    byte[] session_key;		//mp-int

    // create the session id
    //	session_id = md5(hostkey->n || servkey->n || cookie) //protocol V 1.5. (we use this one)
    //	session_id = md5(servkey->n || hostkey->n || cookie) //protocol V 1.1.(Why is it different ??)
    //

    byte[] session_id_byte = new byte[host_key_public_modulus.length + server_key_public_modulus.length + anti_spoofing_cookie.length];

    System.arraycopy(host_key_public_modulus, 0, session_id_byte, 0, host_key_public_modulus.length);
    System.arraycopy(server_key_public_modulus, 0, session_id_byte, host_key_public_modulus.length, server_key_public_modulus.length);
    System.arraycopy(anti_spoofing_cookie, 0, session_id_byte, host_key_public_modulus.length + server_key_public_modulus.length, anti_spoofing_cookie.length);

    byte[] hash_md5 = md5.digest(session_id_byte);


    //	SSH_CMSG_SESSION_KEY : Sent by the client
    //	    1 byte       cipher_type (must be one of the supported values)
    // 	    8 bytes      anti_spoofing_cookie (must match data sent by the server)
    //	    mp-int       double-encrypted session key (uses the session-id)
    //	    32-bit int   protocol_flags
    //
    if ((supported_ciphers_mask[3] & (byte) (1 << SSH_CIPHER_BLOWFISH)) != 0) {
      cipher_types = (byte) SSH_CIPHER_BLOWFISH;
      cipher_type = "Blowfish";
    } else {
      if ((supported_ciphers_mask[3] & (1 << SSH_CIPHER_IDEA)) != 0) {
        cipher_types = (byte) SSH_CIPHER_IDEA;
        cipher_type = "IDEA";
      } else {
        if ((supported_ciphers_mask[3] & (1 << SSH_CIPHER_3DES)) != 0) {
          cipher_types = (byte) SSH_CIPHER_3DES;
          cipher_type = "DES3";
        } else {
          if ((supported_ciphers_mask[3] & (1 << SSH_CIPHER_DES)) != 0) {
            cipher_types = (byte) SSH_CIPHER_DES;
            cipher_type = "DES";
          } else {
            System.err.println("SshIO: remote server does not supported IDEA, BlowFish or 3DES, support cypher mask is " + supported_ciphers_mask[3] + ".\n");
            Send_SSH_MSG_DISCONNECT("No more auth methods available.");
            disconnect();
            return "\rRemote server does not support IDEA/Blowfish/3DES blockcipher, closing connection.\r\n";
          }
        }
      }
    }
    if (debug > 0)
      System.out.println("SshIO: Using " + cipher_type + " blockcipher.\n");


    // 	anti_spoofing_cookie : the same
    //      double_encrypted_session_key :
    //		32 bytes of random bits
    //		Xor the 16 first bytes with the session-id
    //		encrypt with the server_key_public (small) then the host_key_public(big) using RSA.
    //

    //32 bytes of random bits
    byte[] random_bits1 = new byte[16], random_bits2 = new byte[16];


    /// java.util.Date date = new java.util.Date(); ////the number of milliseconds since January 1, 1970, 00:00:00 GMT.
    //Math.random()   a pseudorandom double between 0.0 and 1.0.
    // random_bits2 = random_bits1 =
    // md5.hash("" + Math.random() * (new java.util.Date()).getDate());
    // md5.digest(("" + Math.random() * (new java.util.Date()).getTime()).getBytes());

    //random_bits1 = md5.digest(SshMisc.addArrayOfBytes(md5.digest((password + login).getBytes()), random_bits1));
    //random_bits2 = md5.digest(SshMisc.addArrayOfBytes(md5.digest((password + login).getBytes()), random_bits2));


    SecureRandom random = new java.security.SecureRandom(random_bits1); //no supported by netscape :-(
    random.nextBytes(random_bits1);
    random.nextBytes(random_bits2);

    session_key = SshMisc.addArrayOfBytes(random_bits1, random_bits2);

    //Xor the 16 first bytes with the session-id
    byte[] session_keyXored = SshMisc.XORArrayOfBytes(random_bits1, hash_md5);
    session_keyXored = SshMisc.addArrayOfBytes(session_keyXored, random_bits2);

    //We encrypt now!!
    byte[] encrypted_session_key =
      SshCrypto.encrypteRSAPkcs1Twice(session_keyXored,
                                      server_key_public_exponent,
                                      server_key_public_modulus,
                                      host_key_public_exponent,
                                      host_key_public_modulus);

    //	protocol_flags :protocol extension   cf. page 18
    int protocol_flags = 0; /* currently 0 */

    SshPacket1 packet = new SshPacket1(SSH_CMSG_SESSION_KEY);
    packet.putByte((byte) cipher_types);
    packet.putBytes(anti_spoofing_cookie);
    packet.putBytes(encrypted_session_key);
    packet.putInt32(protocol_flags);
    sendPacket1(packet);
    crypto = new SshCrypto(cipher_type, session_key);
    return "";
  }

  /**
   * SSH_MSG_DISCONNECT
   *   string       disconnect reason
   */
  private String Send_SSH_MSG_DISCONNECT(String reason) throws IOException {
    SshPacket1 p = new SshPacket1(SSH_MSG_DISCONNECT);
    p.putString(reason);    // String   Disconnect reason
    sendPacket1(p);
    return "";
  }

  /**
   * SSH_CMSG_USER
   * string   user login name on server
   */
  private String Send_SSH_CMSG_USER() throws IOException {
    if (debug > 0) System.err.println("Send_SSH_CMSG_USER(" + login + ")");

    SshPacket1 p = new SshPacket1(SSH_CMSG_USER);
    p.putString(login);
    sendPacket1(p);

    return "";
  }

  /**
   * Send_SSH_CMSG_AUTH_PASSWORD
   * string   user password
   */
  private String Send_SSH_CMSG_AUTH_PASSWORD() throws IOException {
    SshPacket1 p = new SshPacket1(SSH_CMSG_AUTH_PASSWORD);
    p.putString(password);
    sendPacket1(p);
    return "";
  }

  /**
   * Send_SSH_CMSG_EXEC_SHELL
   *  (no arguments)
   *   Starts a shell (command interpreter), and enters interactive
   *   session mode.
   */
  private String Send_SSH_CMSG_EXEC_SHELL() throws IOException {
    SshPacket1 packet = new SshPacket1(SSH_CMSG_EXEC_SHELL);
    sendPacket1(packet);
    return "";
  }

  /**
   * Send_SSH_CMSG_STDIN_DATA
   *
   */
  private String Send_SSH_CMSG_STDIN_DATA(String str) throws IOException {
    SshPacket1 packet = new SshPacket1(SSH_CMSG_STDIN_DATA);
    packet.putString(str);
    sendPacket1(packet);
    return "";
  }

  /**
   * Send_SSH_CMSG_WINDOW_SIZE
   *   string       TERM environment variable value (e.g. vt100)
   *   32-bit int   terminal height, rows (e.g., 24)
   *   32-bit int   terminal width, columns (e.g., 80)
   *   32-bit int   terminal width, pixels (0 if no graphics) (e.g., 480)
   */
  private String Send_SSH_CMSG_WINDOW_SIZE(int c, int r) throws IOException {
    SshPacket1 p = new SshPacket1(SSH_CMSG_WINDOW_SIZE);

    p.putInt32(r);		// Int32	rows
    p.putInt32(c);		// Int32	columns
    p.putInt32(0);		// Int32	x pixels
    p.putInt32(0);		// Int32	y pixels
    sendPacket1(p);
    return "";
  }

  /**
   * Send_SSH_CMSG_REQUEST_PTY
   *   string       TERM environment variable value (e.g. vt100)
   *   32-bit int   terminal height, rows (e.g., 24)
   *   32-bit int   terminal width, columns (e.g., 80)
   *   32-bit int   terminal width, pixels (0 if no graphics) (e.g., 480)
   */
  private String Send_SSH_CMSG_REQUEST_PTY() throws IOException {
    SshPacket1 p = new SshPacket1(SSH_CMSG_REQUEST_PTY);

    p.putString(getTerminalType());
    p.putInt32(24);		// Int32	rows
    p.putInt32(80);		// Int32	columns
    p.putInt32(0);		// Int32	x pixels
    p.putInt32(0);		// Int32	y pixels
    p.putByte((byte) 0);		// Int8		terminal modes
    sendPacket1(p);
    return "";
  }

  private String Send_SSH_CMSG_EXIT_CONFIRMATION() throws IOException {
    SshPacket1 packet = new SshPacket1(SSH_CMSG_EXIT_CONFIRMATION);
    sendPacket1(packet);
    return "";
  }
}
