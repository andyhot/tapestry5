// Copyright 2007, 2008, 2009, 2011 The Apache Software Foundation
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

import org.apache.tapestry5.*;
import org.apache.tapestry5.annotations.Environmental;
import org.apache.tapestry5.annotations.Events;
import org.apache.tapestry5.annotations.Parameter;
import org.apache.tapestry5.corelib.base.AbstractField;
import org.apache.tapestry5.internal.TapestryInternalUtils;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.services.ComponentDefaultProvider;
import org.apache.tapestry5.services.Environment;
import org.apache.tapestry5.services.FormSupport;
import org.apache.tapestry5.services.Request;

/**
 * A wrapper component around some number of {@link Radio} components, used to organize the selection and define the
 * property to be edited. Examples of its use are in the {@link Radio} documentation.
 * 
 * @tapestrydoc
 */
@Events(EventConstants.VALIDATE)
public class RadioGroup extends AbstractField
{
    /**
     * The property read and updated by the group as a whole.
     */
    @Parameter(required = true, principal = true, autoconnect = true)
    private Object value;

    /**
     * If true, then the field will render out with a disabled attribute (to turn off client-side behavior). Further, a
     * disabled field ignores any value in the request when the form is submitted.
     */
    @Parameter("false")
    private boolean disabled;

    /**
     * A ValueEncoder used to convert server-side objects (provided by the
     * selected Radio componnent's "value" parameter) into unique client-side
     * strings (typically IDs) and back. Note: this parameter may be OMITTED if
     * Tapestry is configured to provide a ValueEncoder automatically for the
     * type of property bound to the "value" parameter. 
     */
    @Parameter(required = true, allowNull = false)
    private ValueEncoder encoder;

    /**
     * The object that will perform input validation. The validate binding prefix is generally used to provide this
     * object in a declarative fashion.
     */
    @Parameter(defaultPrefix = BindingConstants.VALIDATE)
    @SuppressWarnings("unchecked")
    private FieldValidator<Object> validate;

    @Inject
    private ComponentDefaultProvider defaultProvider;

    @Inject
    private ComponentResources resources;

    @Environmental
    private FormSupport formSupport;

    @Inject
    private Environment environment;

    @Inject
    private Request request;

    @Environmental
    private ValidationTracker tracker;

    @Inject
    private FieldValidationSupport fieldValidationSupport;

    final ValueEncoder defaultEncoder()
    {
        return defaultProvider.defaultValueEncoder("value", resources);
    }

    public void processSubmission(String elementName)
    {
        String rawValue = request.getParameter(elementName);

        tracker.recordInput(this, rawValue);
        try
        {
            if (validate != null)
                fieldValidationSupport.validate(rawValue, resources, validate);
        }
        catch (ValidationException ex)
        {
            tracker.recordError(this, ex.getMessage());
        }

        value = encoder.toValue(rawValue);
    }

    /**
     * Obtains the element name for the group, and stores a {@link RadioContainer} into the {@link Environment} (so that
     * the {@link Radio} components can find it).
     */
    final void setupRender()
    {
        String submittedValue = tracker.getInput(this);

        final String selectedValue = submittedValue != null ? submittedValue : encoder.toClient(value);
        final String controlName = getControlName();

        environment.push(RadioContainer.class, new RadioContainer()
        {
            public String getControlName()
            {
                return controlName;
            }

            public boolean isDisabled()
            {
                return disabled;
            }

            @SuppressWarnings("unchecked")
            public String toClient(Object value)
            {
                // TODO: Ensure that value is of the expected type?

                return encoder.toClient(value);
            }

            public boolean isSelected(Object value)
            {
                return TapestryInternalUtils.isEqual(encoder.toClient(value), selectedValue);
            }
        });
    }

    final boolean beforeRenderBody()
    {
        // need to explicitly do this because all other AbstractFields don't render their body
        return true;
    }

    /**
     * Pops the {@link RadioContainer} off the Environment.
     */
    final void afterRender()
    {
        environment.pop(RadioContainer.class);
    }

    public boolean isRequired()
    {
        return validate.isRequired();
    }
}
