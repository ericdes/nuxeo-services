/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.platform.types;

import java.util.Collection;

public interface TypeManager {

    /**
     * Gets the super type names for the given type.
     *
     * @return an array of supertypes or an empty array if no supertype exists.
     *         null is returned if no such type exists
     */
    String[] getSuperTypes(String typeName);

    Collection<Type> getTypes();

    Type getType(String typeName);

    boolean hasType(String typeName);

    Collection<Type> getAllowedSubTypes(String typeName);

}
