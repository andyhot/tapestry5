// Copyright 2010, 2011 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.internal.services;

import org.apache.tapestry5.internal.structure.Page;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.services.InvalidationListener;
import org.apache.tapestry5.services.pageload.ComponentRequestSelectorAnalyzer;
import org.apache.tapestry5.services.pageload.ComponentResourceSelector;

import java.lang.ref.SoftReference;
import java.util.Map;

public class PageSourceImpl implements PageSource, InvalidationListener
{
    private final ComponentRequestSelectorAnalyzer selectorAnalyzer;

    private final PageLoader pageLoader;

    private static final class CachedPageKey
    {
        final String pageName;

        final ComponentResourceSelector selector;

        public CachedPageKey(String pageName, ComponentResourceSelector selector)
        {
            this.pageName = pageName;
            this.selector = selector;
        }

        public int hashCode()
        {
            return 37 * pageName.hashCode() + selector.hashCode();
        }

        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;

            if (!(obj instanceof CachedPageKey))
                return false;

            CachedPageKey other = (CachedPageKey) obj;

            return pageName.equals(other.pageName) && selector.equals(other.selector);
        }
    }

    private final Map<CachedPageKey, SoftReference<Page>> pageCache = CollectionFactory.newConcurrentMap();

    public PageSourceImpl(PageLoader pageLoader, ComponentRequestSelectorAnalyzer selectorAnalyzer)
    {
        this.pageLoader = pageLoader;
        this.selectorAnalyzer = selectorAnalyzer;
    }

    public void objectWasInvalidated()
    {
        clearCache();
    }

    public Page getPage(String canonicalPageName)
    {
        ComponentResourceSelector selector = selectorAnalyzer.buildSelectorForRequest();

        CachedPageKey key = new CachedPageKey(canonicalPageName, selector);

        // The while loop looks superfluous, but it helps to ensure that the Page instance,
        // with all of its mutable construction-time state, is properly published to other
        // threads (at least, as I understand Brian Goetz's explanation, it should be).

        while (true)
        {
            SoftReference<Page> ref = pageCache.get(key);

            Page page = ref == null ? null : ref.get();

            if (page != null)
            {
                return page;
            }

            // In rare race conditions, we may see the same page loaded multiple times across
            // different threads. The last built one will "evict" the others from the page cache,
            // and the earlier ones will be GCed.

            page = pageLoader.loadPage(canonicalPageName, selector);

            ref = new SoftReference<Page>(page);

            pageCache.put(key, ref);
        }
    }

    public void clearCache()
    {
        pageCache.clear();
    }
}
