/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.struts2.interceptor;

import static org.apache.struts2.interceptor.ResourceIsolationPolicy.SEC_FETCH_DEST_HEADER;
import static org.apache.struts2.interceptor.ResourceIsolationPolicy.SEC_FETCH_MODE_HEADER;
import static org.apache.struts2.interceptor.ResourceIsolationPolicy.SEC_FETCH_SITE_HEADER;
import static org.apache.struts2.interceptor.ResourceIsolationPolicy.VARY_HEADER;

import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;
import com.opensymphony.xwork2.interceptor.PreResultListener;
import com.opensymphony.xwork2.util.TextParseUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.Set;

/**
 * Interceptor that implements Fetch Metadata policy on incoming requests used to protect against
 * CSRF, XSSI, and cross-origin information leaks. Uses {@link StrutsResourceIsolationPolicy} to
 * filter the requests allowed to be processed.
 *
 * @see <a href="https://web.dev/fetch-metadata/">https://web.dev/fetch-metadata/</a>
 **/

public class FetchMetadataInterceptor extends AbstractInterceptor implements PreResultListener {

    private final Set<String> exemptedPaths = new HashSet<String>();
    private final ResourceIsolationPolicy resourceIsolationPolicy = new StrutsResourceIsolationPolicy();
    private static final String VARY_HEADER_VALUE = String.format("%s,%s,%s", SEC_FETCH_DEST_HEADER, SEC_FETCH_SITE_HEADER, SEC_FETCH_MODE_HEADER);
    private static final String SC_FORBIDDEN = String.valueOf(HttpServletResponse.SC_FORBIDDEN);

    public void setExemptedPaths(String paths){
        this.exemptedPaths.addAll(TextParseUtil.commaDelimitedStringToSet(paths));
    }

    @Override
    public void beforeResult(ActionInvocation invocation, String resultCode) {
        // Add Vary headers
        HttpServletResponse response = invocation.getInvocationContext().getServletResponse();
        response.setHeader(VARY_HEADER, VARY_HEADER_VALUE);
    }

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        ActionContext context = invocation.getInvocationContext();
        HttpServletRequest request = context.getServletRequest();

        // Adds listener that operates between interceptor and result rendering to set Vary headers
        invocation.addPreResultListener(this);

        // Apply exemptions: paths/endpoints meant to be served cross-origin
        if (exemptedPaths.contains(request.getContextPath())) {
            return invocation.invoke();
        }

        // Check if request is allowed
        if (resourceIsolationPolicy.isRequestAllowed(request)) {
            return invocation.invoke();
        }

        beforeResult(invocation, SC_FORBIDDEN);
        return SC_FORBIDDEN;
    }
}
