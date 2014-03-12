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
import de.mud.jta.VisualTransferPlugin;
import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.FocusStatus;
import de.mud.jta.event.LocalEchoListener;
import de.mud.jta.event.OnlineStatusListener;
import de.mud.jta.event.ReturnFocusListener;
import de.mud.jta.event.SoundRequest;
import de.mud.jta.event.TelnetCommandRequest;
import de.mud.jta.event.SetWindowSizeRequest;
import de.mud.jta.event.TerminalTypeListener;
import de.mud.jta.event.WindowSizeListener;
import de.mud.terminal.vt320;
import de.mud.terminal.SwingTerminal;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;

/**
 * The terminal plugin represents the actual terminal where the
 * data will be displayed and the gets the keyboard input to sent
 * back to the remote host.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: Terminal.java 510 2005-10-28 06:46:44Z marcus $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public class Terminal extends Plugin
        implements FilterPlugin, VisualTransferPlugin, ClipboardOwner, Runnable {

  private final static boolean personalJava = false;

  private final static int debug = 0;

  /** holds the actual terminal emulation */
  protected SwingTerminal terminal;
  protected vt320 emulation;

  /**
   * The default encoding is ISO 8859-1 (western).
   * However, as you see the value is set to latin1 which is a value that
   * is not even documented and thus incorrect, but it forces the default
   * behaviour for western encodings. The correct value does not work in
   * most available browsers.
   */
  protected String encoding = "latin1"; // "ISO8859_1";
  /** if we have a url to an audioclip use it as ping */
  protected SoundRequest audioBeep = null;

  /** the terminal panel that is displayed on-screen */
  protected JPanel tPanel;

  /** holds the terminal menu */
  protected JMenu menu;

  private Thread reader = null;

  private Hashtable colors = new Hashtable();

  private boolean localecho_overridden = false;

  /** Access to the system clipboard */
  private Clipboard clipboard = null;

  private Color codeToColor(String code) {
    if (colors.get(code) != null)
      return (Color) colors.get(code);
    else
      try {
        if (Color.getColor(code) != null)
          return Color.getColor(code);
        else
          return Color.decode(code);
      } catch (Exception e) {
	try {
          // try one last time
	  return Color.decode(code);
        } catch(Exception ex) {
	  // ignore
        }
        error("ignoring unknown color code: " + code);
      }
    return null;
  }

  /**
   * Create a new terminal plugin and initialize the terminal emulation.
   */
  public Terminal(final PluginBus bus, final String id) {
    super(bus, id);

    // create the terminal emulation
    emulation = new vt320() {
      public void write(byte[] b) {
        try {
          Terminal.this.write(b);
        } catch (IOException e) {
          reader = null;
        }
      }
      // provide audio feedback if that is configured
      public void beep() {
        if (audioBeep != null) bus.broadcast(audioBeep);
      }

      public void sendTelnetCommand(byte cmd) {
        bus.broadcast(new TelnetCommandRequest(cmd));
      }

      public void setWindowSize(int c, int r) {
        bus.broadcast(new SetWindowSizeRequest(c,r));
      }
    };

    // create terminal
    terminal = new SwingTerminal(emulation);

    // initialize colors
    colors.put("black", Color.black);
    colors.put("red", Color.red);
    colors.put("green", Color.green);
    colors.put("yellow", Color.yellow);
    colors.put("blue", Color.blue);
    colors.put("magenta", Color.magenta);
    colors.put("orange", Color.orange);
    colors.put("pink", Color.pink);
    colors.put("cyan", Color.cyan);
    colors.put("white", Color.white);
    colors.put("gray", Color.gray);
    colors.put("darkgray", Color.darkGray);

    if (!personalJava) {

      menu = new JMenu("Terminal");
      JMenuItem item;

      JMenu fgm = new JMenu("Foreground");
      JMenu bgm = new JMenu("Background");
      Enumeration cols = colors.keys();
      ActionListener fgl = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          terminal.setForeground((Color) colors.get(e.getActionCommand()));
          tPanel.repaint();
        }
      };
      ActionListener bgl = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          terminal.setBackground((Color) colors.get(e.getActionCommand()));
          tPanel.repaint();
        }
      };
      while (cols.hasMoreElements()) {
        String color = (String) cols.nextElement();
        fgm.add(item = new JMenuItem(color));
        item.addActionListener(fgl);
        bgm.add(item = new JMenuItem(color));
        item.addActionListener(bgl);
      }
      menu.add(fgm);
      menu.add(bgm);

      menu.add(item = new JMenuItem("Smaller Font"));
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Font font = terminal.getFont();
          terminal.setFont(new Font(font.getName(),
                                    font.getStyle(), font.getSize() - 1));
          if (tPanel.getParent() != null) {
            Container parent = tPanel;
            do {
              parent = parent.getParent();
            } while(parent != null && !(parent instanceof JFrame));

            if (parent instanceof JFrame)
              ((java.awt.Frame) parent).pack();
            tPanel.getParent().doLayout();
            tPanel.getParent().validate();
          }
        }
      });
      menu.add(item = new JMenuItem("Larger Font"));
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          Font font = terminal.getFont();
          terminal.setFont(new Font(font.getName(),
                                    font.getStyle(), font.getSize() + 1));
          if (tPanel.getParent() != null) {
            Container parent = tPanel;
            do {
              parent = parent.getParent();
            } while(parent != null && !(parent instanceof JFrame));

            if (parent instanceof JFrame)
              ((java.awt.Frame) parent).pack();
            tPanel.getParent().doLayout();
            tPanel.getParent().validate();
          }
        }
      });
      menu.add(item = new JMenuItem("Buffer +50"));
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          emulation.setBufferSize(emulation.getBufferSize() + 50);
        }
      });
      menu.add(item = new JMenuItem("Buffer -50"));
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          emulation.setBufferSize(emulation.getBufferSize() - 50);
        }
      });
      menu.addSeparator();
      menu.add(item = new JMenuItem("Reset Terminal"));
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          emulation.reset();
        }
      });

    } // !personalJava



    // the container for our terminal must use double-buffering
    // or at least reduce flicker by overloading update()
    tPanel = new JPanel(new BorderLayout()) {
      // reduce flickering
      public void update(java.awt.Graphics g) {
        paint(g);
      }

      // we don't want to print the container, just the terminal contents
      public void print(java.awt.Graphics g) {
        terminal.print(g);
      }
    };
    tPanel.add("Center", terminal);

    terminal.addFocusListener(new FocusListener() {
      public void focusGained(FocusEvent evt) {
        if (debug > 0)
          System.err.println("Terminal: focus gained");
        terminal.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        bus.broadcast(new FocusStatus(Terminal.this, evt));
      }

      public void focusLost(FocusEvent evt) {
        if (debug > 0)
          System.err.println("Terminal: focus lost");
        terminal.setCursor(Cursor.getDefaultCursor());
        bus.broadcast(new FocusStatus(Terminal.this, evt));
      }
    });

    // get a reference to the system clipboard.  
    try {
      clipboard = tPanel.getToolkit().getSystemClipboard();
      System.out.println("Got the clipboard reference ok - copy & paste enabled");
    } catch(Exception ex) {
      System.out.println("Failed to get clipboard - copy and paste will not work");
      /* ex.printStackTrace(); */
    }
    
    //*******************************
    //code to handle copy and paste
    //from embeded terminal
    //*******************************
    terminal.addMouseListener(new MouseListener() {
      public void mouseClicked(MouseEvent me) {
        //make sure it only does the paste on button2(right mouse)
        if (me.getButton() == me.BUTTON3 && clipboard != null) {
          paste(clipboard);
        }
      }
      public void mouseExited(MouseEvent arg0) {}
      public void mousePressed(MouseEvent arg0) {
        // System.out.println(">>>>MOUSE pressed");
      }
      public void mouseReleased(MouseEvent me) {
        //make sure it only does the copy on button 1 (left mouse)
        //System.out.println(">>>>MOUSE RELEASED");
        if (me.getButton() == me.BUTTON1 && clipboard != null) {
          String selection = terminal.getSelection();
          // System.out.println(">>>>SELECTION = " + selection);
          if (selection != null && selection.trim().length() > 0){
            copy(clipboard);
          }
        } else {
          //not left mouse
          // System.out.println("NOT BUTTON 1(left mouse): " + me.getButton());
        }
      }
      public void mouseEntered(MouseEvent arg0) {}
    });

    // register an online status listener
    bus.registerPluginListener(new OnlineStatusListener() {
      public void online() {
        if (debug > 0) System.err.println("Terminal: online " + reader);
        if (reader == null) {
          reader = new Thread(Terminal.this);
          reader.start();
        }
      }

      public void offline() {
        if (debug > 0) System.err.println("Terminal: offline");
        if (reader != null)
          reader = null;
      }
    });

    bus.registerPluginListener(new TerminalTypeListener() {
      public String getTerminalType() {
        return emulation.getTerminalID();
      }
    });

    bus.registerPluginListener(new WindowSizeListener() {
      public Dimension getWindowSize() {
        return new Dimension(emulation.getColumns(), emulation.getRows());
      }
    });

    bus.registerPluginListener(new LocalEchoListener() {
      public void setLocalEcho(boolean echo) {
        if (!localecho_overridden)
          emulation.setLocalEcho(echo);
      }
    });

    bus.registerPluginListener(new ConfigurationListener() {
      public void setConfiguration(PluginConfig config) {
        configure(config);
      }
    });

    bus.registerPluginListener(new ReturnFocusListener() {
      public void returnFocus() {
        terminal.requestFocus();
      }
    });
  }

  private void configure(PluginConfig cfg) {
    String tmp;
    if ((tmp = cfg.getProperty("Terminal", id, "foreground")) != null)
      terminal.setForeground(Color.decode(tmp));
    if ((tmp = cfg.getProperty("Terminal", id, "background")) != null)
      terminal.setBackground(Color.decode(tmp));


    if ((tmp = cfg.getProperty("Terminal", id, "print.color")) != null)
      try {
        terminal.setColorPrinting(Boolean.valueOf(tmp).booleanValue());
      } catch (Exception e) {
        error("Terminal.color.print: must be either true or false, not " + tmp);
      }

    System.err.print("colorSet: ");
    if ((tmp = cfg.getProperty("Terminal", id, "colorSet")) != null) {
      System.err.println(tmp);
      Properties colorSet = new Properties();

      try {
        colorSet.load(getClass().getResourceAsStream(tmp));
      } catch (Exception e) {
        try {
          colorSet.load(new URL(tmp).openStream());
        } catch (Exception ue) {
          error("cannot find colorSet: " + tmp);
          error("resource access failed: " + e);
          error("URL access failed: " + ue);
          colorSet = null;
        }
      }

      if (colorSet != null) {
        Color set[] = terminal.getColorSet();
        Color color = null;
        for (int i = 0; i < 8; i++) {
          if ((tmp = colorSet.getProperty("color" + i)) != null &&
                  (color = codeToColor(tmp)) != null) {
            set[i] = color;
          }
        }
        // special color for bold
        if ((tmp = colorSet.getProperty("bold")) != null &&
                (color = codeToColor(tmp)) != null) {
          set[SwingTerminal.COLOR_BOLD] = color;
        }
        // special color for invert
        if ((tmp = colorSet.getProperty("invert")) != null &&
                (color = codeToColor(tmp)) != null) {
          set[SwingTerminal.COLOR_INVERT] = color;
        }
        terminal.setColorSet(set);
      }
    }

    String cFG = cfg.getProperty("Terminal", id, "cursor.foreground");
    String cBG = cfg.getProperty("Terminal", id, "cursor.background");
    if (cFG != null || cBG != null)
      try {
        Color fg = (cFG == null ?
                terminal.getBackground() :
                (Color.getColor(cFG) != null ? Color.getColor(cFG):Color.decode(cFG)));
        Color bg = (cBG == null ?
                terminal.getForeground() :
                (Color.getColor(cBG) != null ? Color.getColor(cBG):Color.decode(cBG)));
        terminal.setCursorColors(fg, bg);
      } catch (Exception e) {
        error("ignoring unknown cursor color code: " + tmp);
      }

    if ((tmp = cfg.getProperty("Terminal", id, "border")) != null) {
      String size = tmp;
      boolean raised = false;
      if ((tmp = cfg.getProperty("Terminal", id, "borderRaised")) != null)
        raised = Boolean.valueOf(tmp).booleanValue();
      terminal.setBorder(Integer.parseInt(size), raised);
    }

    if ((tmp = cfg.getProperty("Terminal", id, "localecho")) != null) {
      emulation.setLocalEcho(Boolean.valueOf(tmp).booleanValue());
      localecho_overridden = true;
    }

    if ((tmp = cfg.getProperty("Terminal", id, "scrollBar")) != null &&
            !personalJava) {
      String direction = tmp;
      if (!direction.equals("none")) {
        if (!direction.equals("East") && !direction.equals("West"))
          direction = "East";
        JScrollBar scrollBar = new JScrollBar();
        tPanel.add(direction, scrollBar);
        terminal.setScrollbar(scrollBar);
      }
    }

    if ((tmp = cfg.getProperty("Terminal", id, "id")) != null)
      emulation.setTerminalID(tmp);

    if ((tmp = cfg.getProperty("Terminal", id, "answerback")) != null)
      emulation.setAnswerBack(tmp);

    if ((tmp = cfg.getProperty("Terminal", id, "buffer")) != null)
      emulation.setBufferSize(Integer.parseInt(tmp));

    if ((tmp = cfg.getProperty("Terminal", id, "size")) != null)
      try {
        int idx = tmp.indexOf(',');
        int width = Integer.parseInt(tmp.substring(1, idx).trim());
        int height = Integer.parseInt(tmp.substring(idx + 1, tmp.length() - 1).trim());
        emulation.setScreenSize(width, height, false);
      } catch (Exception e) {
        error("screen size is wrong: " + tmp);
        error("error: " + e);
      }

    if ((tmp = cfg.getProperty("Terminal", id, "resize")) != null)
      if (tmp.equals("font"))
        terminal.setResizeStrategy(SwingTerminal.RESIZE_FONT);
      else if (tmp.equals("screen"))
        terminal.setResizeStrategy(SwingTerminal.RESIZE_SCREEN);
      else
        terminal.setResizeStrategy(SwingTerminal.RESIZE_NONE);


    if ((tmp = cfg.getProperty("Terminal", id, "font")) != null) {
      String font = tmp;
      int style = Font.PLAIN, fsize = 12;
      if ((tmp = cfg.getProperty("Terminal", id, "fontSize")) != null)
        fsize = Integer.parseInt(tmp);
      String fontStyle = cfg.getProperty("Terminal", id, "fontStyle");
      if (fontStyle == null || fontStyle.equals("plain"))
        style = Font.PLAIN;
      else if (fontStyle.equals("bold"))
        style = Font.BOLD;
      else if (fontStyle.equals("italic"))
        style = Font.ITALIC;
      else if (fontStyle.equals("bold+italic"))
        style = Font.BOLD | Font.ITALIC;
      terminal.setFont(new Font(font, style, fsize));
    }

    if ((tmp = cfg.getProperty("Terminal", id, "keyCodes")) != null) {
      Properties keyCodes = new Properties();

      try {
        keyCodes.load(getClass().getResourceAsStream(tmp));
      } catch (Exception e) {
        try {
          keyCodes.load(new URL(tmp).openStream());
        } catch (Exception ue) {
          error("cannot find keyCodes: " + tmp);
          error("resource access failed: " + e);
          error("URL access failed: " + ue);
          keyCodes = null;
        }
      }

      // set the key codes if we got the properties
      if (keyCodes != null)
        emulation.setKeyCodes(keyCodes);
    }

    if ((tmp = cfg.getProperty("Terminal", id, "VMS")) != null)
      emulation.setVMS((Boolean.valueOf(tmp)).booleanValue());
    if ((tmp = cfg.getProperty("Terminal", id, "IBM")) != null)
      emulation.setIBMCharset((Boolean.valueOf(tmp)).booleanValue());
    if ((tmp = cfg.getProperty("Terminal", id, "encoding")) != null)
      encoding = tmp;

    if ((tmp = cfg.getProperty("Terminal", id, "beep")) != null)
      try {
        audioBeep = new SoundRequest(new URL(tmp));
      } catch (MalformedURLException e) {
        error("incorrect URL for audio ping: " + e);
      }

    tPanel.setBackground(terminal.getBackground());
  }

  /**
   * Continuously read from our back end and display the data on screen.
   */
  public void run() {
    byte[] b = new byte[256];
    int n = 0;
    while (n >= 0)
      try {
        n = read(b);
        if (debug > 1 && n > 0)
          System.err.println("Terminal: \"" + (new String(b, 0, n, encoding)) + "\"");
        if (n > 0) emulation.putString(new String(b, 0, n, encoding));
        tPanel.repaint();
      } catch (IOException e) {
        reader = null;
        break;
      }
  }

  protected FilterPlugin source;

  public void setFilterSource(FilterPlugin source) {
    if (debug > 0) System.err.println("Terminal: connected to: " + source);
    this.source = source;
  }

  public FilterPlugin getFilterSource() {
    return source;
  }

  public int read(byte[] b) throws IOException {
    return source.read(b);
  }

  public void write(byte[] b) throws IOException {
    source.write(b);
  }

  public JComponent getPluginVisual() {
    return tPanel;
  }

  public JMenu getPluginMenu() {
    return menu;
  }

  public void copy(Clipboard clipboard) {
    String data = terminal.getSelection();
    // check due to a bug in the hotspot vm
    if (data == null) return;
    StringSelection selection = new StringSelection(data);
    clipboard.setContents(selection, this);
  }

  public void paste(Clipboard clipboard) {
    if (clipboard == null) return;
    Transferable t = clipboard.getContents(this);
    try {
      /*
      InputStream is =
        (InputStream)t.getTransferData(DataFlavor.plainTextFlavor);
      if(debug > 0)
        System.out.println("Clipboard: available: "+is.available());
      byte buffer[] = new byte[is.available()];
      is.read(buffer);
      is.close();
      */
      byte buffer[] =
              ((String) t.getTransferData(DataFlavor.stringFlavor)).getBytes();
      try {
        write(buffer);
      } catch (IOException e) {
        reader = null;
      }
    } catch (Exception e) {
      // ignore any clipboard errors
      if (debug > 0) e.printStackTrace();
    }
  }

  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    terminal.clearSelection();
  }
}
