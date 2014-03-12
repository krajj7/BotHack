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

import de.mud.jta.event.AppletRequest;
import de.mud.jta.event.FocusStatusListener;
import de.mud.jta.event.OnlineStatusListener;
import de.mud.jta.event.ReturnFocusRequest;
import de.mud.jta.event.SocketRequest;
import de.mud.jta.event.SoundListener;

import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.RootPaneContainer;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.PrintJob;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

/**
 * <B>JTA - Telnet/SSH for the JAVA(tm) platform: Applet</B><P>
 * This is the implementation of whole set of applications. It's modular
 * structure allows to configure the software to act either as a sophisticated
 * terminal emulation and/or, adding the network backend, as telnet
 * implementation. Additional modules provide features like scripting or an
 * improved graphical user interface.<P>
 * This software is written entirely in Java<SUP>tm</SUP>.<P>
 * This is the <I>Applet</I> implementation for the software. It initializes
 * the system and adds all needed components, such as the telnet backend and
 * the terminal front end.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: Applet.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Meissner
 */
public class Applet extends JApplet {

  private final static int debug = 0;

  private String frameTitle = null;
  private RootPaneContainer appletFrame;

  /** holds the defaults */
  private Properties options = new Properties();

  /** hold the common part of the jta */
  private Common pluginLoader;

  /** hold the host and port for our connection */
  private String host, port;

  /** disconnect on leave, this is to force applets to break the connection */
  private boolean disconnect = true;
  /** connect on startup, this is to force applets to connect on detach */
  private boolean connect = false;
  /** close the window (if it exists) after the connection is lost */
  private boolean disconnectCloseWindow = true;

  private Plugin focussedPlugin;
  private Clipboard clipboard;
  private boolean online = false;

  /**
   * Read all parameters from the applet configuration and
   * do initializations for the plugins and the applet.
   */
  public void init() {
    if (debug > 0) System.err.println("Applet: init()");
    if (pluginLoader == null) {
      try {
        options.load(Applet.class
                     .getResourceAsStream("/de/mud/jta/default.conf"));
      } catch (Exception e) {
        try {
          URL url = new URL(getCodeBase() + "default.conf");
          options.load(url.openStream());
        } catch (Exception e1) {
          System.err.println("jta: cannot load default.conf");
          System.err.println("jta: try extracting it from the jar file");
          System.err.println("jta: expected file here: "
                             + getCodeBase() + "default.conf");
        }
      }

      String value;

      // try to load the local configuration and merge it with the defaults
      if ((value = getParameter("config")) != null) {
        Properties appletParams = new Properties();
        URL url = null;
        try {
          url = new URL(value);
        } catch (Exception e) {
          try {
            url = new URL(getCodeBase() + value);
          } catch (Exception ce) {
            System.err.println("jta: could not find config file: " + ce);
          }
        }

        if (url != null) {
          try {
            appletParams.load(Applet.class.getResourceAsStream("/de/mud/jta/" + value));
            Enumeration ape = appletParams.keys();
            while (ape.hasMoreElements()) {
              String key = (String) ape.nextElement();
              options.put(key, appletParams.getProperty(key));
            }
          } catch (Exception e) {
            try {
              appletParams.load(url.openStream());
              Enumeration ape = appletParams.keys();
              while (ape.hasMoreElements()) {
                String key = (String) ape.nextElement();
                options.put(key, appletParams.getProperty(key));
              }
            } catch (Exception e2) {
              System.err.println("jta: could not load config file: " + e2);
            }
          }
        }
      }

      // see if there are parameters in the html to override properties
      parameterOverride(options);

      // configure the application and load all plugins
      pluginLoader = new Common(options);

      // set the host to our code base, no other hosts are allowed anyway
      host = options.getProperty("Socket.host");
      if (host == null)
        host = getCodeBase().getHost();
      port = options.getProperty("Socket.port");
      if (port == null)
        port = "23";

      if ((new Boolean(options.getProperty("Applet.connect"))
              .booleanValue()))
        connect = true;
      if (!(new Boolean(options.getProperty("Applet.disconnect"))
              .booleanValue()))
        disconnect = false;

      if (!(new Boolean(options.getProperty("Applet.disconnect.closeWindow"))
              .booleanValue()))
        disconnectCloseWindow = false;

      frameTitle = options.getProperty("Applet.detach.title");

      if ((new Boolean(options.getProperty("Applet.detach"))).booleanValue()) {
        if (frameTitle == null) {
          appletFrame = (RootPaneContainer)new JFrame("jta: " + host + (port.equals("23")?"":" " + port));
        } else {
          appletFrame = (RootPaneContainer)new JFrame(frameTitle);
        }
      } else {
        appletFrame = (RootPaneContainer)this;
      }
      appletFrame.getContentPane().setLayout(new BorderLayout());

      Map componentList = pluginLoader.getComponents();
      Iterator names = componentList.keySet().iterator();
      while (names.hasNext()) {
        String name = (String) names.next();
        Component c = (Component) componentList.get(name);
        if ((value = options.getProperty("layout." + name)) != null) {
          appletFrame.getContentPane().add(value, c);
        } else {
          System.err.println("jta: no layout property set for '" + name + "'");
          System.err.println("jta: ignoring '" + name + "'");
        }
      }

      pluginLoader.registerPluginListener(new SoundListener() {
        public void playSound(URL audioClip) {
          Applet.this.getAudioClip(audioClip).play();
        }
      });

      pluginLoader.broadcast(new AppletRequest(this));
      if (appletFrame != this) {
        final String startText = options.getProperty("Applet.detach.startText");
        final String stopText = options.getProperty("Applet.detach.stopText");
        final Button close = new Button();

        // this works for Netscape only!
        Vector privileges =
                Common.split(options.getProperty("Applet.Netscape.privilege"), ',');
        Class privilegeManager = null;
        Method enable = null;
        try {
          privilegeManager =
                  Class.forName("netscape.security.PrivilegeManager");
          enable = privilegeManager
                  .getMethod("enablePrivilege", new Class[]{String.class});
        } catch (Exception e) {
          System.err.println("Applet: This is not Netscape ...");
        }

        if (privilegeManager != null && enable != null && privileges != null)
          for (int i = 0; i < privileges.size(); i++)
            try {
              enable.invoke(privilegeManager,
                            new Object[]{privileges.elementAt(i)});
              System.out.println("Applet: access for '" +
                                 privileges.elementAt(i) + "' allowed");

            } catch (Exception e) {
              System.err.println("Applet: access for '" +
                                 privileges.elementAt(i) + "' denied");
            }

        // set up the clipboard
        try {
          clipboard = appletFrame.getContentPane().getToolkit().getSystemClipboard();
          System.err.println("Applet: acquired system clipboard: " + clipboard);
        } catch (Exception e) {
          System.err.println("Applet: system clipboard access denied: " +
                             ((e instanceof InvocationTargetException) ?
                              ((InvocationTargetException) e).getTargetException() : e));
          // e.printStackTrace();
        } finally {
          if (clipboard == null) {
            System.err.println("Applet: copy & paste only within the JTA");
            clipboard = new Clipboard("de.mud.jta.Main");
          }
        }

        if ((new Boolean(options.getProperty("Applet.detach.immediately"))
                .booleanValue())) {
          if ((new Boolean(options.getProperty("Applet.detach.fullscreen"))
                  .booleanValue()))
            ((JFrame) appletFrame)
                    .setSize(appletFrame.getContentPane().getToolkit().getScreenSize());
          else
            ((JFrame) appletFrame).pack();

          ((JFrame) appletFrame).show();
          pluginLoader.broadcast(new SocketRequest(host, Integer.parseInt(port)));
          pluginLoader.broadcast(new ReturnFocusRequest());
          close.setLabel(startText != null ? stopText : "Disconnect");
        } else
          close.setLabel(startText != null ? startText : "Connect");

        close.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            if (((JFrame) appletFrame).isVisible()) {
              pluginLoader.broadcast(new SocketRequest());
              ((JFrame) appletFrame).setVisible(false);
              close.setLabel(startText != null ? startText : "Connect");
            } else {
              if (frameTitle == null)
                ((JFrame) appletFrame)
                        .setTitle("jta: " + host + (port.equals("23")?"":" " + port));
              if ((new Boolean(options.getProperty("Applet.detach.fullscreen"))
                      .booleanValue()))
                ((JFrame) appletFrame)
                        .setSize(appletFrame.getContentPane().getToolkit().getScreenSize());
              else
                ((JFrame) appletFrame).pack();
              ((JFrame) appletFrame).show();
              if (port == null || port.length() <= 0)
		port = "23";
              getAppletContext().showStatus("Trying " + host + " " + port + " ...");
              pluginLoader.broadcast(new SocketRequest(host,
                                                       Integer.parseInt(port)));
              pluginLoader.broadcast(new ReturnFocusRequest());
              close.setLabel(stopText != null ? stopText : "Disconnect");
            }
          }
        });

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add("Center", close);

        // add a menu bar
        MenuBar mb = new MenuBar();
        Menu file = new Menu("File");
        file.setShortcut(new MenuShortcut(KeyEvent.VK_F, true));
        MenuItem tmp;
        file.add(tmp = new MenuItem("Connect"));
        tmp.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            pluginLoader.broadcast(new SocketRequest(host,
                                                     Integer.parseInt(port)));
          }
        });
        file.add(tmp = new MenuItem("Disconnect"));
        tmp.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            pluginLoader.broadcast(new SocketRequest());
          }
        });
        file.add(new MenuItem("-"));
        file.add(tmp = new MenuItem("Print"));
        tmp.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            if (pluginLoader.getComponents().get("Terminal") != null) {
              PrintJob printJob =
                      appletFrame.getContentPane().getToolkit()
                      .getPrintJob((JFrame) appletFrame, "JTA Terminal", null);
              ((Component) pluginLoader.getComponents().get("Terminal"))
                      .print(printJob.getGraphics());
              printJob.end();
            }
          }
        });
        file.add(new MenuItem("-"));
        file.add(tmp = new MenuItem("Exit"));
        tmp.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            ((JFrame) appletFrame).setVisible(false);
            pluginLoader.broadcast(new SocketRequest());
            close.setLabel(startText != null ? startText : "Connect");
          }
        });
        mb.add(file);

        Menu edit = new Menu("Edit");
        edit.setShortcut(new MenuShortcut(KeyEvent.VK_E, true));
        edit.add(tmp = new MenuItem("Copy"));
        tmp.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            if (debug > 2)
              System.err.println("Applet: copy: " + focussedPlugin);
            if (focussedPlugin instanceof VisualTransferPlugin)
              ((VisualTransferPlugin) focussedPlugin).copy(clipboard);
          }
        });
        edit.add(tmp = new MenuItem("Paste"));
        tmp.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent evt) {
            if (debug > 2)
              System.err.println("Applet: paste: " + focussedPlugin);
            if (focussedPlugin instanceof VisualTransferPlugin)
              ((VisualTransferPlugin) focussedPlugin).paste(clipboard);
          }
        });
        mb.add(edit);

        Map menuList = pluginLoader.getMenus();
        names = menuList.keySet().iterator();
        while (names.hasNext()) {
          String name = (String) names.next();
	  Object o = menuList.get(name);
          if (o instanceof Menu) mb.add((Menu) o);
        }

        Menu help = new Menu("Help");
        help.setShortcut(new MenuShortcut(KeyEvent.VK_HELP, true));
        help.add(tmp = new MenuItem("General"));
        tmp.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            Help.show(appletFrame.getContentPane(), options.getProperty("Help.url"));
          }
        });
        mb.setHelpMenu(help);

        // only add the menubar if the property is true
        if ((new Boolean(options.getProperty("Applet.detach.menuBar"))
                .booleanValue()))
          ((JFrame) appletFrame).setMenuBar(mb);

        // add window closing event handler
        try {
          ((JFrame) appletFrame).addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
              pluginLoader.broadcast(new SocketRequest());
              ((JFrame) appletFrame).setVisible(false);
              close.setLabel(startText != null ? startText : "Connect");
            }
          });
        } catch (Exception e) {
          System.err.println("Applet: could not set up Window event listener");
          System.err.println("Applet: you will not be able to close it");
        }

        pluginLoader.registerPluginListener(new OnlineStatusListener() {
          public void online() {
            if (debug > 0) System.err.println("Terminal: online");
            online = true;
            if (((JFrame) appletFrame).isVisible() == false)
              ((JFrame) appletFrame).setVisible(true);
          }

          public void offline() {
            if (debug > 0) System.err.println("Terminal: offline");
            online = false;
            if (disconnectCloseWindow) {
              ((JFrame) appletFrame).setVisible(false);
              close.setLabel(startText != null ? startText : "Connect");
            }
          }
        });

        // register a focus status listener, so we know when a plugin got focus
        pluginLoader.registerPluginListener(new FocusStatusListener() {
          public void pluginGainedFocus(Plugin plugin) {
            if (Applet.debug > 0)
              System.err.println("Applet: " + plugin + " got focus");
            focussedPlugin = plugin;
          }

          public void pluginLostFocus(Plugin plugin) {
            // we ignore the lost focus
            if (Applet.debug > 0)
              System.err.println("Applet: " + plugin + " lost focus");
          }
        });

      } else
      // if we have no external frame use this online status listener
        pluginLoader.registerPluginListener(new OnlineStatusListener() {
          public void online() {
            if (debug > 0) System.err.println("Terminal: online");
            online = true;
          }

          public void offline() {
            if (debug > 0) System.err.println("Terminal: offline");
            online = false;
          }
        });


    }
  }

  /**
   * Start the applet. Connect to the remote host.
   */
  public void start() {
    if (!online && (appletFrame == this || connect)) {
      if (debug > 0) System.err.println("start(" + host + ", " + port + ")");
      getAppletContext().showStatus("Trying " + host + " " + port + " ...");
      pluginLoader.broadcast(new SocketRequest(host, Integer.parseInt(port)));
      pluginLoader.broadcast(new ReturnFocusRequest());
    }
  }

  /**
   * Stop the applet and disconnect.
   */
  public void stop() {
    if (online && disconnect) {
      if (debug > 0) System.err.println("stop()");
      pluginLoader.broadcast(new SocketRequest());
    }
  }

  /**
   * Override any properties that are found in the configuration files
   * with possible values found as applet parameters.
   * @param options the loaded configuration file properties
   */
  private void parameterOverride(Properties options) {
    Enumeration e = options.keys();
    while (e.hasMoreElements()) {
      String key = (String) e.nextElement(), value = getParameter(key);
      if (value != null) {
        System.out.println("Applet: overriding value of " + key + " with " + value);
        // options.setProperty(key, value);
        options.put(key, value);
      }
    }
  }
}
