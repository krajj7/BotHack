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

import de.mud.jta.event.ConfigurationRequest;

import javax.swing.JComponent;
import javax.swing.JMenu;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

/**
 * The common part of the <B>JTA - Telnet/SSH for the JAVA(tm) platform</B>
 * is handled here. Mainly this includes the loading of the plugins and
 * the screen setup of the visual plugins.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: Common.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Meissner
 */
public class Common extends PluginLoader {

  public final static String DEFAULT_PATH = "de.mud.jta.plugin";

  public Common(Properties config) {
    // configure the plugin path
    super(getPluginPath(config.getProperty("pluginPath")));

    System.out.println("** JTA - Telnet/SSH for the JAVA(tm) platform");
    System.out.println("** Version 2.6 for Java 2+");
    System.out.println("** Copyright (c) 1996-2005 Matthias L. Jugel, "
                       + "Marcus Meissner");

    try {
      Version build =
              (Version) Class.forName("de.mud.jta.Build").newInstance();
      System.out.println("** Build: " + build.getDate());
    } catch (Exception e) {
      System.out.println("** Build: patched or selfmade, no date");
      System.err.println(e);
    }

    Vector names = split(config.getProperty("plugins"), ',');
    if (names == null) {
      System.err.println("jta: no plugins found! aborting ...");
      return;
    }

    Enumeration e = names.elements();
    while (e.hasMoreElements()) {
      String name = (String) e.nextElement();
      String id = null;
      int idx;
      if ((idx = name.indexOf("(")) > 1) {
        if (name.indexOf(")", idx) > idx)
          id = name.substring(idx + 1, name.indexOf(")", idx));
        else
          System.err.println("jta: missing ')' for plugin '" + name + "'");
        name = name.substring(0, idx);
      }
      System.out.println("jta: loading plugin '" + name + "'"
                         + (id != null && id.length() > 0 ?
                            ", ID: '" + id + "'" : ""));
      Plugin plugin = addPlugin(name, id);
      if (plugin == null) {
        System.err.println("jta: ignoring plugin '" + name + "'"
                           + (id != null && id.length() > 0 ?
                              ", ID: '" + id + "'" : ""));
        continue;
      }
    }

    broadcast(new ConfigurationRequest(new PluginConfig(config)));
  }

  /**
   * Get the list of visual components currently registered.
   * @return a map of components
   */

  public Map getComponents() {
    Map plugins = getPlugins();
    Iterator pluginIt = plugins.keySet().iterator();
    Map components = new HashMap();
    while (pluginIt.hasNext()) {
      String name = (String) pluginIt.next();
      Plugin plugin = (Plugin) plugins.get(name);
      if (plugin instanceof VisualPlugin) {
        JComponent c = ((VisualPlugin) plugin).getPluginVisual();
        if (c != null) {
          String id = plugin.getId();
          components.put(name + (id != null ? "(" + id + ")" : ""), c);
        }
      }
    }
    return components;
  }

  public Map getMenus() {
    Map plugins = getPlugins();
    Iterator pluginIt = plugins.keySet().iterator();
    Map menus = new HashMap();
    while (pluginIt.hasNext()) {
      String name = (String) pluginIt.next();
      Plugin plugin = (Plugin) plugins.get(name);
      if (plugin instanceof VisualPlugin) {
        JMenu menu = ((VisualPlugin) plugin).getPluginMenu();
        if (menu != null) {
          String id = plugin.getId();
          menus.put(name + (id != null ? "(" + id + ")" : ""), menu);
        }
      }
    }
    return menus;
  }

  /**
   * Convert the plugin path from a separated string list to a Vector.
   * @param path the string path
   * @return a vector containing the path
   */
  private static Vector getPluginPath(String path) {
    if (path == null)
      path = DEFAULT_PATH;
    return split(path, ':');
  }

  /**
   * Split up comma separated lists of strings. This is quite strict, no
   * whitespace characters are allowed.
   * @param s the string to be split up
   * @return an array of strings
   */
  public static Vector split(String s, char separator) {
    if (s == null) return null;
    Vector v = new Vector();
    int old = -1, idx = s.indexOf(separator);
    while (idx >= 0) {
      v.addElement(s.substring(old + 1, idx));
      old = idx;
      idx = s.indexOf(separator, old + 1);
    }
    v.addElement(s.substring(old + 1));
    return v;
  }
}
