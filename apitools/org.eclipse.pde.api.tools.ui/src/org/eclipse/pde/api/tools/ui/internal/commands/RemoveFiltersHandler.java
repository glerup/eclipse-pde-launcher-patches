/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.ui.internal.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.pde.api.tools.ui.internal.ApiUIPlugin;
import org.eclipse.pde.api.tools.ui.internal.IApiToolsConstants;
import org.eclipse.pde.api.tools.ui.internal.SWTFactory;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Default handler for the remove filters command
 * 
 * @since 1.0.500
 */
public class RemoveFiltersHandler extends AbstractHandler {

	/*
	 * (non-Javadoc)
	 * @see
	 * org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.
	 * ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelectionChecked(event);
		IAdaptable element = getAdaptable(selection);
		if (element != null) {
			SWTFactory.showPropertiesDialog(ApiUIPlugin.getShell(), IApiToolsConstants.ID_FILTERS_PROP_PAGE, element, null);
		}
		return null;
	}

	/**
	 * Returns the {@link IAdaptable} from the current selection context
	 * 
	 * @param selection
	 * @return the {@link IAdaptable} for the current selection context
	 */
	private IAdaptable getAdaptable(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			Object o = ss.getFirstElement();
			if (o instanceof IAdaptable) {
				IAdaptable adapt = (IAdaptable) o;
				IResource resource = (IResource) adapt.getAdapter(IResource.class);
				if (resource != null) {
					return (resource instanceof IProject ? resource : resource.getProject());
				}
			}
		}
		return null;
	}

}
