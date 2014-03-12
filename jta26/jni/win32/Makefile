# This file is part of "JTA - Telnet/SSH for the JAVA(tm) platform".
#
# (c) Matthias L. Jugel, Marcus Meißner 1996-2005. All Rights Reserved.
#
# Please visit http://javatelnet.org/ for updates and contact.
#
# --LICENSE NOTICE--
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
# --LICENSE NOTICE--
#

#### This makefile is incomplete! Someone please finish it.

JAVAH	=	/q/opt/jdk1.2.2/bin/javah
TOPDIR	=	../..
SRCDIR	=	../../de
CDIR	=	../src
 
JNI_INCLUDE =	-Ic:\java\include -Ic:\java\include\win32

CC	=	gcc
SOCFLAGS =	-fPIC -c -Wall -I. $(JNI_INCLUDE)
SOLFLAGS =	-shared -lutil

all:  jtapty.dll

#
# On Win32, the following command builds a dynamic link library hello.dll using Microsoft Visual C++ 4.0: 
#
#    cl -Ic:\java\include -Ic:\java\include\win32
#                    -LD HelloWorldImp.c -Fehello.dll
#
# On Linux libutil contains forkpty()
#
#
# This will most likely not be easily portable to Win32, except probably
# using cygwin. Do not expect it to work.

clean:

realclean: clean

# EOF
