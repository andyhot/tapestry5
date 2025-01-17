// Copyright 2006, 2007, 2008, 2009, 2010 The Apache Software Foundation
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

package org.apache.tapestry5.internal.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tapestry5.ioc.Location;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.ioc.util.IdAllocator;
import org.apache.tapestry5.model.ComponentModel;
import org.apache.tapestry5.model.EmbeddedComponentModel;
import org.apache.tapestry5.model.MutableComponentModel;
import org.apache.tapestry5.model.MutableEmbeddedComponentModel;
import org.apache.tapestry5.model.ParameterModel;
import org.slf4j.Logger;

/**
 * Internal implementation of {@link org.apache.tapestry5.model.MutableComponentModel}.
 */
public final class MutableComponentModelImpl implements MutableComponentModel
{
    private final ComponentModel parentModel;

    private final Resource baseResource;

    private final String componentClassName;

    private final IdAllocator persistentFieldNameAllocator = new IdAllocator();

    private final Logger logger;

    private final boolean pageClass;

    private Map<String, ParameterModel> parameters;

    private Map<String, EmbeddedComponentModel> embeddedComponents;

    /**
     * Maps from field name to strategy.
     */
    private Map<String, String> persistentFields;

    private List<String> mixinClassNames;

    private Map<String, String[]> mixinOrders;

    private boolean informalParametersSupported;

    private boolean mixinAfter;

    private Map<String, String> metaData;

    private Set<Class> handledRenderPhases;

    private Map<String, Boolean> handledEvents;

    public MutableComponentModelImpl(String componentClassName, Logger logger, Resource baseResource,
            ComponentModel parentModel, boolean pageClass)
    {
        this.componentClassName = componentClassName;
        this.logger = logger;
        this.baseResource = baseResource;
        this.parentModel = parentModel;
        this.pageClass = pageClass;

        // Pre-allocate names from the parent, to avoid name collisions.

        if (this.parentModel != null)
        {
            for (String name : this.parentModel.getPersistentFieldNames())
            {
                persistentFieldNameAllocator.allocateId(name);
            }
        }
    }

    @Override
    public String toString()
    {
        return String.format("ComponentModel[%s]", componentClassName);
    }

    public Logger getLogger()
    {
        return logger;
    }

    public Resource getBaseResource()
    {
        return baseResource;
    }

    public String getComponentClassName()
    {
        return componentClassName;
    }

    public void addParameter(String name, boolean required, boolean allowNull, String defaultBindingPrefix,
            boolean cached)
    {
        assert InternalUtils.isNonBlank(name);
        assert InternalUtils.isNonBlank(defaultBindingPrefix);
        if (parameters == null)
            parameters = CollectionFactory.newCaseInsensitiveMap();

        if (parameters.containsKey(name))
            throw new IllegalArgumentException(ModelMessages.duplicateParameter(name, componentClassName));

        parameters.put(name, new ParameterModelImpl(name, required, allowNull, defaultBindingPrefix, cached));
    }

    public void addParameter(String name, boolean required, boolean allowNull, String defaultBindingPrefix)
    {
        // assume /false/ for the default because:
        // if the parameter is actually cached, the only effect will be to reduce that optimization
        // in certain
        // scenarios (mixin BindParameter). But if the value is NOT cached but we say it is,
        // we'll get incorrect behavior.
        addParameter(name, required, allowNull, defaultBindingPrefix, false);
    }

    public ParameterModel getParameterModel(String parameterName)
    {
        ParameterModel result = InternalUtils.get(parameters, parameterName.toLowerCase());

        if (result == null && parentModel != null)
            result = parentModel.getParameterModel(parameterName);

        return result;
    }

    public boolean isFormalParameter(String parameterName)
    {
        return getParameterModel(parameterName) != null;
    }

    public List<String> getParameterNames()
    {
        List<String> names = CollectionFactory.newList();

        if (parameters != null)
            names.addAll(parameters.keySet());

        if (parentModel != null)
            names.addAll(parentModel.getParameterNames());

        Collections.sort(names);

        return names;
    }

    public List<String> getDeclaredParameterNames()
    {
        return InternalUtils.sortedKeys(parameters);
    }

    public MutableEmbeddedComponentModel addEmbeddedComponent(String id, String type, String componentClassName,
            boolean inheritInformalParameters, Location location)
    {
        // TODO: Parent compent model? Or would we simply override the parent?

        if (embeddedComponents == null)
            embeddedComponents = CollectionFactory.newCaseInsensitiveMap();
        else if (embeddedComponents.containsKey(id))
            throw new IllegalArgumentException(ModelMessages.duplicateComponentId(id, this.componentClassName));

        MutableEmbeddedComponentModel embedded = new MutableEmbeddedComponentModelImpl(id, type, componentClassName,
                this.componentClassName, inheritInformalParameters, location);

        embeddedComponents.put(id, embedded);

        return embedded; // So that parameters can be filled in
    }

    public List<String> getEmbeddedComponentIds()
    {
        List<String> result = CollectionFactory.newList();

        if (embeddedComponents != null)
            result.addAll(embeddedComponents.keySet());

        if (parentModel != null)
            result.addAll(parentModel.getEmbeddedComponentIds());

        Collections.sort(result);

        return result;
    }

    public EmbeddedComponentModel getEmbeddedComponentModel(String componentId)
    {
        EmbeddedComponentModel result = InternalUtils.get(embeddedComponents, componentId);

        if (result == null && parentModel != null)
            result = parentModel.getEmbeddedComponentModel(componentId);

        return result;
    }

    public String getFieldPersistenceStrategy(String fieldName)
    {
        String result = InternalUtils.get(persistentFields, fieldName);

        if (result == null && parentModel != null)
            result = parentModel.getFieldPersistenceStrategy(fieldName);

        if (result == null)
            throw new IllegalArgumentException(ModelMessages.missingPersistentField(fieldName));

        return result;
    }

    public List<String> getPersistentFieldNames()
    {
        return persistentFieldNameAllocator.getAllocatedIds();
    }

    public String setFieldPersistenceStrategy(String fieldName, String strategy)
    {
        String logicalFieldName = persistentFieldNameAllocator.allocateId(fieldName);

        if (persistentFields == null)
            persistentFields = CollectionFactory.newMap();

        persistentFields.put(logicalFieldName, strategy);

        return logicalFieldName;
    }

    public boolean isRootClass()
    {
        return parentModel == null;
    }

    public void addMixinClassName(String mixinClassName, String... order)
    {
        if (mixinClassNames == null)
            mixinClassNames = CollectionFactory.newList();

        mixinClassNames.add(mixinClassName);
        if (order != null && order.length > 0)
        {
            if (mixinOrders == null)
                mixinOrders = CollectionFactory.newCaseInsensitiveMap();
            mixinOrders.put(mixinClassName, order);
        }
    }

    public List<String> getMixinClassNames()
    {
        List<String> result = CollectionFactory.newList();

        if (mixinClassNames != null)
            result.addAll(mixinClassNames);

        if (parentModel != null)
            result.addAll(parentModel.getMixinClassNames());

        Collections.sort(result);

        return result;
    }

    public void enableSupportsInformalParameters()
    {
        informalParametersSupported = true;
    }

    public boolean getSupportsInformalParameters()
    {
        return informalParametersSupported;
    }

    public ComponentModel getParentModel()
    {
        return parentModel;
    }

    public boolean isMixinAfter()
    {
        return mixinAfter;
    }

    public void setMixinAfter(boolean mixinAfter)
    {
        this.mixinAfter = mixinAfter;
    }

    public void setMeta(String key, String value)
    {
        assert InternalUtils.isNonBlank(key);
        assert InternalUtils.isNonBlank(value);
        if (metaData == null)
            metaData = CollectionFactory.newCaseInsensitiveMap();

        // TODO: Error if duplicate?

        metaData.put(key, value);
    }

    public void addRenderPhase(Class renderPhase)
    {
        assert renderPhase != null;
        if (handledRenderPhases == null)
            handledRenderPhases = CollectionFactory.newSet();

        handledRenderPhases.add(renderPhase);
    }

    public void addEventHandler(String eventType)
    {
        if (handledEvents == null)
            handledEvents = CollectionFactory.newCaseInsensitiveMap();

        handledEvents.put(eventType, true);
    }

    public String getMeta(String key)
    {
        String result = InternalUtils.get(metaData, key);

        if (result == null && parentModel != null)
            result = parentModel.getMeta(key);

        return result;
    }

    public Set<Class> getHandledRenderPhases()
    {
        Set<Class> result = CollectionFactory.newSet();

        if (parentModel != null)
            result.addAll(parentModel.getHandledRenderPhases());

        if (handledRenderPhases != null)
            result.addAll(handledRenderPhases);

        return result;
    }

    public boolean handlesEvent(String eventType)
    {
        if (InternalUtils.get(handledEvents, eventType) != null)
            return true;

        return parentModel == null ? false : parentModel.handlesEvent(eventType);
    }

    public String[] getOrderForMixin(String mixinClassName)
    {
        final String[] orders = InternalUtils.get(mixinOrders, mixinClassName);

        if ( orders == null && parentModel != null )
            return parentModel.getOrderForMixin(mixinClassName);

        return orders;
    }

    public boolean isPage()
    {
        return pageClass;
    }
}
