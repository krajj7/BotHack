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
package de.mud.jta.event;

import de.mud.jta.PluginMessage;
import de.mud.jta.PluginListener;
import de.mud.jta.event.TelnetCommandListener;

import java.io.IOException;

/**
 * Notification of the end of record event
 * <P>
 * <B>Maintainer:</B> Marcus Meissner
 *
 * @version $Id: TelnetCommandRequest.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class TelnetCommandRequest implements PluginMessage {
  /** Create a new telnet command request with the specified value. */
  byte cmd;
  public TelnetCommandRequest(byte command ) { cmd = command; }

  /**
   * Notify all listeners about the end of record message
   * @param pl the list of plugin message listeners
   * @return always null
   */
  public Object firePluginMessage(PluginListener pl) {
    if(pl instanceof TelnetCommandListener) {
      try {
	  ((TelnetCommandListener)pl).sendTelnetCommand(cmd);
      } catch (IOException io) {
      	System.err.println("io exception caught:"+io);
	io.printStackTrace();
      }
    }
    return null;
  }
}
