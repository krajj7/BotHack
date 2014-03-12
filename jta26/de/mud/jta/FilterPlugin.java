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

import java.io.IOException;

/**
 * The filter plugin is the base interface for plugins that want to intercept
 * the communication between front end and back end plugins. Filters and
 * protocol handlers are a good example.
 * <P>
 * <B>Maintainer:</B> Matthias L. Jugel
 *
 * @version $Id: FilterPlugin.java 499 2005-09-29 08:24:54Z leo $
 * @author Matthias L. Jugel, Marcus Mei�ner
 */
public interface FilterPlugin {
  /**
   * Set the source plugin where we get our data from and where the data
   * sink (write) is. The actual data handling should be done in the
   * read() and write() methods.
   * @param source the data source
   */
  public void setFilterSource(FilterPlugin source) 
    throws IllegalArgumentException;

  public FilterPlugin getFilterSource();

  /**
   * Read a block of data from the back end.
   * @param b the buffer to read the data into
   * @return the amount of bytes actually read
   */
  public int read(byte[] b)
    throws IOException;

  /**
   * Write a block of data to the back end.
   * @param b the buffer to be sent
   */
  public void write(byte[] b)
    throws IOException;
}
