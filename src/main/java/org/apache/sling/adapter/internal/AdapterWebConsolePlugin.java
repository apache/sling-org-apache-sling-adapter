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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.adapter.AdapterFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.adapter.AdapterFactory.ADAPTABLE_CLASSES;
import static org.apache.sling.api.adapter.AdapterFactory.ADAPTER_CLASSES;

@SuppressWarnings("serial")
@Component(
        service = Servlet.class,
        property = {
            Constants.SERVICE_DESCRIPTION + "=Adapter Web Console Plugin",
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            "felix.webconsole.label=adapters",
            "felix.webconsole.title=Sling Adapters",
            "felix.webconsole.css=/adapters/res/ui/adapters.css",
            "felix.webconsole.configprinter.modes=always",
            "felix.webconsole.category=Sling"
        })
public class AdapterWebConsolePlugin extends HttpServlet
        implements ServiceTrackerCustomizer<AdapterFactory, Object>, BundleListener {

    private static final String ADAPTER_CONDITION = "adapter.condition";

    private static final String ADAPTER_DEPRECATED = "adapter.deprecated";

    private final transient Logger logger = LoggerFactory.getLogger(AdapterWebConsolePlugin.class);

    @SuppressWarnings("deprecation")
    @Reference
    private transient org.osgi.service.packageadmin.PackageAdmin packageAdmin;

    private transient List<AdaptableDescription> allAdaptables;
    private transient Map<ServiceReference<AdapterFactory>, List<AdaptableDescription>> adapterServiceReferences;
    private transient Map<Bundle, List<AdaptableDescription>> adapterBundles;

    private transient ServiceTracker<AdapterFactory, Object> adapterTracker;

    private transient BundleContext bundleContext;

    @Override
    public Object addingService(final ServiceReference<AdapterFactory> reference) {
        addServiceMetadata(reference);
        return reference;
    }

    private void addServiceMetadata(final ServiceReference<AdapterFactory> reference) {
        final Converter converter = Converters.standardConverter();
        final String[] adaptables =
                converter.convert(reference.getProperty(ADAPTABLE_CLASSES)).to(String[].class);
        final String[] adapters =
                converter.convert(reference.getProperty(ADAPTER_CLASSES)).to(String[].class);
        final String condition =
                converter.convert(reference.getProperty(ADAPTER_CONDITION)).to(String.class);
        final boolean deprecated = converter
                .convert(reference.getProperty(ADAPTER_DEPRECATED))
                .defaultValue(false)
                .to(Boolean.class);

        if (adapters.length > 0) {
            final List<AdaptableDescription> descriptions = new ArrayList<>(adaptables.length);
            for (final String adaptable : adaptables) {
                descriptions.add(
                        new AdaptableDescription(reference.getBundle(), adaptable, adapters, condition, deprecated));
            }
            synchronized (this) {
                adapterServiceReferences.put(reference, descriptions);
                update();
            }
        }
    }

    @Override
    public void bundleChanged(final BundleEvent event) {
        if (event.getType() == BundleEvent.STOPPED) {
            removeBundle(event.getBundle());
        } else if (event.getType() == BundleEvent.STARTED) {
            addBundle(event.getBundle());
        }
    }

    @Override
    public void modifiedService(final ServiceReference<AdapterFactory> reference, final Object service) {
        addServiceMetadata(reference);
    }

    @Override
    public void removedService(final ServiceReference<AdapterFactory> reference, final Object service) {
        synchronized (this) {
            adapterServiceReferences.remove(reference);
            update();
        }
    }

    private void addBundle(final Bundle bundle) {
        final List<AdaptableDescription> descs = new ArrayList<>();
        try {
            final Enumeration<URL> files = bundle.getResources("SLING-INF/adapters.json");
            if (files != null) {
                while (files.hasMoreElements()) {
                    final Map<String, Object> config = new HashMap<>();
                    config.put("org.apache.johnzon.supports-comments", true);
                    final JsonReaderFactory readerFactory = Json.createReaderFactory(config);
                    try (JsonReader jsonReader =
                            readerFactory.createReader(files.nextElement().openStream(), StandardCharsets.UTF_8)) {
                        final JsonObject obj = jsonReader.readObject();
                        for (final Iterator<String> adaptableNames =
                                        obj.keySet().iterator();
                                adaptableNames.hasNext(); ) {
                            final String adaptableName = adaptableNames.next();
                            final JsonObject adaptable = obj.getJsonObject(adaptableName);
                            for (final Iterator<String> conditions =
                                            adaptable.keySet().iterator();
                                    conditions.hasNext(); ) {
                                final String condition = conditions.next();
                                String[] adapters;
                                final JsonValue value = adaptable.get(condition);
                                if (value instanceof JsonArray jsonArray) {
                                    adapters = toStringArray(jsonArray);
                                } else {
                                    adapters = new String[] {toString(value)};
                                }
                                descs.add(new AdaptableDescription(bundle, adaptableName, adapters, condition, false));
                            }
                        }
                    }
                }
            }
            if (!descs.isEmpty()) {
                synchronized (this) {
                    adapterBundles.put(bundle, descs);
                    update();
                }
            }
        } catch (final IOException | JsonException e) {
            logger.error("Unable to load adapter descriptors for bundle " + bundle, e);
        }
    }

    private String toString(JsonValue value) {
        String strValue;
        if (value instanceof JsonString jsonString) {
            strValue = jsonString.getString();
        } else {
            strValue = value.toString();
        }
        return strValue;
    }

    private String[] toStringArray(final JsonArray value) {
        final List<String> result = new ArrayList<>(value.size());
        for (int i = 0; i < value.size(); i++) {
            result.add(value.getString(i));
        }
        return result.toArray(new String[value.size()]);
    }

    private void removeBundle(final Bundle bundle) {
        synchronized (this) {
            adapterBundles.remove(bundle);
            update();
        }
    }

    private void update() {
        final List<AdaptableDescription> newList = new ArrayList<>();
        for (final List<AdaptableDescription> descriptions : adapterServiceReferences.values()) {
            newList.addAll(descriptions);
        }
        for (final List<AdaptableDescription> list : adapterBundles.values()) {
            newList.addAll(list);
        }
        Collections.sort(newList);
        allAdaptables = newList;
    }

    @Activate
    protected void activate(final BundleContext ctx) throws InvalidSyntaxException {
        this.bundleContext = ctx;
        this.adapterServiceReferences = new HashMap<>();
        this.adapterBundles = new HashMap<>();
        for (final Bundle bundle : this.bundleContext.getBundles()) {
            if (bundle.getState() == Bundle.ACTIVE) {
                addBundle(bundle);
            }
        }

        this.bundleContext.addBundleListener(this);
        final Filter filter = this.bundleContext.createFilter(
                "(&(adaptables=*)(adapters=*)(" + Constants.OBJECTCLASS + "=" + AdapterFactory.SERVICE_NAME + "))");
        this.adapterTracker = new ServiceTracker<>(this.bundleContext, filter, this);
        this.adapterTracker.open();
    }

    @Deactivate
    protected void deactivate() {
        this.bundleContext.removeBundleListener(this);
        this.adapterTracker.close();
        this.adapterServiceReferences = null;
        this.adapterBundles = null;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        if (req.getPathInfo().endsWith("/data.json")) {
            getJson(resp);
        } else {
            getHtml(resp);
        }
    }

    private void getJson(final HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        try {
            Map<String, Map<String, List<String>>> values = new HashMap<>();
            for (final AdaptableDescription desc : allAdaptables) {
                final Map<String, List<String>> adaptableObj;
                if (values.containsKey(desc.adaptable)) {
                    adaptableObj = values.get(desc.adaptable);
                } else {
                    adaptableObj = new HashMap<>();
                    values.put(desc.adaptable, adaptableObj);
                }
                for (final String adapter : desc.adapters) {
                    List<String> conditions = adaptableObj.get(desc.condition == null ? "" : desc.condition);
                    if (conditions == null) {
                        conditions = new ArrayList<>();
                        adaptableObj.put(desc.condition == null ? "" : desc.condition, conditions);
                    }
                    conditions.add(adapter);
                }
            }
            final JsonObjectBuilder obj = Json.createObjectBuilder();

            for (Map.Entry<String, Map<String, List<String>>> entry : values.entrySet()) {
                JsonObjectBuilder adaptable = Json.createObjectBuilder();

                for (Map.Entry<String, List<String>> subEnty : entry.getValue().entrySet()) {
                    if (subEnty.getValue().size() > 1) {
                        JsonArrayBuilder array = Json.createArrayBuilder();
                        for (String condition : subEnty.getValue()) {
                            array.add(condition);
                        }
                        adaptable.add(subEnty.getKey(), array);
                    } else {
                        adaptable.add(subEnty.getKey(), subEnty.getValue().get(0));
                    }
                }

                obj.add(entry.getKey(), adaptable);
            }

            try (JsonGenerator generator = Json.createGenerator(resp.getWriter())) {
                generator.write(obj.build()).flush();
            }
        } catch (final JsonException e) {
            throw new ServletException("Unable to produce JSON", e);
        }
    }

    private void getHtml(final HttpServletResponse resp) throws IOException {
        final PrintWriter writer = resp.getWriter();
        writer.println("<p class=\"statline ui-state-highlight\">${Introduction}</p>");
        writer.println("<p>${intro}</p>");
        writer.println("<p class=\"statline ui-state-highlight\">${How to Use This Information}</p>");
        writer.println("<p>${usage}</p>");
        writer.println("<table class=\"adapters nicetable\">");
        writer.println(
                "<thead><tr><th class=\"header\">${Adaptable Class}</th><th class=\"header\">${Adapter Class}</th><th class=\"header\">${Condition}</th><th class=\"header\">${Deprecated}</th><th class=\"header\">${Providing Bundle}</th></tr></thead>");
        String rowClass = "odd";
        for (final AdaptableDescription desc : allAdaptables) {
            writer.printf("<tr class=\"%s ui-state-default\"><td>", rowClass);
            boolean packageExported = AdapterManagerImpl.checkPackage(packageAdmin, desc.adaptable);
            if (!packageExported) {
                writer.print("<span class='error'>");
            }
            writer.print(desc.adaptable);
            if (!packageExported) {
                writer.print("</span>");
            }
            writer.print("</td>");
            writer.print("<td>");
            for (final String adapter : desc.adapters) {
                packageExported = AdapterManagerImpl.checkPackage(packageAdmin, adapter);
                if (!packageExported) {
                    writer.print("<span class='error'>");
                }
                writer.print(adapter);
                if (!packageExported) {
                    writer.print("</span>");
                }
                writer.print("<br/>");
            }
            writer.print("</td>");
            if (desc.condition == null) {
                writer.print("<td>&nbsp;</td>");
            } else {
                writer.printf("<td>%s</td>", desc.condition);
            }
            if (desc.deprecated) {
                writer.print("<td>${Deprecated}</td>");
            } else {
                writer.print("<td></td>");
            }
            writer.printf(
                    "<td><a href=\"${pluginRoot}/../bundles/%s\">%s (%s)</a></td>",
                    desc.bundle.getBundleId(), desc.bundle.getSymbolicName(), desc.bundle.getBundleId());
            writer.println("</tr>");

            if (rowClass.equals("odd")) {
                rowClass = "even";
            } else {
                rowClass = "odd";
            }
        }
        writer.println("</table>");
    }

    public void printConfiguration(final PrintWriter pw) {
        pw.println("Current Apache Sling Adaptables:");
        for (final AdaptableDescription desc : allAdaptables) {
            pw.printf("Adaptable: %s%n", desc.adaptable);
            if (desc.condition != null) {
                pw.printf("Condition: %s%n", desc.condition);
            }
            pw.printf("Providing Bundle: %s%n", desc.bundle.getSymbolicName());
            pw.printf("Available Adapters:%n");
            for (final String adapter : desc.adapters) {
                pw.print(" * ");
                pw.println(adapter);
            }
            pw.println();
        }
    }

    /**
     * Method to retreive static resources from this bundle.
     */
    @SuppressWarnings("unused")
    private URL getResource(final String path) {
        if (path.startsWith("/adapters/res/ui/")) {
            return this.getClass().getResource(path.substring(9));
        }
        return null;
    }

    class AdaptableDescription implements Comparable<AdaptableDescription> {
        private final @NotNull String adaptable;
        private final @NotNull String[] adapters;
        private final @Nullable String condition;
        private final @NotNull Bundle bundle;
        private final boolean deprecated;

        public AdaptableDescription(
                final @NotNull Bundle bundle,
                final @NotNull String adaptable,
                final @NotNull String[] adapters,
                final @Nullable String condition,
                boolean deprecated) {
            this.adaptable = adaptable;
            this.adapters = adapters;
            this.condition = condition;
            this.bundle = bundle;
            this.deprecated = deprecated;
        }

        @Override
        public String toString() {
            return "AdapterDescription [adaptable=" + this.adaptable + ", adapters=" + Arrays.toString(this.adapters)
                    + ", condition=" + this.condition + ", bundle=" + this.bundle + ", deprecated= " + this.deprecated
                    + "]";
        }

        @Override
        public int compareTo(final AdaptableDescription o) {
            int result = this.adaptable.compareTo(o.adaptable);
            if (result == 0) {
                Comparator<String> safeComparator = Comparator.nullsFirst(Comparator.naturalOrder());
                result = safeComparator.compare(this.condition, o.condition);
                if (result == 0) {
                    result = this.adapters.length - o.adapters.length;
                    if (result == 0) {
                        result = (int) this.bundle.getBundleId() - (int) o.bundle.getBundleId();
                    }
                }
            }
            return result;
        }
    }
}
