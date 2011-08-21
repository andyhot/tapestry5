// Copyright 2007, 2008, 2010, 2011 The Apache Software Foundation
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

package org.apache.tapestry5.corelib.components;

import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.MarkupWriter;
import org.apache.tapestry5.RadioContainer;
import org.apache.tapestry5.annotations.Environmental;
import org.apache.tapestry5.annotations.Mixin;
import org.apache.tapestry5.annotations.Parameter;
import org.apache.tapestry5.corelib.base.AbstractField;
import org.apache.tapestry5.corelib.mixins.RenderDisabled;
import org.apache.tapestry5.corelib.mixins.RenderInformals;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.services.ComponentDefaultProvider;

/**
 * A radio button (i.e., &lt;input type="radio"&gt;). Radio buttons <strong>must</strong> operate within a
 * {@link RadioContainer} (normally, the {@link RadioGroup} component).
 * <p/>
 * If the value parameter is not bound, then the default value is a property of the container component whose name
 * matches the Radio component's id.
 * 
 * @tapestrydoc
 * @see RadioGroup
 * @see Form
 * @see Select
 */
public class Radio extends AbstractField
{
    @Environmental
    private RadioContainer container;

    /**
     * The value associated with this radio button. This is used to determine which radio button will be selected when
     * the page is rendered, and also becomes the value assigned when the form is submitted.
     */
    @Parameter(required = true, principal = true, autoconnect = true)
    private Object value;

    @Inject
    private ComponentDefaultProvider defaultProvider;

    @Inject
    private ComponentResources resources;

    @SuppressWarnings("unused")
    @Mixin
    private RenderInformals renderInformals;

    @SuppressWarnings("unused")
    @Mixin
    private RenderDisabled renderDisabled;

    /**
     * If true, then the field will render out with a disabled attribute (to turn off client-side behavior). Further, a
     * disabled field ignores any value in the request when the form is submitted.
     */
    @Parameter("false")
    private boolean disabled;

    /**
     * Returns true if this component has been expressly disabled (via its disabled parameter), or if the
     * {@link RadioContainer container} has been disabled.
     */
    public boolean isDisabled()
    {
        return disabled || container.isDisabled();
    }

    @Override
    protected void processSubmission(String elementName) {
        // do nothing, all work is done by surrounding RadioGroup
    }

    void beginRender(MarkupWriter writer)
    {
        String value = container.toClient(this.value);

        writer.element("input", "type", "radio", "id", getClientId(), "name", container.getControlName(), "value", value);

        if (container.isSelected(this.value))
            writer.attributes("checked", "checked");
    }

    void afterRender(MarkupWriter writer)
    {
        writer.end();
    }

    /**
     * Returns false; the RadioComponent component does not support declarative field validation.
     */
    public boolean isRequired()
    {
        return false;
    }
}
