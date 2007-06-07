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

package org.apache.tapestry.corelib.components;

import java.util.Locale;

import org.apache.tapestry.Binding;
import org.apache.tapestry.ComponentResources;
import org.apache.tapestry.FieldValidator;
import org.apache.tapestry.MarkupWriter;
import org.apache.tapestry.OptionModel;
import org.apache.tapestry.SelectModel;
import org.apache.tapestry.SelectModelVisitor;
import org.apache.tapestry.ValidationException;
import org.apache.tapestry.ValidationTracker;
import org.apache.tapestry.ValueEncoder;
import org.apache.tapestry.annotations.BeforeRenderTemplate;
import org.apache.tapestry.annotations.Environmental;
import org.apache.tapestry.annotations.Inject;
import org.apache.tapestry.annotations.Parameter;
import org.apache.tapestry.corelib.base.AbstractField;
import org.apache.tapestry.internal.util.SelectModelRenderer;
import org.apache.tapestry.services.FieldValidatorDefaultSource;
import org.apache.tapestry.services.FormSupport;
import org.apache.tapestry.services.Request;
import org.apache.tapestry.util.EnumSelectModel;
import org.apache.tapestry.util.EnumValueEncoder;

/**
 * Select an item from a list of values, using an [X]HTML &lt;select&gt; element on the client side.
 * An validation decorations will go around the entire &lt;select&gt; element.
 */
public final class Select extends AbstractField
{
    private class Renderer extends SelectModelRenderer
    {

        public Renderer(MarkupWriter writer)
        {
            super(writer, _encoder);
        }

        @Override
        protected boolean isOptionSelected(OptionModel optionModel)
        {
            Object value = optionModel.getValue();

            return value == _value || (value != null && value.equals(_value));
        }
    }

    /**
     * The default encoder encodes strings, passing them to the client and back unchanged.
     */
    @Parameter
    private ValueEncoder _encoder = new ValueEncoder<String>()
    {
        public String toClient(String value)
        {
            return value;
        }

        public String toValue(String primaryKey)
        {
            // We don't do a conversion here, so it stays a String. When that String is assigned to
            // _value, it will be coerced to the appropriate type (if possible) or an exception
            // will be thrown.

            return primaryKey;
        }
    };

    @Inject
    private FieldValidatorDefaultSource _fieldValidatorDefaultSource;

    @Inject
    private Locale _locale;

    // Maybe this should default to property "<componentId>Model"?
    /**
     * The model used to identify the option groups and options to be presented to the user. This
     * can be generated automatically for Enum types.
     */
    @Parameter(required = true)
    private SelectModel _model;

    @Inject
    private Request _request;

    @Inject
    private ComponentResources _resources;

    @Environmental
    private ValidationTracker _tracker;

    /** Performs input validation on the value supplied by the user in the form submission. */
    @Parameter(defaultPrefix = "validate")
    @SuppressWarnings("unchecked")
    private FieldValidator<Object> _validate = NOOP_VALIDATOR;

    /** The value to read or update. */
    @Parameter(required = true, principal = true)
    private Object _value;

    @Override
    protected void processSubmission(FormSupport formSupport, String elementName)
    {
        String primaryKey = _request.getParameter(elementName);

        Object selectedValue = _encoder.toValue(primaryKey);

        try
        {
            _validate.validate(selectedValue);

            _value = selectedValue;
        }
        catch (ValidationException ex)
        {
            _tracker.recordError(this, ex.getMessage());
            return;
        }
    }

    void afterRender(MarkupWriter writer)
    {
        writer.end();
    }

    void beginRender(MarkupWriter writer)
    {
        writer.element("select", "name", getElementName(), "id", getClientId());

        // Disabled, informals via mixins
    }

    @SuppressWarnings("unchecked")
    ValueEncoder defaultEncoder()
    {
        Class valueType = _resources.getBoundType("value");

        if (valueType == null) return null;

        if (Enum.class.isAssignableFrom(valueType)) return new EnumValueEncoder(valueType);

        return null;
    }

    @SuppressWarnings("unchecked")
    SelectModel defaultModel()
    {
        Class valueType = _resources.getBoundType("value");

        if (valueType == null) return null;

        if (Enum.class.isAssignableFrom(valueType))
            return new EnumSelectModel(valueType, _resources.getContainerMessages());

        return null;
    }

    /**
     * Computes a default value for the "validate" parameter using
     * {@link FieldValidatorDefaultSource}.
     */
    FieldValidator defaultValidate()
    {
        Class type = _resources.getBoundType("value");

        if (type == null) return null;

        return _fieldValidatorDefaultSource.createDefaultValidator(
                this,
                _resources.getId(),
                _resources.getContainerMessages(),
                _locale,
                type,
                _resources.getAnnotationProvider("value"));
    }

    Binding defaultValue()
    {
        return createDefaultParameterBinding("value");
    }

    @BeforeRenderTemplate
    void options(MarkupWriter writer)
    {
        SelectModelVisitor renderer = new Renderer(writer);

        _model.visit(renderer);
    }

    // For testing.

    void setModel(SelectModel model)
    {
        _model = model;
    }

    void setValue(Object value)
    {
        _value = value;
    }
}
