/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.adapter.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.ServletException;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingJakartaHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Test.None;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import static org.junit.Assert.*;

/**
 */
public class AdapterWebConsolePluginTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);

    private AdapterWebConsolePlugin plugin;

    @SuppressWarnings("deprecation")
    private org.osgi.service.packageadmin.PackageAdmin mockPackageAdmin;

    @SuppressWarnings("deprecation")
    @Before
    public void beforeEach() {
        mockPackageAdmin = context.registerService(
                org.osgi.service.packageadmin.PackageAdmin.class,
                Mockito.mock(org.osgi.service.packageadmin.PackageAdmin.class));

        plugin = context.registerInjectActivateService(AdapterWebConsolePlugin.class);
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#addingService(org.osgi.framework.ServiceReference)}.
     */
    @Test(expected = None.class)
    public void testAddingService() {
        // simulate serviceref with no config
        @SuppressWarnings("unchecked")
        ServiceReference<AdapterFactory> serviceRef1 = Mockito.mock(ServiceReference.class);
        plugin.addingService(serviceRef1);

        // simulate serviceref with empty config
        @SuppressWarnings("unchecked")
        ServiceReference<AdapterFactory> serviceRef2 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(new String[0]).when(serviceRef2).getProperty(AdapterFactory.ADAPTER_CLASSES);
        plugin.addingService(serviceRef2);

        // simulate serviceref with non-empty config
        @SuppressWarnings("unchecked")
        ServiceReference<AdapterFactory> serviceRef3 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(new String[] {"org.apache.sling.Adapter1"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTER_CLASSES);
        Mockito.doReturn(new String[] {"org.apache.sling.Adaptable1"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTABLE_CLASSES);
        plugin.addingService(serviceRef3);
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#bundleChanged(org.osgi.framework.BundleEvent)}.
     */
    @Test(expected = None.class)
    public void testBundleChanged() {
        Bundle bundle1 = Mockito.mock(Bundle.class);
        Mockito.doReturn(Bundle.INSTALLED).when(bundle1).getState();

        // bundle stopped event
        BundleEvent event1 = Mockito.mock(BundleEvent.class);
        Mockito.doReturn(bundle1).when(event1).getBundle();
        Mockito.doReturn(BundleEvent.STOPPED).when(event1).getType();
        plugin.bundleChanged(event1);

        // bundle started event
        BundleEvent event2 = Mockito.mock(BundleEvent.class);
        Mockito.doReturn(bundle1).when(event2).getBundle();
        Mockito.doReturn(BundleEvent.STARTED).when(event2).getType();
        plugin.bundleChanged(event2);

        // bundle other event
        BundleEvent event3 = Mockito.mock(BundleEvent.class);
        Mockito.doReturn(bundle1).when(event3).getBundle();
        Mockito.doReturn(BundleEvent.UPDATED).when(event3).getType();
        plugin.bundleChanged(event3);
    }

    @Test(expected = None.class)
    public void testBundleChangedWithAdaptersJson() throws IOException {
        Bundle bundle1 = Mockito.mock(Bundle.class);
        Mockito.doReturn(Bundle.ACTIVE).when(bundle1).getState();

        final URL resource1 = getClass().getResource("/SLING-INF/adapters.json");
        Mockito.doReturn(Collections.enumeration(List.of(resource1)))
                .when(bundle1)
                .getResources("SLING-INF/adapters.json");

        // bundle started event
        BundleEvent event1 = Mockito.mock(BundleEvent.class);
        Mockito.doReturn(bundle1).when(event1).getBundle();
        Mockito.doReturn(BundleEvent.STARTED).when(event1).getType();
        plugin.bundleChanged(event1);
    }

    @Test(expected = None.class)
    public void testBundleChangedWithInvalidAdaptersJson() throws IOException {
        Bundle bundle1 = Mockito.mock(Bundle.class);
        Mockito.doReturn(Bundle.ACTIVE).when(bundle1).getState();

        final URL resource2 = getClass().getResource("/SLING-INF/adapters-invalid.json");
        Mockito.doReturn(Collections.enumeration(List.of(resource2)))
                .when(bundle1)
                .getResources("SLING-INF/adapters.json");

        // bundle started event
        BundleEvent event1 = Mockito.mock(BundleEvent.class);
        Mockito.doReturn(bundle1).when(event1).getBundle();
        Mockito.doReturn(BundleEvent.STARTED).when(event1).getType();
        plugin.bundleChanged(event1);
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#modifiedService(org.osgi.framework.ServiceReference, java.lang.Object)}.
     */
    @Test(expected = None.class)
    public void testModifiedService() {
        // simulate serviceref with non-empty config
        @SuppressWarnings("unchecked")
        ServiceReference<AdapterFactory> serviceRef3 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(new String[] {"org.apache.sling.Adapter1"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTER_CLASSES);
        Mockito.doReturn(new String[] {"org.apache.sling.Adaptable1"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTABLE_CLASSES);
        Object mockSvc = new Object();
        plugin.modifiedService(serviceRef3, mockSvc);
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#removedService(org.osgi.framework.ServiceReference, java.lang.Object)}.
     */
    @Test(expected = None.class)
    public void testRemovedService() {
        // simulate serviceref with non-empty config
        @SuppressWarnings("unchecked")
        ServiceReference<AdapterFactory> serviceRef3 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(new String[] {"org.apache.sling.Adapter1"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTER_CLASSES);
        Mockito.doReturn(new String[] {"org.apache.sling.Adaptable1"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTABLE_CLASSES);
        plugin.addingService(serviceRef3);
        Object mockSvc = new Object();
        plugin.removedService(serviceRef3, mockSvc);
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#activate(org.osgi.framework.BundleContext)}.
     */
    @Test(expected = None.class)
    public void testActivate() throws InvalidSyntaxException {
        final BundleContext bundleContext = Mockito.spy(context.bundleContext());

        // mock some deployed bundles
        Bundle bundle1 = Mockito.mock(Bundle.class);
        Mockito.doReturn(Bundle.INSTALLED).when(bundle1).getState();
        Bundle bundle2 = Mockito.mock(Bundle.class);
        Mockito.doReturn(Bundle.ACTIVE).when(bundle2).getState();
        final Bundle[] mockBundles = new Bundle[] {bundle1, bundle2};
        Mockito.doReturn(mockBundles).when(bundleContext).getBundles();

        new AdapterWebConsolePlugin(bundleContext);
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#deactivate()}.
     */
    @Test(expected = None.class)
    public void testDeactivate() {
        plugin.deactivate();
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#doGet(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse)}.
     */
    @SuppressWarnings("deprecation")
    @Test
    public void testDoGetWithHtmlOutput() throws ServletException, IOException {
        // simulate an exported package
        org.osgi.service.packageadmin.ExportedPackage mockExportedPackage =
                Mockito.mock(org.osgi.service.packageadmin.ExportedPackage.class);
        Mockito.doReturn(mockExportedPackage).when(mockPackageAdmin).getExportedPackage("org.apache.sling.exported");

        final String outputAsString = doGet("");
        assertNotNull(outputAsString);
    }

    @Test
    public void testDoGetWithJsonOutput() throws ServletException, IOException {
        final String outputAsString = doGet("/data.json");
        assertNotNull(outputAsString);
    }

    private String doGet(String pathInfo) throws ServletException, IOException {
        mockAdapters();

        final MockSlingJakartaHttpServletRequest req = context.jakartaRequest();
        req.setPathInfo(pathInfo);
        final MockSlingJakartaHttpServletResponse resp = context.jakartaResponse();
        plugin.doGet(req, resp);
        return resp.getOutputAsString();
    }

    @SuppressWarnings("unchecked")
    private void mockAdapters() {
        // simulate serviceref
        ServiceReference<AdapterFactory> serviceRef1 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(context.bundleContext().getBundle()).when(serviceRef1).getBundle();
        Mockito.doReturn(new String[] {"org.apache.sling.Adapter1", "org.apache.sling.Adapter2"})
                .when(serviceRef1)
                .getProperty(AdapterFactory.ADAPTER_CLASSES);
        Mockito.doReturn(new String[] {"org.apache.sling.Adaptable1"})
                .when(serviceRef1)
                .getProperty(AdapterFactory.ADAPTABLE_CLASSES);
        Mockito.doReturn("my condition1").when(serviceRef1).getProperty("adapter.condition");
        Mockito.doReturn(true).when(serviceRef1).getProperty("adapter.deprecated");
        plugin.addingService(serviceRef1);

        // simulate another serviceref for code coverage
        ServiceReference<AdapterFactory> serviceRef2 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(context.bundleContext().getBundle()).when(serviceRef2).getBundle();
        Mockito.doReturn(new String[] {"org.apache.sling.Adapter1", "org.apache.sling.Adapter2"})
                .when(serviceRef2)
                .getProperty(AdapterFactory.ADAPTER_CLASSES);
        Mockito.doReturn(new String[] {"org.apache.sling.Adaptable1"})
                .when(serviceRef2)
                .getProperty(AdapterFactory.ADAPTABLE_CLASSES);
        Mockito.doReturn("my condition1").when(serviceRef2).getProperty("adapter.condition");
        plugin.addingService(serviceRef2);

        // simulate serviceref with null condition
        ServiceReference<AdapterFactory> serviceRef3 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(context.bundleContext().getBundle()).when(serviceRef3).getBundle();
        Mockito.doReturn(new String[] {"org.apache.sling.Adapter3"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTER_CLASSES);
        Mockito.doReturn(new String[] {"org.apache.sling.Adaptable1"})
                .when(serviceRef3)
                .getProperty(AdapterFactory.ADAPTABLE_CLASSES);
        // simulate serviceref with exported package
        ServiceReference<AdapterFactory> serviceRef4 = Mockito.mock(ServiceReference.class);
        Mockito.doReturn(context.bundleContext().getBundle()).when(serviceRef4).getBundle();
        Mockito.doReturn(new String[] {"org.apache.sling.exported.Adapter4"})
                .when(serviceRef4)
                .getProperty(AdapterFactory.ADAPTER_CLASSES);
        Mockito.doReturn(new String[] {"org.apache.sling.exported.Adaptable2"})
                .when(serviceRef4)
                .getProperty(AdapterFactory.ADAPTABLE_CLASSES);
        plugin.addingService(serviceRef4);
    }

    /**
     * Test method for {@link org.apache.sling.adapter.internal.AdapterWebConsolePlugin#printConfiguration(java.io.PrintWriter)}.
     */
    @Test
    public void testPrintConfiguration() {
        mockAdapters();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        plugin.printConfiguration(pw);
        String outputAsString = sw.toString();
        assertNotNull(outputAsString);
    }

    /**
     * Test method for {@link
     * org.apache.sling.adapter.internal.AdapterWebConsolePlugin#getResource(java.lang.String)}.
     */
    @Test
    public void testGetResource() throws SecurityException, IllegalArgumentException {
        final URL value1 = ReflectionTools.invokeMethodWithReflection(
                plugin, "getResource", new Class[] {String.class}, URL.class, new Object[] {"/invalid"});
        assertNull(value1);

        final URL value2 = ReflectionTools.invokeMethodWithReflection(
                plugin, "getResource", new Class[] {String.class}, URL.class, new Object[] {
                    "/adapters/res/ui/adapters.css"
                });
        assertNotNull(value2);

        final URL value3 = ReflectionTools.invokeMethodWithReflection(
                plugin, "getResource", new Class[] {String.class}, URL.class, new Object[] {
                    "/adapters/res/ui/invalid.css"
                });
        assertNull(value3);
    }
}
