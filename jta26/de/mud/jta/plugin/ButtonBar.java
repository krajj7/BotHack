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
import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.SocketRequest;
import de.mud.jta.event.TelnetCommandRequest;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of a programmable button bar to be used as a plugin
 * in the Java(tm) Telnet Applet/Application. The button bar is configured
 * using a input file that contains the setup for the button bar.<P>
 * A typical setup file may look like:
 * <PRE>
 * #
 * # Example for writing a button bar config file.
 * #
 * # The syntaxt for defining buttons, input fields and breaks is as follows:
 * #
 * # - defining a button:
 * # A button is defined by giving the keyword 'button' followed by the text
 * # of the button and the command that should be sent when pressing the
 * # button. If the command contains whitespace characters, enclode it in
 * # quote (") characters!
 * #
 * button		Connect		"\$connect(\@host@,\@port@)"
 * #
 * # - defining a label:
 * # A labvel is defined by giving the keyword 'label' followed by the text
 * # of the label. If the label contains whitespace characters, enclode it in
 * # quote (") characters!
 * #
 * label 		"Hello User"
 * #
 * # - defining an input field:
 * # An input field is defined just like the button above, but it has one more
 * # parameter, the size of the input field. So you define it, by giving the
 * # keyword 'input' followed by the name of the input field (for reference)
 * # followed by the size of the input field and optionally a third parameter
 * # which is the initial text to be displayed in that field.
 * #
 * input		host	20	"tanis"
 * stretch
 * input		port	4	"23"
 * #
 * # Now after the button and two input fields we define another button which
 * # will be shown last in the row. Order is significant for the order in
 * # which the buttons and fields appear.
 * #
 * button		Disconnect	"\\$disconnect()" break
 * #
 * # To implement an input line that is cleared and sends text use this:
 * # The following line send the text in the input field "send" and appends
 * # a newline.
 * input		send	20	"\\@send@\n"	"ls"
 * #
 * # - Defining a choice
 * # A choice is defined just like the button above, but it has multiple
 * # text/command pairs. If the text or command contain whitespace characters,
 * # enclose them in quote (") characters. The text and command data may be
 * # spread over several lines for better readability. Make the first command
 * # empty because it is initially selected, and choosing it will have no
 * # effect until some other item has been chosen.
 * #choice       "- choose -"   ""
 * #             "Text 1"       "Command 1"
 * #             "Text 2"       "Command 2"
 * #             "Text 3"       "Command 3"
 * # etc...

 * </PRE>
 * Other possible keywords are <TT>break</TT> which does introduce a new
 * line so that buttons and input fields defined next will appear in a new
 * line below and <TT>stretch</TT> to make the just defined button or input
 * field stretched as far as possible on the line. That last keyword is
 * useful to fill the space.
 *
 * @version $Id: ButtonBar.java 499 2005-09-29 08:24:54Z leo $
 * @author  Matthias L. Jugel, Marcus Mei�ner
 */
public class ButtonBar extends Plugin
        implements FilterPlugin, VisualPlugin, ActionListener, ListSelectionListener {

  /** the panel that contains the buttons and input fields */
  protected JPanel panel = new JPanel();

  // these tables contain our buttons and fields.
  private Map buttons = null;
  private Map choices = null;
  private Map fields = null;

  // the switch for clearing input fields after enter
  private boolean clearFields = true;

  /**
   * Initialize the button bar and register plugin listeners
   */
  public ButtonBar(PluginBus bus, final String id) {
    super(bus, id);

    // configure the button bar
    bus.registerPluginListener(new ConfigurationListener() {
      public void setConfiguration(PluginConfig cfg) {
        String file = cfg.getProperty("ButtonBar", id, "setup");
        clearFields =
                (new Boolean(cfg.getProperty("ButtonBar", id, "clearFields")))
                .booleanValue();

        // check for the setup file
        if (file == null) {
          ButtonBar.this.error("no setup file");
          return;
        }

        StreamTokenizer setup = null;
        InputStream is = null;

        try {
          is = getClass().getResourceAsStream(file);
        } catch (Exception e) {
          // ignore any errors here
        }

        // if the resource access fails, try URL
        if (is == null)
          try {
            is = new URL(file).openStream();
          } catch (Exception ue) {
            ButtonBar.this.error("could not find: " + file);
            return;
          }

        // create a new stream tokenizer to read the file
        try {
          InputStreamReader ir = new InputStreamReader(is);
          setup = new StreamTokenizer(new BufferedReader(ir));
        } catch (Exception e) {
          ButtonBar.this.error("cannot load " + file + ": " + e);
          return;
        }

        setup.commentChar('#');
        setup.quoteChar('"');

        fields = new HashMap();
        buttons = new HashMap();
        choices = new HashMap();

        GridBagLayout l = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        panel.setLayout(l);
        c.fill = GridBagConstraints.BOTH;

        int token;
        int ChoiceCount = 0;
        JList list;
        // parse the setup file
        try {
          while ((token = setup.nextToken()) != StreamTokenizer.TT_EOF) {
            switch (token) {
              case StreamTokenizer.TT_WORD:
                // reset the constraints
                c.gridwidth = 1;
                c.weightx = 0.0;
                c.weighty = 0.0;
                // keyword found, parse arguments
                if (setup.sval.equals("button")) {
                  if ((token = setup.nextToken()) != StreamTokenizer.TT_EOF) {
                    String descr = setup.sval;
                    if ((token = setup.nextToken()) != StreamTokenizer.TT_EOF) {
                      JButton b = new JButton(descr);
                      buttons.put(b, setup.sval);
                      b.addActionListener(ButtonBar.this);
                      l.setConstraints(b, constraints(c, setup));
                      panel.add(b);
                    } else
                      ButtonBar.this.error(descr + ": missing button command");
                  } else
                    ButtonBar.this.error("unexpected end of file");
                } else if (setup.sval.equals("label")) {
                  if ((token = setup.nextToken()) != StreamTokenizer.TT_EOF) {
                    String descr = setup.sval;
                    JLabel b = new JLabel(descr);
                    l.setConstraints(b, constraints(c, setup));
                    panel.add(b);
                  } else
                    ButtonBar.this.error("unexpected end of file");
                  /* choice - new stuff added APS 07-dec-2001 for Choice
                   * buttons
                   * Choice info is held in the choices hash. There are two
                   * sorts of hash entry:
                   * 1) for each choices button on the terminal, key=choice
                   *    object, value=ID ("C1.", "C2.", etc)
                   * 2) for each item, key=ID+user's text (eg "C1.opt1"),
                   *    value=user's command
                   */

                } else if (setup.sval.equals("choice")) {
                  ChoiceCount++;
                  String ident = "C" + ChoiceCount + ".";
                  list = new JList();
                  choices.put(list, ident);
                  list.addListSelectionListener(ButtonBar.this);// Choices use ItemListener, not Action
                  l.setConstraints(list, constraints(c, setup));
                  panel.add(list);
                  while ((token = setup.nextToken()) != StreamTokenizer.TT_EOF) {
                    if (isKeyword(setup.sval)) {// Got command ... Back off.
                      setup.pushBack();
                      break;
                    }
                    String descr = setup.sval;  // This is the hash key.
                    token = setup.nextToken();
                    if (token == StreamTokenizer.TT_EOF) {
                      ButtonBar.this.error("unexpected end of file");
                    } else {
                      String value = setup.sval;
                      if (isKeyword(value)) {   // Missing command - complain but continue
                        System.err.println(descr + ": missing choice command");
                        setup.pushBack();
                        break;
                      }
                      System.out.println("choice: name='" + descr + "', value='" + value);
                      list.add(descr, new JLabel(descr));
                      choices.put(ident + descr, value);
                    }
                  }
                  ButtonBar.this.error("choices hash: " + choices);
                } else if (setup.sval.equals("input")) {
                  if ((token = setup.nextToken()) != StreamTokenizer.TT_EOF) {
                    String descr = setup.sval;
                    if ((token = setup.nextToken()) ==
                            StreamTokenizer.TT_NUMBER) {
                      int size = (int) setup.nval;
                      String init = "", command = "";
                      token = setup.nextToken();
                      if (isKeyword(setup.sval))
                        setup.pushBack();
                      else
                        command = setup.sval;
                      token = setup.nextToken();
                      if (isKeyword(setup.sval)) {
                        setup.pushBack();
                        init = command;
                      } else
                        init = setup.sval;
                      JTextField t = new JTextField(init, size);
                      if (!init.equals(command)) {
                        buttons.put(t, command);
                        t.addActionListener(ButtonBar.this);
                      }
                      fields.put(descr, t);
                      l.setConstraints(t, constraints(c, setup));
                      panel.add(t);
                    } else
                      ButtonBar.this.error(descr + ": missing field size");
                  } else
                    ButtonBar.this.error("unexpected end of file");
                }
                break;
              default:
                ButtonBar.this.error("syntax error at line " + setup.lineno());
            }
          }
        } catch (IOException e) {
          ButtonBar.this.error("unexpected error while reading setup: " + e);
        }
        panel.validate();
      }
    });
  }

  private GridBagConstraints constraints(GridBagConstraints c,
                                         StreamTokenizer setup)
          throws IOException {
    if (setup.nextToken() == StreamTokenizer.TT_WORD)
      if (setup.sval.equals("break"))
        c.gridwidth = GridBagConstraints.REMAINDER;
      else if (setup.sval.equals("stretch"))
        c.weightx = 1.0;
      else
        setup.pushBack();
    else
      setup.pushBack();
    return c;
  }

  public void valueChanged(ListSelectionEvent evt) {
    String tmp, ident;
    if ((ident = (String) choices.get(evt.getSource())) != null) {
      // It's a choice - get the text from the selected item
      JList list = (JList) evt.getSource();
      tmp = (String) choices.get(ident + list.getSelectedValue());
      if (tmp != null) processEvent(tmp);
    }
  }

  public void actionPerformed(ActionEvent evt) {
    String tmp;
    if ((tmp = (String) buttons.get(evt.getSource())) != null)
      processEvent(tmp);
  }


  private void processEvent(String tmp) {
    String cmd = "", function = null;
    int idx = 0, oldidx = 0;
    while ((idx = tmp.indexOf('\\', oldidx)) >= 0 &&
            ++idx <= tmp.length()) {
      cmd += tmp.substring(oldidx, idx - 1);
      switch (tmp.charAt(idx)) {
        case 'b':
          cmd += "\b";
          break;
        case 'e':
          cmd += "";
          break;
        case 'n':
          cmd += "\n";
          break;
        case 'r':
          cmd += "\r";
          break;
        case '$':
          {
            int ni = tmp.indexOf('(', idx + 1);
            if (ni < idx) {
              error("ERROR: Function: missing '('");
              break;
            }
            if (ni == ++idx) {
              error("ERROR: Function: missing name");
              break;
            }
            function = tmp.substring(idx, ni);
            idx = ni + 1;
            ni = tmp.indexOf(')', idx);
            if (ni < idx) {
              error("ERROR: Function: missing ')'");
              break;
            }
            tmp = tmp.substring(idx, ni);
            idx = oldidx = 0;
            continue;
          }
        case '@':
          {
            int ni = tmp.indexOf('@', idx + 1);
            if (ni < idx) {
              error("ERROR: Input Field: '@'-End Marker not found");
              break;
            }
            if (ni == ++idx) {
              error("ERROR: Input Field: no name specified");
              break;
            }
            String name = tmp.substring(idx, ni);
            idx = ni;
            JTextField t;
            if (fields == null || (t = (JTextField) fields.get(name)) == null) {
              error("ERROR: Input Field: requested input \"" +
                    name + "\" does not exist");
              break;
            }
            cmd += t.getText();
            if (clearFields) t.setText("");
            break;
          }
        default :
          cmd += tmp.substring(idx, ++idx);
      }
      oldidx = ++idx;
    }

    if (oldidx <= tmp.length()) cmd += tmp.substring(oldidx, tmp.length());

    if (function != null) {
      if (function.equals("break")) {
        bus.broadcast(new TelnetCommandRequest((byte) 243)); // BREAK
        return;
      }
      if (function.equals("exit")) {
        try {
          System.exit(0);
        } catch (Exception e) {
          error("cannot exit: " + e);
        }
      }
      if (function.equals("connect")) {
        String address = null;
        int port = -1;
        try {
          if ((idx = cmd.indexOf(",")) >= 0) {
            try {
              port = Integer.parseInt(cmd.substring(idx + 1, cmd.length()));
            } catch (Exception e) {
              port = -1;
            }
            cmd = cmd.substring(0, idx);
          }
          if (cmd.length() > 0) address = cmd;
          if (address != null)
            if (port != -1)
              bus.broadcast(new SocketRequest(address, port));
            else
              bus.broadcast(new SocketRequest(address, 23));
          else
            error("connect: no address");
        } catch (Exception e) {
          error("connect(): failed");
          e.printStackTrace();
        }
      } else if (function.equals("disconnect"))
        bus.broadcast(new SocketRequest());
      else if (function.equals("detach")) {
        error("detach not implemented yet");
      } else
        error("ERROR: function not implemented: \"" + function + "\"");
      return;
    }
    // cmd += tmp.substring(oldidx, tmp.length());
    if (cmd.length() > 0)
      try {
        write(cmd.getBytes());
      } catch (IOException e) {
        error("send: " + e);
      }
  }

  public JComponent getPluginVisual() {
    return panel;
  }

  public JMenu getPluginMenu() {
    return null;
  }


  FilterPlugin source;

  public void setFilterSource(FilterPlugin source) {
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

  private static boolean isKeyword(String txt) {
    return (
            txt.equals("button") ||
            txt.equals("label") ||
            txt.equals("input") ||
            txt.equals("stretch") ||
            txt.equals("choice") ||
            txt.equals("break")
            );
  }
}
