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
 * $Id: TestDocumentFileCodec.java 28925 2008-01-10 14:39:42Z sfermigier $
 */

package org.nuxeo.ecm.platform.ui.web.rest;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.ui.web.rest.api.DocumentView;
import org.nuxeo.ecm.platform.ui.web.rest.codecs.DocumentFileCodec;
import org.nuxeo.ecm.platform.ui.web.util.DocumentLocation;
import org.nuxeo.ecm.platform.ui.web.util.DocumentLocationImpl;

/**
 * @author <a href="mailto:at@nuxeo.com">Anahide Tchertchian</a>
 *
 */
public class TestDocumentFileCodec extends TestCase {

    public void testGetUrlFromDocumentView() {
        DocumentFileCodec codec = new DocumentFileCodec();
        DocumentLocation docLoc = new DocumentLocationImpl("demo", new IdRef(
                "dbefd5a0-35ee-4ed2-a023-6817714f32cf"));
        Map<String, String> params = new HashMap<String, String>();
        params.put(DocumentFileCodec.FILE_PROPERTY_PATH_KEY, "file:content");
        params.put(DocumentFileCodec.FILENAME_KEY, "mydoc.odt");
        DocumentView docView = new DocumentViewImpl(docLoc, null, params);

        String url = "nxfile/demo/dbefd5a0-35ee-4ed2-a023-6817714f32cf/file:content/mydoc.odt";
        assertEquals(url, codec.getUrlFromDocumentView(docView));
    }

    // same with spaces in file name
    public void testGetUrlFromDocumentViewEncoding() {
        DocumentFileCodec codec = new DocumentFileCodec();
        DocumentLocation docLoc = new DocumentLocationImpl("demo", new IdRef(
                "dbefd5a0-35ee-4ed2-a023-6817714f32cf"));
        Map<String, String> params = new HashMap<String, String>();
        params.put(DocumentFileCodec.FILE_PROPERTY_PATH_KEY, "file:content");
        params.put(DocumentFileCodec.FILENAME_KEY, "my doc \u00e9.odt");
        DocumentView docView = new DocumentViewImpl(docLoc, null, params);

        String url = "nxfile/demo/dbefd5a0-35ee-4ed2-a023-6817714f32cf/file:content/my+doc+%C3%A9.odt";
        assertEquals(url, codec.getUrlFromDocumentView(docView));
    }

    public void testGetDocumentViewFromUrl() {
        DocumentFileCodec codec = new DocumentFileCodec();
        String url = "nxfile/demo/dbefd5a0-35ee-4ed2-a023-6817714f32cf/file:content/mydoc.odt";
        DocumentView docView = codec.getDocumentViewFromUrl(url);

        DocumentLocation docLoc = docView.getDocumentLocation();
        assertEquals("demo", docLoc.getServerLocationName());
        assertEquals(new IdRef("dbefd5a0-35ee-4ed2-a023-6817714f32cf"),
                docLoc.getDocRef());
        assertNull(docView.getViewId());
        assertNull(docView.getSubURI());

        Map<String, String> params = docView.getParameters();
        assertEquals("file:content",
                params.get(DocumentFileCodec.FILE_PROPERTY_PATH_KEY));
        assertEquals("mydoc.odt", params.get(DocumentFileCodec.FILENAME_KEY));
    }

    // same with spaces in file name
    public void testGetDocumentViewFromUrlDecoding() {
        DocumentFileCodec codec = new DocumentFileCodec();
        String url = "nxfile/demo/dbefd5a0-35ee-4ed2-a023-6817714f32cf/file:content/my+doc+%C3%A9.odt";
        DocumentView docView = codec.getDocumentViewFromUrl(url);

        DocumentLocation docLoc = docView.getDocumentLocation();
        assertEquals("demo", docLoc.getServerLocationName());
        assertEquals(new IdRef("dbefd5a0-35ee-4ed2-a023-6817714f32cf"),
                docLoc.getDocRef());
        assertNull(docView.getViewId());
        assertNull(docView.getSubURI());

        Map<String, String> params = docView.getParameters();
        assertEquals("file:content",
                params.get(DocumentFileCodec.FILE_PROPERTY_PATH_KEY));
        assertEquals("my doc \u00e9.odt", params.get(DocumentFileCodec.FILENAME_KEY));
    }

    // do the same with filename property path
    public void testGetDocumentViewFromUrlNoViewId() {
        DocumentFileCodec codec = new DocumentFileCodec();
        String url = "nxfile/demo/dbefd5a0-35ee-4ed2-a023-6817714f32cf/file:content/mydoc.odt?FILENAME_PROPERTY_PATH=file:filename";
        DocumentView docView = codec.getDocumentViewFromUrl(url);

        DocumentLocation docLoc = docView.getDocumentLocation();
        assertEquals("demo", docLoc.getServerLocationName());
        assertEquals(new IdRef("dbefd5a0-35ee-4ed2-a023-6817714f32cf"),
                docLoc.getDocRef());
        assertNull(docView.getViewId());
        assertNull(docView.getSubURI());

        Map<String, String> params = docView.getParameters();
        assertEquals("file:content",
                params.get(DocumentFileCodec.FILE_PROPERTY_PATH_KEY));
        assertEquals("mydoc.odt", params.get(DocumentFileCodec.FILENAME_KEY));
        assertEquals("file:filename",
                params.get(DocumentFileCodec.FILENAME_PROPERTY_PATH_KEY));
    }

}
