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

import java.util.Map;

import org.apache.sling.adapter.Adaption;
import org.apache.sling.api.adapter.AdapterFactory;
import org.apache.sling.api.adapter.SlingAdaptable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterManagerTest {

    private AdapterManagerImpl am;

    @SuppressWarnings("deprecation")
    @BeforeEach
    void setUp() {
        final PackageAdmin pa = Mockito.mock(PackageAdmin.class);
        final ExportedPackage ep = Mockito.mock(ExportedPackage.class);
        Mockito.when(pa.getExportedPackage(Mockito.anyString())).thenReturn(ep);

        this.am = new AdapterManagerImpl(pa);
    }

    /**
     * Helper method to create a mock service reference
     */
    protected ServiceReference<AdapterFactory> createServiceReference() {
        return createServiceReference(
                1, new String[] {TestSlingAdaptable.class.getName()}, new String[] {ITestAdapter.class.getName()});
    }

    /**
     * Helper method to create a mock service reference
     */
    protected ServiceReference<AdapterFactory> createServiceReference(
            final int ranking, final String[] adaptables, final String[] adapters) {
        @SuppressWarnings("unchecked")
        final ServiceReference<AdapterFactory> ref = Mockito.mock(ServiceReference.class);
        Mockito.when(ref.getProperty(Constants.SERVICE_RANKING)).thenReturn(ranking);
        Mockito.when(ref.getProperty(AdapterFactory.ADAPTABLE_CLASSES)).thenReturn(adaptables);
        Mockito.when(ref.getProperty(AdapterFactory.ADAPTER_CLASSES)).thenReturn(adapters);

        final Bundle bundle = Mockito.mock(Bundle.class);
        final BundleContext ctx = Mockito.mock(BundleContext.class);

        Mockito.when(ref.getBundle()).thenReturn(bundle);
        Mockito.when(bundle.getBundleContext()).thenReturn(ctx);

        return ref;
    }

    /**
     * Helper method to create a mock service reference
     */
    protected ServiceReference<AdapterFactory> createServiceReference2() {
        return createServiceReference(
                2, new String[] {TestSlingAdaptable2.class.getName()}, new String[] {TestAdapter.class.getName()});
    }

    @Test
    void testInitialized() {
        assertNotNull(am.getFactories(), "AdapterFactoryDescriptors must not be null");
        assertTrue(am.getFactories().isEmpty(), "AdapterFactoryDescriptors must be empty");
        assertTrue(am.getFactoryCache().isEmpty(), "AdapterFactory cache must be empty");
    }

    @Test
    void testInvalidRegistrations() {
        ServiceReference<AdapterFactory> ref =
                createServiceReference(0, null, new String[] {TestAdapter.class.getName()});
        am.bindAdapterFactory(Mockito.mock(AdapterFactory.class), ref);
        assertTrue(am.getFactories().isEmpty(), "AdapterFactoryDescriptors must be empty");

        ref = createServiceReference(0, new String[0], new String[] {TestAdapter.class.getName()});
        am.bindAdapterFactory(Mockito.mock(AdapterFactory.class), ref);
        assertTrue(am.getFactories().isEmpty(), "AdapterFactoryDescriptors must be empty");

        ref = createServiceReference(0, new String[] {TestSlingAdaptable.class.getName()}, null);
        am.bindAdapterFactory(Mockito.mock(AdapterFactory.class), ref);
        assertTrue(am.getFactories().isEmpty(), "AdapterFactoryDescriptors must be empty");

        ref = createServiceReference(0, new String[] {TestSlingAdaptable.class.getName()}, new String[0]);
        am.bindAdapterFactory(Mockito.mock(AdapterFactory.class), ref);
        assertTrue(am.getFactories().isEmpty(), "AdapterFactoryDescriptors must be empty");
    }

    @Test
    void testBindUnbind() {
        final ServiceReference<AdapterFactory> ref = createServiceReference();
        @SuppressWarnings("unchecked")
        final ServiceRegistration<Adaption> registration = Mockito.mock(ServiceRegistration.class);
        Mockito.when(ref.getBundle()
                        .getBundleContext()
                        .registerService(Mockito.eq(Adaption.class), Mockito.eq(AdaptionImpl.INSTANCE), Mockito.any()))
                .thenReturn(registration);
        am.bindAdapterFactory(Mockito.mock(AdapterFactory.class), ref);

        // check that a service is registered
        Mockito.verify(ref.getBundle().getBundleContext())
                .registerService(Mockito.eq(Adaption.class), Mockito.eq(AdaptionImpl.INSTANCE), Mockito.any());

        // expect the factory, but cache is empty
        assertNotNull(am.getFactories(), "AdapterFactoryDescriptors must not be null");
        assertEquals(1, am.getFactories().size(), "AdapterFactoryDescriptors must contain one entry");
        assertEquals(
                1, am.getFactories().get(TestSlingAdaptable.class.getName()).size());
        assertTrue(am.getFactoryCache().isEmpty(), "AdapterFactory cache must be empty");

        Map<String, AdapterFactoryDescriptorMap> f = am.getFactories();
        AdapterFactoryDescriptorMap afdm = f.get(TestSlingAdaptable.class.getName());
        assertNotNull(afdm);

        AdapterFactoryDescriptor afd = afdm.get(ref);
        assertNotNull(afd);
        assertNotNull(afd.getFactory());
        assertNotNull(afd.getAdapters());
        assertEquals(1, afd.getAdapters().length);
        assertEquals(ITestAdapter.class.getName(), afd.getAdapters()[0]);

        assertNull(f.get(TestSlingAdaptable2.class.getName()));

        Mockito.verify(registration, Mockito.never()).unregister();
        am.unbindAdapterFactory(ref);
        Mockito.verify(registration).unregister();
        assertTrue(am.getFactories().get(TestSlingAdaptable.class.getName()).isEmpty());
        assertTrue(am.getFactoryCache().isEmpty(), "AdapterFactory cache must be empty");
    }

    @Test
    void testBindModifiedUnbind() {
        ServiceReference<AdapterFactory> ref = createServiceReference();
        @SuppressWarnings("unchecked")
        final ServiceRegistration<Adaption> registration = Mockito.mock(ServiceRegistration.class);
        Mockito.when(ref.getBundle()
                        .getBundleContext()
                        .registerService(Mockito.eq(Adaption.class), Mockito.eq(AdaptionImpl.INSTANCE), Mockito.any()))
                .thenReturn(registration);
        am.bindAdapterFactory(Mockito.mock(AdapterFactory.class), ref);

        assertEquals(1, am.getFactories().size(), "AdapterFactoryDescriptors must contain one entry");
        assertEquals(
                1, am.getFactories().get(TestSlingAdaptable.class.getName()).size());

        Mockito.when(ref.getProperty(AdapterFactory.ADAPTABLE_CLASSES))
                .thenReturn(new String[] {TestSlingAdaptable2.class.getName()});
        am.updatedAdapterFactory(Mockito.mock(AdapterFactory.class), ref);
        assertEquals(2, am.getFactories().size(), "AdapterFactoryDescriptors must contain two entries");
        assertEquals(
                0, am.getFactories().get(TestSlingAdaptable.class.getName()).size());
        assertEquals(
                1, am.getFactories().get(TestSlingAdaptable2.class.getName()).size());
    }

    @Test
    void testAdaptBase() {
        TestSlingAdaptable data = new TestSlingAdaptable();
        assertNull(am.getAdapter(data, ITestAdapter.class), "Expect no adapter");

        final ServiceReference<AdapterFactory> ref = createServiceReference();
        final AdapterFactory af = Mockito.mock(AdapterFactory.class);
        Mockito.when(af.getAdapter(data, ITestAdapter.class)).thenReturn(Mockito.mock(ITestAdapter.class));
        am.bindAdapterFactory(af, ref);

        Object adapter = am.getAdapter(data, ITestAdapter.class);
        assertNotNull(adapter);
        assertTrue(adapter instanceof ITestAdapter);
    }

    @Test
    void testAdaptExtended() {
        TestSlingAdaptable2 data = new TestSlingAdaptable2();
        assertNull(am.getAdapter(data, ITestAdapter.class), "Expect no adapter");

        final ServiceReference<AdapterFactory> ref = createServiceReference();
        final AdapterFactory af = Mockito.mock(AdapterFactory.class);
        Mockito.when(af.getAdapter(data, ITestAdapter.class)).thenReturn(Mockito.mock(ITestAdapter.class));
        am.bindAdapterFactory(af, ref);

        Object adapter = am.getAdapter(data, ITestAdapter.class);
        assertNotNull(adapter);
        assertTrue(adapter instanceof ITestAdapter);
    }

    @Test
    void testAdaptBase2() {
        TestSlingAdaptable data = new TestSlingAdaptable();
        assertNull(am.getAdapter(data, ITestAdapter.class), "Expect no adapter");

        final ServiceReference<AdapterFactory> ref = createServiceReference();
        final AdapterFactory af = Mockito.mock(AdapterFactory.class);
        Mockito.when(af.getAdapter(data, ITestAdapter.class)).thenReturn(Mockito.mock(ITestAdapter.class));
        am.bindAdapterFactory(af, ref);

        final ServiceReference<AdapterFactory> ref2 = createServiceReference2();
        am.bindAdapterFactory(af, ref2);

        Object adapter = am.getAdapter(data, ITestAdapter.class);
        assertNotNull(adapter);
        assertTrue(adapter instanceof ITestAdapter);
    }

    @Test
    void testAdaptExtended2() {
        TestSlingAdaptable data = new TestSlingAdaptable();
        TestSlingAdaptable2 data2 = new TestSlingAdaptable2();

        final AdapterFactory af1 = Mockito.mock(AdapterFactory.class);
        Mockito.when(af1.getAdapter(data, ITestAdapter.class)).thenReturn(Mockito.mock(ITestAdapter.class));
        Mockito.when(af1.getAdapter(data2, ITestAdapter.class)).thenReturn(Mockito.mock(ITestAdapter.class));
        final AdapterFactory af2 = Mockito.mock(AdapterFactory.class);
        Mockito.when(af2.getAdapter(data2, TestAdapter.class)).thenReturn(Mockito.mock(TestAdapter.class));
        final ServiceReference<AdapterFactory> ref1 = createServiceReference();
        final ServiceReference<AdapterFactory> ref2 = createServiceReference2();
        Mockito.when(ref1.compareTo(ref2)).thenReturn(-1);
        Mockito.when(ref2.compareTo(ref1)).thenReturn(1);
        am.bindAdapterFactory(af1, ref1);
        am.bindAdapterFactory(af2, ref2);

        Object adapter = am.getAdapter(data, ITestAdapter.class);
        assertNotNull(adapter);
        assertTrue(adapter instanceof ITestAdapter);
        adapter = am.getAdapter(data, TestAdapter.class);
        assertNull(adapter);

        adapter = am.getAdapter(data2, ITestAdapter.class);
        assertNotNull(adapter);
        assertTrue(adapter instanceof ITestAdapter);
        adapter = am.getAdapter(data2, TestAdapter.class);
        assertNotNull(adapter);
        assertTrue(adapter instanceof TestAdapter);
    }

    @Test
    void testAdaptMultipleAdapterFactories() {
        final ServiceReference<AdapterFactory> firstAdaptable =
                this.createServiceReference(1, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), FirstImplementation.class.getName()
                });
        final ServiceReference<AdapterFactory> secondAdaptable =
                this.createServiceReference(2, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), SecondImplementation.class.getName()
                });
        Mockito.when(firstAdaptable.compareTo(secondAdaptable)).thenReturn(-1);
        Mockito.when(secondAdaptable.compareTo(firstAdaptable)).thenReturn(1);

        AdapterObject first = new AdapterObject(Want.FIRST_IMPL);
        assertNull(am.getAdapter(first, ParentInterface.class), "Expect no adapter");

        AdapterObject second = new AdapterObject(Want.SECOND_IMPL);
        assertNull(am.getAdapter(second, ParentInterface.class), "Expect no adapter");

        am.bindAdapterFactory(new FirstImplementationAdapterFactory(), firstAdaptable);
        am.bindAdapterFactory(new SecondImplementationAdapterFactory(), secondAdaptable);

        Object adapter = am.getAdapter(first, ParentInterface.class);
        assertNotNull(adapter, "Did not get an adapter back for first implementation, service ranking 1");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 1, ");

        adapter = am.getAdapter(second, ParentInterface.class);
        assertNotNull(adapter, "Did not get an adapter back for second implementation, service ranking 2");
        assertTrue(
                adapter instanceof SecondImplementation,
                "Did not get the correct adaptable back for second implementation, service ranking 2, ");

        adapter = am.getAdapter(first, FirstImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for first implementation, service ranking 1");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 1, ");

        adapter = am.getAdapter(second, SecondImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for second implementation, service ranking 2");
        assertTrue(
                adapter instanceof SecondImplementation,
                "Did not get the correct adaptable back for second implementation, service ranking 2, ");
    }

    @Test
    void testAdaptMultipleAdapterFactoriesReverseOrder() {
        final ServiceReference<AdapterFactory> firstAdaptable = this.createServiceReference(
                2, new String[] {AdapterObject.class.getName()}, new String[] {ParentInterface.class.getName()});
        final ServiceReference<AdapterFactory> secondAdaptable = this.createServiceReference(
                1, new String[] {AdapterObject.class.getName()}, new String[] {ParentInterface.class.getName()});
        Mockito.when(firstAdaptable.compareTo(secondAdaptable)).thenReturn(1);
        Mockito.when(secondAdaptable.compareTo(firstAdaptable)).thenReturn(-1);

        AdapterObject first = new AdapterObject(Want.FIRST_IMPL);
        assertNull(am.getAdapter(first, ParentInterface.class), "Expect no adapter");

        AdapterObject second = new AdapterObject(Want.SECOND_IMPL);
        assertNull(am.getAdapter(second, ParentInterface.class), "Expect no adapter");

        am.bindAdapterFactory(new FirstImplementationAdapterFactory(), firstAdaptable);
        am.bindAdapterFactory(new SecondImplementationAdapterFactory(), secondAdaptable);

        Object adapter = am.getAdapter(first, ParentInterface.class);
        assertNotNull(adapter, "Did not get an adapter back for first implementation, service ranking 2");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 2, ");
    }

    @Test
    void testAdaptMultipleAdapterFactoriesServiceRanking() {
        final ServiceReference<AdapterFactory> firstAdaptable =
                createServiceReference(1, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), FirstImplementation.class.getName()
                });
        final ServiceReference<AdapterFactory> secondAdaptable =
                createServiceReference(2, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), SecondImplementation.class.getName()
                });
        Mockito.when(firstAdaptable.compareTo(secondAdaptable)).thenReturn(-1);
        Mockito.when(secondAdaptable.compareTo(firstAdaptable)).thenReturn(1);

        AdapterObject first = new AdapterObject(Want.INDIFFERENT);
        assertNull(am.getAdapter(first, ParentInterface.class), "Expect no adapter");

        AdapterObject second = new AdapterObject(Want.INDIFFERENT);
        assertNull(am.getAdapter(second, ParentInterface.class), "Expect no adapter");

        am.bindAdapterFactory(new FirstImplementationAdapterFactory(), firstAdaptable);
        am.bindAdapterFactory(new SecondImplementationAdapterFactory(), secondAdaptable);

        Object adapter = am.getAdapter(first, ParentInterface.class);
        assertNotNull(
                adapter,
                "Did not get an adapter back for first implementation (from ParentInterface), service ranking 1");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 1, ");

        adapter = am.getAdapter(first, FirstImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for first implementation, service ranking 1");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 1, ");

        adapter = am.getAdapter(second, SecondImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for second implementation, service ranking 2");
        assertTrue(
                adapter instanceof SecondImplementation,
                "Did not get the correct adaptable back for second implementation, service ranking 2, ");
    }

    @Test
    void testAdaptMultipleAdapterFactoriesServiceRankingSecondHigherOrder() {
        final ServiceReference<AdapterFactory> firstAdaptable =
                createServiceReference(2, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), FirstImplementation.class.getName()
                });
        final ServiceReference<AdapterFactory> secondAdaptable =
                createServiceReference(1, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), SecondImplementation.class.getName()
                });
        Mockito.when(firstAdaptable.compareTo(secondAdaptable)).thenReturn(1);
        Mockito.when(secondAdaptable.compareTo(firstAdaptable)).thenReturn(-1);

        AdapterObject first = new AdapterObject(Want.INDIFFERENT);
        assertNull(am.getAdapter(first, ParentInterface.class), "Expect no adapter");

        AdapterObject second = new AdapterObject(Want.INDIFFERENT);
        assertNull(am.getAdapter(second, ParentInterface.class), "Expect no adapter");

        am.bindAdapterFactory(new FirstImplementationAdapterFactory(), firstAdaptable);
        am.bindAdapterFactory(new SecondImplementationAdapterFactory(), secondAdaptable);

        Object adapter = am.getAdapter(first, ParentInterface.class);
        assertNotNull(
                adapter,
                "Did not get an adapter back for second implementation (from ParentInterface), service ranking 1");
        assertTrue(
                adapter instanceof SecondImplementation,
                "Did not get the correct adaptable back for second implementation, service ranking 1, ");

        adapter = am.getAdapter(first, FirstImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for first implementation, service ranking 1");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 1, ");

        adapter = am.getAdapter(second, SecondImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for second implementation, service ranking 2");
        assertTrue(
                adapter instanceof SecondImplementation,
                "Did not get the correct adaptable back for second implementation, service ranking 2, ");
    }

    @Test
    void testAdaptMultipleAdapterFactoriesServiceRankingReverse() {
        final ServiceReference<AdapterFactory> firstAdaptable =
                this.createServiceReference(1, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), FirstImplementation.class.getName()
                });
        final ServiceReference<AdapterFactory> secondAdaptable =
                this.createServiceReference(2, new String[] {AdapterObject.class.getName()}, new String[] {
                    ParentInterface.class.getName(), SecondImplementation.class.getName()
                });
        Mockito.when(firstAdaptable.compareTo(secondAdaptable)).thenReturn(-1);
        Mockito.when(secondAdaptable.compareTo(firstAdaptable)).thenReturn(1);

        AdapterObject first = new AdapterObject(Want.INDIFFERENT);
        assertNull(am.getAdapter(first, ParentInterface.class), "Expect no adapter");

        AdapterObject second = new AdapterObject(Want.INDIFFERENT);
        assertNull(am.getAdapter(second, ParentInterface.class), "Expect no adapter");

        // bind these in reverse order from the non-reverse test
        am.bindAdapterFactory(new SecondImplementationAdapterFactory(), secondAdaptable);
        am.bindAdapterFactory(new FirstImplementationAdapterFactory(), firstAdaptable);

        Object adapter = am.getAdapter(first, ParentInterface.class);
        assertNotNull(
                adapter,
                "Did not get an adapter back for first implementation (from ParentInterface), service ranking 1");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 1, ");

        adapter = am.getAdapter(first, FirstImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for first implementation, service ranking 1");
        assertTrue(
                adapter instanceof FirstImplementation,
                "Did not get the correct adaptable back for first implementation, service ranking 1, ");

        adapter = am.getAdapter(second, SecondImplementation.class);
        assertNotNull(adapter, "Did not get an adapter back for second implementation, service ranking 2");
        assertTrue(
                adapter instanceof SecondImplementation,
                "Did not get the correct adaptable back for second implementation, service ranking 2, ");
    }

    // ---------- Test Adaptable and Adapter Classes ---------------------------

    public static class TestSlingAdaptable extends SlingAdaptable {}

    public static class TestSlingAdaptable2 extends TestSlingAdaptable {}

    public static interface ITestAdapter {}

    public static class TestAdapter {}

    public class FirstImplementationAdapterFactory implements AdapterFactory {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getAdapter(Object adaptable, Class<T> type) {
            if (adaptable instanceof AdapterObject) {
                AdapterObject adapterObject = (AdapterObject) adaptable;
                switch (adapterObject.getWhatWeWant()) {
                    case FIRST_IMPL, INDIFFERENT:
                        return (T) new FirstImplementation();
                    case SECOND_IMPL:
                        return null;
                }
            }
            throw new RuntimeException("Must pass the correct adaptable");
        }
    }

    public class SecondImplementationAdapterFactory implements AdapterFactory {

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getAdapter(Object adaptable, Class<T> type) {
            if (adaptable instanceof AdapterObject) {
                AdapterObject adapterObject = (AdapterObject) adaptable;
                switch (adapterObject.getWhatWeWant()) {
                    case SECOND_IMPL, INDIFFERENT:
                        return (T) new SecondImplementation();
                    case FIRST_IMPL:
                        return null;
                }
            }
            throw new RuntimeException("Must pass the correct adaptable");
        }
    }

    public static interface ParentInterface {}

    public static class FirstImplementation implements ParentInterface {}

    public static class SecondImplementation implements ParentInterface {}

    public enum Want {

        /**
         * Indicates we definitively want the "first implementation" adapter factory to execute the adapt
         */
        FIRST_IMPL,

        /**
         * Indicates we definitively want the "second implementation" adapter factory to execute the adapt
         */
        SECOND_IMPL,

        /**
         * Indicates we are indifferent to which factory is used to execute the adapt, used for testing service ranking
         */
        INDIFFERENT
    }

    public static class AdapterObject {
        private Want whatWeWant;

        public AdapterObject(Want whatWeWant) {
            this.whatWeWant = whatWeWant;
        }

        public Want getWhatWeWant() {
            return whatWeWant;
        }
    }
}
