/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.plugin.rows;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.ui.IJavaElementSearchConstants;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.ischema.ISchemaAttribute;
import org.eclipse.pde.internal.ui.editor.IContextPart;
import org.eclipse.pde.internal.ui.editor.contentassist.TypeFieldAssistDisposer;
import org.eclipse.pde.internal.ui.editor.plugin.JavaAttributeValue;
import org.eclipse.pde.internal.ui.util.PDEJavaHelper;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.widgets.FormToolkit;

public class ClassAttributeRow extends ButtonAttributeRow {
	
	private TypeFieldAssistDisposer fTypeFieldAssistDisposer;
	
	public ClassAttributeRow(IContextPart part, ISchemaAttribute att) {
		super(part, att);
	}
	protected boolean isReferenceModel() {
		return !part.getPage().getModel().isEditable();
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.pde.internal.ui.neweditor.plugin.ReferenceAttributeRow#openReference()
	 */
	protected void openReference() {
		String name = PDEJavaHelper.trimNonAlphaChars(text.getText()).replace('$', '.');
		name = PDEJavaHelper.createClass(
				name, getProject(),
				createJavaAttributeValue(name), true);
		if (name != null)
			text.setText(name);
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.pde.internal.ui.editor.plugin.rows.ReferenceAttributeRow#createContents(org.eclipse.swt.widgets.Composite, org.eclipse.ui.forms.widgets.FormToolkit, int)
	 */
	public void createContents(Composite parent, FormToolkit toolkit, int span) {
		super.createContents(parent, toolkit, span);

		if (part.isEditable()) {
			fTypeFieldAssistDisposer = 
				PDEJavaHelper.addTypeFieldAssistToText(text, 
						getProject(),
						IJavaSearchConstants.CLASS_AND_INTERFACE);			
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.pde.internal.ui.neweditor.plugin.ReferenceAttributeRow#browse()
	 */
	protected void browse() {
		BusyIndicator.showWhile(text.getDisplay(), new Runnable() {
			public void run() {
				doOpenSelectionDialog();
			}
		});
	}
	private JavaAttributeValue createJavaAttributeValue(String name) {
		IProject project = part.getPage().getPDEEditor().getCommonProject();
		IPluginModelBase model = (IPluginModelBase) part.getPage().getModel();
		return new JavaAttributeValue(project, model, getAttribute(), name);
	}
	private void doOpenSelectionDialog() {
		IResource resource = getPluginBase().getModel().getUnderlyingResource();
		String type = PDEJavaHelper.selectType(
				resource, 
				IJavaElementSearchConstants.CONSIDER_CLASSES_AND_INTERFACES, 
				text.getText());
		if (type != null)
			text.setText(type);

	}
	private IPluginBase getPluginBase() {
		IBaseModel model = part.getPage().getPDEEditor().getAggregateModel();
		return ((IPluginModelBase) model).getPluginBase();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.plugin.rows.ExtensionAttributeRow#dispose()
	 */
	public void dispose() {
		super.dispose();
		if (fTypeFieldAssistDisposer != null) {
			fTypeFieldAssistDisposer.dispose();
		}		
	}
}
