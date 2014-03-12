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
import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.SocketListener;
import de.mud.jta.event.SocketRequest;

import java.io.IOException;

/**
 * The timeout plugin looks at the incoming and outgoing data stream and
 * tries to close the connection gracefully if the timeout occured or if
 * not graceful exit command was configured simply closed the connection.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: Timeout.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class Timeout extends Plugin 
  implements FilterPlugin, SocketListener, Runnable {

  private final static int debug = 0;

  protected int timeout = 0;
  protected String timeoutCommand = null;
  protected String timeoutWarning = null;
  protected Thread timeoutThread = null;

  private PluginBus pluginBus;

  /**
   * Create the new timeout plugin.
   */
  public Timeout(final PluginBus bus, final String id) {
    super(bus, id);

    // register socket listener
    bus.registerPluginListener(this);

     bus.registerPluginListener(new ConfigurationListener() {
       public void setConfiguration(PluginConfig config) {
	 String tos = config.getProperty("Timeout", id, "seconds");
	 if(tos != null) {
	   try {
	     timeout = Integer.parseInt(tos);
           } catch(Exception e) {
	     Timeout.this.error("timeout ("+timeout+") "
	                       +"is not an integer, timeout disabled");
	   }
	   timeoutCommand = 
	     config.getProperty("Timeout", id, "command");
	   timeoutWarning = 
	     config.getProperty("Timeout", id, "warning");
	 }
       }
     });
     pluginBus = bus; 
  }

  /**
   * Sleep for the timeout beeing. The thread gets interrupted if data
   * is transmitted and will shutdown the connection as soon as the
   * timeout wakes up normally.
   */
  public void run() {
    boolean ok = false;

    // loop around until the thread is kicked down the stream ...
    while(timeoutThread != null) {
      try {
        ok = false;
        timeoutThread.sleep(1000*timeout);
      } catch(InterruptedException e) {
	ok = true;
      }

      // if the timeout finished sucessfully close the connection
      if(!ok) {
        error("data connection timeout, shutting down");

	// first try it gracefully by sending the configured exit command
	if(timeoutCommand != null) {
	  error("sending graceful exit command ...");
	  try {
	    write(timeoutCommand.getBytes());
	  } catch(IOException e) {
	    error("could not send exit command");
	  }
	  timeoutThread = null;
	  final Thread grace = new Thread(new Runnable() {
	    public void run() {
	      try {
                Thread.currentThread().sleep(1000*timeout); 
	        Timeout.this.pluginBus.broadcast(new SocketRequest());
              } catch(InterruptedException e) {
	        // ignore exception
	      }
	    }
	  });
	  grace.start();
	} else // if not graceful exit exists, be rude
	  bus.broadcast(new SocketRequest());
      }
    }
  }

  /** Start the timeout countdown. */
  public void connect(String host, int port) throws IOException {
    if(timeout > 0) {
      timeoutThread = new Thread(Timeout.this);
      timeoutThread.start();
    }
  }  

  /** Stop the timeout */ 
  public void disconnect() throws IOException {
    if(timeoutThread != null) {
      Thread tmp = timeoutThread;
      timeoutThread = null;
      tmp.interrupt();
    }
  }

  FilterPlugin source;

  public void setFilterSource(FilterPlugin plugin) {
    source = plugin;
  }

  public FilterPlugin getFilterSource() {
    return source;
  }

  public int read(byte[] b) throws IOException {
    int n = source.read(b);
    if(n > 0 && timeoutThread != null) timeoutThread.interrupt();
    return n;
  }

  public void write(byte[] b) throws IOException {
      source.write(b);
      if(timeoutThread != null) timeoutThread.interrupt();
  }
}
