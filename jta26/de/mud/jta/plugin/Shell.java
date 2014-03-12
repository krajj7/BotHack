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

import de.mud.jta.Plugin;
import de.mud.jta.FilterPlugin;
import de.mud.jta.PluginBus;
import de.mud.jta.PluginConfig;
import de.mud.jta.event.LocalEchoRequest;
import de.mud.jta.event.SocketListener;
import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.OnlineStatus;

// import java.io.InputStream;
// import java.io.OutputStream;
import java.io.IOException;

/**
 * The shell plugin is the backend component for terminal emulation using
 * a shell. It provides the i/o streams of the shell as data source.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: Shell.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner, Pete Zaitcev
 */
public class Shell extends Plugin implements FilterPlugin {

  protected String shellCommand;

  private HandlerPTY pty;

  public Shell(final PluginBus bus, final String id) {
    super(bus, id);

    bus.registerPluginListener(new ConfigurationListener() {
      public void setConfiguration(PluginConfig cfg) {
        String tmp;
	if((tmp = cfg.getProperty("Shell", id, "command")) != null) {
	  shellCommand = tmp;
	  // System.out.println("Shell: Setting config " + tmp); // P3
        } else {
	  // System.out.println("Shell: Not setting config"); // P3
	  shellCommand = "/bin/sh";
        }
      }
    });

    bus.registerPluginListener(new SocketListener() {
      // we do actually ignore these parameters
      public void connect(String host, int port) {
        // XXX Fix this together with window size changes
        // String ttype = (String)bus.broadcast(new TerminalTypeRequest());
        // String ttype = getTerminalType();
        // if(ttype == null) ttype = "dumb";

	// XXX Add try around here to catch missing DLL/.so.
	pty = new HandlerPTY();

        if(pty.start(shellCommand) == 0) {
	  bus.broadcast(new OnlineStatus(true));
        } else {
	  bus.broadcast(new OnlineStatus(false));
        }
      }
      public void disconnect() {
        bus.broadcast(new OnlineStatus(false));
        pty = null;
      }
    });
  }

  public void setFilterSource(FilterPlugin plugin) {
    // we do not have a source other than our socket
  }

  public FilterPlugin getFilterSource() {
    return null;
  }

  public int read(byte[] b) throws IOException {
    if(pty == null) return 0;
    int ret = pty.read(b);
    if(ret <= 0) {
      throw new IOException("EOF on PTY");
    }
    return ret;
  }

  public void write(byte[] b) throws IOException {
    if(pty != null) pty.write(b);
  }
}
