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
 *     anguenot
 *
 * $Id: SearchServiceSession.java 28476 2008-01-04 09:52:52Z sfermigier $
 */

package org.nuxeo.ecm.core.search.api.client.indexing.session;

import java.io.Serializable;

/**
 * Search service session.
 *
 * <p>
 * Minimum API for now. It should exposes from here basic search service
 * operations. Planned for NXP 5.2 branch.
 * </p>
 *
 * @author <a href="mailto:ja@nuxeo.com">Julien Anguenot</a>
 *
 */
public interface SearchServiceSession extends Serializable {

    /**
     * Returns the session identifier.
     *
     * @return the session identifier.
     */
    String getSessionId();

}
