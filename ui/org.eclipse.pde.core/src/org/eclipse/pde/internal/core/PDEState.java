package org.eclipse.pde.internal.core;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

import org.eclipse.core.runtime.*;
import org.eclipse.osgi.service.pluginconversion.*;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;
import org.osgi.util.tracker.*;

// This class provides a higher level API on the state
public class PDEState {
	private StateObjectFactory fStateObjectFactory;
	protected State fState;
	private long fId;
	private HashMap fBundleClasspaths;
	
	protected long getNextId() {
		return ++fId;
	}
	public PDEState() {
		fStateObjectFactory = Platform.getPlatformAdmin().getFactory();
		fState = fStateObjectFactory.createState();
		fState.setResolver(Platform.getPlatformAdmin().getResolver());
		fId = 0;
		fBundleClasspaths = new HashMap();
	}
	
	public StateObjectFactory getFactory() {
		return fStateObjectFactory;
	}
	
	public void addBundleDescription(BundleDescription toAdd) {
		fState.addBundle(toAdd);
	}
	
	private PluginConverter acquirePluginConverter() throws Exception {
		ServiceTracker tracker = new ServiceTracker(PDECore.getDefault()
				.getBundleContext(), PluginConverter.class.getName(), null);
		tracker.open();
		return (PluginConverter) tracker.getService();
	}
	
	public BundleDescription addBundle(Dictionary manifest, File bundleLocation) {
		try {
			BundleDescription descriptor = fStateObjectFactory.createBundleDescription(manifest, bundleLocation.getAbsolutePath(), getNextId());
			fBundleClasspaths.put(new Long(descriptor.getBundleId()), manifest);
			fState.addBundle(descriptor);
			return descriptor;
		} catch (BundleException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	public BundleDescription addBundle(File bundleLocation) {
		Dictionary manifest;
		manifest = loadManifest(bundleLocation);
		if (manifest == null) {
			return null;
		}
		if (manifest.get(Constants.BUNDLE_SYMBOLICNAME).equals("org.eclipse.osgi")) {
			//TODO We need to handle the special case of the osgi bundle for 
			//whose bundle-classpath is specified in the eclipse.properties file 
			// in the osgi folder
			manifest.put(Constants.BUNDLE_CLASSPATH, "core.jar, console.jar, osgi.jar, resolver.jar, defaultAdaptor.jar, eclipseAdaptor.jar");
		} else if (manifest.get(Constants.BUNDLE_SYMBOLICNAME).equals("org.eclipse.swt")) {
			manifest.put(Constants.BUNDLE_CLASSPATH, "$ws$/swt.jar");
		}
		return addBundle(manifest, bundleLocation);
	}
	
	private Dictionary loadManifest(File bundleLocation) {
		InputStream manifestStream = null;
		try {
			URL manifestLocation = null;
			if (bundleLocation.getName().endsWith("jar")) {
				manifestLocation = new URL("jar:file:" + bundleLocation + "!/" + JarFile.MANIFEST_NAME);
				manifestStream = manifestLocation.openStream();
			} else {
				manifestStream = new FileInputStream(new File(bundleLocation, JarFile.MANIFEST_NAME));
			}
		} catch (IOException e) {
		}
		if (manifestStream == null) {
			try {
				PluginConverter converter = acquirePluginConverter();
				return converter.convertManifest(bundleLocation, false);
			} catch (Exception e1) {
				return null;
			}
		}
		try {
			Manifest m = new Manifest(manifestStream);
			return manifestToProperties(m.getMainAttributes());
		} catch (IOException e) {
			return null;
		} finally {
			try {
				manifestStream.close();
			} catch (IOException e1) {
				//Ignore
			}
		}
	}
	
	private Properties manifestToProperties(Attributes d) {
		Iterator iter = d.keySet().iterator();
		Properties result = new Properties();
		while (iter.hasNext()) {
			Attributes.Name key = (Attributes.Name) iter.next();
			result.put(key.toString(), d.get(key));
		}
		return result;
	}
	
	public void addBundles(Collection bundles) {
		for (Iterator iter = bundles.iterator(); iter.hasNext();) {
			File bundle = (File) iter.next();
			addBundle(bundle);
		}
	}
	
	public void resolveState() {
		fState.resolve(false);
	}
	
	public void resolveState(boolean incremental) {
		fState.resolve(incremental);
	}
	
	public void removeBundleDescription(BundleDescription description) {
		fState.removeBundle(description);
	}
	
	public State getState() {
		return fState;
	}
	
	public BundleDescription[] getDependentBundles(String bundleId, Version version) {
		BundleDescription root = fState.getBundle(bundleId, version);
		return PDEStateHelper.getDependentBundles(root);
	}
	public BundleDescription getResolvedBundle(String bundleId, String version) {
		if (version == null)
			return getResolvedBundle(bundleId);
		BundleDescription description = getState().getBundle(bundleId, new Version(version));
		if (description.isResolved())
			return description;
		return null;
	}
	public BundleDescription getResolvedBundle(String bundleId) {
		BundleDescription[] description = getState().getBundles(bundleId);
		if (description == null)
			return null;
		for (int i = 0; i < description.length; i++) {
			if (description[i].isResolved())
				return description[i];
		}
		return null;
	}
	public HashMap getExtraData() {
		return fBundleClasspaths;
	}
	
	public Dictionary getManifest(long bundleID) {
		return (Dictionary)fBundleClasspaths.get(new Long(bundleID));
	}
}