// Copyright 2006 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry.services;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Service used to store the current request objects, both the Servlet API versions, and the
 * Tapestry generic versions.
 * 
 * 
 */
public interface RequestGlobals
{
    /** Stores the servlet API request and response objects, for access via the properties. */
    void store(HttpServletRequest request, HttpServletResponse response);

    HttpServletRequest getHTTPServletRequest();

    HttpServletResponse getHTTPServletResponse();

    void store(Request request, Response response);

    /** Accessible as injected object "infrastructure:Request". */
    Request getRequest();

    /** Accessible as injected object "infrastructure:Response". */
    Response getResponse();
}