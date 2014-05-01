/*
 * (c) Thomas Pasch, 2003. All Rights Reserved.
 *
 * --LICENSE NOTICE--
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of license of JSch
 * (http://www.jcraft.com/jsch/).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * --LICENSE NOTICE--
 */

package de.mud.jta.plugin;

import de.mud.jta.Plugin;
import de.mud.jta.FilterPlugin;
import de.mud.jta.PluginBus;
import de.mud.jta.PluginConfig;
import de.mud.jta.VisualPlugin;

import de.mud.jta.event.ConfigurationListener;
import de.mud.jta.event.OnlineStatusListener;
import de.mud.jta.event.TerminalTypeRequest;
import de.mud.jta.event.WindowSizeRequest;
import de.mud.jta.event.LocalEchoRequest;

import de.mud.ssh.SshIO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mud.jta.event.SocketListener;
import de.mud.jta.event.OnlineStatus;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.ProxySOCKS5;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

/**
 * Modified for ANBF: made .read() blocking, .write() flushes automatically, terminal id set to xterm
 * 
 * Secure Shell plugin for the Java Telnet Application (JTA). This is a plugin
 * to be used instead of Telnet for secure remote terminal sessions over
 * insecure networks.
 * <p>
 * This plugin works in conjuction with JSch, a open source client site pure
 * java implementation of the SSH protocol version 2 with a BSD styled 
 * license.
 * </p><p>
 * This plugin was written mainly because JSch is a more complete
 * implementation of SSH2 than the SSH plugin that comes with JTA. JSch comes 
 * with proxy and socks5 support, zlib compression and X11 forwarding. After 
 * copying the compiled class file of this source to de.mud.jta.plugin, it is
 * invoked like this:
 * </p><p><code>
 * java -cp <include jta and jsch here> de.mud.jta.Main -plugins Status,JTAJSch,Terminal [hostname]
 * </code></p><p>
 * Known Bugs:<br/><ul>
 * <li>Port always defaults to 22 and cannot be changed.</li>
 * <li>No SFTP support.</li>
 * </ul></p><p>
 * <b>Maintainer:</b> Thomas Pasch <thomas.pasch@gmx.de>
 * </p>
 *
 * @see <a href="http://www.sourceforge.net/projects/jta/">Java Telnet Application</a>
 * @see <a href="http://www.jcraft.com/jsch/">JSch homepage</a>
 * @see <a href="http://www.openssh.org/">free Open Source version of SSH</a>
 * @see <a href="http://www.ssh.com/">Official SSH homepage</a>
 * @version $Id:$
 * @author Thomas Pasch <thomas.pasch@gmx.de>
 */
public class JTAJSch extends Plugin implements FilterPlugin, VisualPlugin {
    
    private static final String MENU = "JSch";
    private static final String MENU_HTTP_PROXY = "Http Proxy ...";
    private static final String MENU_SOCKS_PROXY = "Socks Proxy ...";
    private static final String MENU_X11 = "X11 ...";
    private static final String MENU_LOCAL_PORT = "Local Port ...";
    private static final String MENU_REMOTE_PORT = "Remote Port ...";
    
    private static final JSch jSch_ = new JSch();
    
    private String proxyHttpHost_ = null;
    private int proxyHttpPort_;
    private String proxySOCKS5Host_ = null;
    private int proxySOCKS5Port_;
    private String xHost_ = null;
    private int xPort_;
    
    protected FilterPlugin source;
    // protected SshIO handler;
    protected Session session_;
    private Channel channel_;
    private InputStream in_;
    private OutputStream out_;
    private String host_;
    private int port_;
    
    // protected String user, pass;
    
    private final static int debug = 0;
    
    private volatile boolean auth = false;
    
    protected MyUserInfo userInfo_;
    
    /**
     * Create a new ssh plugin.
     */
    public JTAJSch(final PluginBus bus, final String id) {
        super(bus, id);

        // create a new telnet protocol handler
        //    handler = new SshIO() {
        //      /** get the current terminal type */
        //      public String getTerminalType() {
        //        return (String)bus.broadcast(new TerminalTypeRequest());
        //      }
        //      /** get the current window size */
        //      public Dimension getWindowSize() {
        //        return (Dimension)bus.broadcast(new WindowSizeRequest());
        //      }
        //      /** notify about local echo */
        //      public void setLocalEcho(boolean echo) {
        //        bus.broadcast(new LocalEchoRequest(echo));
        //      }
        //      /** write data to our back end */
        //      public void write(byte[] b) throws IOException {
        //        source.write(b);
        //      }
        //    };
        
        bus.registerPluginListener(new ConfigurationListener() {
            public void setConfiguration(PluginConfig config) {
                String user = config.getProperty("SSH", id, "user");
                String pass = config.getProperty("SSH", id, "password");
                userInfo_ = new MyUserInfo(user, pass, null);
            }
        });
        
        // reset the protocol handler just in case :-)
        bus.registerPluginListener(new OnlineStatusListener() {
            public void online() {
                String user = null, pass = null;
                if (userInfo_ != null) {
                    user = userInfo_.getUser();
                    pass = userInfo_.getPassword();
                }
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
                            //	      handler.setLogin(login.getText());
                            //	      handler.setPassword(passw.getText());
                            userInfo_ = new MyUserInfo(login.getText(), passw.getText(), null);
                            frame.dispose();
                            connect();
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
                    //	  handler.setLogin(user);
                    //	  handler.setPassword(pass);
                    userInfo_ = new MyUserInfo(user, pass, null);
                    connect();
                }
            }
            
            private void connect() {
                if (auth) {
                    throw new IllegalStateException();
                }
                try {
                    session_ = jSch_.getSession(userInfo_.getUser(), host_, 22);
                    session_.setUserInfo(userInfo_);
                    if (isProxyHttp()) {
                        session_.setProxy(new ProxyHTTP(proxyHttpHost_, proxyHttpPort_));
                    }
                    else if (isProxySOCKS5()) {
                        session_.setProxy(new ProxySOCKS5(proxySOCKS5Host_, proxySOCKS5Port_));
                    }
                    if (isXForwarding()) {
                        session_.setX11Host(xHost_);
                        session_.setX11Port(xPort_);
                    }
                    session_.connect();
                channel_ = session_.openChannel("shell");
                ((ChannelShell)channel_).setPtyType("xterm");
                try {
                    in_ = channel_.getInputStream();
                    out_ = channel_.getOutputStream();
                }
                catch (IOException ee) {
                    ee.printStackTrace();
                    throw new RuntimeException(ee);
                }
                channel_.connect();
		}
		catch (JSchException e) {
		e.printStackTrace();
		throw new RuntimeException(e);
		}
                auth = true;
            }
            
            public void offline() {
                //        handler.disconnect();
                try {
                    if (in_ != null) in_.close();
                    if (out_ != null) out_.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                finally {
                    auth=false;
                    userInfo_ = null;
                    if (channel_ != null) channel_.disconnect();
                    if (session_ != null) session_.disconnect();
                }
            }
        });
        bus.registerPluginListener(new SocketListener() {
            public void connect(String host, int port) {
                host_ = host;
                port_ = port;
                bus.broadcast(new OnlineStatus(true));
            }
            public void disconnect() {
                bus.broadcast(new OnlineStatus(false));
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
        //    int n = source.read(b);
        //    if(n > 0) {
        int n;
        final int length = b.length;
        n = in_.read(b,0, b.length);
        byte[] tmp = new byte[n];
        System.arraycopy(b, 0, tmp, 0, n);
        pos = 0;
        //      buffer = handler.handleSSH(tmp);
        buffer = tmp;
        
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
        //    handler.sendData(new String(b));
        out_.write(b);
        out_.flush();
    }
    
    public JComponent getPluginVisual() {
        // JTextArea comp = new JTextArea("getPluginVisual");
        return null;
    }
    
    public JMenu getPluginMenu() {
        final JMenu menu = new JMenu(MENU);
        //
        JMenuItem item = new JMenuItem(MENU_HTTP_PROXY);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String foo = getProxyHttpHost();
                int bar = getProxyHttpPort();
                String proxy = JOptionPane.showInputDialog(
                    "HTTP proxy server (hostname:port)",
                    ((foo!=null&&bar!=0)? foo + ":" + bar : ""));
                if (proxy == null) return;
                if (proxy.length() == 0 ) {
                    setProxyHttp(null, 0);
                    return;
                }
                try{
                    foo=proxy.substring(0, proxy.indexOf(':'));
                    bar=Integer.parseInt(proxy.substring(proxy.indexOf(':')+1));
                    if(foo!=null){
                        setProxyHttp(foo, bar);
                    }
                }
                catch(Exception ee){
                }
            }
        });
        menu.add(item);
        //
        item = new JMenuItem(MENU_SOCKS_PROXY);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String foo=getProxySOCKS5Host();
                int bar=getProxySOCKS5Port();
                String proxy=
                JOptionPane.showInputDialog(
                    "SOCKS5 server (hostname:1080)",
                    ((foo!=null&&bar!=0)? foo+":"+bar : ""));
                if(proxy==null)return;
                if(proxy.length()==0){
                    setProxySOCKS5(null, 0);
                    return;
                }              
                try{
                    foo=proxy.substring(0, proxy.indexOf(':'));
                    bar=Integer.parseInt(proxy.substring(proxy.indexOf(':')+1));
                    if(foo!=null){
                        setProxySOCKS5(foo, bar);
                    }
                }
                catch(Exception ee){
                }
            }
        });
        menu.add(item);
        //
        item = new JMenuItem(MENU_X11);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String display=JOptionPane.showInputDialog(
                    "XDisplay name (hostname:0)",
                    (getXHost() == null) ? "": (getXHost() + ":" + getXPort()));
                try{
                    if(display!=null){
                        String host = display.substring(0, display.indexOf(':'));
                        int port = Integer.parseInt(display.substring(display.indexOf(':')+1));
                        setXForwarding(host, port);
                    }
                }
                catch(Exception ee){
                    setXForwarding(null, -1);
                }
            }
        });
        menu.add(item);
        //
        item = new JMenuItem(MENU_LOCAL_PORT);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (session_ == null){
                    JOptionPane.showMessageDialog(menu.getParent(), 
                        "Establish the connection before this setting.");
                    return;
                }                
                try{
                    String title = "Local port forwarding (port:host:hostport)";
                    String foo = JOptionPane.showInputDialog(title, "");
                    if (foo == null) return;
                    int port1 = Integer.parseInt(foo.substring(0, foo.indexOf(':')));
                    foo = foo.substring(foo.indexOf(':') + 1);
                    String host = foo.substring(0, foo.indexOf(':'));
                    int port2 = Integer.parseInt(foo.substring(foo.indexOf(':') + 1));
                    setPortForwardingL(port1, host, port2);
                }
                catch(Exception ee){
                }
            }
        });
        menu.add(item);
        //
        item = new JMenuItem(MENU_REMOTE_PORT);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (session_ == null) {
                    JOptionPane.showMessageDialog(menu.getParent(),
                        "Establish the connection before this setting.");
                    return;
                }                
                try{
                    String title="Remote port forwarding (port:host:hostport)";
                    String foo=JOptionPane.showInputDialog(title, "");
                    if (foo == null) return;
                    int port1 = Integer.parseInt(foo.substring(0, foo.indexOf(':')));
                    foo = foo.substring(foo.indexOf(':') + 1);
                    String host = foo.substring(0, foo.indexOf(':'));
                    int port2 = Integer.parseInt(foo.substring(foo.indexOf(':') + 1));
                    setPortForwardingR(port1, host, port2);
                }
                catch(Exception ee){
                }
            }
        });
        menu.add(item);
        //
        return menu;
    }
    
    private boolean isProxyHttp() {
        return proxyHttpHost_ != null;
    }
    
    private String getProxyHttpHost() {
        return proxyHttpHost_;
    }
    
    private int getProxyHttpPort() {
        return proxyHttpPort_;
    }
    
    private void setProxyHttp(String host, int port) {
        proxyHttpHost_ = host;
        switch (port) {
            case -1:
                proxyHttpHost_ = null;
                proxyHttpPort_ = port;
                break;
            case 0:
                proxyHttpPort_ = 3128;
                break;
            default:
                proxyHttpPort_ = port;
        }
    }
    
    private boolean isProxySOCKS5() {
        return proxySOCKS5Host_ != null;
    }
    
    private String getProxySOCKS5Host() {
        return proxySOCKS5Host_;
    }
    
    private int getProxySOCKS5Port() {
        return proxySOCKS5Port_;
    }
    
    private void setProxySOCKS5(String host, int port) {
        proxySOCKS5Host_ = host;
        switch (port) {
            case -1:
                proxySOCKS5Host_ = null;
                proxySOCKS5Port_ = port;
                break;
            case 0:
                proxySOCKS5Port_ = 3128;
                break;
            default:
                proxySOCKS5Port_ = port;
        }
    }
    
    private boolean isXForwarding() {
        return xHost_ != null;
    }
    
    private String getXHost() {
        return xHost_;
    }
    
    private int getXPort() {
        return xPort_;
    }
    
    private void setXForwarding(String host, int port) {
        xHost_ = host;
        switch (port) {
            case -1:
                xHost_ = null;
                xPort_ = port;
                break;
            default:
                xPort_ = port;
        }
    }
    
    private void setPortForwardingR(int port1, String host, int port2) {
        try{ 
            session_.setPortForwardingR(port1, host, port2); 
        }
        catch (JSchException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    private void setPortForwardingL(int port1, String host, int port2) {
        try{ 
        session_.setPortForwardingL(port1, host, port2);
        }
        catch (JSchException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
       
}

final class MyUserInfo implements UserInfo {
    
    private final String user_;
    private final String password_;
    private final String passphrase_;
    
    public MyUserInfo(String user, String password, String passphrase) {
        user_ = user;
        password_ = password;
        passphrase_ = passphrase;
    }
    
    public String getUser() {
        return user_;
    }
    
    public String getPassphrase() {
        return passphrase_;
    }
    
    public String getPassword() {
        return password_;
    }
    
    public boolean promptPassphrase(String message) {
        return true;
    }
    
    public boolean promptPassword(String message) {
        return true;
    }
    
    public boolean promptYesNo(String message) {
        return true;
    }
    
    public void showMessage(String message) {
    }
    
}
