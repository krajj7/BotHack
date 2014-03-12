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

package de.mud.jta.plugin;

import de.mud.jta.FilterPlugin;
import de.mud.jta.Plugin;
import de.mud.jta.PluginBus;
import de.mud.jta.PluginConfig;
import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.OnlineStatus;
import de.mud.jta.event.SocketListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The socket plugin acts as the data source for networked operations.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: Socket.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class Socket extends Plugin
        implements FilterPlugin, SocketListener {

  private final static int debug = 0;

  protected java.net.Socket socket;
  protected InputStream in;
  protected OutputStream out;

  protected String relay = null;
  protected int relayPort = 31415;

  /**
   * Create a new socket plugin.
   */
  public Socket(final PluginBus bus, final String id) {
    super(bus, id);

    // register socket listener
    bus.registerPluginListener(this);

    bus.registerPluginListener(new ConfigurationListener() {
      public void setConfiguration(PluginConfig config) {
        if ((relay = config.getProperty("Socket", id, "relay"))
                != null)
          if (config.getProperty("Socket", id, "relayPort") != null)
            try {
              relayPort = Integer.parseInt(
                      config.getProperty("Socket", id, "relayPort"));
            } catch (NumberFormatException e) {
              Socket.this.error("relayPort is not a number");
            }
      }
    });
  }

  private String error = null;

  /**
   * Connect to the host and port passed. If the multi relayd (mrelayd) is
   * used to allow connections to any host and the Socket.relay property
   * is configured this method will connect to the relay first, send
   * off the string "relay host port\n" and then the real connection will
   * be published to be online.
   */
  public void connect(String host, int port) throws IOException {
    if (host == null) return;
    if (debug > 0) error("connect(" + host + "," + port + ")");
    try {
      // check the relay settings, this is for the mrelayd only!
      if (relay == null)
        socket = new java.net.Socket(host, port);
      else
        socket = new java.net.Socket(relay, relayPort);
      in = socket.getInputStream();
      out = socket.getOutputStream();
      // send the string to relay to the target host, port
      if (relay != null)
        write(("relay " + host + " " + port + "\n").getBytes());
    } catch (Exception e) {
      error = "Sorry, Could not connect to: "+host+" "+port + "\r\n" +
              "Reason: " + e + "\r\n\r\n";
      error("can't connect: " + e);
    }
    bus.broadcast(new OnlineStatus(true));
  }

  /** Disconnect the socket and close the connection. */
  public void disconnect() throws IOException {
    if (debug > 0) error("disconnect()");
    bus.broadcast(new OnlineStatus(false));
    if (socket != null) {
      socket.close();
      in = null;
      out = null;
    }
  }

  public void setFilterSource(FilterPlugin plugin) {
    // we do not have a source other than our socket
  }

  public FilterPlugin getFilterSource() {
    return null;
  }

  public int read(byte[] b) throws IOException {
    // send error messages upward
    if (error != null && error.length() > 0) {
      int n = error.length() < b.length ? error.length() : b.length;
      System.arraycopy(error.getBytes(), 0, b, 0, n);
      error = error.substring(n);
      return n;
    }

    if (in == null) {
      disconnect();
      return -1;
    }

    int n = in.read(b);
    if (n < 0) disconnect();
    return n;
  }

  public void write(byte[] b) throws IOException {
    if (out == null) return;
    try {
      out.write(b);
    } catch (IOException e) {
      disconnect();
    }
  }
}
