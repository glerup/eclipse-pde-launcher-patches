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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.pde.api.tools.internal.provisional.builder.IReference;
import org.eclipse.pde.api.tools.internal.provisional.descriptors.IElementDescriptor;
import org.eclipse.pde.api.tools.internal.provisional.descriptors.IReferenceTypeDescriptor;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiMember;
import org.eclipse.pde.api.tools.internal.provisional.problems.IApiProblem;


/**
 * Base implementation of a problem detector for type references
 * 
 * @since 1.1
 * @noextend This class is not intended to be subclassed by clients.
 */
public abstract class AbstractIllegalTypeReference extends AbstractProblemDetector {

	/**
	 * Map of fully qualified type names to associated component IDs that
	 * represent illegal references 
	 */
	private Map fIllegalTypes = new HashMap();
	
	/**
	 * Adds the given type as not to be extended.
	 * 
	 * @param type a type that is marked no extend
	 * @param componentId the component the type is located in
	 */
	void addIllegalType(IReferenceTypeDescriptor type, String componentId) {
		fIllegalTypes.put(type.getQualifiedName(), componentId);
	}	
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.provisional.search.IApiProblemDetector#considerReference(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	public boolean considerReference(IReference reference) {
		if (fIllegalTypes.containsKey(reference.getReferencedTypeName())) {
			retainReference(reference);
			return true;
		}
		return false;
	}	
	
	/**
	 * Returns if the mapping contains the referenced type name
	 * @param reference
	 * @return true of the mapping contains the key false otherwise
	 */
	protected boolean isIllegalType(IReference reference) {
		return fIllegalTypes.containsKey(reference.getReferencedTypeName());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#isProblem(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected boolean isProblem(IReference reference) {
		try {
			IApiMember type = reference.getResolvedReference();
			Object componentId = fIllegalTypes.get(type.getName());
			return componentId != null && type.getApiComponent().getId().equals(componentId);
		} catch (CoreException e) {
			return false;
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getSourceRange(org.eclipse.jdt.core.IType, org.eclipse.jface.text.IDocument, org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected Position getSourceRange(IType type, IDocument doc, IReference reference) throws CoreException, BadLocationException {
		ISourceRange range = type.getNameRange();
		Position pos = null;
		if(range != null) {
			pos = new Position(range.getOffset(), range.getLength());
		}
		if(pos == null) {
			noSourcePosition(type, reference);
		}
		return pos; 
	}	
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getElementType(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected int getElementType(IReference reference) {
		return IElementDescriptor.TYPE;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getMessageArgs(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected String[] getMessageArgs(IReference reference) throws CoreException {
		return new String[] {getSimpleTypeName(reference.getResolvedReference()), getSimpleTypeName(reference.getMember())};
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getQualifiedMessageArgs(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected String[] getQualifiedMessageArgs(IReference reference) throws CoreException {
		return new String[] {getTypeName(reference.getResolvedReference()), getTypeName(reference.getMember())};
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.internal.search.AbstractProblemDetector#getProblemFlags(org.eclipse.pde.api.tools.internal.provisional.model.IReference)
	 */
	protected int getProblemFlags(IReference reference) {
		return IApiProblem.NO_FLAGS;
	}
}