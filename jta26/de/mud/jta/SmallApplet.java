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

import de.mud.telnet.TelnetProtocolHandler;
import de.mud.terminal.SwingTerminal;
import de.mud.terminal.vt320;
import de.mud.terminal.SwingTerminal;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * <B>Small Telnet Applet implementation</B><P>
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: SmallApplet.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class SmallApplet extends java.applet.Applet implements Runnable {

  private final static int debug = 0;

  /** hold the host and port for our connection */
  private String host, port;

  /** hold the socket */
  private Socket socket;
  private InputStream is;
  private OutputStream os;

  private Thread reader;

  /** the terminal */
  private vt320 emulation;
  private SwingTerminal terminal;

  /** the telnet protocol handler */
  private TelnetProtocolHandler telnet;

  private boolean localecho = false;

  /**
   * Read all parameters from the applet configuration and
   * do initializations for the plugins and the applet.
   */
  public void init() {
    if (debug > 0) System.err.println("jta: init()");

    host = getParameter("host");
    port = getParameter("port");


    // we now create a new terminal that is used for the system
    // if you want to configure it please refer to the api docs
    emulation = new vt320() {
      /** before sending data transform it using telnet (which is sending it) */
      public void write(byte[] b) {
        try {
          if (localecho)
            emulation.putString(new String(b));
          telnet.transpose(b);
        } catch (IOException e) {
          System.err.println("jta: error sending data: " + e);
        }
      }
    };

    terminal = new SwingTerminal(emulation);

    // put terminal into the applet
    setLayout(new BorderLayout());
    add("Center", terminal);

    // then we create the actual telnet protocol handler that will negotiate
    // incoming data and transpose outgoing (see above)
    telnet = new TelnetProtocolHandler() {
      /** get the current terminal type */
      public String getTerminalType() {
        return emulation.getTerminalID();
      }

      /** get the current window size */
      public Dimension getWindowSize() {
        return new Dimension(emulation.getColumns(), emulation.getRows());
      }

      /** notify about local echo */
      public void setLocalEcho(boolean echo) {
        localecho = true;
      }

      /** notify about EOR end of record */
      public void notifyEndOfRecord() {
        // only used when EOR needed, like for line mode
      }

      /** write data to our back end */
      public void write(byte[] b) throws IOException {
        os.write(b);
      }
    };
  }

  boolean running = false;

  /**
   * Start the applet. Connect to the remote host.
   */
  public void start() {
    if (debug > 0)
      System.err.println("jta: start()");
    // disconnect if we are already connected
    if (socket != null) stop();

    try {
      // open new socket and get streams
      socket = new Socket(host, Integer.parseInt(port));
      is = socket.getInputStream();
      os = socket.getOutputStream();

      reader = new Thread(this);
      running = true;
      reader.start();

    } catch (Exception e) {
      System.err.println("jta: error connecting: " + e);
      stop();
    }
  }

  /**
   * Stop the applet and disconnect.
   */
  public void stop() {
    if (debug > 0)
      System.err.println("jta: stop()");
    // when applet stops, disconnect
    if (socket != null) {
      try {
        socket.close();
      } catch (Exception e) {
        System.err.println("jta: could not cleanly disconnect: " + e);
      }
      socket = null;
      try {
        running = false;
      } catch (Exception e) {
        // ignore
      }
      reader = null;
    }
  }

  /**
   * Continuously read from remote host and display the data on screen.
   */
  public void run() {
    if (debug > 0)
      System.err.println("jta: run()");
    byte[] b = new byte[256];
    int n = 0;
    while (running && n >= 0)
      try {
        do {
          n = telnet.negotiate(b);
          if (debug > 0 && n > 0)
            System.err.println("jta: \"" + (new String(b, 0, n)) + "\"");
          if (n > 0) emulation.putString(new String(b, 0, n));
        } while (running && n > 0);
        n = is.read(b);
        telnet.inputfeed(b, n);
      } catch (IOException e) {
        stop();
        break;
      }
  }

  public void update(Graphics g) {
    paint(g);
  }
}
