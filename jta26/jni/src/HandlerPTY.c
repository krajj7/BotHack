/*
 * Native methods for HandlerPTY (part of Shell).
 */
#include <HandlerPTY.h>		/* includes <jni.h> */
#include <stdio.h>		/* diagnostics */
#include <stdlib.h>		/* malloc/free on Linux et. al. */
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <pty.h>		/* most interesting part: openpty, forkpty */

static void fill_termios(struct termios *tp);
static void fill_winsize(struct winsize *wp);

JNIEXPORT jint JNICALL
Java_de_mud_jta_plugin_HandlerPTY_start
  (JNIEnv *env, jobject this, jstring jcmd)
{
	jclass cls = (*env)->GetObjectClass(env, this);
	jfieldID fid_good, fid_fd;
	const char *scmd;
	char *ncmd;
	jboolean good;
	jint fd;
	int fd_buf;	/* different type from fd */
	struct termios tiob;
	struct winsize winb;
	int rc;

	scmd = (*env)->GetStringUTFChars(env, jcmd, 0);
	ncmd = strdup(scmd);		// We are more comfortable with free()
	(*env)->ReleaseStringUTFChars(env, jcmd, scmd);
	if (ncmd == NULL) {
		return -1;
	}

	fid_good = (*env)->GetFieldID(env, cls, "good", "Z");
	if (fid_good == 0) {
		fprintf(stderr, "HandlerPTY.start: null FID for \"good\"\n");
		free(ncmd);
		return -1;
	}
	fid_fd = (*env)->GetFieldID(env, cls, "fd", "I");
	if (fid_fd == 0) {
		fprintf(stderr, "HandlerPTY.start: null FID for \"fd\"\n");
		free(ncmd);
		return -1;
	}

	good = (*env)->GetBooleanField(env, this, fid_good);
	fd = (*env)->GetIntField(env, this, fid_fd);

	if (good) {
		/* P3 remove later */ fprintf(stderr,
		    "HandlerPTY.start: PTY is already open\n");
		free(ncmd);
		return -1;
	}

	/*
	 * At least on my system NULL is legal as PTY name.
	 * We use it because we do not want to use a buffer of unknown size.
	 */
	fill_termios(&tiob);
	fill_winsize(&winb);

	if ((rc = forkpty(&fd_buf, NULL, &tiob, &winb)) < 0) {
		fprintf(stderr, "HandlerPTY.start: forkpty() failed\n");
		free(ncmd);
		return -1;
	}
	if (rc == 0) {	/* child */
		char *eargv[] = { NULL, NULL };
		char *eenvp[] = { NULL };
		eargv[0] = ncmd;
		execve(ncmd, eargv, eenvp);
		fprintf(stderr, "HandlerPTY.start: execve(%s) failed: %s\n",
		    ncmd, strerror(errno));
		exit(1);
	}
	fd = fd_buf;
	good = JNI_TRUE;

	/* Would be nice to linger here until the child is known to be good. */

	(*env)->SetBooleanField(env, this, fid_good, good);
	(*env)->SetIntField(env, this, fid_fd, fd);

	free(ncmd);
	return 0;
}

JNIEXPORT void JNICALL
Java_de_mud_jta_plugin_HandlerPTY_close
  (JNIEnv *env, jobject this)
{
	jclass cls = (*env)->GetObjectClass(env, this);
	jfieldID fid_good, fid_fd;
	jboolean good;
	jint fd;

	if ((fid_good = (*env)->GetFieldID(env, cls, "good", "Z")) == 0) {
		fprintf(stderr, "HandlerPTY.close: null FID for \"good\"\n");
		return;
	}
	if ((fid_fd = (*env)->GetFieldID(env, cls, "fd", "I")) == 0) {
		fprintf(stderr, "HandlerPTY.close: null FID for \"fd\"\n");
		return;
	}

	good = (*env)->GetBooleanField(env, this, fid_good);
	fd = (*env)->GetIntField(env, this, fid_fd);

	if (good) {
		close(fd);
		good = JNI_FALSE;
	}

	(*env)->SetBooleanField(env, this, fid_good, good);
	(*env)->SetIntField(env, this, fid_fd, fd);
}

JNIEXPORT jint JNICALL Java_de_mud_jta_plugin_HandlerPTY_read
  (JNIEnv *env, jobject this, jbyteArray b)
{
	jclass cls = (*env)->GetObjectClass(env, this);
	jfieldID fid_good, fid_fd;
	jboolean good;
	jint fd;
	jsize blen;
	jbyte *buf;
	int rc;

	if ((fid_good = (*env)->GetFieldID(env, cls, "good", "Z")) == 0) {
		fprintf(stderr, "HandlerPTY.read: null FID for \"good\"\n");
		return -1;
	}
	if ((fid_fd = (*env)->GetFieldID(env, cls, "fd", "I")) == 0) {
		fprintf(stderr, "HandlerPTY.read: null FID for \"fd\"\n");
		return -1;
	}

	good = (*env)->GetBooleanField(env, this, fid_good);
	fd = (*env)->GetIntField(env, this, fid_fd);

	if (!good) {
		printf("HandlerPTY.read: no good\n");
		return -1;
	}

	blen = (*env)->GetArrayLength(env, b);
	buf = (*env)->GetByteArrayElements(env, b, 0);
	if (blen > 0) {
		rc = read(fd, buf, blen);
	} else {
		rc = 0;
	}
	(*env)->ReleaseByteArrayElements(env, b, buf, 0);

	if (rc < 0) {
		// Dunno if we want this printout... xterm is silent here.
		fprintf(stderr, "HandlerPTY.read: %s\n", strerror(errno));
		return -1;
	}

	return rc;
}

JNIEXPORT jint JNICALL Java_de_mud_jta_plugin_HandlerPTY_write
  (JNIEnv *env, jobject this, jbyteArray b)
{
	jclass cls = (*env)->GetObjectClass(env, this);
	jfieldID fid_good, fid_fd;
	jboolean good;
	jint fd;
	jsize blen;
	jbyte *buf;
	int rc;

	if ((fid_good = (*env)->GetFieldID(env, cls, "good", "Z")) == 0) {
		fprintf(stderr, "HandlerPTY.write: null FID for \"good\"\n");
		return -1;
	}
	if ((fid_fd = (*env)->GetFieldID(env, cls, "fd", "I")) == 0) {
		fprintf(stderr, "HandlerPTY.write: null FID for \"fd\"\n");
		return -1;
	}

	good = (*env)->GetBooleanField(env, this, fid_good);
	fd = (*env)->GetIntField(env, this, fid_fd);

	if (!good) {
		printf("HandlerPTY.write: no good\n");
		return -1;
	}

	blen = (*env)->GetArrayLength(env, b);
	buf = (*env)->GetByteArrayElements(env, b, 0);
	if (blen > 0) {
		rc = write(fd, buf, blen);
	} else {
		rc = 0;
	}
	(*env)->ReleaseByteArrayElements(env, b, buf, 0);

	if (rc < 0) {
		fprintf(stderr, "HandlerPTY.write: %s\n", strerror(errno));
		return -1;
	}

	return rc;
}

static void
fill_termios(struct termios *tp)
{
        memset(tp, 0, sizeof(struct termios));

	tp->c_iflag = IXON | IXOFF;			/* ICRNL? */
	tp->c_oflag = OPOST | ONLCR;
        tp->c_cflag = CS8 | CREAD | CLOCAL | B9600;	/* HUPCL? CRTSCTS? */
	tp->c_lflag = ICANON | ISIG | ECHO | ECHOE | ECHOK | ECHOKE | ECHOCTL;
	tp->c_cc[VSTART] = 'Q' & 0x1F;
	tp->c_cc[VSTOP]  = 'S' & 0x1F;
	tp->c_cc[VERASE] = 0x7F;
	tp->c_cc[VKILL]  = 'U' & 0x1F;
	tp->c_cc[VINTR]  = 'C' & 0x1F;
	tp->c_cc[VQUIT]  = '\\' & 0x1F;
	tp->c_cc[VEOF]   = 'D' & 0x1F;
	tp->c_cc[VSUSP]  = 'Z' & 0x1F;
	tp->c_cc[VWERASE] = 'W' & 0x1F;
	tp->c_cc[VREPRINT] = 'R' & 0x1F;
}

static void
fill_winsize(struct winsize *wp)
{
	memset(wp, 0, sizeof(struct winsize));

	wp->ws_row = 24;
	wp->ws_col = 80;
}
