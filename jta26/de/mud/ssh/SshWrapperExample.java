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


/**
 * This is an example for using the SshWrapper class. Note that the
 * password here is in plaintext, so do not make this .class file
 * available with your password inside it.
 *
 * <P>
 * <B>Maintainer:</B>Marcus Meissner
 *
 * @version $Id: SshWrapperExample.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class SshWrapperExample {
  public static void main(String args[]) {
    SshWrapper ssh = new SshWrapper();
    try {
      byte[] buffer = new byte[256];
      ssh.connect(args[0], 22);
      ssh.login("marcus", "xxxxx");
      ssh.setPrompt("marcus");

      System.out.println("after login");

      ssh.send("ls -l");
      ssh.read(buffer);
      System.out.println(new String(buffer));
    } catch (java.io.IOException e) {
      e.printStackTrace();
    }
  }
}
