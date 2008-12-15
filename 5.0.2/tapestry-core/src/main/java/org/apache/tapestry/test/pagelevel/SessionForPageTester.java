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

package org.apache.tapestry.test.pagelevel;

import static org.apache.tapestry.ioc.internal.util.CollectionFactory.newList;
import static org.apache.tapestry.ioc.internal.util.CollectionFactory.newMap;

import java.util.List;
import java.util.Map;

import org.apache.tapestry.ioc.internal.util.InternalUtils;
import org.apache.tapestry.services.Session;

public class SessionForPageTester implements Session
{
    private Map<String, Object> attributes = newMap();

    public List<String> getAttributeNames()
    {
        return InternalUtils.sortedKeys(attributes);
    }

    public List<String> getAttributeNames(String prefix)
    {
        List<String> result = newList();

        for (String name : getAttributeNames())
        {
            if (name.startsWith(prefix))
                result.add(name);
        }
        return result;
    }

    public Object getAttribute(String name)
    {
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value)
    {
        if (value == null)
        {
            attributes.remove(name);
        }
        else
        {
            attributes.put(name, value);
        }

    }

}