/*******************************************************************************
 * Copyright (c) 2007, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.api.tools.internal.model.AbstractApiTypeRoot;
import org.eclipse.pde.api.tools.internal.provisional.ApiPlugin;
import org.eclipse.pde.api.tools.internal.provisional.builder.IReference;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiField;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiMember;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiMethod;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiType;
import org.eclipse.pde.api.tools.internal.util.Signatures;
import org.eclipse.pde.api.tools.internal.util.Util;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.ClassNode;

/**
 * Extracts references from a class file
 * 
 * @since 1.0.0
 */
public class ReferenceExtractor extends ClassVisitor {

	/**
	 * A visitor for visiting java 5+ signatures
	 * 
	 * ClassSignature = (visitFormalTypeParameter visitClassBound?
	 * visitInterfaceBound* )* (visitSuperClass visitInterface* )
	 * MethodSignature = (visitFormalTypeParameter visitClassBound?
	 * visitInterfaceBound* )* (visitParameterType visitReturnType
	 * visitExceptionType* ) TypeSignature = visitBaseType | visitTypeVariable |
	 * visitArrayType | (visitClassType visitTypeArgument* (visitInnerClassType
	 * visitTypeArgument* )* visitEnd</tt> ) )
	 */
	class ClassFileSignatureVisitor extends SignatureVisitor {

		protected int kind = -1;
		protected int originalkind = -1;
		protected int argumentcount = 0;
		protected int type = 0;
		protected String signature = null;
		protected String name = null;
		protected List<Reference> references;

		public ClassFileSignatureVisitor() {
			super(Opcodes.ASM5);
			this.references = new ArrayList<Reference>();
		}

		/**
		 * Resets the visitor to its initial state. This method should be called
		 * after processing is done with the visitor
		 */
		protected void reset() {
			// do not reset argument count, as it is needed once the signature
			// visitor is done
			this.kind = -1;
			this.originalkind = -1;
			this.name = null;
			this.signature = null;
			this.type = 0;
			this.references.clear();
		}

		/**
		 * Processes the type specified by the name for the current signature
		 * context. The kind flag is set to a parameterized type as subsequent
		 * calls to this method without visiting other nodes only occurs when we
		 * are processing parameterized types of generic declarations
		 * 
		 * @param name the name of the type
		 */
		protected void processType(String name) {
			Type type = ReferenceExtractor.this.resolveType(Type.getObjectType(name).getDescriptor());
			if (type != null) {
				String tname = type.getClassName();
				if (tname.equals("E") || tname.equals("T")) { //$NON-NLS-1$//$NON-NLS-2$
					type = Type.getObjectType("java.lang.Object"); //$NON-NLS-1$
					tname = type.getClassName();
				}
				if (ReferenceExtractor.this.consider(tname) && this.kind != -1) {
					if (this.name != null && this.signature != null) {
						this.references.add(Reference.typeReference(ReferenceExtractor.this.getMember(), tname, this.signature, this.kind));
					}
				}
			}
			this.kind = this.originalkind;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitClassType(java.
		 * lang.String)
		 */
		@Override
		public void visitClassType(String name) {
			this.processType(name);
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitFormalTypeParameter
		 * (java.lang.String)
		 */
		@Override
		public void visitFormalTypeParameter(String name) {
			if (this.type != TYPE) {
				this.processType(name);
			}
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitTypeVariable(java
		 * .lang.String)
		 */
		@Override
		public void visitTypeVariable(String name) {
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitInnerClassType(
		 * java.lang.String)
		 */
		@Override
		public void visitInnerClassType(String name) {
			this.processType(name);
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitParameterType()
		 */
		@Override
		public SignatureVisitor visitParameterType() {
			this.argumentcount++;
			this.kind = IReference.REF_PARAMETER;
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.signature.SignatureVisitor#visitInterface()
		 */
		@Override
		public SignatureVisitor visitInterface() {
			this.kind = IReference.REF_IMPLEMENTS;
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitExceptionType()
		 */
		@Override
		public SignatureVisitor visitExceptionType() {
			this.kind = IReference.REF_THROWS;
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.signature.SignatureVisitor#visitArrayType()
		 */
		@Override
		public SignatureVisitor visitArrayType() {
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.signature.SignatureVisitor#visitReturnType()
		 */
		@Override
		public SignatureVisitor visitReturnType() {
			this.kind = IReference.REF_RETURNTYPE;
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.signature.SignatureVisitor#visitClassBound()
		 */
		@Override
		public SignatureVisitor visitClassBound() {
			this.kind = IReference.REF_PARAMETERIZED_TYPEDECL;
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitInterfaceBound()
		 */
		@Override
		public SignatureVisitor visitInterfaceBound() {
			this.kind = IReference.REF_PARAMETERIZED_TYPEDECL;
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.signature.SignatureVisitor#visitSuperclass()
		 */
		@Override
		public SignatureVisitor visitSuperclass() {
			this.kind = IReference.REF_EXTENDS;
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.signature.SignatureVisitor#visitTypeArgument(char)
		 */
		@Override
		public SignatureVisitor visitTypeArgument(char wildcard) {
			return this;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.signature.SignatureVisitor#visitEnd()
		 */
		@Override
		public void visitEnd() {
		}

		@Override
		public void visitBaseType(char descriptor) {
			switch (descriptor) {
				case 'J':
				case 'D':
					argumentcount += 2;
					break;
				default:
					this.argumentcount++;
			}
		}

		@Override
		public void visitTypeArgument() {
		}
	}

	/**
	 * Visitor used to visit the methods of a type [ visitCode ( visitFrame |
	 * visit<i>X</i>Insn | visitLabel | visitTryCatchBlock | visitLocalVariable
	 * | visitLineNumber)* visitMaxs ] visitEnd
	 */
	class ClassFileMethodVisitor extends MethodVisitor {
		int argumentcount = 0;
		LinePositionTracker linePositionTracker;
		/**
		 * Most recent string literal encountered. Used to infer
		 * Class.forName("...") references.
		 */
		String stringLiteral;
		String methodName;
		int lastLineNumber;
		boolean implicitConstructor = false;
		LocalLineNumberMarker localVariableMarker;

		HashMap<Label, List<LocalLineNumberMarker>> labelsToLocalMarkers;

		/**
		 * Constructor
		 * 
		 * @param mv
		 */
		public ClassFileMethodVisitor(MethodVisitor mv, String name, int argumentcount) {
			super(Opcodes.ASM5, mv);
			this.argumentcount = argumentcount;
			this.linePositionTracker = new LinePositionTracker();
			this.lastLineNumber = -1;
			this.labelsToLocalMarkers = new HashMap<Label, List<LocalLineNumberMarker>>();
			this.methodName = name;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodAdapter#visitEnd()
		 */
		@Override
		public void visitEnd() {
			this.implicitConstructor = false;
			this.argumentcount = 0;
			ReferenceExtractor.this.exitMember();
			this.linePositionTracker.computeLineNumbers();
			this.labelsToLocalMarkers = null;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodAdapter#visitVarInsn(int, int)
		 */
		@Override
		public void visitVarInsn(int opcode, int var) {
			this.stringLiteral = null;
			switch (opcode) {
				case Opcodes.ASTORE: {
					if (this.lastLineNumber != -1) {
						this.localVariableMarker = new LocalLineNumberMarker(this.lastLineNumber, var);
					}
					break;
				}
				default: {
					break;
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodAdapter#visitFieldInsn(int,
		 * java.lang.String, java.lang.String, java.lang.String)
		 */
		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String desc) {
			int refType = -1;
			switch (opcode) {
				case Opcodes.PUTSTATIC: {
					refType = IReference.REF_PUTSTATIC;
					break;
				}
				case Opcodes.PUTFIELD: {
					refType = IReference.REF_PUTFIELD;
					break;
				}
				case Opcodes.GETSTATIC: {
					refType = IReference.REF_GETSTATIC;
					break;
				}
				case Opcodes.GETFIELD: {
					refType = IReference.REF_GETFIELD;
					break;
				}
				default: {
					break;
				}
			}
			if (refType != -1) {
				Reference reference = ReferenceExtractor.this.addFieldReference(Type.getObjectType(owner), name, refType);
				if (reference != null) {
					this.linePositionTracker.addLocation(reference);
					if (refType == IReference.REF_GETFIELD || refType == IReference.REF_PUTFIELD) {
						ReferenceExtractor.this.fieldtracker.addField(reference);
					}
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.MethodAdapter#visitTryCatchBlock(org.objectweb.
		 * asm.Label, org.objectweb.asm.Label, org.objectweb.asm.Label,
		 * java.lang.String)
		 */
		@Override
		public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
			if (type != null) {
				Type ctype = Type.getObjectType(type);
				Reference reference = ReferenceExtractor.this.addTypeReference(ctype, IReference.REF_CATCHEXCEPTION);
				if (reference != null) {
					this.linePositionTracker.addCatchLabelInfos(reference, handler);
					this.linePositionTracker.addLocation(reference);
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodAdapter#visitLabel(Label)
		 */
		@Override
		public void visitLabel(Label label) {
			this.linePositionTracker.addLabel(label);
			if (this.localVariableMarker != null) {
				List<LocalLineNumberMarker> list = this.labelsToLocalMarkers.get(label);
				if (list != null) {
					list.add(this.localVariableMarker);
				} else {
					list = new ArrayList<LocalLineNumberMarker>();
					list.add(this.localVariableMarker);
					this.labelsToLocalMarkers.put(label, list);
				}
				this.localVariableMarker = null;
			}
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean inf) {
			Type declaringType = Type.getObjectType(owner);
			int kind = -1;
			int flags = 0;
			switch (opcode) {
				case Opcodes.INVOKESPECIAL: {
					kind = ("<init>".equals(name) ? IReference.REF_CONSTRUCTORMETHOD : IReference.REF_SPECIALMETHOD); //$NON-NLS-1$
					if (kind == IReference.REF_CONSTRUCTORMETHOD) {
						if (!implicitConstructor && this.methodName.equals("<init>") && !fSuperStack.isEmpty() && (fSuperStack.peek()).equals(declaringType.getClassName())) { //$NON-NLS-1$
							implicitConstructor = true;
							kind = IReference.REF_SUPER_CONSTRUCTORMETHOD;
						} else {
							Reference reference = ReferenceExtractor.this.addTypeReference(declaringType, IReference.REF_INSTANTIATE);
							if (reference != null) {
								this.linePositionTracker.addLocation(reference);
							}
						}
					}
					break;
				}
				case Opcodes.INVOKESTATIC: {
					kind = IReference.REF_STATICMETHOD;
					// check for reference to a class literal
					if (name.equals("forName")) { //$NON-NLS-1$
						if (ReferenceExtractor.this.processName(owner).equals("java.lang.Class")) { //$NON-NLS-1$
							if (this.stringLiteral != null) {
								try {
									Type classLiteral = Type.getObjectType(this.stringLiteral);
									Reference reference = ReferenceExtractor.this.addTypeReference(classLiteral, IReference.REF_CONSTANTPOOL);
									if (reference != null) {
										this.linePositionTracker.addLocation(reference);
									}
								} catch (Exception e) {
									// do nothing, but prevent bogus strings
									// from causing problems in ASM
									// https://bugs.eclipse.org/bugs/show_bug.cgi?id=399898
								}
							}
						}
					}
					break;
				}
				case Opcodes.INVOKEVIRTUAL: {
					kind = IReference.REF_VIRTUALMETHOD;
					// try to determine if this is a default method
					if (fVersion >= Opcodes.V1_8) {
						IApiMember member = ReferenceExtractor.this.getMember();
						if (member != null) {
							try {
								IApiType type = member.getEnclosingType();
								if (type != null && getDefaultDefined(type, name, desc, false) != null) {
									flags = IReference.F_DEFAULT_METHOD;
								}
							} catch (CoreException ce) {
								// do nothing, give up
							}
						}
					}
					break;
				}
				case Opcodes.INVOKEINTERFACE: {
					kind = IReference.REF_INTERFACEMETHOD;
					break;
				}
				default: {
					break;
				}
			}
			if (kind != -1) {
				Reference reference = ReferenceExtractor.this.addMethodReference(declaringType, name, desc, kind, flags);
				if (reference != null) {
					this.linePositionTracker.addLocation(reference);
					if (kind == IReference.REF_STATICMETHOD) {
						ReferenceExtractor.this.fieldtracker.addAccessor(reference);
					}
				}
			}
			this.stringLiteral = null;
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
			for (Object arg : bsmArgs) {
				if (arg instanceof Handle) {
					Handle handle = (Handle) arg;
					Type declaringType = Type.getObjectType(handle.getOwner());
					Reference reference = ReferenceExtractor.this.addMethodReference(declaringType, handle.getName(), handle.getDesc(), IReference.REF_VIRTUALMETHOD, 0);
					if (reference != null) {
						this.linePositionTracker.addLocation(reference);
					}
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodVisitor#visitInsnAnnotation(int,
		 * org.objectweb.asm.TypePath, java.lang.String, boolean)
		 */
		@Override
		public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
			return null;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.MethodAdapter#visitMultiANewArrayInsn(java.lang
		 * .String, int)
		 */
		@Override
		public void visitMultiANewArrayInsn(String desc, int dims) {
			Type type = this.getTypeFromDescription(desc);
			Reference reference = ReferenceExtractor.this.addTypeReference(type, IReference.REF_ARRAYALLOC);
			if (reference != null) {
				this.linePositionTracker.addLocation(reference);
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodAdapter#visitLineNumber(int,
		 * org.objectweb.asm.Label)
		 */
		@Override
		public void visitLineNumber(int line, Label start) {
			this.lastLineNumber = line;
			this.linePositionTracker.addLineInfo(line, start);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			buffer.append("Method visitor for: "); //$NON-NLS-1$
			buffer.append(methodName);
			buffer.append("\nCurrent line number: "); //$NON-NLS-1$
			buffer.append(lastLineNumber);
			return buffer.toString();
		}

		/**
		 * Creates a type from a type description. Works around bugs creating
		 * types from array type signatures in ASM.
		 * 
		 * @param desc signature
		 * @return Type
		 */
		private Type getTypeFromDescription(String desc) {
			String ldesc = desc;
			while (ldesc.charAt(0) == '[') {
				ldesc = ldesc.substring(1);
			}
			Type type = null;
			if (ldesc.endsWith(";")) { //$NON-NLS-1$
				type = Type.getType(ldesc);
			} else {
				type = Type.getObjectType(ldesc);
			}
			return type;
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodAdapter#visitTypeInsn(int,
		 * java.lang.String)
		 */
		@Override
		public void visitTypeInsn(int opcode, String desc) {
			Type type = this.getTypeFromDescription(desc);
			int kind = -1;
			switch (opcode) {
				case Opcodes.ANEWARRAY: {
					kind = IReference.REF_ARRAYALLOC;
					break;
				}
				case Opcodes.CHECKCAST: {
					kind = IReference.REF_CHECKCAST;
					break;
				}
				case Opcodes.INSTANCEOF: {
					kind = IReference.REF_INSTANCEOF;
					break;
				}
				case Opcodes.NEW: {
					// we can omit the NEW case as it is caught by the
					// constructor call
					// handle it only for anonymous / local types
					List<Reference> refs = fAnonymousTypes.get(processName(type.getInternalName()));
					if (refs != null) {
						for (Iterator<Reference> iterator = refs.iterator(); iterator.hasNext();) {
							this.linePositionTracker.addLocation(iterator.next());
						}
					}
					break;
				}
				default: {
					break;
				}
			}
			if (kind != -1) {
				Reference reference = ReferenceExtractor.this.addTypeReference(type, kind);
				if (reference != null) {
					this.linePositionTracker.addLocation(reference);
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.MethodAdapter#visitLocalVariable(java.lang.String,
		 * java.lang.String, java.lang.String, org.objectweb.asm.Label,
		 * org.objectweb.asm.Label, int)
		 */
		@Override
		public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
			if (desc.length() == 1) {
				// base type
				return;
			}
			if (index > this.argumentcount) {
				List<LocalLineNumberMarker> list = this.labelsToLocalMarkers.get(start);
				int lineNumber = -1;
				if (list != null) {
					// list of potential localMarker
					// iterate the list to find the one that matches the
					// index
					LocalLineNumberMarker removeMarker = null;
					loop: for (Iterator<LocalLineNumberMarker> iterator = list.iterator(); iterator.hasNext();) {
						LocalLineNumberMarker marker = iterator.next();
						if (marker.varIndex == index) {
							lineNumber = marker.lineNumber;
							removeMarker = marker;
							break loop;
						}
					}
					if (removeMarker != null) {
						list.remove(removeMarker);
						if (list.isEmpty()) {
							this.labelsToLocalMarkers.remove(start);
						}
					}
				}
				if (lineNumber == -1) {
					return;
				}
				if (signature != null) {
					List<Reference> references = ReferenceExtractor.this.processSignature(name, signature, IReference.REF_PARAMETERIZED_VARIABLE, METHOD);
					for (Iterator<Reference> iterator = references.iterator(); iterator.hasNext();) {
						Reference reference = iterator.next();
						reference.setLineNumber(lineNumber);
					}
				} else {
					Type type = Type.getType(desc);
					if (type.getSort() == Type.OBJECT) {
						Reference reference = ReferenceExtractor.this.addTypeReference(type, IReference.REF_LOCALVARIABLEDECL);
						if (reference != null) {
							reference.setLineNumber(lineNumber);
						}
					}
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.MethodAdapter#visitLdcInsn(java.lang.Object)
		 */
		@Override
		public void visitLdcInsn(Object cst) {
			if (cst instanceof Type) {
				Type type = (Type) cst;
				Reference reference = ReferenceExtractor.this.addTypeReference(type, IReference.REF_CONSTANTPOOL);
				if (reference != null) {
					this.linePositionTracker.addLocation(reference);
				}
			} else if (cst instanceof String) {
				String str = (String) cst;
				this.stringLiteral = (Util.EMPTY_STRING.equals(str) ? null : str);
			}
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.MethodAdapter#visitAnnotation(java.lang.String,
		 * boolean)
		 */
		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			Type ctype = this.getTypeFromDescription(desc);
			Reference reference = ReferenceExtractor.this.addTypeReference(ctype, IReference.REF_ANNOTATION_USE);
			if (reference != null) {
				linePositionTracker.addLocation(reference);
			}
			return null;
		}
	}

	/**
	 * {@link FieldVisitor} to track use of types in annotations
	 * 
	 * @since 1.0.600
	 */
	class ClassFileFieldVisitor extends FieldVisitor {

		ClassFileFieldVisitor() {
			super(Opcodes.ASM5);
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.FieldVisitor#visitAnnotation(java.lang.String,
		 * boolean)
		 */
		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			addTypeReference(Type.getType(desc), IReference.REF_ANNOTATION_USE);
			return null;
		}

		/*
		 * (non-Javadoc)
		 * @see
		 * org.objectweb.asm.FieldVisitor#visitAttribute(org.objectweb.asm.Attribute
		 * )
		 */
		@Override
		public void visitAttribute(Attribute attr) {
		}

		/*
		 * (non-Javadoc)
		 * @see org.objectweb.asm.FieldVisitor#visitEnd()
		 */
		@Override
		public void visitEnd() {
			exitMember();
		}
	}

	/**
	 * @since 1.1
	 */
	static class FieldTracker {
		HashMap<String, List<Reference>> accessors = new HashMap<String, List<Reference>>();
		ArrayList<Reference> fields = new ArrayList<Reference>();
		ReferenceExtractor extractor = null;

		/**
		 * Constructor
		 */
		public FieldTracker(ReferenceExtractor extractor) {
			this.extractor = extractor;
		}

		/**
		 * Add a field to be tracked
		 * 
		 * @param field
		 */
		public void addField(Reference ref) {
			if (ref != null) {
				fields.add(ref);
			}
		}

		/**
		 * Add an accessor to be tracked
		 * 
		 * @param accessor
		 */
		public void addAccessor(Reference ref) {
			if (ref != null) {
				String key = ref.getReferencedMemberName();
				List<Reference> refs = accessors.get(key);
				if (refs == null) {
					refs = new ArrayList<Reference>();
					accessors.put(key, refs);
				}
				refs.add(ref);
			}

		}

		/**
		 * Resolve any synthetic field access to their accessor
		 */
		public void resolveSyntheticFields() {
			Reference field = null;
			List<Reference> refs = null;
			for (int i = 0; i < fields.size(); i++) {
				field = fields.get(i);
				refs = accessors.get(field.getMember().getName());
				if (refs != null) {
					for (Reference accessor : refs) {
						Reference refer = Reference.fieldReference(accessor.getMember(), field.getReferencedTypeName(), field.getReferencedMemberName(), field.getReferenceKind());
						refer.setLineNumber(accessor.getLineNumber());
						this.extractor.collector.add(refer);
					}
					// we resolved it, remove it
					this.extractor.collector.remove(field);
				}
			}
		}
	}

	static class LinePositionTracker {
		List<Object> labelsAndLocations;
		SortedSet<LineInfo> lineInfos;
		List<LabelInfo> catchLabelInfos;
		HashMap<Label, Integer> lineMap;

		public LinePositionTracker() {
			this.labelsAndLocations = new ArrayList<Object>();
			this.lineInfos = new TreeSet<LineInfo>();
			this.catchLabelInfos = new ArrayList<LabelInfo>();
			this.lineMap = new HashMap<Label, Integer>();
		}

		void addLocation(Reference location) {
			this.labelsAndLocations.add(location);
		}

		void addLineInfo(int line, Label label) {
			this.lineInfos.add(new LineInfo(line, label));
			this.lineMap.put(label, new Integer(line));
		}

		void addCatchLabelInfos(Reference location, Label label) {
			this.catchLabelInfos.add(new LabelInfo(location, label));
		}

		void addLabel(Label label) {
			this.labelsAndLocations.add(label);
		}

		public void computeLineNumbers() {

			if (this.lineInfos.size() < 1 || this.labelsAndLocations.size() < 1) {
				// nothing to do
				return;
			}
			Iterator<LineInfo> lineInfosIterator = this.lineInfos.iterator();
			LineInfo firstLineInfo = lineInfosIterator.next();
			int currentLineNumber = firstLineInfo.line;

			List<LabelInfo> remainingCatchLabelInfos = new ArrayList<LabelInfo>();
			for (Iterator<LabelInfo> iterator = this.catchLabelInfos.iterator(); iterator.hasNext();) {
				LabelInfo catchLabelInfo = iterator.next();
				Integer lineValue = this.lineMap.get(catchLabelInfo.label);
				if (lineValue != null) {
					catchLabelInfo.location.setLineNumber(lineValue.intValue());
				} else {
					remainingCatchLabelInfos.add(catchLabelInfo);
				}
			}
			// Iterate over List of Labels and SourceLocations.
			List<Object> computedEntries = new ArrayList<Object>();
			for (Iterator<Object> iterator = this.labelsAndLocations.iterator(); iterator.hasNext();) {
				Object current = iterator.next();
				if (current instanceof Label) {
					// label
					Integer lineValue = this.lineMap.get(current);
					if (lineValue != null) {
						computedEntries.add(new LineInfo(lineValue.intValue(), (Label) current));
					} else {
						computedEntries.add(current);
					}
				} else {
					// location
					computedEntries.add(current);
				}
			}
			List<LabelInfo> remaingEntriesTemp;
			for (Iterator<Object> iterator = computedEntries.iterator(); iterator.hasNext();) {
				Object current = iterator.next();
				if (current instanceof Label) {
					// try to set the line number for remaining catch labels
					if (remainingCatchLabelInfos != null) {
						remaingEntriesTemp = new ArrayList<LabelInfo>();
						loop: for (Iterator<LabelInfo> catchLabelInfosIterator = remainingCatchLabelInfos.iterator(); catchLabelInfosIterator.hasNext();) {
							LabelInfo catchLabelInfo = catchLabelInfosIterator.next();
							if (!current.equals(catchLabelInfo.label)) {
								remaingEntriesTemp.add(catchLabelInfo);
								continue loop;
							}
							catchLabelInfo.location.setLineNumber(currentLineNumber);
						}
						if (remaingEntriesTemp.size() == 0) {
							remainingCatchLabelInfos = null;
						} else {
							remainingCatchLabelInfos = remaingEntriesTemp;
						}
					}
				} else if (current instanceof Reference) {
					Reference ref = (Reference) current;
					if (ref.getLineNumber() == -1) {
						ref.setLineNumber(currentLineNumber);
					} else {
						currentLineNumber = ref.getLineNumber();
					}
				} else if (current instanceof LineInfo) {
					LineInfo lineInfo = (LineInfo) current;
					currentLineNumber = lineInfo.line;
				}
			}
		}
	}

	static class LabelInfo {
		public Reference location;
		public Label label;

		public LabelInfo(Reference location, Label label) {
			this.location = location;
			this.label = label;
		}

		@Override
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			buffer.append('(').append(this.label).append(',').append(this.location).append(')');
			return String.valueOf(buffer);
		}
	}

	static class LineInfo implements Comparable<Object> {
		int line;
		Label label;

		LineInfo(int line, Label label) {
			this.line = line;
			this.label = label;
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(Object o) {
			return this.line - ((LineInfo) o).line;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof LineInfo) {
				LineInfo lineInfo2 = (LineInfo) obj;
				return this.line == lineInfo2.line && this.label.equals(lineInfo2.label);
			}
			return super.equals(obj);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return this.line + (this.label != null ? this.label.hashCode() : 0);
		}

		@Override
		public String toString() {
			StringBuffer buffer = new StringBuffer();
			buffer.append('(').append(this.line).append(',').append(this.label).append(')');
			return String.valueOf(buffer);
		}
	}

	static class LocalLineNumberMarker {
		int lineNumber;
		int varIndex;

		public LocalLineNumberMarker(int line, int varIndex) {
			this.lineNumber = line;
			this.varIndex = varIndex;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof LocalLineNumberMarker) {
				LocalLineNumberMarker marker = (LocalLineNumberMarker) obj;
				return this.lineNumber == marker.lineNumber && this.varIndex == marker.varIndex;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return this.varIndex;
		}
	}

	/**
	 * The list we collect references in. Entries in the list are of the type
	 * {@link org.eclipse.pde.api.tools.internal.provisional.builder.IReference}
	 */
	Set<Reference> collector = null;

	/**
	 * The full internal name of the class we are extracting references from
	 */
	private String classname = null;

	/**
	 * Current type being visited.
	 */
	IApiType fType;

	/**
	 * Stack of members being visited. When a member is entered its element
	 * descriptor is pushed onto the stack. When a member is exited, the stack
	 * is popped.
	 */
	Stack<IApiMember> fMemberStack = new Stack<IApiMember>();

	/**
	 * Stack of super types *names* (String) being visited. When a type is
	 * entered, its super type is pushed onto the stack. When a type is exited,
	 * the stack is popped.
	 */
	Stack<String> fSuperStack = new Stack<String>();

	/**
	 * Mapping of anonymous type names to their reference
	 */
	HashMap<String, List<Reference>> fAnonymousTypes = new HashMap<String, List<Reference>>();

	/**
	 * Whether to extract references to elements within the classfile being
	 * scanned.
	 */
	private boolean fIncludeLocalRefs = false;

	/**
	 * Bit mask of {@link ReferenceModifiers} to extract.
	 */
	private int fReferenceKinds = 0;

	/**
	 * Track synthetic field / accessor
	 * 
	 * @since 1.1
	 */
	FieldTracker fieldtracker = null;

	/**
	 * The version for the class being visited
	 * 
	 * @since 1.0.600
	 */
	private int fVersion;

	/**
	 * Bit mask that determines if we need to visit members
	 */
	private static final int VISIT_MEMBERS_MASK = IReference.MASK_REF_ALL ^ (IReference.REF_EXTENDS | IReference.REF_IMPLEMENTS);

	/**
	 * If members should be visited for type visits
	 */
	private boolean fIsVisitMembers = false;

	/**
	 * Current field being visited, or <code>null</code> (when not within a
	 * field).
	 */
	private ClassFileSignatureVisitor signaturevisitor = new ClassFileSignatureVisitor();
	static int TYPE = 0, FIELD = 1, METHOD = 2;

	/**
	 * {@link FieldVisitor} used to track and collect references to annotation
	 * types
	 * 
	 * @since 1.0.600
	 */
	private ClassFileFieldVisitor fieldvisitor = new ClassFileFieldVisitor();

	/**
	 * Constructor
	 * 
	 * @param type the type to extract references from
	 * @param collector the listing of references to annotate from this pass
	 * @param referenceKinds kinds of references to extract as defined by
	 *            {@link ReferenceModifiers}
	 */
	public ReferenceExtractor(IApiType type, Set<Reference> collector, int referenceKinds) {
		super(Opcodes.ASM5, new ClassNode());
		fType = type;
		this.collector = collector;
		fReferenceKinds = referenceKinds;
		fIsVisitMembers = (VISIT_MEMBERS_MASK & fReferenceKinds) > 0;
		fieldtracker = new FieldTracker(this);
	}

	/**
	 * Constructor
	 * 
	 * @param type
	 * @param collector
	 * @param referenceKinds
	 * @param tracker
	 */
	protected ReferenceExtractor(IApiType type, Set<Reference> collector, int referenceKinds, FieldTracker tracker) {
		super(Opcodes.ASM5, new ClassNode());
		fType = type;
		this.collector = collector;
		fReferenceKinds = referenceKinds;
		fIsVisitMembers = (VISIT_MEMBERS_MASK & fReferenceKinds) > 0;
		fieldtracker = tracker;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Reference extractor for: "); //$NON-NLS-1$
		buffer.append(fType.getName());
		buffer.append("\n"); //$NON-NLS-1$
		buffer.append("Reference kinds: "); //$NON-NLS-1$
		buffer.append(Reference.getReferenceText(fReferenceKinds));
		buffer.append("\n"); //$NON-NLS-1$
		buffer.append("Is visiting members: "); //$NON-NLS-1$
		buffer.append(fIsVisitMembers);
		return buffer.toString();
	}

	/**
	 * Returns whether to consider a reference to the specified type. Configured
	 * by setting to include references within the same class file.
	 * 
	 * @param owner
	 * @return true if considered, false otherwise
	 */
	protected boolean consider(String owner) {
		if (this.fIncludeLocalRefs) {
			return true;
		}
		return !(this.classname.equals(owner) || this.classname.startsWith(owner) || "<clinit>".equals(owner) || "this".equals(owner)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Returns whether the specified reference should be considered when
	 * extracting references. Configured by setting on whether to include
	 * references within the same class file.
	 * 
	 * @param ref reference
	 * @return whether to include the reference
	 */
	protected boolean consider(Reference ref) {
		int kind = ref.getReferenceKind();
		if ((kind & fReferenceKinds) == 0) {
			return false;
		}
		if (this.fIncludeLocalRefs) {
			return true;
		}
		// don't consider references to anonymous types or elements in them
		String referencedTypeName = ref.getReferencedTypeName();
		if (kind == IReference.REF_VIRTUALMETHOD || kind == IReference.REF_OVERRIDE || kind == IReference.REF_GETFIELD || kind == IReference.REF_PUTFIELD) {
			return true;
		}
		if (referencedTypeName.startsWith(fType.getName())) {
			// don't include references within this type or a member type
			if (referencedTypeName.length() > fType.getName().length()) {
				return referencedTypeName.charAt(fType.getName().length()) != '$';
			}
			return false;
		}
		return true;
	}

	/**
	 * Returns the full internal name (if available) from the given simple name.
	 * The returned name has been modified to be '.' separated
	 * 
	 * @param name
	 * @return
	 */
	protected String processName(String name) {
		String newname = name;
		Type type = Type.getObjectType(name);
		if (type != null && type.getSort() == Type.OBJECT) {
			newname = type.getInternalName();
		}
		return newname.replaceAll("/", "."); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Adds a reference to the given type from the current member. Discards the
	 * reference if the type corresponds to the class file being scanned or if
	 * the type is a primitive type.
	 * 
	 * @param type referenced type
	 * @param linenumber line number where referenced
	 * @param kind kind of reference
	 * @return reference added, or <code>null</code> if none
	 */
	protected Reference addTypeReference(Type type, int kind) {
		Type rtype = this.resolveType(type.getDescriptor());
		if (rtype != null) {
			return addReference(Reference.typeReference(getMember(), rtype.getClassName(), kind));
		}
		return null;
	}

	/**
	 * Adds a reference to the given field from the current member. Discards the
	 * reference if the field is defined in the class file being scanned.
	 * 
	 * @param declaringType type declaring the field being referenced
	 * @param name of the field being referenced
	 * @param linenumber line number where referenced
	 * @param kind kind of reference
	 * @return reference added, or <code>null</code> if none
	 */
	protected Reference addFieldReference(Type declaringType, String name, int kind) {
		Type rtype = this.resolveType(declaringType.getDescriptor());
		if (rtype != null) {
			return addReference(Reference.fieldReference(getMember(), rtype.getClassName(), name, kind));
		}
		return null;
	}

	/**
	 * Adds a reference to the given method from the current member. Discards
	 * the reference if the method is defined in the class file being scanned.
	 * 
	 * @param declaringType type declaring the method (but could be a virtual
	 *            lookup)
	 * @param name of the method being referenced
	 * @param signature signature of the method
	 * @param linenumber line number where referenced
	 * @param kind kind of reference
	 * @param flags the flags for the reference
	 * @return reference added, or <code>null</code> if none
	 */
	protected Reference addMethodReference(Type declaringType, String name, String signature, int kind, int flags) {
		Type rtype = this.resolveType(declaringType.getDescriptor());
		if (rtype != null) {
			return this.addReference(Reference.methodReference(getMember(), rtype.getClassName(), name, signature, kind, flags));
		}
		return null;
	}

	/**
	 * Adds a reference to the given target member from the given line number in
	 * the class file being scanned. If the target member is contained in the
	 * class file being scanned it is discarded based on the setting to include
	 * local references.
	 * 
	 * @param target reference
	 * @param reference added, or <code>null</code> if none
	 */
	protected Reference addReference(Reference target) {
		if (this.consider(target)) {
			this.collector.add(target);
			return target;
		}
		return null;
	}

	/**
	 * Processes the member signature from the specified type with the given
	 * signature and kind. A member can be either a type, method, field or local
	 * variable
	 * 
	 * @param name the name of the member to process
	 * @param signature the signature of the member to process
	 * @param kind the kind
	 * @param type the type of member wanting to use the visitor
	 * 
	 * @return the collection of references created for this signature
	 */
	protected List<Reference> processSignature(String name, String signature, int kind, int type) {
		SignatureReader reader = new SignatureReader(signature);
		this.signaturevisitor.kind = kind;
		this.signaturevisitor.name = this.processName(name);
		this.signaturevisitor.signature = signature;
		this.signaturevisitor.originalkind = kind;
		this.signaturevisitor.argumentcount = 0;
		this.signaturevisitor.type = type;
		if (kind == IReference.REF_PARAMETERIZED_TYPEDECL || kind == IReference.REF_PARAMETERIZED_METHODDECL) {
			reader.accept(this.signaturevisitor);
		} else {
			reader.acceptType(this.signaturevisitor);
		}
		List<Reference> result = new ArrayList<Reference>();
		result.addAll(this.signaturevisitor.references);
		this.collector.addAll(this.signaturevisitor.references);
		this.signaturevisitor.reset();
		return result;
	}

	/**
	 * Resolves the type from the string description. This method takes only
	 * type descriptions as a parameter, all else will throw an exception from
	 * the ASM framework If the description is an array, the underlying type of
	 * the array is returned.
	 * 
	 * @param desc
	 * @return the {@link Type} of the description or <code>null</code>
	 */
	protected Type resolveType(String desc) {
		Type type = Type.getType(desc);
		if (type.getSort() == Type.OBJECT) {
			return type;
		}
		if (type.getSort() == Type.ARRAY) {
			type = type.getElementType();
			if (type.getSort() == Type.OBJECT) {
				return type;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.objectweb.asm.ClassVisitor#visit(int, int, java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.fVersion = version;
		this.classname = this.processName(name);
		if (ApiPlugin.DEBUG_REFERENCE_EXTRACTOR) {
			System.out.println("Starting visit of type: [" + this.fType.getName() + "]"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		this.enterMember(this.fType);
		// if there is a signature we get more information from it, so we don't
		// need to do both
		if (signature != null) {
			this.processSignature(name, signature, IReference.REF_PARAMETERIZED_TYPEDECL, TYPE);
		} else {
			if ((access & Opcodes.ACC_INTERFACE) != 0) {
				// the type is an interface and we need to treat the interfaces
				// set as extends, not implements
				Type supertype = null;
				for (int i = 0; i < interfaces.length; i++) {
					supertype = Type.getObjectType(interfaces[i]);
					this.addTypeReference(supertype, IReference.REF_EXTENDS);
					this.fSuperStack.add(supertype.getClassName());
				}
			} else {
				Type supertype = null;
				if (superName != null) {
					supertype = Type.getObjectType(superName);
					this.addTypeReference(supertype, IReference.REF_EXTENDS);
					this.fSuperStack.add(supertype.getClassName());
				}
				for (int i = 0; i < interfaces.length; i++) {
					supertype = Type.getObjectType(interfaces[i]);
					this.addTypeReference(supertype, IReference.REF_IMPLEMENTS);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.objectweb.asm.ClassVisitor#visitEnd()
	 */
	@Override
	public void visitEnd() {
		this.exitMember();
		if (!this.fSuperStack.isEmpty()) {
			String typeName = this.fSuperStack.pop();
			if (ApiPlugin.DEBUG_REFERENCE_EXTRACTOR) {
				System.out.println("ending visit of type: [" + typeName + "]"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		if (!this.fType.isMemberType()) {
			fieldtracker.resolveSyntheticFields();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.objectweb.asm.ClassVisitor#visitField(int, java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.Object)
	 */
	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		if (fIsVisitMembers) {
			IApiType owner = (IApiType) this.getMember();
			IApiField field = owner.getField(name);
			if (field == null) {
				ApiPlugin.log(new Status(IStatus.WARNING, ApiPlugin.PLUGIN_ID, NLS.bind(BuilderMessages.ReferenceExtractor_failed_to_lookup_field, new String[] {
						name, Signatures.getQualifiedTypeSignature(owner) })));
				// if we can't find the method there is no point trying to
				// process it
				return null;
			}
			this.enterMember(field);
			if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
				if (signature != null) {
					this.processSignature(name, signature, IReference.REF_PARAMETERIZED_FIELDDECL, FIELD);
				} else {
					this.addTypeReference(Type.getType(desc), IReference.REF_FIELDDECL);
				}
			} else {
				fieldtracker.addField(addTypeReference(Type.getType(desc), IReference.REF_FIELDDECL));
			}
			return fieldvisitor;
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.objectweb.asm.ClassAdapter#visitInnerClass(java.lang.String,
	 * java.lang.String, java.lang.String, int)
	 */
	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		try {
			String pname = processName(name);
			if (fType.getName().equals(pname) || !pname.startsWith(fType.getName())) {
				return;
			}
			IApiComponent comp = fType.getApiComponent();
			if (comp == null) {
				return;
			}
			AbstractApiTypeRoot root = (AbstractApiTypeRoot) comp.findTypeRoot(pname);
			if (root != null) {
				IApiType type = root.getStructure();
				if (type == null) {
					// do nothing for a bad classfile
					return;
				}
				Set<Reference> refs = processInnerClass(type, fReferenceKinds);
				if (type.isAnonymous() || type.isLocal()) {
					// visit the class files for the dependent anonymous and
					// local inner types
					// set a line number for all references with no line numbers
					List<Reference> allRefs = new ArrayList<Reference>();
					for (Reference reference : refs) {
						if (reference.getLineNumber() < 0) {
							allRefs.add(reference);
						}
					}
					fAnonymousTypes.put(pname, allRefs);
				}
				if (refs != null && !refs.isEmpty()) {
					this.collector.addAll(refs);
				}
			}
		} catch (CoreException ce) {
		}
	}

	/**
	 * Processes the dependent inner class
	 * 
	 * @param type
	 * @param refkinds
	 * @return
	 * @throws CoreException
	 */
	private Set<Reference> processInnerClass(IApiType type, int refkinds) throws CoreException {
		HashSet<Reference> refs = new HashSet<Reference>();
		ReferenceExtractor extractor = new ReferenceExtractor(type, refs, refkinds, this.fieldtracker);
		ClassReader reader = new ClassReader(((AbstractApiTypeRoot) type.getTypeRoot()).getContents());
		reader.accept(extractor, ClassReader.SKIP_FRAMES);
		return refs;
	}

	/*
	 * (non-Javadoc)
	 * @see org.objectweb.asm.ClassAdapter#visitAnnotation(java.lang.String,
	 * boolean)
	 */
	@Override
	public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
		try {
			addTypeReference(Type.getType(desc), IReference.REF_ANNOTATION_USE);
		} catch (ArrayIndexOutOfBoundsException e) {
			// when file has compile errors this gets thrown, but we can ignore
			// it
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.objectweb.asm.ClassVisitor#visitMethod(int, java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String[])
	 */
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		if (fIsVisitMembers) {
			IApiMember member = this.getMember();
			IApiType owner = null;
			if (member instanceof IApiType) {
				owner = (IApiType) member;
			} else {
				try {
					owner = member.getEnclosingType();
				} catch (CoreException e) {
					// should not happen for field or method
					ApiPlugin.log(e.getStatus());
				}
			}
			if (owner == null) {
				return null;
			}
			IApiMethod method = owner.getMethod(name, desc);
			if (method == null) {
				ApiPlugin.log(new Status(IStatus.WARNING, ApiPlugin.PLUGIN_ID, NLS.bind(BuilderMessages.ReferenceExtractor_failed_to_lookup_method, new String[] {
						name, desc, Signatures.getQualifiedTypeSignature(owner) })));
				// if we can't find the method there is no point trying to
				// process it
				return null;
			}
			this.enterMember(method);
			// record potential method override reference
			if ((access & (Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC)) > 0) {
				try {
					IApiType def = null;
					if (fVersion >= Opcodes.V1_8) {
						// See if we are overriding a default interface method
						def = getDefaultDefined(owner, name, desc, true);
					}
					if (def != null) {
						addReference(Reference.methodReference(method, def.getName(), method.getName(), method.getSignature(), IReference.REF_OVERRIDE, IReference.F_DEFAULT_METHOD));
					} else if (!this.fSuperStack.isEmpty()) {
						String superTypeName = this.fSuperStack.peek();
						addReference(Reference.methodReference(method, superTypeName, method.getName(), method.getSignature(), IReference.REF_OVERRIDE));
					}
				} catch (CoreException e) {
					// Do nothing, skip this reference
				}
			}
			int argumentcount = 0;
			if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
				if (signature != null) {
					this.processSignature(name, signature, IReference.REF_PARAMETERIZED_METHODDECL, METHOD);
					argumentcount = this.signaturevisitor.argumentcount;
				} else {
					Type[] arguments = Type.getArgumentTypes(desc);
					for (int i = 0; i < arguments.length; i++) {
						Type type = arguments[i];
						this.addTypeReference(type, IReference.REF_PARAMETER);
						argumentcount += type.getSize();
					}
					this.addTypeReference(Type.getReturnType(desc), IReference.REF_RETURNTYPE);
					if (exceptions != null) {
						for (int i = 0; i < exceptions.length; i++) {
							this.addTypeReference(Type.getObjectType(exceptions[i]), IReference.REF_THROWS);
						}
					}
				}
			}
			MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
			if (mv != null && ((access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0)) {
				return new ClassFileMethodVisitor(mv, name, argumentcount);
			}
		}
		return null;
	}

	/**
	 * Called when a member is entered. Pushes the member onto the member stack.
	 * 
	 * @param member current member
	 */
	protected void enterMember(IApiMember member) {
		this.fMemberStack.push(member);
	}

	/**
	 * Called when a member is exited. Pops the top member off the stack.
	 */
	protected void exitMember() {
		this.fMemberStack.pop();
	}

	/**
	 * Returns the member currently being visited.
	 * 
	 * @return current member
	 */
	protected IApiMember getMember() {
		return this.fMemberStack.peek();
	}

	/**
	 * Find out if the method declaration is a default method and return the
	 * type defining it. Uses the JLS specified order of lookup between
	 * superclasses and superinterfaces.
	 * 
	 * @param type the type used as a starting point for the search, will not be
	 *            searched if <code>isOverride</code> is <code>true</code>
	 * @param name name of the method
	 * @param signature signature of the method
	 * @param isOverride is <code>true</code> the provided IApiType will not be
	 *            searched for a declaration
	 * @return the IApiType containing the default method definition or
	 *         <code>null</code>
	 * @throws CoreException
	 */
	static IApiType getDefaultDefined(IApiType type, String name, String signature, boolean isOverride) throws CoreException {
		if (type != null) {
			if (!isOverride) {
				IApiMethod method = type.getMethod(name, signature);
				if (method != null) {
					if (method.isDefaultMethod()) {
						return type;
					}
				}
			}
			// TODO We should skip checking super class if it is
			// java.lang.Object (or system library class)
			IApiType superclass = getDefaultDefined(type.getSuperclass(), name, signature, false);
			if (superclass != null) {
				return superclass;
			}
			IApiType ints[] = type.getSuperInterfaces();
			for (int i = 0; i < ints.length; i++) {
				IApiType superint = getDefaultDefined(ints[i], name, signature, false);
				if (superint != null) {
					return superint;
				}
			}
		}
		return null;
	}
	
	

}
