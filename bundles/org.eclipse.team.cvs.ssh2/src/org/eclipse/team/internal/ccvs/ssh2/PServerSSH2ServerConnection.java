/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Atsuhiko Yamanaka, JCraft,Inc. - initial API and implementation.
 *     IBM Corporation - ongoing maintenance
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ssh2;

import java.io.*;
import java.util.Properties;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.connection.CVSAuthenticationException;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;

import com.jcraft.jsch.*;

public class PServerSSH2ServerConnection implements IServerConnection {

	private ICVSRepositoryLocation location;
	private String password;
	private Session session;
	private static int localport = 2403;
	private IServerConnection psc = null;

	protected PServerSSH2ServerConnection(ICVSRepositoryLocation location, String password) {
		this.location = location;
		this.password = password;
	}

	public void close() throws IOException {
		psc.close();
	}

	public InputStream getInputStream() {
		return psc.getInputStream();
	}
	public OutputStream getOutputStream() {
		return psc.getOutputStream();
	}

	public void open(IProgressMonitor monitor) throws IOException, CVSAuthenticationException {
		monitor.subTask("PServerSSH2ServerConnection.open"); //$NON-NLS-1$
		monitor.worked(1);
		String cvs_root = location.getRootDirectory();
		int cvs_port = location.getPort();
		if (cvs_port == 0)
			cvs_port = 2401;
		String cvs_host = location.getHost();
		String ssh_host = cvs_host;
		String ssh_user = location.getUsername();

		String host = cvs_host;
		if (host.indexOf('@') != -1) {
			cvs_host = host.substring(host.lastIndexOf('@') + 1);
			host = host.substring(0, host.lastIndexOf('@'));
			if (host.indexOf('@') != -1) {
				ssh_host = host.substring(host.lastIndexOf('@') + 1);
				if (ssh_host.length() == 0)
					ssh_host = cvs_host;
				ssh_user = host.substring(0, host.lastIndexOf('@'));
			} else {
				ssh_host = host;
			}
		}

		int ssh_port = 0;
		if (ssh_host.indexOf('#') != -1) {
			try {
				ssh_port = Integer.parseInt(ssh_host.substring(ssh_host.lastIndexOf('#') + 1));
				ssh_host = ssh_host.substring(0, ssh_host.lastIndexOf('#'));
			} catch (Exception e) {
			}
		}

		int lport = cvs_port;
		String rhost = (cvs_host.equals(ssh_host) ? "localhost" : cvs_host); //$NON-NLS-1$
		int rport = cvs_port;

		// ssh -L lport:rhost:rport ssh_user@ssh_host
		int retry = 1;
		while (true) {
			try {
				session = JSchSession.getSession(location, ssh_user, "", ssh_host, ssh_port, new JSchSession.ResponsiveSocketFacory(monitor)).getSession(); //$NON-NLS-1$
				String[] list = session.getPortForwardingL();
				String name = ":" + rhost + ":" + rport; //$NON-NLS-1$ //$NON-NLS-2$
				boolean done = false;
				for (int i = 0; i < list.length; i++) {
					if (list[i].endsWith(name)) {
						try {
							String foo = list[i].substring(0, list[i].indexOf(':'));
							lport = Integer.parseInt(foo);
						} catch (Exception ee) {
						}
						done = true;
						break;
					}
				}
				if (!done) {
					lport = localport++;
					session.setPortForwardingL(lport, rhost, rport);
				}
			} catch (JSchException ee) {
				  retry--;
				  if(retry<0){
				    throw new CVSAuthenticationException(Policy.bind("CVSSSH2ServerConnection.3"), CVSAuthenticationException.NO_RETRY); //$NON-NLS-1$
				  }
				  if(session.isConnected()){
				    session.disconnect();
				  }
				  continue;
			}
			break;
		}
		// password for location will be over-written in JSchSession ;-<
		((CVSRepositoryLocation)location).setPassword(password);
		
		// CVSROOT=":pserver:localhost:"+lport+""cvs_root
		try {
			Properties prop = new Properties();
			prop.put("connection", "pserver"); //$NON-NLS-1$ //$NON-NLS-2$
			prop.put("user", location.getUsername()); //$NON-NLS-1$
			prop.put("password", password); //$NON-NLS-1$
			prop.put("host", "localhost"); //$NON-NLS-1$ //$NON-NLS-2$
			prop.put("port", Integer.toString(lport)); //$NON-NLS-1$
			prop.put("root", cvs_root); //$NON-NLS-1$

			CVSRepositoryLocation cvsrl = CVSRepositoryLocation.fromProperties(prop);

			IConnectionMethod method = cvsrl.getMethod();
			psc = method.createConnection(cvsrl, password);
		} catch (Exception e) {
			throw new CVSAuthenticationException(e.toString(), CVSAuthenticationException.NO_RETRY);
		}
		psc.open(monitor);
	}
}
