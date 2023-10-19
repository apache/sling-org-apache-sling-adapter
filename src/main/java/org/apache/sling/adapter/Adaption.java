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
package org.apache.sling.adapter;

/**
 * The <pre>Adaption</pre> is a marker interface which is registered as a service by the <pre>AdapterManager</pre> once a
 * certain <pre>AdapterFactory</pre> is available
 * 
 * <p>
 * Its intended use is to make it simple for declarative service components to wait for a certain
 * <pre>AdapterFactory</pre> to be available
 * 
 * <p>
 * A usage sample is
 * 
 * <code>@Reference(referenceInterface=Adaption.class,target="(&amp;(adaptables=com.myco.MyClass)(adapters=org.apache.sling.api.Resource))", name = "ignore", strategy = ReferenceStrategy.LOOKUP)</code>
 *
 */
public interface Adaption {

}
