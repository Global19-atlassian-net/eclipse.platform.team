package org.eclipse.team.internal.core.target;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.team.internal.core.Policy;
import org.eclipse.core.runtime.IProgressMonitor;

public class StreamUtil {

	protected final static byte[] COPY_BUFFER = new byte[4096];

	/*
	 * 
	 */
	public static void pipe(
		InputStream in,
		OutputStream out,
		long sizeEstimate,
		IProgressMonitor progress,
		String title)
		throws IOException {

		// Only show progress for files larger than 25Kb.
		Long kilobytesEstimate = new Long(sizeEstimate / 1024);
		boolean showProgress = (progress != null) && (sizeEstimate > 25000);
		long bytesCopied = 0;

		synchronized (COPY_BUFFER) {
			// Read the initial chunk.
			int read = in.read(COPY_BUFFER, 0, COPY_BUFFER.length);

			while (read != -1) {
				out.write(COPY_BUFFER, 0, read);

				// Report progress
				if (showProgress) {
					bytesCopied = bytesCopied + read;
					progress.subTask(
						Policy.bind(
							"filetransfer.monitor",
							new Object[] { title, new Long(bytesCopied / 1024), kilobytesEstimate }));
				}

				// Read the next chunk.
				read = in.read(COPY_BUFFER, 0, COPY_BUFFER.length);
			} // end while
		} // end synchronized
	}

}