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

/**
 * Tests all tags are invalid when parent annotation is private or package default
 */
public @interface test3 {

	public @interface inner1 {

		/**
		 * @noextend
		 * @noinstantiate
		 * @noreference
		 */
		public static class Clazz {
		}

		/**
		 * @noextend
		 * @noimplement
		 * @noreference
		 */
		public interface inter {
		}

		/**
		 */
		public int field = 0;

		/**
		 * @noreference
		 */
		public @interface annot {

		}

		/**
		 * @noreference
		 */
		enum enu {
		}
	}

}
