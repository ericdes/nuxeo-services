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
 *     Florent Guillaume
 *
 * $Id: MultiDirectorySession.java 29556 2008-01-23 00:59:39Z jcarsique $
 */

package org.nuxeo.ecm.directory.multi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.directory.BaseSession;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.runtime.api.Framework;

/**
 * Directory session aggregating entries from different sources.
 * <p>
 * Each source can build an entry aggregating fields from one or several
 * directories.
 *
 * @author Florent Guillaume
 * @author Anahide Tchertchian
 */
public class MultiDirectorySession extends BaseSession {

    private static final Log log = LogFactory.getLog(MultiDirectorySession.class);

    private final DirectoryService directoryService;

    private final SchemaManager schemaManager;

    private final MultiDirectory directory;

    private final MultiDirectoryDescriptor descriptor;

    private final String schemaName;

    private final String schemaIdField;

    private final String schemaPasswordField;

    private List<SourceInfo> sourceInfos;

    public MultiDirectorySession(MultiDirectory directory) {
        directoryService = MultiDirectoryFactory.getDirectoryService();
        schemaManager = Framework.getLocalService(SchemaManager.class);
        this.directory = directory;
        this.descriptor = directory.getDescriptor();
        this.schemaName = descriptor.schemaName;
        this.schemaIdField = descriptor.idField;
        this.schemaPasswordField = descriptor.passwordField;
    }

    protected class SubDirectoryInfo {

        final String dirName;

        final String dirSchemaName;

        final String idField;

        final boolean isAuthenticating;

        final Map<String, String> fromSource;

        final Map<String, String> toSource;

        final Map<String, Serializable> defaultEntry;

        final boolean isOptional;

        Session session;

        SubDirectoryInfo(String dirName, String dirSchemaName, String idField,
                boolean isAuthenticating, Map<String, String> fromSource,
                Map<String, String> toSource,
                Map<String, Serializable> defaultEntry, boolean isOptional) {
            this.dirName = dirName;
            this.dirSchemaName = dirSchemaName;
            this.idField = idField;
            this.isAuthenticating = isAuthenticating;
            this.fromSource = fromSource;
            this.toSource = toSource;
            this.defaultEntry = defaultEntry;
            this.isOptional = isOptional;
        }

        Session getSession() throws DirectoryException {
            if (session == null) {
                session = directoryService.open(dirName);
            }
            return session;
        }

        @Override
        public String toString() {
            return String.format("{directory=%s fromSource=%s toSource=%s}",
                    dirName, fromSource, toSource);
        }
    }

    protected static class SourceInfo {

        final SourceDescriptor source;

        final List<SubDirectoryInfo> subDirectoryInfos;

        final List<SubDirectoryInfo> requiredSubDirectoryInfos;

        final List<SubDirectoryInfo> optionalSubDirectoryInfos;

        final SubDirectoryInfo authDirectoryInfo;

        SourceInfo(SourceDescriptor source,
                List<SubDirectoryInfo> subDirectoryInfos,
                SubDirectoryInfo authDirectoryInfo) {
            this.source = source;
            this.subDirectoryInfos = subDirectoryInfos;
            requiredSubDirectoryInfos = new ArrayList<SubDirectoryInfo>();
            optionalSubDirectoryInfos = new ArrayList<SubDirectoryInfo>();
            for (SubDirectoryInfo subDirInfo : subDirectoryInfos) {
                if (subDirInfo.isOptional) {
                    optionalSubDirectoryInfos.add(subDirInfo);
                } else {
                    requiredSubDirectoryInfos.add(subDirInfo);
                }
            }
            this.authDirectoryInfo = authDirectoryInfo;
        }

        @Override
        public String toString() {
            return String.format("{source=%s infos=%s}", source.name,
                    subDirectoryInfos);
        }
    }

    private void init() throws DirectoryException {
        if (sourceInfos == null) {
            recomputeSourceInfos();
        }
    }

    /**
     * Recomputes all the info needed for efficient access.
     */
    private void recomputeSourceInfos() throws DirectoryException {

        final Schema schema = schemaManager.getSchema(schemaName);
        if (schema == null) {
            throw new DirectoryException(String.format(
                    "Directory '%s' has unknown schema '%s'",
                    directory.getName(), schemaName));
        }
        final Set<String> sourceFields = new HashSet<String>();
        for (Field f : schema.getFields()) {
            sourceFields.add(f.getName().getLocalName());
        }
        if (!sourceFields.contains(schemaIdField)) {
            throw new DirectoryException(String.format(
                    "Directory '%s' schema '%s' has no id field '%s'",
                    directory.getName(), schemaName, schemaIdField));
        }

        List<SourceInfo> newSourceInfos = new ArrayList<SourceInfo>(2);
        for (SourceDescriptor source : descriptor.sources) {
            int ndirs = source.subDirectories.length;
            if (ndirs == 0) {
                throw new DirectoryException(String.format(
                        "Directory '%s' source '%s' has no subdirectories",
                        directory.getName(), source.name));
            }

            final List<SubDirectoryInfo> subDirectoryInfos = new ArrayList<SubDirectoryInfo>(
                    ndirs);

            SubDirectoryInfo authDirectoryInfo = null;
            boolean hasRequiredDir = false;
            for (SubDirectoryDescriptor subDir : source.subDirectories) {
                final String dirName = subDir.name;
                final String dirSchemaName = directoryService.getDirectorySchema(dirName);
                final String dirIdField = directoryService.getDirectoryIdField(dirName);
                final boolean dirIsAuth = directoryService.getDirectoryPasswordField(dirName) != null;
                final Map<String, String> fromSource = new HashMap<String, String>();
                final Map<String, String> toSource = new HashMap<String, String>();
                final Map<String, Serializable> defaultEntry = new HashMap<String, Serializable>();
                final boolean dirIsOptional = subDir.isOptional;

                // XXX check authenticating
                final Schema dirSchema = schemaManager.getSchema(dirSchemaName);
                if (dirSchema == null) {
                    throw new DirectoryException(String.format(
                            "Directory '%s' source '%s' subdirectory '%s' "
                                    + "has unknown schema '%s'",
                            directory.getName(), source.name, dirName,
                            dirSchemaName));
                }
                // record default field mappings if same name and record default
                // values
                final Set<String> dirSchemaFields = new HashSet<String>();
                for (Field f : dirSchema.getFields()) {
                    final String fieldName = f.getName().getLocalName();
                    dirSchemaFields.add(fieldName);
                    if (sourceFields.contains(fieldName)) {
                        // XXX check no duplicates!
                        fromSource.put(fieldName, fieldName);
                        toSource.put(fieldName, fieldName);
                    }
                    // XXX cast to Serializable
                    defaultEntry.put(fieldName,
                            (Serializable) f.getDefaultValue());
                }
                // treat renamings
                // XXX id field ?
                for (FieldDescriptor field : subDir.fields) {
                    final String sourceFieldName = field.forField;
                    final String fieldName = field.name;
                    if (!sourceFields.contains(sourceFieldName)) {
                        throw new DirectoryException(String.format(
                                "Directory '%s' source '%s' subdirectory '%s' "
                                        + "has mapping for unknown field '%s'",
                                directory.getName(), source.name, dirName,
                                sourceFieldName));
                    }
                    if (!dirSchemaFields.contains(fieldName)) {
                        throw new DirectoryException(String.format(
                                "Directory '%s' source '%s' subdirectory '%s' "
                                        + "has mapping of unknown field' '%s'",
                                directory.getName(), source.name, dirName,
                                fieldName));
                    }
                    fromSource.put(sourceFieldName, fieldName);
                    toSource.put(fieldName, sourceFieldName);
                }
                SubDirectoryInfo subDirectoryInfo = new SubDirectoryInfo(
                        dirName, dirSchemaName, dirIdField, dirIsAuth,
                        fromSource, toSource, defaultEntry, dirIsOptional);
                subDirectoryInfos.add(subDirectoryInfo);

                if (dirIsAuth) {
                    if (authDirectoryInfo != null) {
                        throw new DirectoryException(
                                String.format(
                                        "Directory '%s' source '%s' has two subdirectories "
                                                + "with a password field, '%s' and '%s'",
                                        directory.getName(), source.name,
                                        authDirectoryInfo.dirName, dirName));
                    }
                    authDirectoryInfo = subDirectoryInfo;
                }
                if (!dirIsOptional) {
                    hasRequiredDir = true;
                }
            }
            if (isAuthenticating() && authDirectoryInfo == null) {
                throw new DirectoryException(String.format(
                        "Directory '%s' source '%s' has no subdirectory "
                                + "with a password field", directory.getName(),
                        source.name));
            }
            if (!hasRequiredDir) {
                throw new DirectoryException(String.format(
                        "Directory '%s' source '%s' only has optional subdirectories: "
                                + "no directory can be used has a reference.",
                        directory.getName(), source.name));
            }
            newSourceInfos.add(new SourceInfo(source, subDirectoryInfos,
                    authDirectoryInfo));
        }
        sourceInfos = newSourceInfos;
    }

    public void close() throws DirectoryException {
        if (sourceInfos == null) {
            return;
        }
        DirectoryException exc = null;
        for (SourceInfo sourceInfo : sourceInfos) {
            for (SubDirectoryInfo subDirectoryInfo : sourceInfo.subDirectoryInfos) {
                Session session = subDirectoryInfo.session;
                subDirectoryInfo.session = null;
                if (session != null) {
                    try {
                        session.close();
                    } catch (DirectoryException e) {
                        // remember exception, we want to close all session
                        // first
                        if (exc == null) {
                            exc = e;
                        } else {
                            // we can't reraise both, log this one
                            log.error("Error closing directory "
                                    + subDirectoryInfo.dirName, e);
                        }
                    }
                }
            }
        }
        directory.removeSession(this);
        if (exc != null) {
            throw exc;
        }
    }

    public void commit() throws ClientException {
        if (sourceInfos == null) {
            return;
        }
        for (SourceInfo sourceInfo : sourceInfos) {
            for (SubDirectoryInfo subDirectoryInfo : sourceInfo.subDirectoryInfos) {
                Session session = subDirectoryInfo.session;
                if (session != null) {
                    session.commit();
                }
            }
        }
    }

    public void rollback() throws ClientException {
        if (sourceInfos == null) {
            return;
        }
        for (SourceInfo sourceInfo : sourceInfos) {
            for (SubDirectoryInfo subDirectoryInfo : sourceInfo.subDirectoryInfos) {
                Session session = subDirectoryInfo.session;
                if (session != null) {
                    session.rollback();
                }
            }
        }
    }

    public String getIdField() {
        return schemaIdField;
    }

    public String getPasswordField() {
        return schemaPasswordField;
    }

    public boolean isAuthenticating() {
        return schemaPasswordField != null;
    }

    public boolean isReadOnly() {
        return Boolean.TRUE.equals(descriptor.readOnly);
    }

    public boolean authenticate(String username, String password)
            throws ClientException {
        init();
        for (SourceInfo sourceInfo : sourceInfos) {
            for (SubDirectoryInfo dirInfo : sourceInfo.subDirectoryInfos) {
                if (!dirInfo.isAuthenticating) {
                    continue;
                }
                if (dirInfo.getSession().authenticate(username, password)) {
                    return true;
                }
                if (dirInfo.isOptional
                        && dirInfo.getSession().getEntry(username) == null) {
                    // check if given password equals to default value
                    String passwordField = dirInfo.getSession().getPasswordField();
                    String defaultPassword = (String) dirInfo.defaultEntry.get(passwordField);
                    if (defaultPassword != null
                            && defaultPassword.equals(password)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public DocumentModel getEntry(String id) throws DirectoryException {
        return getEntry(id, true);
    }

    public DocumentModel getEntry(String id, boolean fetchReferences)
            throws DirectoryException {
        init();
        source_loop: for (SourceInfo sourceInfo : sourceInfos) {
            final Map<String, Object> map = new HashMap<String, Object>();

            for (SubDirectoryInfo dirInfo : sourceInfo.subDirectoryInfos) {
                final DocumentModel entry = dirInfo.getSession().getEntry(id,
                        fetchReferences);
                boolean isOptional = dirInfo.isOptional;
                if (entry == null && !isOptional) {
                    // not in this source
                    continue source_loop;
                }
                for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
                    if (entry != null) {
                        try {
                            map.put(e.getValue(), entry.getProperty(
                                    dirInfo.dirSchemaName, e.getKey()));
                        } catch (ClientException e1) {
                            throw new DirectoryException(e1);
                        }
                    } else {
                        // fill with default values for this directory
                        if (!map.containsKey(e.getValue())) {
                            map.put(e.getValue(),
                                    dirInfo.defaultEntry.get(e.getKey()));
                        }
                    }
                }
            }
            // ok we have the data
            try {
                final DocumentModel entry = BaseSession.createEntryModel(null,
                        schemaName, id, map);
                return entry;
            } catch (PropertyException e) {
                throw new DirectoryException(e);
            }
        }
        return null;
    }

    @SuppressWarnings("boxing")
    public DocumentModelList getEntries() throws ClientException {
        init();

        // list of entries
        final DocumentModelList results = new DocumentModelListImpl();
        // entry ids already seen (mapped to the source name)
        final Map<String, String> seen = new HashMap<String, String>();

        for (SourceInfo sourceInfo : sourceInfos) {
            // accumulated map for each entry
            final Map<String, Map<String, Object>> maps = new HashMap<String, Map<String, Object>>();
            // number of dirs seen for each entry
            final Map<String, Integer> counts = new HashMap<String, Integer>();
            for (SubDirectoryInfo dirInfo : sourceInfo.requiredSubDirectoryInfos) {
                final DocumentModelList entries = dirInfo.getSession().getEntries();
                for (DocumentModel entry : entries) {
                    final String id = entry.getId();
                    // find or create map for this entry
                    Map<String, Object> map = maps.get(id);
                    if (map == null) {
                        map = new HashMap<String, Object>();
                        maps.put(id, map);
                        counts.put(id, 1);
                    } else {
                        counts.put(id, counts.get(id) + 1);
                    }
                    // put entry data in map
                    for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
                        map.put(e.getValue(), entry.getProperty(
                                dirInfo.dirSchemaName, e.getKey()));
                    }
                }
            }
            for (SubDirectoryInfo dirInfo : sourceInfo.optionalSubDirectoryInfos) {
                final DocumentModelList entries = dirInfo.getSession().getEntries();
                Set<String> existingIds = new HashSet<String>();
                for (DocumentModel entry : entries) {
                    final String id = entry.getId();
                    final Map<String, Object> map = maps.get(id);
                    if (map != null) {
                        existingIds.add(id);
                        // put entry data in map
                        for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
                            map.put(e.getValue(), entry.getProperty(
                                    dirInfo.dirSchemaName, e.getKey()));
                        }
                    } else {
                        log.warn(String.format(
                                "Entry '%s' for source '%s' is present in optional directory '%s' "
                                        + "but not in any required one. "
                                        + "It will be skipped.", id,
                                sourceInfo.source.name, dirInfo.dirName));
                    }
                }
                for (Entry<String, Map<String, Object>> mapEntry : maps.entrySet()) {
                    if (!existingIds.contains(mapEntry.getKey())) {
                        final Map<String, Object> map = mapEntry.getValue();
                        // put entry data in map
                        for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
                            // fill with default values for this directory
                            if (!map.containsKey(e.getValue())) {
                                map.put(e.getValue(),
                                        dirInfo.defaultEntry.get(e.getKey()));
                            }
                        }
                    }
                }
            }
            // now create entries for all full maps
            int numdirs = sourceInfo.requiredSubDirectoryInfos.size();
            ((ArrayList<?>) results).ensureCapacity(results.size()
                    + maps.size());
            for (Entry<String, Map<String, Object>> e : maps.entrySet()) {
                final String id = e.getKey();
                if (seen.containsKey(id)) {
                    log.warn(String.format(
                            "Entry '%s' is present in source '%s' but also in source '%s'. "
                                    + "The second one will be ignored.", id,
                            seen.get(id), sourceInfo.source.name));
                    continue;
                }
                final Map<String, Object> map = e.getValue();
                if (counts.get(id) != numdirs) {
                    log.warn(String.format(
                            "Entry '%s' for source '%s' is not present in all directories. "
                                    + "It will be skipped.", id,
                            sourceInfo.source.name));
                    continue;
                }
                seen.put(id, sourceInfo.source.name);
                final DocumentModel entry = BaseSession.createEntryModel(null,
                        schemaName, id, map);
                results.add(entry);
            }
        }
        return results;
    }

    public DocumentModel createEntry(Map<String, Object> fieldMap)
            throws ClientException {
        init();
        final Object rawid = fieldMap.get(schemaIdField);
        if (rawid == null) {
            throw new DirectoryException(String.format(
                    "Entry is missing id field '%s'", schemaIdField));
        }
        final String id = String.valueOf(rawid); // XXX allow longs too
        for (SourceInfo sourceInfo : sourceInfos) {
            if (!sourceInfo.source.creation) {
                continue;
            }
            for (SubDirectoryInfo dirInfo : sourceInfo.subDirectoryInfos) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put(dirInfo.idField, id);
                for (Entry<String, String> e : dirInfo.fromSource.entrySet()) {
                    map.put(e.getValue(), fieldMap.get(e.getKey()));
                }
                dirInfo.getSession().createEntry(map);
            }
            return getEntry(id);
        }
        throw new DirectoryException(String.format(
                "Directory '%s' has no source allowing creation",
                directory.getName()));
    }

    public void deleteEntry(DocumentModel docModel) throws ClientException {
        deleteEntry(docModel.getId());
    }

    public void deleteEntry(String id) throws ClientException {
        init();
        for (SourceInfo sourceInfo : sourceInfos) {
            for (SubDirectoryInfo dirInfo : sourceInfo.subDirectoryInfos) {
                dirInfo.getSession().deleteEntry(id);
            }
        }
    }

    public void deleteEntry(String id, Map<String, String> map)
            throws DirectoryException {
        log.warn("Calling deleteEntry extended on multi directory");
        try {
            deleteEntry(id);
        } catch (DirectoryException e) {
            throw e;
        } catch (ClientException e) {
            throw new DirectoryException(e);
        }
    }

    private void updateSubDirectoryEntry(SubDirectoryInfo dirInfo,
            Map<String, Object> fieldMap, String id, boolean canCreateIfOptional)
            throws ClientException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(dirInfo.idField, id);
        for (Entry<String, String> e : dirInfo.fromSource.entrySet()) {
            map.put(e.getValue(), fieldMap.get(e.getKey()));
        }
        if (map.size() > 1) {
            if (canCreateIfOptional && dirInfo.isOptional
                    && dirInfo.getSession().getEntry(id) == null) {
                // if entry does not exist, create it
                dirInfo.getSession().createEntry(map);
            } else {
                final DocumentModel entry = BaseSession.createEntryModel(null,
                        dirInfo.dirSchemaName, id, map);
                dirInfo.getSession().updateEntry(entry);
            }
        }
    }

    public void updateEntry(DocumentModel docModel) throws ClientException {
        init();
        final String id = docModel.getId();
        Map<String, Object> fieldMap = docModel.getDataModel(schemaName).getMap();
        for (SourceInfo sourceInfo : sourceInfos) {
            // check if entry exists in this source, in case it can be created
            // in optional subdirectories
            boolean canCreateIfOptional = false;
            for (SubDirectoryInfo dirInfo : sourceInfo.requiredSubDirectoryInfos) {
                if (!canCreateIfOptional) {
                    canCreateIfOptional = dirInfo.getSession().getEntry(id) != null;
                }
                updateSubDirectoryEntry(dirInfo, fieldMap, id, false);
            }
            for (SubDirectoryInfo dirInfo : sourceInfo.optionalSubDirectoryInfos) {
                updateSubDirectoryEntry(dirInfo, fieldMap, id,
                        canCreateIfOptional);
            }
        }
    }

    public DocumentModelList query(Map<String, Serializable> filter)
            throws ClientException {
        return query(filter, Collections.<String> emptySet());
    }

    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext) throws ClientException {
        return query(filter, fulltext, Collections.<String, String> emptyMap());
    }

    @SuppressWarnings("boxing")
    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext, Map<String, String> orderBy)
            throws ClientException {
        return query(filter, fulltext, orderBy, false);
    }

    @SuppressWarnings("boxing")
    public DocumentModelList query(Map<String, Serializable> filter,
            Set<String> fulltext, Map<String, String> orderBy,
            boolean fetchReferences) throws ClientException {
        init();
        // list of entries
        final DocumentModelList results = new DocumentModelListImpl();
        // entry ids already seen (mapped to the source name)
        final Map<String, String> seen = new HashMap<String, String>();

        for (SourceInfo sourceInfo : sourceInfos) {
            // accumulated map for each entry
            final Map<String, Map<String, Object>> maps = new HashMap<String, Map<String, Object>>();
            // number of dirs seen for each entry
            final Map<String, Integer> counts = new HashMap<String, Integer>();

            // list of optional dirs where filter matches default values
            List<SubDirectoryInfo> optionalDirsMatching = new ArrayList<SubDirectoryInfo>();
            for (SubDirectoryInfo dirInfo : sourceInfo.subDirectoryInfos) {
                // compute filter
                final Map<String, Serializable> dirFilter = new HashMap<String, Serializable>();
                for (Entry<String, Serializable> e : filter.entrySet()) {
                    final String fieldName = dirInfo.fromSource.get(e.getKey());
                    if (fieldName == null) {
                        continue;
                    }
                    dirFilter.put(fieldName, e.getValue());
                }
                if (dirInfo.isOptional) {
                    // check if filter matches directory default values
                    boolean matches = true;
                    for (Map.Entry<String, Serializable> dirFilterEntry : dirFilter.entrySet()) {
                        Object defaultValue = dirInfo.defaultEntry.get(dirFilterEntry.getKey());
                        Object filterValue = dirFilterEntry.getValue();
                        if (defaultValue == null && filterValue != null) {
                            matches = false;
                        } else if (defaultValue != null
                                && !defaultValue.equals(filterValue)) {
                            matches = false;
                        }
                    }
                    if (matches) {
                        optionalDirsMatching.add(dirInfo);
                    }
                }
                // compute fulltext
                Set<String> dirFulltext = new HashSet<String>();
                for (String sourceFieldName : fulltext) {
                    final String fieldName = dirInfo.fromSource.get(sourceFieldName);
                    if (fieldName != null) {
                        dirFulltext.add(fieldName);
                    }
                }
                // make query to subdirectory
                DocumentModelList l = dirInfo.getSession().query(dirFilter,
                        dirFulltext, null, fetchReferences);
                for (DocumentModel entry : l) {
                    final String id = entry.getId();
                    Map<String, Object> map = maps.get(id);
                    if (map == null) {
                        map = new HashMap<String, Object>();
                        maps.put(id, map);
                        counts.put(id, 1);
                    } else {
                        counts.put(id, counts.get(id) + 1);
                    }
                    for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
                        map.put(e.getValue(), entry.getProperty(
                                dirInfo.dirSchemaName, e.getKey()));
                    }
                }
            }
            // add default entry values for optional dirs
            for (SubDirectoryInfo dirInfo : optionalDirsMatching) {
                // add entry for every data found in other dirs
                Set<String> existingIds = new HashSet<String>(
                        dirInfo.getSession().getProjection(
                                Collections.<String, Serializable> emptyMap(),
                                dirInfo.idField));
                for (Entry<String, Map<String, Object>> result : maps.entrySet()) {
                    final String id = result.getKey();
                    if (!existingIds.contains(id)) {
                        counts.put(id, counts.get(id) + 1);
                        final Map<String, Object> map = result.getValue();
                        for (Entry<String, String> e : dirInfo.toSource.entrySet()) {
                            String value = e.getValue();
                            if (!map.containsKey(value)) {
                                map.put(value,
                                        dirInfo.defaultEntry.get(e.getKey()));
                            }
                        }
                    }
                }
            }
            // intersection, ignore entries not in all subdirectories
            final int numdirs = sourceInfo.subDirectoryInfos.size();
            for (Iterator<String> it = maps.keySet().iterator(); it.hasNext();) {
                final String id = it.next();
                if (counts.get(id) != numdirs) {
                    it.remove();
                }
            }
            // now create entries
            ((ArrayList<?>) results).ensureCapacity(results.size()
                    + maps.size());
            for (Entry<String, Map<String, Object>> e : maps.entrySet()) {
                final String id = e.getKey();
                if (seen.containsKey(id)) {
                    log.warn(String.format(
                            "Entry '%s' is present in source '%s' but also in source '%s'. "
                                    + "The second one will be ignored.", id,
                            seen.get(id), sourceInfo.source.name));
                    continue;
                }
                final Map<String, Object> map = e.getValue();
                seen.put(id, sourceInfo.source.name);
                final DocumentModel entry = BaseSession.createEntryModel(null,
                        schemaName, id, map);
                results.add(entry);
            }
        }
        if (orderBy != null && !orderBy.isEmpty()) {
            directory.orderEntries(results, orderBy);
        }
        return results;
    }

    public List<String> getProjection(Map<String, Serializable> filter,
            String columnName) throws ClientException {
        return getProjection(filter, Collections.<String> emptySet(),
                columnName);
    }

    public List<String> getProjection(Map<String, Serializable> filter,
            Set<String> fulltext, String columnName) throws ClientException {

        // There's no way to do an efficient getProjection to a source with
        // multiple subdirectories given the current API (we'd need an API that
        // passes several columns).
        // So just do a non-optimal implementation for now.

        final DocumentModelList entries = query(filter, fulltext);
        final List<String> results = new ArrayList<String>(entries.size());
        for (DocumentModel entry : entries) {
            final Object value = entry.getProperty(schemaName, columnName);
            if (value == null) {
                results.add(null);
            } else {
                results.add(value.toString());
            }
        }
        return results;
    }

    public DocumentModel createEntry(DocumentModel entry)
            throws ClientException {
        Map<String, Object> fieldMap = entry.getProperties(schemaName);
        return createEntry(fieldMap);
    }

    public boolean hasEntry(String id) throws ClientException {
        init();
        for (SourceInfo sourceInfo : sourceInfos) {
            for (SubDirectoryInfo dirInfo : sourceInfo.subDirectoryInfos) {
                Session session = dirInfo.getSession();
                if (session.hasEntry(id)) {
                    return true;
                }
            }
        }
        return false;
    }

}
