package org.springframework.cloud.deployer.resource.support;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by ericbottard on 08/12/16.
 */
public class EOFInputStream extends FilterInputStream {

	private long remaining;

	private final Runnable callback;

	private boolean callbackRan;

	public EOFInputStream(long remaining, Runnable callback, InputStream delegate) {
		super(delegate);
		this.remaining = remaining;
		this.callback = callback;
	}

	@Override
	public int read(byte[] b) throws IOException {
		int read = super.read(b);
		remaining -= read;
		invokeCallbackIfDone();
		return read;
	}

	@Override
	public int read() throws IOException {
		int read = super.read();
		if (read != -1) {
			remaining--;
			invokeCallbackIfDone();
		}
		return read;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read = super.read(b, off, len);
		remaining -= read;
		invokeCallbackIfDone();
		return read;
	}

	@Override
	public void close() throws IOException {
		super.close();
		callback.run();
	}

	@Override
	public void mark(int readlimit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void reset() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public long skip(long n) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	private void invokeCallbackIfDone() {
		if (remaining == 0 && !callbackRan) {
			callback.run();
			callbackRan = true;
		}
	}
}
