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

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;

/**
 * The plugin loader tries to load the plugin by name and returns a
 * corresponding plugin object. It takes care of connecting filter
 * plugins
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: PluginLoader.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class PluginLoader implements PluginBus {
  /** holds the current version id */
  public final static String ID = "$Id: PluginLoader.java 499 2005-09-29 08:24:54Z leo $";

  private final static int debug = 0;

  /** the path to standard plugins */
  private Vector PATH = null;

  /** holds all the filters */
  private List filter = new ArrayList();

  private Map plugins;

  /**
   * Create new plugin loader and set up with default plugin path.
   */
  public PluginLoader() {
    this(null);
  }

  /**
   * Create new plugin loader and set up with specified plugin path.
   * @param path the default search path for plugins
   */
  public PluginLoader(Vector path) {
    plugins = new HashMap();
    if (path == null) {
      PATH = new Vector();
      PATH.addElement("de.mud.jta.plugin");
    } else
      PATH = path;
  }

  /**
   * Add a new plugin to the system and register the plugin load as its
   * communication bus. If the plugin is a filter plugin and if it is
   * not the first filter the last added filter will be set as its filter
   * source.
   * @param name the string name of the plugin
   * @return the newly created plugin or null in case of an error
   */
  public Plugin addPlugin(String name, String id) {
    Plugin plugin = loadPlugin(name, id);

    // if it was not found, try without a path as a last resort
    if (plugin == null)
      plugin = loadPlugin(null, name, id);

    // nothing found, tell the user
    if (plugin == null) {
      System.err.println("plugin loader: plugin '" + name + "' was not found!");
      return null;
    }

    // configure the filter plugins
    if (plugin instanceof FilterPlugin) {
      if (filter.size() > 0)
        ((FilterPlugin) plugin)
                .setFilterSource((FilterPlugin) filter.get(filter.size() - 1));
      filter.add(plugin);
    }

    plugins.put(name+(id == null ? "" : "(" + id + ")"), plugin);
    return plugin;
  }

  /**
   * Replace a plugin with a new one, actually reloads the plugin.
   * @param name name of plugin to be replaced
   * @param id unique id
   * @return the newly loaded plugin
   */
  public Plugin replacePlugin(String name, String id) {
    Plugin plugin = loadPlugin(name, id);

    if(plugin != null) {
      Plugin oldPlugin = (Plugin)plugins.get(name + (id == null ? "" : "(" + id + ")"));

      if(filter.contains(oldPlugin)) {
        int index = filter.indexOf(oldPlugin);
        filter.set(index, plugin);
        ((FilterPlugin)plugin).setFilterSource(((FilterPlugin)oldPlugin).getFilterSource());
        if(index < filter.size() - 1) {
          ((FilterPlugin)filter.get(index + 1)).setFilterSource((FilterPlugin)plugin);
        }
      }
    }
    return plugin;
  }

  /**
   * Load a plugin by cycling through the plugin path.
   * @param name the class name of the plugin
   * @param id an id in case of multiple plugins of the same name
   * @return the loaded plugin or null if none was found
   */
  private Plugin loadPlugin(String name, String id) {
    Plugin plugin = null;

    // cycle through the PATH to load plugin
    Enumeration pathList = PATH.elements();
    while (pathList.hasMoreElements()) {
      String path = (String) pathList.nextElement();
      plugin = loadPlugin(path, name, id);
      if (plugin != null) {
        return plugin;
      }
    }
    plugin = loadPlugin(null, name, id);
    return plugin;
  }

  /**
   * Load a plugin using the specified path name and id.
   * @param path the package where the plugin can be found
   * @param name the class name of the plugin
   * @param id an id for multiple plugins of the same name
   * @return a loaded plugin or null if none was found
   */
  private Plugin loadPlugin(String path, String name, String id) {
    Plugin plugin = null;
    String fullClassName = (path == null) ? name : path + "." + name;

    // load the plugin by name and instantiate it
    try {
      Class c = Class.forName(fullClassName);
      Constructor cc = c.getConstructor(new Class[]{PluginBus.class,
                                                    String.class});
      plugin = (Plugin) cc.newInstance(new Object[]{this, id});
      return plugin;
    } catch (ClassNotFoundException ce) {
      if (debug > 0)
        System.err.println("plugin loader: plugin not found: " + fullClassName);
    } catch (Exception e) {
      System.err.println("plugin loader: can't load plugin: " + fullClassName);
      e.printStackTrace();
    }

    return null;

  }

  /** holds the plugin listener we serve */
  private Vector listener = new Vector();

  /**
   * Register a new plugin listener.
   */
  public void registerPluginListener(PluginListener l) {
    listener.addElement(l);
  }

  /**
   * Implementation of the plugin bus. Broadcast a message to all
   * listeners we know of. The message takes care that the right
   * methods are called in the  listeners.
   * @param message the plugin message to be sent
   * @return the answer to the sent message
   */
  public Object broadcast(PluginMessage message) {
    if (debug > 0) System.err.println("broadcast(" + message + ")");
    if (message == null || listener == null)
      return null;
    Enumeration e = listener.elements();
    Object res = null;
    while (res == null && e.hasMoreElements())
      res = message.firePluginMessage((PluginListener) e.nextElement());
    return res;
  }

  public Map getPlugins() {
    return plugins;
  }

}

