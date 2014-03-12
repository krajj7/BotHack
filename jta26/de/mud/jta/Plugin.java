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
 * Plugin base class for the JTA. A plugin is a component
 * for the PluginBus and may occur several times. If we have more than one
 * plugin of the same type the protected value id contains the unique plugin
 * id as configured in the configuration.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: Plugin.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class Plugin {
  /** holds the plugin bus used for communication between plugins */
  protected PluginBus bus;
  /**
   * in case we have several plugins of the same type this contains their
   * unique id
   */
  protected String id;

  /**
   * Create a new plugin and set the plugin bus used by this plugin and
   * the unique id. The unique id may be null if there is only one plugin
   * used by the system.
   * @param bus the plugin bus
   * @param id the unique plugin id
   */
  public Plugin(PluginBus bus, String id) {
    this.bus = bus;
    this.id = id;
  }

  /**
   * Return identifier for this plugin.
   * @return id string
   */
  public String getId() {
    return id;
  }

  /**
   * Print an error message to stderr prepending the plugin name. This method
   * is public due to compatibility with Java 1.1
   * @param msg the error message
   */
  public void error(String msg) {
    String name = getClass().toString();
    name = name.substring(name.lastIndexOf('.') + 1);
    System.err.println(name + (id != null ? "(" + id + ")" : "") + ": " + msg);
  }
}
