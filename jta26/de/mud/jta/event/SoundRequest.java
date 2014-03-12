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
import de.mud.jta.event.SoundListener;

import java.net.URL;

/**
 * Play a sound.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: SoundRequest.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class SoundRequest implements PluginMessage {
  protected URL audioClip;

  public SoundRequest(URL audioClip) {
    this.audioClip = audioClip;
  }

  /**
   * Notify all listeners that they may play the sound.
   * @param pl the list of plugin message listeners
   */
  public Object firePluginMessage(PluginListener pl) {
    if(pl instanceof SoundListener) {
      ((SoundListener)pl).playSound(audioClip);
    }
    return null;
  }
}
