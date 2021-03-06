/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     bstefanescu
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.rendering.fm.adapters;

import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;

import freemarker.template.AdapterTemplateModel;
import freemarker.template.TemplateCollectionModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateModelIterator;
import freemarker.template.TemplateSequenceModel;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class ListPropertyTemplate extends PropertyWrapper implements
        TemplateCollectionModel, TemplateSequenceModel, AdapterTemplateModel {

    protected final ListProperty property;

    public ListPropertyTemplate(DocumentObjectWrapper wrapper,
            ListProperty property) {
        super(wrapper);
        this.property = property;
    }

    @SuppressWarnings("unchecked")
    public Object getAdaptedObject(Class hint) {
        return property;
    }

    public TemplateModelIterator iterator() throws TemplateModelException {
        return new PropertyIteratorTemplate(wrapper, property.iterator());
    }

    public TemplateModel get(int arg0) throws TemplateModelException {
        Property p = property.get(arg0);
        return wrap(p);
    }

    public int size() throws TemplateModelException {
        return property.size();
    }

}
