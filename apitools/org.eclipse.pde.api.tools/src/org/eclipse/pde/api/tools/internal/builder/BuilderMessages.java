/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.builder;

import org.eclipse.osgi.util.NLS;

public class BuilderMessages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.pde.api.tools.internal.builder.buildermessages"; //$NON-NLS-1$
	public static String ApiToolBuilder_0;
	public static String ApiToolBuilder_1;
	public static String ApiToolBuilder_10;
	public static String ApiToolBuilder_11;
	public static String ApiToolBuilder_2;
	public static String ApiToolBuilder_3;
	public static String ApiToolBuilder_4;
	public static String ApiToolBuilder_5;
	public static String ApiToolBuilder_6;
	public static String ApiToolBuilder_7;
	public static String ApiToolBuilder_8;
	public static String ApiToolBuilder_9;
	
	public static String VersionManagementMissingSinceTag;
	public static String VersionManagementMalformedSinceTag;
	public static String VersionManagementSinceTagGreaterThanComponentVersion;
	public static String VersionManagementIncorrectMajorVersionForAPIBreakage;
	public static String VersionManagementIncorrectMajorVersionForAPIChange;
	public static String VersionManagementIncorrectMinorVersionForAPIChange;
	
	public static String ApiAnalyserTaskName;
	public static String ApiUseAnalyzer_0;
	public static String ApiUseAnalyzer_2;

	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, BuilderMessages.class);
	}

	private BuilderMessages() {
	}
}
