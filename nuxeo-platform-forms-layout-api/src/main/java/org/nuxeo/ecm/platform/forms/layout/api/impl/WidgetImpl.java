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
 *     <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 *
 * $Id: WidgetImpl.java 28478 2008-01-04 12:53:58Z sfermigier $
 */

package org.nuxeo.ecm.platform.forms.layout.api.impl;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import org.nuxeo.ecm.platform.forms.layout.api.FieldDefinition;
import org.nuxeo.ecm.platform.forms.layout.api.Widget;

/**
 * Implementation for widgets.
 *
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 */
public class WidgetImpl implements Widget {

    private static final long serialVersionUID = -2954101230598440812L;

    String id;

    final String layoutName;

    final String name;

    final String mode;

    final String type;

    final FieldDefinition[] fields;

    final String helpLabel;

    final Widget[] subWidgets;

    final Map<String, Serializable> properties;

    final boolean required;

    String valueName;

    String label;

    boolean translated = false;

    int level;

    public WidgetImpl(String layoutName, String name, String mode, String type,
            String valueName, FieldDefinition[] fields, String label,
            String helpLabel, boolean translated,
            Map<String, Serializable> properties, boolean required,
            Widget[] subWidgets, int level) {
        this.layoutName = layoutName;
        this.name = name;
        this.mode = mode;
        this.type = type;
        this.valueName = valueName;
        this.fields = fields;
        this.label = label;
        this.helpLabel = helpLabel;
        this.translated = translated;
        this.properties = properties;
        this.required = required;
        this.subWidgets = subWidgets;
        this.level = level;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLayoutName() {
        return layoutName;
    }

    public String getName() {
        return name;
    }

    public String getMode() {
        return mode;
    }

    public String getType() {
        return type;
    }

    public String getLabel() {
        if (label == null) {
            // compute default label name
            label = String.format("label.widget.%s.%s", layoutName, name);
        }
        return label;
    }

    public String getHelpLabel() {
        return helpLabel;
    }

    public boolean isTranslated() {
        return translated;
    }

    public Map<String, Serializable> getProperties() {
        if (properties == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(properties);
    }

    public Serializable getProperty(String name) {
        if (properties != null) {
            return properties.get(name);
        }
        return null;
    }

    public boolean isRequired() {
        return required;
    }

    public FieldDefinition[] getFieldDefinitions() {
        return fields;
    }

    public Widget[] getSubWidgets() {
        return subWidgets;
    }

    public String getValueName() {
        return valueName;
    }

    public void setValueName(String valueName) {
        this.valueName = valueName;
    }

    public int getLevel() {
        return level;
    }

}
