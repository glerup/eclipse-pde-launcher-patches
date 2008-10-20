/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.EEVMType;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.IVMInstallChangedListener;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.PropertyChangeEvent;
import org.eclipse.jdt.launching.VMStandin;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
import org.eclipse.jdt.launching.environments.IExecutionEnvironmentsManager;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.ResolverError;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.osgi.service.resolver.StateHelper;
import org.eclipse.osgi.service.resolver.StateObjectFactory;
import org.eclipse.pde.api.tools.internal.AnyValue;
import org.eclipse.pde.api.tools.internal.CoreMessages;
import org.eclipse.pde.api.tools.internal.SystemLibraryApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.ApiPlugin;
import org.eclipse.pde.api.tools.internal.provisional.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiBaseline;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiElement;
import org.eclipse.pde.api.tools.internal.util.Util;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

import com.ibm.icu.text.MessageFormat;

/**
 * Implementation of an {@link IApiBaseline}
 * 
 * @since 1.0
 */
public class ApiBaseline extends ApiElement implements IApiBaseline, IVMInstallChangedListener {
	
	/**
	 * Empty array of component
	 */
	private static final IApiComponent[] EMPTY_COMPONENTS = new IApiComponent[0];
	
	/**
	 * OSGi bundle state
	 */
	private State fState;
	
	/**
	 * Execution environment identifier
	 */
	private String fExecutionEnvironment;
	
	/**
	 * Component representing the system library
	 */
	private IApiComponent fSystemLibraryComponent;
	
	/**
	 * Whether an execution environment should be automatically resolved 
	 * as API components are added.
	 */
	private boolean fAutoResolve = false;
	
	/**
	 * Execution environment status
	 */
	private IStatus fEEStatus = null;

	/**
	 * Constant to match any value for ws, os, arch.
	 */
	private AnyValue ANY_VALUE = new AnyValue("*"); //$NON-NLS-1$
	
	/**
	 * Cache of resolved packages. 
	 * <p>Map of <code>PackageName -> Map(componentName -> IApiComponent[])</code></p>
	 * For each package the cache contains a map of API components that provide that package,
	 * by source component name (including the <code>null</code> component name).
	 */
	private HashMap fComponentsProvidingPackageCache = null;
	
	/**
	 * Maps component id's to components.
	 * <p>Map of <code>componentId -> {@link IApiComponent}</code></p>
	 */
	private HashMap fComponentsById = null;
	
	/**
	 * Cache of system package names
	 */
	private HashSet fSystemPackageNames = null;
	
	/**
	 * The VM install this profile is bound to for system libraries or <code>null</code>.
	 * Only used in the IDE when OSGi is running.
	 */
	private IVMInstall fVMBinding = null;

	/**
	 * Constructs a new API profile with the given name.
	 * 
	 * @param name profile name
	 */
	public ApiBaseline(String name) {
		super(null, IApiElement.BASELINE, name);
		fAutoResolve = true;
		fEEStatus = new Status(IStatus.ERROR, ApiPlugin.PLUGIN_ID, CoreMessages.ApiProfile_0);
	}	
		
	/**
	 * Constructs a new API profile with the given attributes.
	 * 
	 * @param name profile name
	 * @param eeDescriptoin execution environment description file
	 * @throws CoreException if unable to create a profile with the given attributes
	 */
	public ApiBaseline(String name, File eeDescription) throws CoreException {
		this(name);
		fAutoResolve = false;
		EEVMType.clearProperties(eeDescription);
		String profile = EEVMType.getProperty(EEVMType.PROP_CLASS_LIB_LEVEL, eeDescription);
		initialize(profile, eeDescription);
		fEEStatus = new Status(IStatus.OK, ApiPlugin.PLUGIN_ID,
				MessageFormat.format(CoreMessages.ApiProfile_1, new String[]{profile}));
	}

	/**
	 * Initializes this profile to resolve in the execution environment
	 * associated with the given symbolic name.
	 * 
	 * @param environmentId execution environment symbolic name
	 * @param eeFile execution environment description file
	 * @throws CoreException if unable to initialize based on the given id
	 */
	private void initialize(String environmentId, File eeFile) throws CoreException {
		Properties properties = null;
		if (ApiPlugin.isRunningInFramework()) {
			properties = getJavaProfileProperties(environmentId);
		} else {
			properties = Util.getEEProfile(eeFile);
		}
		if (properties == null) {
			abort("Unknown execution environment: " + environmentId, null); //$NON-NLS-1$
		} else {
			initialize(properties, eeFile);
		}
	}
	
	/**
	 * Throws a core exception with the given message and underlying exception,
	 * if any.
	 * 
	 * @param message error message
	 * @param e underlying exception or <code>null</code>
	 * @throws CoreException
	 */
	private static void abort(String message, Throwable e) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, ApiPlugin.PLUGIN_ID, message, e));
	}	
	
	/**
	 * Returns the property file for the given environment or <code>null</code>.
	 * 
	 * @param ee execution environment symbolic name
	 * @return properties file or <code>null</code> if none
	 */
	public static Properties getJavaProfileProperties(String ee) throws CoreException {
		Bundle osgiBundle = Platform.getBundle("org.eclipse.osgi"); //$NON-NLS-1$
		if (osgiBundle == null) 
			return null;
		URL profileURL = osgiBundle.getEntry(ee.replace('/', '_') + ".profile"); //$NON-NLS-1$
		if (profileURL != null) {
			InputStream is = null;
			try {
				profileURL = FileLocator.resolve(profileURL);
				URLConnection openConnection = profileURL.openConnection();
				openConnection.setUseCaches(false);
				is = openConnection.getInputStream();
				if (is != null) {
					Properties profile = new Properties();
					profile.load(is);
					return profile;
				}
			} catch (IOException e) {
				abort("Unable to read profile: " + ee, e); //$NON-NLS-1$
			} finally {
				try {
					if (is != null) {
						is.close();
					}
				} catch (IOException e) {
					ApiPlugin.log(e);
				}
			}
		}
		return null;
	}		
	
	/**
	 * Initializes this profile from the given properties.
	 * 
	 * @param profile OGSi profile properties
	 * @param description execution environment description file
	 * @throws CoreException if unable to initialize
	 */
	private void initialize(Properties profile, File description) throws CoreException {
		String value = profile.getProperty(Constants.FRAMEWORK_SYSTEMPACKAGES);
		Dictionary dictionary = new Hashtable();
		String[] systemPackages = null;
		if (value != null) {
			systemPackages = value.split(","); //$NON-NLS-1$
			dictionary.put(Constants.FRAMEWORK_SYSTEMPACKAGES, value);
		}
		value = profile.getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
		if (value != null) {
			dictionary.put(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, value);
		}
		fExecutionEnvironment = profile.getProperty("osgi.java.profile.name"); //$NON-NLS-1$
		if (fExecutionEnvironment == null) {
			abort("Profile file missing 'osgi.java.profile.name'" , null); //$NON-NLS-1$
		}
		dictionary.put("osgi.os", ANY_VALUE); //$NON-NLS-1$
		dictionary.put("osgi.arch", ANY_VALUE); //$NON-NLS-1$
		dictionary.put("osgi.ws", ANY_VALUE); //$NON-NLS-1$
		dictionary.put("osgi.nl", ANY_VALUE); //$NON-NLS-1$
		getState().setPlatformProperties(dictionary);
		// clean up previous system library
		if (fSystemLibraryComponent != null && fComponentsById != null) {
			fComponentsById.remove(fSystemLibraryComponent.getId());
		}
		if(fSystemPackageNames != null) {
			fSystemPackageNames.clear();
			fSystemPackageNames = null;
		}
		clearComponentsCache();
		// set new system library
		fSystemLibraryComponent = new SystemLibraryApiComponent(this, description, systemPackages);
		addComponent(fSystemLibraryComponent);
	}

	/**
	 * Clears the package -> components cache and sets it to <code>null</code>
	 */
	private void clearComponentsCache() {
		if(fComponentsProvidingPackageCache != null) {
			fComponentsProvidingPackageCache.clear();
			fComponentsProvidingPackageCache = null;
		}
	}
	
	/**
	 * Adds an {@link IApiComponent} to the fComponentsById mapping
	 * @param component
	 */
	private void addComponent(IApiComponent component) {
		if(component == null) {
			return;
		}
		if(fComponentsById == null) {
			fComponentsById = new HashMap();
		}
		fComponentsById.put(component.getId(), component);
	}
	
	/* (non-Javadoc)
	 * @see IApiProfile#addApiComponents(org.eclipse.pde.api.tools.model.component.IApiComponent[], boolean)
	 */
	public void addApiComponents(IApiComponent[] components) {
		HashSet ees = new HashSet();
		for (int i = 0; i < components.length; i++) {
			BundleApiComponent component = (BundleApiComponent) components[i];
			if (component.isSourceComponent()) {
				continue;
			}
			BundleDescription description = component.getBundleDescription();
			getState().addBundle(description);
			addComponent(component);
			ees.addAll(Arrays.asList(component.getExecutionEnvironments()));
		}
		resolveSystemLibrary(ees);
		getState().resolve();
	}

	/**
	 * Resolves and initializes the system library to use based on API component requirements.
	 * Only works when running in the framework. Has no effect if not running in the framework.
	 */
	private void resolveSystemLibrary(HashSet ees) {
		if (ApiPlugin.isRunningInFramework() && fAutoResolve) {
			IStatus error = null;
			IExecutionEnvironmentsManager manager = JavaRuntime.getExecutionEnvironmentsManager();
			Iterator iterator = ees.iterator();
			Map VMsToEEs = new HashMap();
			while (iterator.hasNext()) {
				String ee = (String) iterator.next();
				IExecutionEnvironment environment = manager.getEnvironment(ee);
				if (environment != null) {
					IVMInstall[] compatibleVMs = environment.getCompatibleVMs();
					for (int i = 0; i < compatibleVMs.length; i++) {
						IVMInstall vm = compatibleVMs[i];
						Set EEs = (Set) VMsToEEs.get(vm);
						if (EEs == null) {
							EEs = new HashSet();
							VMsToEEs.put(vm, EEs);
						}
						EEs.add(ee);
					}
				}
			}
			// select VM that is compatible with most required environments
			iterator = VMsToEEs.entrySet().iterator();
			IVMInstall bestFit = null;
			int bestCount = 0;
			while (iterator.hasNext()) {
				Entry entry = (Entry) iterator.next();
				Set EEs = (Set)entry.getValue();
				if (EEs.size() > bestCount) {
					bestCount = EEs.size();
					bestFit = (IVMInstall)entry.getKey();
				}
			}
			String systemEE = null;
			if (bestFit != null) {
				// find the EE this VM is strictly compatible with
				IExecutionEnvironment[] environments = manager.getExecutionEnvironments();
				for (int i = 0; i < environments.length; i++) {
					IExecutionEnvironment environment = environments[i];
					if (environment.isStrictlyCompatible(bestFit)) {
						systemEE = environment.getId();
						break;
					}
				}
				if (systemEE == null) {
					// a best fit, but not strictly compatible with any environment (e.g.
					// a 1.7 VM for which there is no profile yet). This is a bit of a hack
					// until an OSGi profile exists for 1.7.
					if (bestFit instanceof IVMInstall2) {
			            String javaVersion = ((IVMInstall2)bestFit).getJavaVersion();
			            if (javaVersion != null) {
			            	if (javaVersion.startsWith(JavaCore.VERSION_1_7)) {
			            		// set EE to 1.6 when 1.7 is detected
			            		systemEE = "JavaSE-1.6"; //$NON-NLS-1$
			            	}
			            }
					}
				}
				if (systemEE != null) {
					// only update if different from current or missing VM binding
					if (!systemEE.equals(getExecutionEnvironment()) || fVMBinding == null) {
						try {
							File file = Util.createEEFile(bestFit, systemEE);
							JavaRuntime.addVMInstallChangedListener(this);
							fVMBinding = bestFit;
							initialize(systemEE, file);
						} catch (CoreException e) {
							error = new Status(IStatus.ERROR, ApiPlugin.PLUGIN_ID, CoreMessages.ApiProfile_2, e);
						} catch (IOException e) {
							error = new Status(IStatus.ERROR, ApiPlugin.PLUGIN_ID, CoreMessages.ApiProfile_2, e);
						}
					}
				} else {
					// VM is not strictly compatible with any EE
					error = new Status(IStatus.ERROR, ApiPlugin.PLUGIN_ID, CoreMessages.ApiProfile_4);
				}
			} else {
				// no VMs match any required EE
				error = new Status(IStatus.ERROR, ApiPlugin.PLUGIN_ID, CoreMessages.ApiProfile_4);
			}
			if (error == null) {
				// build status for unbound required EE's
				Set missing = new HashSet(ees);
				Set covered = new HashSet((Set)VMsToEEs.get(bestFit));
				missing.removeAll(covered);
				if (missing.isEmpty()) {
					fEEStatus = new Status(IStatus.OK, ApiPlugin.PLUGIN_ID,
							MessageFormat.format(CoreMessages.ApiProfile_1, new String[]{systemEE}));
				} else {
					iterator = missing.iterator();
					MultiStatus multi = new MultiStatus(ApiPlugin.PLUGIN_ID, 0, CoreMessages.ApiProfile_7, null);
					while (iterator.hasNext()) {
						String id = (String) iterator.next();
						multi.add(new Status(IStatus.WARNING, ApiPlugin.PLUGIN_ID,
								MessageFormat.format(CoreMessages.ApiProfile_8, new String[]{id})));
					}
					fEEStatus = multi;
				}
			} else {
				fEEStatus = error;
			}
		}
	}

	/**
	 * Returns true if the {@link IApiBaseline} has infos loaded (components) false otherwise.
	 * This is a handle only method that will not load infos.
	 * 
	 * @return true if the {@link IApiBaseline} has infos loaded (components) false otherwise.
	 */
	public boolean peekInfos() {
		return fComponentsById != null;
	}
	
	/* (non-Javadoc)
	 * @see IApiProfile#getApiComponents()
	 */
	public IApiComponent[] getApiComponents() {
		loadBaselineInfos();
		if(fComponentsById == null) {
			return EMPTY_COMPONENTS;
		}
		Collection values = fComponentsById.values();
		return (IApiComponent[]) values.toArray(new IApiComponent[values.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiProfile#resolvePackage(org.eclipse.pde.api.tools.internal.provisional.IApiComponent, java.lang.String)
	 */
	public synchronized IApiComponent[] resolvePackage(IApiComponent sourceComponent, String packageName) throws CoreException {
		HashMap componentsForPackage = null;
		if(fComponentsProvidingPackageCache != null){
			componentsForPackage = (HashMap) fComponentsProvidingPackageCache.get(packageName);
		}
		else {
			fComponentsProvidingPackageCache = new HashMap(8);
		}
		IApiComponent[] cachedComponents = null;
		if (componentsForPackage != null) {
			cachedComponents = (IApiComponent[]) componentsForPackage.get(sourceComponent);
			if (cachedComponents != null) {
				return cachedComponents;
			}
		} else {
			componentsForPackage = new HashMap(8);
			fComponentsProvidingPackageCache.put(packageName, componentsForPackage);
		}
		// check system packages first
		if (isSystemPackage(packageName)) {
			if (fSystemLibraryComponent != null) {
				cachedComponents = new IApiComponent[] { fSystemLibraryComponent };
			} else {
				return EMPTY_COMPONENTS;
			}
		} else {
			if (sourceComponent != null) {
				List componentsList = new ArrayList();
				resolvePackage0(sourceComponent, packageName, componentsList);
				if (componentsList.size() != 0) {
					cachedComponents = new IApiComponent[componentsList.size()];
					componentsList.toArray(cachedComponents);
				}
			}
		}
		if (cachedComponents == null) {
			cachedComponents = EMPTY_COMPONENTS;
		}
		componentsForPackage.put(sourceComponent, cachedComponents);
		return cachedComponents;
	}

	/**
	 * Resolves the listing of {@link IApiComponent}s that export the given package name. The collection 
	 * of {@link IApiComponent}s is written into the specified list <code>componentList</code> 
	 * @param component
	 * @param packageName
	 * @param componentsList
	 * @throws CoreException
	 */
	private void resolvePackage0(IApiComponent component, String packageName, List componentsList) throws CoreException {
		if (component instanceof BundleApiComponent) {
			BundleDescription bundle = ((BundleApiComponent)component).getBundleDescription();
			if (bundle != null) {
				StateHelper helper = getState().getStateHelper();
				ExportPackageDescription[] visiblePackages = helper.getVisiblePackages(bundle);
				for (int i = 0; i < visiblePackages.length; i++) {
					ExportPackageDescription pkg = visiblePackages[i];
					if (packageName.equals(pkg.getName())) {
						BundleDescription bundleDescription = pkg.getExporter();
						IApiComponent exporter = getApiComponent(bundleDescription.getSymbolicName());
						if (exporter != null) {
							componentsList.add(exporter);
						}
					}
				}
				// check for package within the source component
				String[] packageNames = component.getPackageNames();
				// TODO: would be more efficient to have containsPackage(...) or something
				for (int i = 0; i < packageNames.length; i++) {
					if (packageName.equals(packageNames[i])) {
						componentsList.add(component);
					}
				}
			}
		}
	}
	
	/**
	 * Returns whether the specified package is supplied by the system
	 * library.
	 * 
	 * @param packageName package name
	 * @return whether the specified package is supplied by the system
	 * 	library 
	 */
	private boolean isSystemPackage(String packageName) {
		if (packageName.startsWith("java.")) { //$NON-NLS-1$
			return true;
		}
		if (fSystemPackageNames == null) {
			ExportPackageDescription[] systemPackages = getState().getSystemPackages();
			fSystemPackageNames = new HashSet(systemPackages.length);
			for (int i = 0; i < systemPackages.length; i++) {
				fSystemPackageNames.add(systemPackages[i].getName());
			}
		}
		return fSystemPackageNames.contains(packageName);
	}
	
	/**
	 * @return the OSGi state for this {@link IApiProfile}
	 */
	public State getState() {
		if(fState == null) {
			fState = StateObjectFactory.defaultFactory.createState(true);
		}
		return fState;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiProfile#getApiComponent(java.lang.String)
	 */
	public IApiComponent getApiComponent(String id) {
		loadBaselineInfos();
		if(fComponentsById == null) {
			return null;
		}
		return (IApiComponent) fComponentsById.get(id);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.model.component.IApiState#getExecutionEnvironment()
	 */
	public String getExecutionEnvironment() {
		return fExecutionEnvironment;
	}
	
	/**
	 * Loads the infos from the *.profile file the first time the baseline is accessed
	 */
	private void loadBaselineInfos() {
		if(fComponentsById != null) {
			return;
		}
		try {
			ApiBaselineManager.getManager().loadBaselineInfos(this);
		}
		catch(CoreException ce) {
			ApiPlugin.log(ce);
		}
	}
	
	/**
	 * Returns all errors in the state.
	 * 
	 * @return state errors
	 */
	public ResolverError[] getErrors() {
		List errs = new ArrayList();
		BundleDescription[] bundles = getState().getBundles();
		for (int i = 0; i < bundles.length; i++) {
			ResolverError[] errors = getState().getResolverErrors(bundles[i]);
			for (int j = 0; j < errors.length; j++) {
				errs.add(errors[j]);
			}
		}
		return (ResolverError[]) errs.toArray(new ResolverError[errs.size()]);
	}
	
	/**
	 * @see org.eclipse.pde.api.tools.internal.model.ApiElement#setName(java.lang.String)
	 */
	public void setName(String name) {
		super.setName(name);
	}
	
	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if(obj instanceof IApiBaseline) {
			IApiBaseline baseline = (IApiBaseline) obj;
			return this.getName().equals(baseline.getName());
		}
		return super.equals(obj);
	}
	
	/**
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return this.getName().hashCode();
	}
	
	/* (non-Javadoc)
	 * @see IApiProfile#dispose()
	 */
	public void dispose() {
		if (ApiPlugin.isRunningInFramework()) {
			JavaRuntime.removeVMInstallChangedListener(this);
		}
		IApiComponent[] components = getApiComponents();
		for (int i = 0; i < components.length; i++) {
			components[i].dispose();
		}
		clearComponentsCache();
		if(fComponentsById != null) {
			fComponentsById.clear();
			fComponentsById = null;
		}
		if (fSystemPackageNames != null) {
			fSystemPackageNames.clear();
		}
		if(fSystemLibraryComponent != null) {
			fSystemLibraryComponent.dispose();
			fSystemLibraryComponent = null;
		}
		fState = null;
	}

	/**
	 * @see org.eclipse.pde.api.tools.internal.provisional.model.IApiBaseline#close()
	 */
	public void close() throws CoreException {
		IApiComponent[] components = getApiComponents();
		for (int i = 0; i < components.length; i++) {
			components[i].close();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.IApiProfile#getDependentComponents(org.eclipse.pde.api.tools.IApiComponent[])
	 */
	public IApiComponent[] getDependentComponents(IApiComponent[] components) {
		ArrayList bundles = getBundleDescriptions(components);
		BundleDescription[] bundleDescriptions = getState().getStateHelper().getDependentBundles((BundleDescription[]) bundles.toArray(new BundleDescription[bundles.size()]));
		return getApiComponents(bundleDescriptions);
	}

	/**
	 * Returns an array of API components corresponding to the given bundle descriptions.
	 * 
	 * @param bundles bundle descriptions
	 * @return corresponding API components
	 */
	private IApiComponent[] getApiComponents(BundleDescription[] bundles) {
		ArrayList dependents = new ArrayList(bundles.length);
		for (int i = 0; i < bundles.length; i++) {
			BundleDescription bundle = bundles[i];
			IApiComponent component = getApiComponent(bundle.getSymbolicName());
			if (component != null) {
				dependents.add(component);
			}
		}
		return (IApiComponent[]) dependents.toArray(new IApiComponent[dependents.size()]);
	}

	/**
	 * Returns an array of bundle descriptions corresponding to the given API components.
	 * 
	 * @param components API components
	 * @return corresponding bundle descriptions
	 */
	private ArrayList getBundleDescriptions(IApiComponent[] components) {
		ArrayList bundles = new ArrayList(components.length);
		for (int i = 0; i < components.length; i++) {
			IApiComponent component = components[i];
			if (component instanceof BundleApiComponent) {
				bundles.add(((BundleApiComponent)component).getBundleDescription());
			}
		}
		return bundles;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.IApiProfile#getPrerequisiteComponents(org.eclipse.pde.api.tools.IApiComponent[])
	 */
	public IApiComponent[] getPrerequisiteComponents(IApiComponent[] components) {
		ArrayList bundles = getBundleDescriptions(components);
		BundleDescription[] bundlesDescriptions = getState().getStateHelper().getPrerequisites((BundleDescription[]) bundles.toArray(new BundleDescription[bundles.size()]));
		return getApiComponents(bundlesDescriptions);
	}

	/**
	 * Clear cached settings for the given package.
	 * 
	 * @param packageName
	 */
	protected synchronized void clearPackage(String packageName) {
		if(fComponentsProvidingPackageCache != null) {
			fComponentsProvidingPackageCache.remove(packageName);
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiProfile#getExecutionEnvironmentStatus()
	 */
	public IStatus getExecutionEnvironmentStatus() {
		return fEEStatus;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IVMInstallChangedListener#defaultVMInstallChanged(org.eclipse.jdt.launching.IVMInstall, org.eclipse.jdt.launching.IVMInstall)
	 */
	public void defaultVMInstallChanged(IVMInstall previous, IVMInstall current) {}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IVMInstallChangedListener#vmAdded(org.eclipse.jdt.launching.IVMInstall)
	 */
	public void vmAdded(IVMInstall vm) {
		if (!(vm instanceof VMStandin)) {
			// there may be a better fit for VMs/EEs
			rebindVM();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IVMInstallChangedListener#vmChanged(org.eclipse.jdt.launching.PropertyChangeEvent)
	 */
	public void vmChanged(PropertyChangeEvent event) {
		if (!(event.getSource() instanceof VMStandin)) {
			String property = event.getProperty();
			if (IVMInstallChangedListener.PROPERTY_INSTALL_LOCATION.equals(property) ||
					IVMInstallChangedListener.PROPERTY_LIBRARY_LOCATIONS.equals(property)) {
				rebindVM();
			}
		}
	}

	/**
	 * Re-binds the VM this profile is bound to.
	 */
	private void rebindVM() {
		fVMBinding = null;
		IApiComponent[] components = getApiComponents();
		HashSet ees = new HashSet();
		for (int i = 0; i < components.length; i++) {
			ees.addAll(Arrays.asList(components[i].getExecutionEnvironments()));
		}
		resolveSystemLibrary(ees);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IVMInstallChangedListener#vmRemoved(org.eclipse.jdt.launching.IVMInstall)
	 */
	public void vmRemoved(IVMInstall vm) {
		if (vm.equals(fVMBinding)) {
			rebindVM();
		}
	}
}