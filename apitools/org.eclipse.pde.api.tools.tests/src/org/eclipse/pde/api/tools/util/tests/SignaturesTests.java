/*******************************************************************************
 * Copyright (c) 2008, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.util.tests;

import junit.framework.TestCase;

import org.eclipse.jdt.core.Flags;
import org.eclipse.pde.api.tools.internal.model.ApiField;
import org.eclipse.pde.api.tools.internal.model.ApiMethod;
import org.eclipse.pde.api.tools.internal.model.ApiType;
import org.eclipse.pde.api.tools.internal.provisional.descriptors.IMethodDescriptor;
import org.eclipse.pde.api.tools.internal.util.Signatures;

/**
 * Test class for the {@link Signatures} utility class
 * 
 * @since 1.0.0
 */
public class SignaturesTests extends TestCase {

	/**
	 * Constructor
	 * @param name
	 */
	public SignaturesTests(String name) {
		super(name);
	}

	/**
	 * Tests the {@link Signatures#dequalifySignature(String)} method
	 */
	public void testDequalifySignatures() {
		assertEquals("Wrong conversion", "(QObject;QException;)V", Signatures.dequalifySignature("(Ljava/lang/Object;Ljava/lang/Exception;)V")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "(QObject;QException;)QException;", Signatures.dequalifySignature("(Ljava/lang/Object;Ljava/lang/Exception;)Ljava/lang/Exception;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "(IJCQObject;IJCQException;IJC)I", Signatures.dequalifySignature("(IJCLjava/lang/Object;IJCLjava/lang/Exception;IJC)I")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "([IJC[[[QObject;IJCQException;IJC)I", Signatures.dequalifySignature("([IJC[[[Ljava/lang/Object;IJCLjava/lang/Exception;IJC)I")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "(QObject;QException;)V", Signatures.dequalifySignature("(Ljava.lang.Object;Ljava.lang.Exception;)V")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "(QObject;QException;)QException;", Signatures.dequalifySignature("(Ljava.lang.Object;Ljava.lang.Exception;)Ljava.lang.Exception;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "(IJCQObject;IJCQException;IJC)I", Signatures.dequalifySignature("(IJCLjava.lang.Object;IJCLjava.lang.Exception;IJC)I")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "([IJC[[[QObject;IJCQException;IJC)I", Signatures.dequalifySignature("([IJC[[[Ljava.lang.Object;IJCLjava.lang.Exception;IJC)I")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "(QList;)QList;", Signatures.dequalifySignature("(Ljava.util.List;)Ljava.util.List;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("wrong conversion", "(QList;)QList;", Signatures.dequalifySignature("(QList;)QList;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("wrong converstion", "(QLanguage;)V;", Signatures.dequalifySignature("(Lfoo.test.Language;)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("wrong converstion", "(QJokes;)V;", Signatures.dequalifySignature("(Lfoo.test.Jokes;)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("wrong conversion", "(QDiff;)Z", Signatures.dequalifySignature("(LDiff;)Z")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong conversion", "(QList<QString;>;)QList;", Signatures.dequalifySignature("(Ljava.util.List<Ljava.lang.String;>;)Ljava.util.List;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * Tests the {@link Signatures#isQualifiedSignature(String)} method
	 */
	public void testIsQualifiedSignature() {
		assertTrue("should return true", Signatures.isQualifiedSignature("(Ljava/lang/Object;Ljava/lang/Exception;)V")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("should return false", !Signatures.isQualifiedSignature("(IJCQObject;IJCQException;IJC)I")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("should return true", Signatures.isQualifiedSignature("(IJCLjava.lang.Object;IJCLjava.lang.Exception;IJC)I")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("should return false", !Signatures.isQualifiedSignature("([IJC[[[QObject;IJCQException;IJC)I")); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#processMethodSignature(org.eclipse.pde.api.tools.internal.provisional.model.IApiMethod)} method
	 */
	public void testProcessMethodSignature() {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		ApiMethod method = type.addMethod("m1", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Signature processed incorrectly", "()V;", Signatures.processMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m2", "(Ljava.lang.String;)Ljava.util.List;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Signature processed incorrectly", "(QString;)QList;", Signatures.processMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m3", "(I[Ljava.lang.String;J)[Ljava.lang.Integer;", null, Flags.AccPublic, new String[] {"Ljava.lang.Throwable"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Signature processed incorrectly", "(I[QString;J)[QInteger;", Signatures.processMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m4", "(ILjava.util.List;J)[Ljava.lang.Integer;", "(ILjava.util.List<Ljava.lang.String;>;J)[Ljava.lang.Integer;", Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Signature procesed incorrectly", "(IQList<QString;>;J)[QInteger;", Signatures.processMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getMethodName(org.eclipse.pde.api.tools.internal.provisional.model.IApiMethod)} method
	 * @throws Exception
	 */
	public void testGetMethodName() throws Exception {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		ApiMethod method = type.addMethod("m1", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong method name", "m1", Signatures.getMethodName(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("<init>", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong method name", "Parent", Signatures.getMethodName(method)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getMethodSignature(org.eclipse.pde.api.tools.internal.provisional.model.IApiMethod)} method
	 * @throws Exception
	 */
	public void testGetMethodSignature() throws Exception {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		ApiMethod method = type.addMethod("m1", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong method signature returned", "m1()", Signatures.getMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("<init>", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong method signature returned", "Parent()", Signatures.getMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m3", "(I[Ljava.lang.String;J)Ljava.util.List;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong method signature returned", "m3(int, String[], long)", Signatures.getMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m4", "(ILjava.util.List;J)[Ljava.lang.Integer;", "(ILjava.util.List<Ljava.lang.String;>;J)[Ljava.lang.Integer;", Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong method signature returned", "m4(int, List<String>, long)", Signatures.getMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getQualifiedMethodSignature(org.eclipse.pde.api.tools.internal.provisional.model.IApiMethod)} method
	 * @throws Exception
	 */
	public void testGetQualifiedMethodSignature() throws Exception {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null);  //$NON-NLS-1$//$NON-NLS-2$
		ApiMethod method = type.addMethod("m1", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent.m1()", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("<init>", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent.Parent()", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m2", "(I[Ljava.lang.String;J)Ljava.util.List;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent.m2(int, String[], long)", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m3", "(ILjava.util.List;J)[Ljava.lang.Integer;", "(ILjava.util.List<Ljava.lang.String;>;J)[Ljava.lang.Integer;", Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent.m3(int, List<String>, long)", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent2", "Lx.y.z.Parent2;", "<T:Ljava/lang/Object;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		method = type.addMethod("<init>", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent2<T>.Parent2()", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m2", "(I[Ljava.lang.String;J)Ljava.util.List;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent2<T>.m2(int, String[], long)", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent3", "Lx.y.z.Parent3;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		method = type.addMethod("<init>", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent3<T, E>.Parent3()", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m2", "(I[Ljava.lang.String;J)Ljava.util.List;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent3<T, E>.m2(int, String[], long)", Signatures.getQualifiedMethodSignature(method)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getQualifiedMethodSignature(org.eclipse.pde.api.tools.internal.provisional.descriptors.IMethodDescriptor, boolean)} method
	 * @throws Exception
	 */
	public void testGetQialifiedMethodSignature2() throws Exception {
		ApiType type = new ApiType(null, "x.y.z.Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		ApiMethod method = type.addMethod("m1", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent.m1() : void", Signatures.getQualifiedMethodSignature((IMethodDescriptor) method.getHandle(), false, true)); //$NON-NLS-1$ //$NON-NLS-2$
		method = type.addMethod("m2", "(I[Ljava.lang.String;J)Ljava.util.List;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent.m2(int, String[], long) : java.util.List", Signatures.getQualifiedMethodSignature((IMethodDescriptor) method.getHandle(), false, true)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "x.y.z.Parent2", "Lx.y.z.Parent2;", "<T:Ljava/lang/Object;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		method = type.addMethod("<init>", "()V;", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong qualified method signature returned", "x.y.z.Parent2.Parent2() : void", Signatures.getQualifiedMethodSignature((IMethodDescriptor) method.getHandle(), false, true)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getTypeSignature(String, String, boolean)} method
	 */
	public void testGetTypeSignature() {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong type signature returned", "Parent", Signatures.getTypeSignature(type.getSignature(), type.getGenericSignature(), false)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong type signature returned", "x.y.z.Parent", Signatures.getTypeSignature(type.getSignature(), type.getGenericSignature(), true)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent2", "Lx.y.z.Parent2;", "<T:Ljava/lang/Object;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "Parent2<T>", Signatures.getTypeSignature(type.getSignature(), type.getGenericSignature(), false)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong type signature returned", "x.y.z.Parent2<T>", Signatures.getTypeSignature(type.getSignature(), type.getGenericSignature(), true)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent3", "Lx.y.z.Parent3;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "Parent3<T, E>", Signatures.getTypeSignature(type.getSignature(), type.getGenericSignature(), false)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong type signature returned", "x.y.z.Parent3<T, E>", Signatures.getTypeSignature(type.getSignature(), type.getGenericSignature(), true)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getTypeSignature(org.eclipse.pde.api.tools.internal.provisional.model.IApiType)} method
	 */
	public void testGetTypeSignature2() {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong type signature returned", "Parent", Signatures.getTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent2", "Lx.y.z.Parent2;", "<T:Ljava/lang/Object;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "Parent2<T>", Signatures.getTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent3", "Lx.y.z.Parent3;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "Parent3<T, E>", Signatures.getTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent4", "Lx.y.z.Parent4$inner;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "Parent4.inner<T, E>", Signatures.getTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getQualifiedTypeSignature(org.eclipse.pde.api.tools.internal.provisional.model.IApiType)} method
	 */
	public void testGetQualifiedTypeSignature() {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong type signature returned", "x.y.z.Parent", Signatures.getQualifiedTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent2", "Lx.y.z.Parent2;", "<T:Ljava/lang/Object;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "x.y.z.Parent2<T>", Signatures.getQualifiedTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent3", "Lx.y.z.Parent3;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "x.y.z.Parent3<T, E>", Signatures.getQualifiedTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent4", "Lx.y.z.Parent4$inner;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type signature returned", "x.y.z.Parent4.inner<T, E>", Signatures.getQualifiedTypeSignature(type)); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getAnonymousTypeName(String)} method
	 */
	public void getAnonymousTypeName() {
		assertEquals("Wrong anonymous name returned", null, Signatures.getAnonymousTypeName("Test$3")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong anonymous name returned", null, Signatures.getAnonymousTypeName("x.y.z.Test$3")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong anonymous name returned", null, Signatures.getAnonymousTypeName("x.y.z.Test$3$4local$5")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong anonymous name returned", "local", Signatures.getAnonymousTypeName("Test$1local")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong anonymous name returned", "local", Signatures.getAnonymousTypeName("x.y.z.Test$1local")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong anonymous name returned", "local2", Signatures.getAnonymousTypeName("x.y.z.Test$1local$2$5local2")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong anonymous name returned", null, Signatures.getAnonymousTypeName("x.y.z.Test$local")); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#appendTypeParameters(StringBuffer, String[])} method
	 */
	public void testAppendTypeParameters() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("Type"); //$NON-NLS-1$
		Signatures.appendTypeParameters(buffer, null);
		assertEquals("Should be no type parameters appended", "Type", buffer.toString()); //$NON-NLS-1$ //$NON-NLS-2$
		Signatures.appendTypeParameters(buffer, new String[] {});
		assertEquals("Should be no type parameters appended", "Type", buffer.toString()); //$NON-NLS-1$ //$NON-NLS-2$
		Signatures.appendTypeParameters(buffer, new String[] {"T:Ljava.lang.Object;"}); //$NON-NLS-1$
		assertEquals("Should be type parameters appended", "Type<T>", buffer.toString()); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getComma()} method
	 */
	public void testGetComma() {
		assertEquals("Incorrect comma returned", ", ", Signatures.getComma()); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getGT()} method
	 */
	public void testGetGT() {
		assertEquals("Incorrect '>' returned", ">", Signatures.getGT()); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getLT()} method
	 */
	public void testGetLT() {
		assertEquals("Incorrect '<' returned", "<", Signatures.getLT()); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getTypeName(String)} method
	 */
	public void testGetTypeName() {
		assertEquals("Wrong type name returned", "Clazz", Signatures.getTypeName("Clazz")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type name returned", "Clazz", Signatures.getTypeName("a.Clazz")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type name returned", "Clazz", Signatures.getTypeName("a.b.c.Clazz")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type name returned", "Clazz<T>", Signatures.getTypeName("Clazz<T>")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type name returned", "Clazz$Inner", Signatures.getTypeName("Clazz$Inner")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("Wrong type name returned", "Clazz$Inner", Signatures.getTypeName("a.b.c.Clazz$Inner")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * Tests the {@link Signatures#matchesSignatures(String, String)} method
	 */
	public void testMatchesSignatures() {
		assertTrue("Signatures should match", Signatures.matchesSignatures("()V;", "()V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue("Signatures should match", Signatures.matchesSignatures("(ILjava.lang.String;)V;", "(ILjava.lang.String;)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue("Signatures should match", Signatures.matchesSignatures("(ILjava.util.List<Ljava.lang.String;>;)V;", "(ILjava.util.List<Ljava.lang.String;>;)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue("Signatures should match", Signatures.matchesSignatures("(ILjava.lang.String;)V;", "(IQString;)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue("Signatures should match", Signatures.matchesSignatures("(ILjava.util.List<Ljava.lang.String;>;)V;", "(ILjava.util.List<QString;>;)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue("Signatures should not match", !Signatures.matchesSignatures("(ILjava.lang.String;)V;", "(Ljava.lang.String;I)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertTrue("Signatures should not match", !Signatures.matchesSignatures("(ILjava.util.List<Ljava.lang.String;>;)V;", "(ILjava.util.List;)V;")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * Tests the {@link Signatures#getPackageName(String)} method
	 */
	public void testGetPackageName() {
		String pname = Signatures.getPackageName("a.b.c.Type"); //$NON-NLS-1$
		assertEquals("The package name should be 'a.b.c'", "a.b.c", pname); //$NON-NLS-1$ //$NON-NLS-2$
		pname = Signatures.getPackageName("Type"); //$NON-NLS-1$
		assertEquals("the default package should be returned", "", pname); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getFieldSignature(org.eclipse.pde.api.tools.internal.provisional.model.IApiField)} method
	 */
	public void testGetFieldSignature() {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		ApiField field = type.addField("f1", "f1", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong field signature returned", Signatures.getFieldSignature(field), "f1"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getQualifiedFieldSignature(org.eclipse.pde.api.tools.internal.provisional.model.IApiField)} method
	 */
	public void testGetQualifiedFieldSignature() throws Exception {
		ApiType type = new ApiType(null, "Parent", "Lx.y.z.Parent;", null, Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		ApiField field = type.addField("f1", "f1", null, Flags.AccPublic, null);  //$NON-NLS-1$//$NON-NLS-2$
		assertEquals("Wrong field signature returned", Signatures.getQualifiedFieldSignature(field), "x.y.z.Parent.f1"); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent2", "Lx.y.z.Parent2;", "<T:Ljava/lang/Object;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		field = type.addField("f1", "f1", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong field signature returned", Signatures.getQualifiedFieldSignature(field), "x.y.z.Parent2<T>.f1"); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent3", "Lx.y.z.Parent3;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		field = type.addField("f1", "f1", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong field signature returned", Signatures.getQualifiedFieldSignature(field), "x.y.z.Parent3<T, E>.f1"); //$NON-NLS-1$ //$NON-NLS-2$
		type = new ApiType(null, "Parent4", "Lx.y.z.Parent4$inner;", "<T:Ljava/lang/Object;E::Ljava/util/List<Ljava/util/List<Ljava/lang/String;>;>;>", Flags.AccPublic, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		field = type.addField("f1", "f1", null, Flags.AccPublic, null); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("Wrong field signature returned", Signatures.getQualifiedFieldSignature(field), "x.y.z.Parent4.inner<T, E>.f1"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Tests the {@link Signatures#getPrimaryTypeName(String)} method
	 */
	public void testGetPrimaryTypeName() {
		assertEquals("the type name x.y.z should have been returned", "x.y.z.Type", Signatures.getPrimaryTypeName("x.y.z.Type$Member")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("the type name x.y.z should have been returned", "x.y.z.Type", Signatures.getPrimaryTypeName("x.y.z.Type")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("the type name x.y.z should have been returned", "x.y.z.Type", Signatures.getPrimaryTypeName("x.y.z.Type$Member$Member")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	/**
	 * Tests the {@link Signatures#getSimpleTypeName(String)} method
	 */
	public void testGetSimpleTypeName() {
		assertEquals("the type name Type should have been returned", "Type", Signatures.getSimpleTypeName("a.b.c.Type")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("the type name Type$Member should have been returned", "Type$Member", Signatures.getSimpleTypeName("a.b.c.Type$Member")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals("the type name Type should have been returned", "Type", Signatures.getSimpleTypeName("Type")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
