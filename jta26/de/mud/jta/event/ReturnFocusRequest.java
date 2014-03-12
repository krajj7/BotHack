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
import de.mud.jta.event.ReturnFocusListener;

/**
 * Notify listeners that the focus is to be returned to whoever wants it.
 * <P>
 * Implemented after a suggestion by Dave &lt;david@mirrabooka.com&gt;
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class ReturnFocusRequest implements PluginMessage {
  /** Create a new return focus request.*/
  public ReturnFocusRequest() { }

  /**
   * Notify all listeners about return focus message.
   * @param pl the list of plugin message listeners
   * @return always null
   */
  public Object firePluginMessage(PluginListener pl) {
    if(pl instanceof ReturnFocusListener)
      ((ReturnFocusListener)pl).returnFocus();
    return null;
  }
}
