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
import de.mud.jta.VisualPlugin;
import de.mud.jta.event.AppletListener;
import de.mud.jta.event.ConfigurationListener;

import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.applet.AppletContext;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StreamTokenizer;
import java.net.URL;
import java.util.Vector;

/**
 *
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: URLFilter.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class URLFilter extends Plugin
        implements FilterPlugin, VisualPlugin, Runnable {

  /** debugging level */
  private final static int debug = 0;

  /* contains the recognized protocols */
  protected Vector protocols = new Vector();

  protected JList urlList = new JList();
  protected JPanel urlPanel;
  protected JMenu urlMenu;

  protected PipedInputStream pin;
  protected PipedOutputStream pout;

  protected AppletContext context;


  /**
   * Create a new scripting plugin.
   */
  public URLFilter(PluginBus bus, final String id) {
    super(bus, id);

    urlPanel = new JPanel(new BorderLayout());
    urlList.setVisibleRowCount(4);
    urlList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    urlList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        showURL((String) ((JList) e.getSource()).getSelectedValue());
      }
    });
    urlPanel.add("Center", urlList);
    JPanel p = new JPanel(new GridLayout(3, 1));
    JButton b = new JButton("Clear List");
    b.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        urlCache.removeAllElements();
        urlList.removeAll();
      }
    });
    p.add(b);
    b = new JButton("Remove URL");
    b.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        String item = (String) urlList.getSelectedValue();
        if (item != null) {
          urlCache.removeElement(item);
          urlList.remove(urlList.getSelectedIndex());
        }
      }
    });
    p.add(b);
    b = new JButton("Show URL");
    b.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        String item = (String) urlList.getSelectedValue();
        if (item != null) showURL(item);
      }
    });
    p.add(b);
    urlPanel.add("East", p);

    bus.registerPluginListener(new AppletListener() {
      public void setApplet(JApplet applet) {
        context = applet.getAppletContext();
      }
    });

    bus.registerPluginListener(new ConfigurationListener() {
      public void setConfiguration(PluginConfig config) {
        String s;
        if ((s = config.getProperty("URLFilter", id, "protocols")) != null) {
          int old = -1, idx = s.indexOf(',');
          while (idx >= 0) {
            System.out.println("URLFilter: adding protocol '" +
                               s.substring(old + 1, idx) + "'");
            protocols.addElement(s.substring(old + 1, idx));
            old = idx;
            idx = s.indexOf(',', old + 1);
          }
          System.out.println("URLFilter: adding protocol '" +
                             s.substring(old + 1) + "'");
          protocols.addElement(s.substring(old + 1));
        } else {
          protocols.addElement("http");
          protocols.addElement("ftp");
          protocols.addElement("gopher");
          protocols.addElement("file");
        }
      }
    });

    // create the recognizer pipe
    pin = new PipedInputStream();
    pout = new PipedOutputStream();

    try {
      pout.connect(pin);
    } catch (IOException e) {
      System.err.println("URLFilter: error installing recognizer: " + e);
    }

    // start the recognizer
    Thread recognizer = new Thread(this);
    recognizer.start();
  }

  private Vector urlCache = new Vector();

  public void run() {
    try {
      StreamTokenizer st =
              new StreamTokenizer(new BufferedReader(new InputStreamReader(pin)));
      st.eolIsSignificant(true);
      st.slashSlashComments(false);
      st.slashStarComments(false);
      st.whitespaceChars(0, 31);
      st.ordinaryChar('"');
      st.ordinaryChar('<');
      st.ordinaryChar('>');
      st.ordinaryChar('/');
      st.ordinaryChar(':');

      int token;
      while ((token = st.nextToken()) != StreamTokenizer.TT_EOF) {
        if (token == StreamTokenizer.TT_WORD) {
          String word = st.sval.toLowerCase();

          // see if we have found a protocol
          if (protocols.contains(word)) {
            // check that the next chars are ":/"
            if (st.nextToken() == ':' && st.nextToken() == '/') {
              String url = word + ":/";
              // collect the test of the url
              while ((token = st.nextToken()) == StreamTokenizer.TT_WORD ||
                      token == '/')
                if (token == StreamTokenizer.TT_WORD)
                  url += st.sval;
                else
                  url += (char) token;

              // urls that end with a dot are usually wrong, so cut it off
              if (url.endsWith("."))
                url = url.substring(0, url.length() - 1);

              // check for duplicate urls by consulting the urlCache
              if (!urlCache.contains(url)) {
                urlCache.addElement(url);
                urlList.add(url, new JLabel(url));
                System.out.println("URLFilter: found \"" + url + "\"");
              }
            }
          }
        }
      }
    } catch (IOException e) {
      System.err.println("URLFilter: recognition aborted: " + e);
    }
  }

  /**
   * Show a URL if the applet context is available.
   * We may make it later able to run a web browser or use an HTML
   * component.
   * @param url the URL to display
   */
  protected void showURL(String url) {
    if (context == null) {
      System.err.println("URLFilter: no url-viewer available\n");
      return;
    }
    try {
      context.showDocument(new URL(url), "URLFilter");
    } catch (Exception e) {
      System.err.println("URLFilter: cannot load url: " + e);
    }
  }

  /** holds the data source for input and output */
  protected FilterPlugin source;

  /**
   * Set the filter source where we can read data from and where to
   * write the script answer to.
   * @param plugin the filter plugin we use as source
   */
  public void setFilterSource(FilterPlugin plugin) {
    source = plugin;
  }

  public FilterPlugin getFilterSource() {
    return source;
  }

  /**
   * Read an array of bytes from the back end and send it to the
   * url parser to see if it matches.
   * @param b the array where to read the bytes in
   * @return the amount of bytes actually read
   */
  public int read(byte[] b) throws IOException {
    int n = source.read(b);
    if (n > 0) pout.write(b, 0, n);
    return n;
  }

  public void write(byte[] b) throws IOException {
    source.write(b);
  }

  public JComponent getPluginVisual() {
    return urlPanel;
  }

  public JMenu getPluginMenu() {
    return urlMenu;
  }
}
