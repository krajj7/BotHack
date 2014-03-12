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
import de.mud.jta.VisualPlugin;

import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.SetWindowSizeListener;
import de.mud.jta.event.OnlineStatusListener;
import de.mud.jta.event.TerminalTypeRequest;
import de.mud.jta.event.WindowSizeRequest;
import de.mud.jta.event.LocalEchoRequest;
import de.mud.jta.event.SocketRequest;

import de.mud.ssh.SshIO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.io.IOException;

/**
 * Secure Shell plugin for the JTA. This is a plugin
 * to be used instead of Telnet for secure remote terminal sessions over
 * insecure networks. 
 * Take a look at the package de.mud.ssh for further information
 * about ssh or look at the official ssh homepage:
 * <A HREF="http://www.ssh.org/">http://www.ssh.fi/</A>.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: SSH.java 513 2005-12-19 07:59:45Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class SSH extends Plugin implements FilterPlugin, VisualPlugin {

  protected FilterPlugin source;
  protected SshIO handler;

  protected String user, pass;

  private final static int debug = 0;

  private boolean auth = false;

  /**
   * Create a new ssh plugin.
   */
  public SSH(final PluginBus bus, final String id) {
    super(bus, id);

    // create a new telnet protocol handler
    handler = new SshIO() {
      /** get the current terminal type */
      public String getTerminalType() {
        return (String)bus.broadcast(new TerminalTypeRequest());
      }
      /** get the current window size */
      public Dimension getWindowSize() {
        return (Dimension)bus.broadcast(new WindowSizeRequest());
      }
      /** notify about local echo */
      public void setLocalEcho(boolean echo) {
        bus.broadcast(new LocalEchoRequest(echo));
      }
      /** write data to our back end */
      public void write(byte[] b) throws IOException {
        source.write(b);
      }
    };

    bus.registerPluginListener(new ConfigurationListener() {
      public void setConfiguration(PluginConfig config) {
        user = config.getProperty("SSH", id, "user");
	pass = config.getProperty("SSH", id, "password");
      }
    });

    bus.registerPluginListener(new SetWindowSizeListener() {
      public void setWindowSize(int columns, int rows) {
        try {
          handler.setWindowSize(columns,rows);
        } catch (java.io.IOException e) {
          System.err.println("IO Exception in set window size");
        }
      }
    });


    // reset the protocol handler just in case :-)
    bus.registerPluginListener(new OnlineStatusListener() {
      public void online() {
	if(pass == null) {

          final Frame frame = new Frame("SSH User Authentication");
          Panel panel = new Panel(new GridLayout(3,1));
	  panel.add(new Label("SSH Authorization required"));
	  frame.add("North", panel);
          panel = new Panel(new GridLayout(2,2));
	  final TextField login = new TextField(user, 10);
	  final TextField passw = new TextField(10);
	  login.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
	      passw.requestFocus();
	    }
	  });
	  passw.setEchoChar('*');
	  panel.add(new Label("User name")); panel.add(login);
	  panel.add(new Label("Password")); panel.add(passw);
	  frame.add("Center", panel);
	  panel = new Panel();
	  Button cancel = new Button("Cancel");
	  Button ok = new Button("Login");
	  ActionListener enter = new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
	      handler.setLogin(login.getText());
	      handler.setPassword(passw.getText());
	      frame.dispose();
	      auth = true;
	    }
	  };
	  ok.addActionListener(enter);
	  passw.addActionListener(enter);
	  cancel.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
	      frame.dispose();
	    }
	  });
	  panel.add(cancel);
	  panel.add(ok);
	  frame.add("South", panel);
  
	  frame.pack();
	  frame.show();
	  frame.setLocation(frame.getToolkit().getScreenSize().width/2 -
	                    frame.getSize().width/2, 
	                    frame.getToolkit().getScreenSize().height/2 -
	                    frame.getSize().height/2);
	  if(user != null) {
	    passw.requestFocus();
	  }
	} else {
	  error(user+":"+pass);
	  handler.setLogin(user);
	  handler.setPassword(pass);
	  auth = true;
	}
      }
      public void offline() {
        handler.disconnect();
	auth=false;
        bus.broadcast(new SocketRequest());
      }
    });
  }

  public void setFilterSource(FilterPlugin source) { 
    if(debug>0) System.err.println("ssh: connected to: "+source);
    this.source = source;
  }

  public FilterPlugin getFilterSource() {
    return source;
  }

  private byte buffer[];
  private int pos;

  /**
   * Read data from the backend and decrypt it. This is a buffering read
   * as the encrypted information is usually smaller than its decrypted
   * pendant. So it will not read from the backend as long as there is
   * data in the buffer.
   * @param b the buffer where to read the decrypted data in
   * @return the amount of bytes actually read.
   */
  public int read(byte[] b) throws IOException {
    // we don't want to read from the pipeline without authorization
    while(!auth) try {
      Thread.sleep(1000);
    } catch(InterruptedException e) {
      e.printStackTrace();
    }

    // Empty the buffer before we do anything else
    if(buffer != null) {
      int amount = ((buffer.length - pos) <= b.length) ? 
                      buffer.length - pos : b.length;
      System.arraycopy(buffer, pos, b, 0, amount);
      if(pos + amount < buffer.length) {
        pos += amount;
      } else 
        buffer = null;
      return amount;
    }
 
    // now that the buffer is empty let's read more data and decrypt it
    int n = source.read(b);
    if(n > 0) {
      byte[] tmp = new byte[n];
      System.arraycopy(b, 0, tmp, 0, n);
      pos = 0;
      buffer = handler.handleSSH(tmp);

      if(debug > 0 && buffer != null && buffer.length > 0)
        System.err.println("ssh: "+buffer);

      if(buffer != null && buffer.length > 0) {
        if(debug > 0) 
	  System.err.println("ssh: incoming="+n+" now="+buffer.length);
	int amount = buffer.length <= b.length ?  buffer.length : b.length;
        System.arraycopy(buffer, 0, b, 0, amount);
	pos = n = amount;
	if(amount == buffer.length) {
	  buffer = null;
	  pos = 0;
	}
      } else
        return 0;
    }
    return n;
  }

  /**
   * Write data to the back end. This hands the data over to the ssh
   * protocol handler who encrypts the information and writes it to
   * the actual back end pipe.
   * @param b the unencrypted data to be encrypted and sent
   */
  public void write(byte[] b) throws IOException {
    // no write until authorization is done
    if(!auth) return;
    for (int i=0;i<b.length;i++) {
    	switch (b[i]) { 
	case 10: /* \n -> \r */
		b[i] = 13;
		break;
	}
    }
    handler.sendData(new String(b));
  }

  public JComponent getPluginVisual() {
    return null;
  }

  public JMenu getPluginMenu() {
    return null;
  }
}
