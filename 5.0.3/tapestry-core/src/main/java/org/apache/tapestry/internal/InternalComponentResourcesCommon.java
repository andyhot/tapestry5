// Copyright 2006, 2007 The Apache Software Foundation
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

package org.apache.tapestry.internal;

import org.apache.tapestry.Binding;
import org.apache.tapestry.internal.structure.ComponentPageElement;
import org.apache.tapestry.runtime.Component;
import org.apache.tapestry.runtime.RenderQueue;

/**
 * Operations shared by {@link InternalComponentResources} and {@link ComponentPageElement}.
 * Typically, these means methods of InternalComponentResources that are delegated to the component
 * page element.
 */
public interface InternalComponentResourcesCommon
{
    /**
     * Get the current persisted value of the field.
     * 
     * @param fieldName
     *            the name of the field to access
     * @return the value stored for the field, or null if no value is currently stored
     */
    Object getFieldChange(String fieldName);

    /** Checks to see if there is a value stored for the indicated field. */
    boolean hasFieldChange(String fieldName);

    /**
     * Returns true if the component has finished loading. Initially, this value will be false.
     * 
     * @see org.apache.tapestry.runtime.PageLifecycleListener#containingPageDidLoad()
     */
    boolean isLoaded();

    /**
     * Used during construction of the page to identify the binding for a particular parameter.
     * <p>
     */
    void bindParameter(String parameterName, Binding binding);

    /**
     * Returns the mixin instance for the fully qualfied mixin class name.
     * 
     * @param mixinClassName
     *            fully qualified class name
     * @return IllegalArgumentException if no such mixin is associated with the core component
     */
    Component getMixinByClassName(String mixinClassName);

    /** Invoked to make the receiver queue itself to be rendered. */
    void queueRender(RenderQueue queue);
}