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
 * $Id$
 */

package org.nuxeo.ecm.platform.content.template.tests;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.repository.jcr.testing.RepositoryTestCase;
import org.nuxeo.ecm.platform.content.template.service.ContentFactory;
import org.nuxeo.ecm.platform.content.template.service.ContentFactoryDescriptor;
import org.nuxeo.ecm.platform.content.template.service.ContentTemplateService;
import org.nuxeo.ecm.platform.content.template.service.ContentTemplateServiceImpl;
import org.nuxeo.ecm.platform.content.template.service.FactoryBindingDescriptor;
import org.nuxeo.runtime.api.Framework;

public class TestContentTemplateFactory extends RepositoryTestCase {

    protected CoreSession session;

    protected ContentTemplateService service;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        deployBundle("org.nuxeo.ecm.core.event");

        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "RepositoryManager.xml");
        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "CoreTestExtensions.xml");
        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "DemoRepository.xml");
        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "LifeCycleService.xml");
        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "DefaultPlatform.xml");

        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "test-content-template-framework.xml");
        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "test-content-template-contrib.xml");
        deployContrib("org.nuxeo.ecm.platform.content.template.tests",
                "test-content-template-listener.xml");

        // Framework.login();
        RepositoryManager mgr = Framework.getService(RepositoryManager.class);
        assertNotNull(mgr);
        Map<String, Serializable> ctx = new HashMap<String, Serializable>();
        ctx.put("username", "Administrator");
        session = mgr.getDefaultRepository().open(ctx);
        assertNotNull(session);
        service = Framework.getLocalService(ContentTemplateService.class);
        assertNotNull(service);
    }

    public void testServiceFactoryContribs() {
        ContentTemplateServiceImpl serviceImpl = (ContentTemplateServiceImpl) service;
        assertNotNull(serviceImpl);
        Map<String, ContentFactoryDescriptor> factories = serviceImpl.getFactories();
        assertTrue(factories.containsKey("SimpleTemplateFactory"));
        assertTrue(factories.containsKey("ImportFactory"));
        assertEquals(2, factories.size());
    }

    public void testServiceFactoryBindingContribs() {
        ContentTemplateServiceImpl serviceImpl = (ContentTemplateServiceImpl) service;
        assertNotNull(serviceImpl);
        Map<String, FactoryBindingDescriptor> factoryBindings = serviceImpl.getFactoryBindings();
        assertEquals(3, factoryBindings.size());
        assertTrue(factoryBindings.containsKey("Root"));
        assertTrue(factoryBindings.containsKey("Domain"));

        assertEquals(3, factoryBindings.get("Domain").getTemplate().size());
        assertEquals("Workspaces",
                factoryBindings.get("Domain").getTemplate().get(0).getId());
    }

    public void testServiceFactoryForSecurity() {
        ContentTemplateServiceImpl serviceImpl = (ContentTemplateServiceImpl) service;
        assertNotNull(serviceImpl);
        Map<String, FactoryBindingDescriptor> factoryBindings = serviceImpl.getFactoryBindings();

        FactoryBindingDescriptor factory = factoryBindings.get("Workspace");
        assertNotNull(factory);

        // check that ACL is not null
        assertNotNull(factory.getTemplate().get(1).getAcl());
        assertEquals(2, factory.getTemplate().get(1).getAcl().size());

        // check root ACL
        factory = factoryBindings.get("Root");
        assertNotNull(factory);
        assertNotNull(factory.getRootAcl());
        assertEquals(3, factory.getRootAcl().size());
    }

    public void testServiceFactoryInstancesContribs() {
        ContentTemplateServiceImpl serviceImpl = (ContentTemplateServiceImpl) service;
        assertNotNull(serviceImpl);
        Map<String, ContentFactory> factoryInstances = serviceImpl.getFactoryInstancesByType();
        assertEquals(3, factoryInstances.size());
        assertTrue(factoryInstances.containsKey("Root"));
        assertTrue(factoryInstances.containsKey("Domain"));
    }

    public void testRootFactory() throws ClientException {
        // Fake repo init for now
        DocumentModel root = session.getRootDocument();
        service.executeFactoryForType(root);

        // check root ACL
        assertTrue(session.getACP(root.getRef()).getAccess("Administrator",
                "Everything").toBoolean());
        assertTrue(session.getACP(root.getRef()).getAccess("Danny", "Dream").toBoolean());
        assertFalse(session.getACP(root.getRef()).getAccess(
                "DannysImaginaryFriend", "RedRum").toBoolean());

        // check that default domain has been created
        DocumentModelList children = session.getChildren(root.getRef());
        assertEquals(1, children.size());

        children = session.getChildren(root.getRef(), "Domain");
        DocumentModel domain = children.get(0);
        assertEquals(1, children.size());
        assertEquals("defaut domain", domain.getTitle());

        // check that the default domain has the template layout
        children = session.getChildren(domain.getRef());
        assertEquals(3, children.size());
        children = session.getChildren(domain.getRef(), "WorkspaceRoot");
        assertEquals(1, children.size());
        assertEquals("Workspaces", children.get(0).getTitle());
    }

    public void testDomainFactory() throws ClientException {
        DocumentModel testDom = session.createDocumentModel("/", "TestDomain",
                "Domain");
        testDom.setProperty("dublincore", "title", "MyTestDomain");
        testDom = session.createDocument(testDom);
        session.save();

        // check that the created domain has the template layout
        DocumentModelList children = session.getChildren(testDom.getRef());
        assertEquals(3, children.size());

        children = session.getChildren(testDom.getRef(), "WorkspaceRoot");
        assertEquals(1, children.size());
        assertEquals("Workspaces", children.get(0).getTitle());
    }

    public void testWSFactory() throws ClientException {
        // reach first available WSRoot
        DocumentModel root = session.getRootDocument();
        service.executeFactoryForType(root);

        DocumentModel firstDomain = session.getChildren(root.getRef()).get(0);
        DocumentModel wsRoot = session.getChildren(firstDomain.getRef(),
                "WorkspaceRoot").get(0);

        // create new WS
        DocumentModel testWS = session.createDocumentModel(
                wsRoot.getPathAsString(), "TestWS", "Workspace");
        testWS.setProperty("dublincore", "title", "MyTestWorkspace");
        testWS = session.createDocument(testWS);
        session.save();

        // Check children, rights and properties
        DocumentModelList children = session.getChildren(testWS.getRef());
        assertEquals(3, children.size());

        for (DocumentModel child : children) {
            if (child.getTitle().equals("Folder1")) {
                ACP acp = session.getACP(child.getRef());
                ACL existingACL = acp.getACL(ACL.LOCAL_ACL);
                if (existingACL != null) {
                    assertEquals(0, existingACL.size());
                }
                // check properties
                assertEquals("Administrator", child.getPropertyValue("dublincore:creator"));
                assertEquals("coverage", child.getPropertyValue("dublincore:coverage"));
            } else if (child.getTitle().equals("Secret Folder")) {
                ACP acp = session.getACP(child.getRef());
                ACL existingACL = acp.getACL(ACL.LOCAL_ACL);
                assertNotNull(existingACL);
                assertEquals(2, existingACL.size());
            } else if (child.getTitle().equals("Folder2")) {
                ACP acp = session.getACP(child.getRef());
                ACL existingACL = acp.getACL(ACL.LOCAL_ACL);
                if (existingACL != null) {
                    assertEquals(0, existingACL.size());
                }
            } else {
                // we should not go here !!!
                fail();
            }
        }
    }

}
