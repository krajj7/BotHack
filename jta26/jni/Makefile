SUBDIRS	= win32 linux solaris

ARCH	= linux

all: 
	cd $(ARCH) && $(MAKE)

clean:
	set -e ; for i in $(SUBDIRS) ; do (cd $$i && $(MAKE) clean) ; done 

realclean:
	set -e ; for i in $(SUBDIRS) ; do (cd $$i && $(MAKE) realclean) ; done 
