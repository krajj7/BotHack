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

import de.mud.jta.PluginListener;

/**
 * This is the interface for a terminal type listener. It should return
 * the terminal type id as a string. Valid terminal types include
 * VT52, VT100, VT200, VT220, VT320, ANSI etc.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: TerminalTypeListener.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public interface TerminalTypeListener extends PluginListener {
  /** Return the terminal type string */
  public String getTerminalType();
}
