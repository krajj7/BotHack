// JNI interface to slave process.
// This is a part of Shell plugin.
// If not for a static member, we'd have HandlerPTY private to Shell. XXX

// HandlerPTY is meant to be robust, in a way that you can
// instantiate it and work with it until it throws an exception,

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

public class HandlerPTY {
  public native int start(String cmd);	// open + fork/exec
  public native void close();
  public native int read(byte[] b);
  public native int write(byte[] b);

  private int fd;
  boolean good = false;

  static {
    // System.loadLibrary("libutil");	// forkpty on Linux lives in libutil
    System.loadLibrary("jtapty");
  }

  protected void finalize() throws Throwable {
    super.finalize();
    if(good) {
      close();
    }
  }
}
