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
import de.mud.jta.PluginBus;
import de.mud.jta.PluginConfig;
import de.mud.jta.VisualPlugin;
import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.ReturnFocusListener;
import de.mud.jta.event.SocketRequest;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * The MudConnector (http://www.mudconnector.com) plugin. The plugin will
 * download a list of MUDs from a special list availabe at the url above
 * and the user can select the mud and connect to it. This usually requires
 * the relayd program to be run on the web server as this plugin tries to
 * establish connections to other hosts than the web server.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: MudConnector.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class MudConnector
        extends Plugin
        implements VisualPlugin, Runnable, ActionListener {

  /** debugging level */
  private final static int debug = 0;

  protected URL listURL = null;
  protected int step;
  protected Map mudList = null;
  protected JList mudListSelector = new JList();
  protected JTextField mudName, mudAddr, mudPort;
  protected JButton connect;
  protected JPanel mudListPanel;
  protected CardLayout layouter;
  protected ProgressBar progress;
  protected JLabel errorLabel;
  protected JMenu MCMenu;

  /**
   * Implementation of a progress bar to display the progress of
   * loading the mud list.
   */
  class ProgressBar extends JComponent {
    int max, current;
    String text;
    Dimension size = new Dimension(250, 20);
    Image backingStore;

    public void setMax(int max) {
      this.max = max;
    }

    public void update(Graphics g) {
      paint(g);
    }

    public void paint(Graphics g) {
      if (backingStore == null) {
        backingStore = createImage(getSize().width, getSize().height);
        redraw();
      }
      g.drawImage(backingStore, 0, 0, this);
    }

    private void redraw() {
      if (backingStore == null || text == null) return;
      Graphics g = backingStore.getGraphics();
      int width = (int) (((float) current / (float) max) * getSize().width);
      g.fill3DRect(0, 0, getSize().width, getSize().height, false);
      g.setColor(getBackground());
      g.fill3DRect(0, 0, width, getSize().height, true);
      g.setColor(getForeground());
      g.setXORMode(getBackground());
      String percent = "" + (current * 100 / (max > 0?max:1)) 
                     + "% / " + current + " of "+max;
      g.drawString(percent,
                   getSize().width / 2 -
                   getFontMetrics(getFont()).stringWidth(percent) / 2,
		   getSize().height / 2);
      g.drawString(text,
                   getSize().width / 2 -
                   getFontMetrics(getFont()).stringWidth(text) / 2,
                   getSize().height / 2 + 12);
      paint(getGraphics());
    }

    public void adjust(int value, String name) {
      if ((current = value) > max)
        current = max;
      text = name;
      if (((float) current / (float) step) == (int) (current / step))
        redraw();
    }

    public void setSize(int width, int height) {
      size = new Dimension(width, height);
    }

    public Dimension getPreferredSize() {
      return size;
    }

    public Dimension getMinimumSize() {
      return size;
    }
  }


  /**
   * Create the list plugin and get the url to the actual list.
   */
  public MudConnector(final PluginBus bus, final String id) {
    super(bus, id);

    bus.registerPluginListener(new ConfigurationListener() {
      public void setConfiguration(PluginConfig config) {
        String url =
                config.getProperty("MudConnector", id, "listURL");
        if (url != null) {
          try {
            listURL = new URL(url);
          } catch (Exception e) {
            MudConnector.this.error("" + e);
            errorLabel.setText("Error: " + e);
          }
        } else {
          MudConnector.this.error("no listURL specified");
          errorLabel.setText("Missing list URL");
          layouter.show(mudListPanel, "ERROR");
        }

        String sstep = config.getProperty("MudConnector", id, "step");

        try {
          step = Integer.parseInt(sstep);
        } catch (Exception e) {
          if (sstep != null)
            MudConnector.this.error("warning: " + sstep + " is not a number");
          step = 10;
        }
      }
    });

    bus.registerPluginListener(new ReturnFocusListener() {
      public void returnFocus() {
        setup();
      }
    });
    mudListPanel = new JPanel(layouter = new CardLayout()) {
      public void update(java.awt.Graphics g) {
        paint(g);
      }
    };

    mudListPanel.add("ERROR", errorLabel = new JLabel("Loading ..."));
    JPanel panel = new JPanel(new BorderLayout());
    panel.add("North", new JLabel("Loading mud list ... please wait"));
    panel.add("Center", progress = new ProgressBar());
    mudListPanel.add("PROGRESS", panel);
    panel = new JPanel(new BorderLayout());
    JScrollPane scrollPane = new JScrollPane(mudListSelector);
    panel.add("Center", scrollPane);
    mudListPanel.add("MUDLIST", panel);
    panel.add("East", panel = new JPanel(new GridLayout(3, 1)));
    panel.add(mudName = new JTextField(20));
    mudName.setEditable(false);
    JPanel apanel = new JPanel(new BorderLayout());
    apanel.add("Center", mudAddr = new JTextField(20));
    mudAddr.setEditable(false);
    apanel.add("East", mudPort = new JTextField(6));
    mudPort.setEditable(false);
    panel.add(apanel);
    panel.add(connect = new JButton("Connect"));

    connect.addActionListener(this);

    mudListSelector.setVisibleRowCount(3);
    mudListSelector.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent evt) {
        JList list = (JList) evt.getSource();
	list.ensureIndexIsVisible(list.getSelectedIndex());
        String item = (String) list.getSelectedValue();
        mudName.setText(item);
        Object mud[] = (Object[]) mudList.get(item);
        mudAddr.setText((String) mud[0]);
        mudPort.setText(((Integer) mud[1]).toString());
      }
    });

    layouter.show(mudListPanel, "PROGRESS");

    MCMenu = new JMenu("MudConnector");
  }

  private void setup() {
    if (mudList == null && listURL != null)
      (new Thread(this)).start();
  }

  public void run() {
    try {

      Map menuList = new HashMap();

      mudList = new HashMap();
      BufferedReader r =
              new BufferedReader(new InputStreamReader(listURL.openStream()));

      String line = r.readLine();
      int mudCount = 0;
      try {
        mudCount = Integer.parseInt(line);
      } catch (NumberFormatException nfe) {
        error("number of muds: " + nfe);
      }
      System.out.println("MudConnector: expecting " + mudCount + " mud entries");
      progress.setMax(mudCount);

      StreamTokenizer ts = new StreamTokenizer(r);
      ts.resetSyntax();
      ts.whitespaceChars(0, 9);
      ts.ordinaryChars(32, 255);
      ts.wordChars(32, 255);

      String name, host;
      Integer port;
      int token, counter = 0, idx = 0;

      while ((token = ts.nextToken()) != ts.TT_EOF) {
        name = ts.sval;

        if ((token = ts.nextToken()) != ts.TT_EOF) {
          if (token == ts.TT_EOL)
            error(name + ": unexpected end of line"
                  + ", missing host and port");
          host = ts.sval;
          port = new Integer(23);
          if ((token = ts.nextToken()) != ts.TT_EOF)
            try {
              if (token == ts.TT_EOL)
                error(name + ": default port 23");
              port = new Integer(ts.sval);
            } catch (NumberFormatException nfe) {
              error("port for " + name + ": " + nfe);
            }

          if (debug > 0)
            error(name + " [" + host + "," + port + "]");
          mudList.put(name, new Object[]{host, port, new Integer(idx++)});
          progress.adjust(++counter, name);
          mudListPanel.repaint();

	  String key = (""+name.charAt(0)).toUpperCase();
          JMenu subMenu = (JMenu) menuList.get(key);
          if (subMenu == null) {
            subMenu = new JMenu(key);
            MCMenu.add(subMenu);
            menuList.put(key, subMenu);
          }
          JMenuItem item = new JMenuItem(name);
          item.addActionListener(MudConnector.this);
          subMenu.add(item);
        }
        while (token != ts.TT_EOF && token != ts.TT_EOL)
          token = ts.nextToken();
      }
      List list = new ArrayList(mudList.keySet());
      Collections.sort(list);
      mudListSelector.setListData(list.toArray());
      System.out.println("MudConnector: found " + mudList.size() + " entries");
    } catch (Exception e) {
      error("error: " + e);
      errorLabel.setText("Error: " + e);
      layouter.show(mudListPanel, "ERROR");
    }
    layouter.show(mudListPanel, "MUDLIST");
  }

  public void actionPerformed(ActionEvent evt) {
    if (evt.getSource() instanceof MenuItem) {
      String item = evt.getActionCommand();
      int idx = ((Integer) ((Object[]) mudList.get(item))[2]).intValue();
      mudListSelector.setSelectedIndex(idx);
      mudName.setText(item);
      Object mud[] = (Object[]) mudList.get(item);
      mudAddr.setText((String) mud[0]);
      mudPort.setText(((Integer) mud[1]).toString());
    }

    String addr = mudAddr.getText();
    String port = mudPort.getText();
    if (addr != null) {
      bus.broadcast(new SocketRequest());
      if (port == null || port.length() <= 0)
        port = "23";
      bus.broadcast(new SocketRequest(addr, Integer.parseInt(port)));
    }
  }

  public JComponent getPluginVisual() {
    return mudListPanel;
  }

  public JMenu getPluginMenu() {
    return MCMenu;
  }
}
