// Copyright 2007 The Apache Software Foundation
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

package org.apache.tapestry.internal.services;

import org.apache.tapestry.services.Ajax;
import org.apache.tapestry.services.ComponentActionRequestFilter;
import org.apache.tapestry.services.ComponentActionRequestHandler;
import org.apache.tapestry.services.Request;

import java.io.IOException;

/**
 * A filter that intercepts Ajax-oriented requests, thos that originate on the client-side using
 * XmlHttpRequest. In these cases, the action processing occurs normally, but the response is quite
 * different.
 */
public class AjaxFilter implements ComponentActionRequestFilter
{
    private final Request _request;

    private final ComponentActionRequestHandler _ajaxHandler;

    public AjaxFilter(Request request, @Ajax ComponentActionRequestHandler ajaxHandler)
    {
        _request = request;
        _ajaxHandler = ajaxHandler;
    }


    public void handle(String logicalPageName, String nestedComponentId, String eventType, String[] context,
                       String[] activationContext, ComponentActionRequestHandler handler) throws IOException
    {
        ComponentActionRequestHandler next = _request.isXHR() ? _ajaxHandler : handler;

        next.handle(logicalPageName, nestedComponentId, eventType, context, activationContext);
    }

}