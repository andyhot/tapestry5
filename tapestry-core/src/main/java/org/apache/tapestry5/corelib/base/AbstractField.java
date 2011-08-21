// Copyright 2006, 2007, 2008, 2010, 2011 The Apache Software Foundation
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

package org.apache.tapestry5.corelib.base;

import org.apache.tapestry5.*;
import org.apache.tapestry5.annotations.*;
import org.apache.tapestry5.corelib.internal.FormSupportImpl;
import org.apache.tapestry5.corelib.mixins.DiscardBody;
import org.apache.tapestry5.corelib.mixins.RenderDisabled;
import org.apache.tapestry5.corelib.mixins.RenderInformals;
import org.apache.tapestry5.internal.BeanValidationContext;
import org.apache.tapestry5.internal.InternalComponentResources;
import org.apache.tapestry5.internal.util.CaptureResultCallback;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.ComponentDefaultProvider;
import org.apache.tapestry5.services.Environment;
import org.apache.tapestry5.services.FormSupport;
import org.apache.tapestry5.services.javascript.JavaScriptSupport;

import java.io.Serializable;

/**
 * Provides initialization of the clientId and elementName properties. In addition, adds the {@link RenderInformals},
 * {@link RenderDisabled} and {@link DiscardBody} mixins.
 */
@SupportsInformalParameters
public abstract class AbstractField implements Field
{
    public static final String CHANGE_EVENT = "change";

    /**
     * The user presentable label for the field. If not provided, a reasonable label is generated from the component's
     * id, first by looking for a message key named "id-label" (substituting the component's actual id), then by
     * converting the actual id to a presentable string (for example, "userId" to "User Id").
     */
    @Parameter(defaultPrefix = BindingConstants.LITERAL)
    private String label;

    /**
     * If true, then the field will render out with a disabled attribute
     * (to turn off client-side behavior). When the form is submitted, the
     * bound value is evaluated again and, if true, the field's value is
     * ignored (not even validated) and the component's events are not fired.
     */
    @Parameter("false")
    private boolean disabled;

    /**
     * Binding the zone parameter will cause any change of the component's value to be handled as an
     * Ajax request that updates the indicated zone. The component will trigger the
     * event {@link EventConstants#VALUE_CHANGED} to inform its container its value has changed.
     *
     * @since 5.3.0
     */
    @Parameter(defaultPrefix = BindingConstants.LITERAL)
    private String zone;

    /**
     * The client-side event that triggeres the zone update.
     *
     * @see #zone
     * @since 5.3.0
     */
    @Parameter(defaultPrefix = BindingConstants.LITERAL)
    private String triggerEvent;

    @SuppressWarnings("unused")
    @Mixin
    private DiscardBody discardBody;

    @Environmental
    private ValidationDecorator decorator;

    @Inject
    private Environment environment;

    @Inject
    private JavaScriptSupport javascriptSupport;

    static class Setup implements ComponentAction<AbstractField>, Serializable
    {
        private static final long serialVersionUID = 2690270808212097020L;

        private final String controlName;

        public Setup(String controlName)
        {
            this.controlName = controlName;
        }

        public void execute(AbstractField component)
        {
            component.setupControlName(controlName);
        }

        @Override
        public String toString()
        {
            return String.format("AbstractField.Setup[%s]", controlName);
        }
    }

    static class ProcessSubmission implements ComponentAction<AbstractField>, Serializable
    {
        private static final long serialVersionUID = -4346426414137434418L;

        public void execute(AbstractField component)
        {
            component.processSubmission();
        }

        @Override
        public String toString()
        {
            return "AbstractField.ProcessSubmission";
        }
    }

    /**
     * Used a shared instance for all types of fields, for efficiency.
     */
    private static final ProcessSubmission PROCESS_SUBMISSION_ACTION = new ProcessSubmission();

    /**
     * The id used to generate a page-unique client-side identifier for the component. If a component renders multiple
     * times, a suffix will be appended to the to id to ensure uniqueness. The uniqued value may be accessed via the
     * {@link #getClientId() clientId property}.
     */
    @Parameter(value = "prop:componentResources.id", defaultPrefix = BindingConstants.LITERAL)
    private String clientId;

    private String assignedClientId;

    private String controlName;

    @Environmental(false)
    private FormSupport formSupport;

    @Environmental
    private JavaScriptSupport jsSupport;

    @Inject
    private ComponentResources resources;

    @Inject
    private ComponentDefaultProvider defaultProvider;

    final String defaultLabel()
    {
        return defaultProvider.defaultLabel(resources);
    }

    protected String defaultTriggerEvent()
    {
        return CHANGE_EVENT;
    }

    public final String getLabel()
    {
        return label;
    }

    @SetupRender
    final void setup()
    {
        // By default, use the component id as the (base) client id. If the clientid
        // parameter is bound, then that is the value to use.

        String id = clientId;

        // Often, these controlName and clientId will end up as the same value. There are many
        // exceptions, including a form that renders inside a loop, or a form inside a component
        // that is used multiple times.

        if (formSupport == null)
            throw new RuntimeException(String.format("Component %s must be enclosed by a Form component.",
                    resources.getCompleteId()));

        assignedClientId = jsSupport.allocateClientId(id);
        String controlName = formSupport.allocateControlName(id);

        formSupport.storeAndExecute(this, new Setup(controlName));
        formSupport.store(this, PROCESS_SUBMISSION_ACTION);
    }

    void afterRender() {
        if (this.zone != null)
        {
            Link link = resources.createEventLink(CHANGE_EVENT);

            JSONObject spec = new JSONObject(
                    "event", triggerEvent,
                    "element", getClientId(),
                    "zoneId", zone,
                    "url", link.toURI());
            javascriptSupport.addInitializerCall("linkEventToZone", spec);
        }
    }

    public final String getClientId()
    {
        return assignedClientId;
    }

    public final String getControlName()
    {
        return controlName;
    }

    public boolean isDisabled()
    {
        return disabled;
    }

    /**
     * Invoked from within a ComponentCommand callback, to restore the component's elementName.
     */
    private void setupControlName(String controlName)
    {
        this.controlName = controlName;
    }

    private void processSubmission()
    {
        if (!disabled)
            processSubmission(controlName);
    }

    Object onChange()
    {
        // don't process the change event if no zone was set
        if (this.zone == null)
        {
            return null;
        }
        // This onChange event was originally found in the Select component.
        // Its contract was to call the event handler passing the the new value as parameter
        // (and at the same time one could get the previous value by direct accessing the
        // component property).
        //
        // If we still need to honour that contract, then we have to use
        // InternalComponentResources.
        // Otherwise, we can just call the event handler with no params and
        // remove internalResources and valueBinding.
        // A third alternative would be to call the handler with the new value, and don't
        // care about returning the previous value when the component property is directly
        // accessed (in that case we can remove oldValue).
        InternalComponentResources internalResources = (InternalComponentResources)resources;
        Binding valueBinding = internalResources.getBinding("value");

        Object oldValue = valueBinding==null ? null : valueBinding.get();

        environment.push(FormSupport.class, new FormSupportImpl(resources, null));
        environment.push(ValidationTracker.class, new ValidationTrackerImpl());

        this.processSubmission("t:value");

        environment.pop(ValidationTracker.class);
        environment.pop(FormSupport.class);

        Object newValue = valueBinding==null ? null : valueBinding.get();

        // set the old value so that the "contract" is kept
        if (valueBinding!=null)
            valueBinding.set(oldValue);

        CaptureResultCallback<Object> callback = new CaptureResultCallback<Object>();

        this.resources.triggerEvent(EventConstants.VALUE_CHANGED, new Object[]
        { newValue }, callback);

        // now set back the new value
        if (valueBinding!=null)
            valueBinding.set(newValue);

        return callback.getResult();
    }

    /**
     * Method implemented by subclasses to actually do the work of processing the submission of the form. The element's
     * elementName property will already have been set. This method is only invoked if the field is <strong>not
     * {@link #isDisabled() disabled}</strong>.
     * 
     * @param elementName
     *            the name of the element (used to find the correct parameter in the request)
     */
    protected abstract void processSubmission(String elementName);

    /**
     * Allows the validation decorator to write markup before the field itself writes markup.
     */
    @BeginRender
    final void beforeDecorator()
    {
        decorator.beforeField(this);
    }

    /**
     * Allows the validation decorator to write markup after the field has written all of its markup.
     */
    @AfterRender
    final void afterDecorator()
    {
        decorator.afterField(this);
    }

    /**
     * Invoked from subclasses after they have written their tag and (where appropriate) their informal parameters
     * <em>and</em> have allowed their {@link Validator} to write markup as well.
     */
    protected final void decorateInsideField()
    {
        decorator.insideField(this);
    }

    protected final void setDecorator(ValidationDecorator decorator)
    {
        this.decorator = decorator;
    }

    protected final void setFormSupport(FormSupport formSupport)
    {
        this.formSupport = formSupport;
    }

    /**
     * Returns false; most components do not support declarative validation.
     */
    public boolean isRequired()
    {
        return false;
    }

    protected void putPropertyNameIntoBeanValidationContext(String parameterName)
    {
        String propertyName = ((InternalComponentResources) resources).getPropertyName(parameterName);

        BeanValidationContext beanValidationContext = environment.peek(BeanValidationContext.class);

        if (beanValidationContext == null)
            return;

        // If field is inside BeanEditForm, then property is already set
        if (beanValidationContext.getCurrentProperty() == null)
        {
            beanValidationContext.setCurrentProperty(propertyName);
        }
    }

    protected void removePropertyNameFromBeanValidationContext()
    {
        BeanValidationContext beanValidationContext = environment.peek(BeanValidationContext.class);

        if (beanValidationContext == null)
            return;

        beanValidationContext.setCurrentProperty(null);
    }
}
