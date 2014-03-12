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
package de.mud.jta;

/**
 * A plugin bus is used for communication between plugins. The interface
 * describes the broadcast method that should broad cast the message
 * to all plugins known and return an answer message immediatly.<P>
 * The functionality is just simuliar to a bus, but depends on the
 * actual implementation of the bus.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: PluginBus.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public interface PluginBus {
  /** Broadcast a plugin message to all listeners. */
  public Object broadcast(PluginMessage message);
  /** Register a plugin listener with this bus object */
  public void registerPluginListener(PluginListener listener);
}
