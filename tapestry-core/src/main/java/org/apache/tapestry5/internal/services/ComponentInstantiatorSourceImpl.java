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

package org.apache.tapestry5.internal.services;

import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.internal.InternalComponentResources;
import org.apache.tapestry5.internal.InternalConstants;
import org.apache.tapestry5.internal.model.MutableComponentModelImpl;
import org.apache.tapestry5.internal.plastic.PlasticInternalUtils;
import org.apache.tapestry5.ioc.Invokable;
import org.apache.tapestry5.ioc.LoggerSource;
import org.apache.tapestry5.ioc.OperationTracker;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.annotations.PostInjection;
import org.apache.tapestry5.ioc.annotations.Primary;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.ioc.internal.services.ClassFactoryImpl;
import org.apache.tapestry5.ioc.internal.services.PlasticProxyFactoryImpl;
import org.apache.tapestry5.ioc.internal.util.ClasspathResource;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.ioc.internal.util.URLChangeTracker;
import org.apache.tapestry5.ioc.services.Builtin;
import org.apache.tapestry5.ioc.services.ClassFactory;
import org.apache.tapestry5.ioc.services.ClasspathURLConverter;
import org.apache.tapestry5.ioc.services.PlasticProxyFactory;
import org.apache.tapestry5.model.ComponentModel;
import org.apache.tapestry5.model.MutableComponentModel;
import org.apache.tapestry5.plastic.*;
import org.apache.tapestry5.plastic.PlasticManager.PlasticManagerBuilder;
import org.apache.tapestry5.runtime.Component;
import org.apache.tapestry5.runtime.ComponentEvent;
import org.apache.tapestry5.runtime.ComponentResourcesAware;
import org.apache.tapestry5.runtime.PageLifecycleListener;
import org.apache.tapestry5.services.*;
import org.apache.tapestry5.services.transform.ComponentClassTransformWorker2;
import org.apache.tapestry5.services.transform.ControlledPackageType;
import org.apache.tapestry5.services.transform.TransformationSupport;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

/**
 * A wrapper around a {@link PlasticManager} that allows certain classes to be modified as they are loaded.
 */
public final class ComponentInstantiatorSourceImpl implements ComponentInstantiatorSource, UpdateListener,
        InvalidationListener, PlasticManagerDelegate, PlasticClassListener
{
    private final Set<String> controlledPackageNames = CollectionFactory.newSet();

    private final URLChangeTracker changeTracker;

    private final ClassLoader parent;

    private final ComponentClassTransformWorker2 transformerChain;

    private final LoggerSource loggerSource;

    private final Logger logger;

    private final OperationTracker tracker;

    private final InternalComponentInvalidationEventHub invalidationHub;

    private final boolean productionMode;

    private final ComponentClassResolver resolver;

    // These change whenever the invalidation event hub sends an invalidation notification

    private ClassFactory classFactory;

    private PlasticProxyFactory proxyFactory;

    private PlasticManager manager;

    /**
     * Map from class name to Instantiator.
     */
    private final Map<String, Instantiator> classToInstantiator = CollectionFactory.newMap();

    private final Map<String, ComponentModel> classToModel = CollectionFactory.newMap();

    private final MethodDescription GET_COMPONENT_RESOURCES = PlasticUtils.getMethodDescription(
            ComponentResourcesAware.class, "getComponentResources");

    private final ConstructorCallback REGISTER_AS_PAGE_LIFECYCLE_LISTENER = new ConstructorCallback()
    {
        public void onConstruct(Object instance, InstanceContext context)
        {
            InternalComponentResources resources = context.get(InternalComponentResources.class);

            resources.addPageLifecycleListener((PageLifecycleListener) instance);
        }
    };

    public ComponentInstantiatorSourceImpl(Logger logger,

                                           LoggerSource loggerSource,

                                           @Builtin
                                           PlasticProxyFactory proxyFactory,

                                           @Primary
                                           ComponentClassTransformWorker2 transformerChain,

                                           ClasspathURLConverter classpathURLConverter,

                                           OperationTracker tracker,

                                           Map<String, ControlledPackageType> configuration,

                                           @Symbol(SymbolConstants.PRODUCTION_MODE)
                                           boolean productionMode,

                                           ComponentClassResolver resolver,

                                           InternalComponentInvalidationEventHub invalidationHub)
    {
        this.parent = proxyFactory.getClassLoader();
        this.transformerChain = transformerChain;
        this.logger = logger;
        this.loggerSource = loggerSource;
        this.changeTracker = new URLChangeTracker(classpathURLConverter);
        this.tracker = tracker;
        this.invalidationHub = invalidationHub;
        this.productionMode = productionMode;
        this.resolver = resolver;

        // For now, we just need the keys of the configuration. When there are more types of controlled
        // packages, we'll need to do more.

        controlledPackageNames.addAll(configuration.keySet());

        initializeService();
    }

    @PostInjection
    public void listenForUpdates(UpdateListenerHub hub)
    {
        invalidationHub.addInvalidationListener(this);
        hub.addUpdateListener(this);
    }

    public synchronized void checkForUpdates()
    {
        if (changeTracker.containsChanges())
        {
            invalidationHub.classInControlledPackageHasChanged();
        }
    }

    public void objectWasInvalidated()
    {
        changeTracker.clear();
        classToInstantiator.clear();

        // Release the existing class pool, loader and so forth.
        // Create a new one.

        initializeService();
    }

    /**
     * Invoked at object creation, or when there are updates to class files (i.e., invalidation), to create a new set of
     * Javassist class pools and loaders.
     */
    private void initializeService()
    {
        PlasticManagerBuilder builder = PlasticManager.withClassLoader(parent).delegate(this)
                .packages(controlledPackageNames);

        if (!productionMode)
        {
            builder.enable(TransformationOption.FIELD_WRITEBEHIND);
        }

        manager = builder.create();

        manager.addPlasticClassListener(this);

        classFactory = new ClassFactoryImpl(manager.getClassLoader(), logger);

        proxyFactory = new PlasticProxyFactoryImpl(manager.getClassLoader(), logger);

        classToInstantiator.clear();
        classToModel.clear();
    }

    public synchronized Instantiator getInstantiator(final String className)
    {
        Instantiator result = classToInstantiator.get(className);

        if (result == null)
        {
            result = createInstantiatorForClass(className);

            classToInstantiator.put(className, result);
        }

        return result;
    }

    private Instantiator createInstantiatorForClass(final String className)
    {
        return tracker.invoke(String.format("Creating instantiator for component class %s", className),
                new Invokable<Instantiator>()
                {
                    public Instantiator invoke()
                    {
                        // Force the creation of the class (and the transformation of the class). This will first
                        // trigger transformations of any base classes.

                        final ClassInstantiator<Component> plasticInstantiator = manager
                                .getClassInstantiator(className);

                        final ComponentModel model = classToModel.get(className);

                        return new Instantiator()
                        {
                            public Component newInstance(InternalComponentResources resources)
                            {
                                return plasticInstantiator.with(ComponentResources.class, resources)
                                        .with(InternalComponentResources.class, resources).newInstance();
                            }

                            public ComponentModel getModel()
                            {
                                return model;
                            }

                            @Override
                            public String toString()
                            {
                                return String.format("[Instantiator[%s]", className);
                            }
                        };
                    }
                });
    }

    public boolean exists(String className)
    {
        return parent.getResource(PlasticInternalUtils.toClassPath(className)) != null;
    }

    public ClassFactory getClassFactory()
    {
        return classFactory;
    }

    public PlasticProxyFactory getProxyFactory()
    {
        return proxyFactory;
    }

    public void transform(final PlasticClass plasticClass)
    {
        tracker.run(String.format("Running component class transformations on %s", plasticClass.getClassName()),
                new Runnable()
                {
                    public void run()
                    {
                        String className = plasticClass.getClassName();
                        String parentClassName = plasticClass.getSuperClassName();

                        // The parent model may not exist, if the super class is not in a controlled package.

                        ComponentModel parentModel = classToModel.get(parentClassName);

                        final boolean isRoot = parentModel == null;

                        if (isRoot
                                && !(parentClassName.equals("java.lang.Object") || parentClassName
                                .equals("groovy.lang.GroovyObjectSupport")))
                        {
                            String suggestedPackageName = buildSuggestedPackageName(className);

                            throw new RuntimeException(ServicesMessages.baseClassInWrongPackage(parentClassName,
                                    className, suggestedPackageName));
                        }

                        // Tapestry 5.2 was more sensitive that the parent class have a public no-args constructor.
                        // Plastic
                        // doesn't care, and we don't have the tools to dig that information out.

                        Logger logger = loggerSource.getLogger(className);

                        Resource baseResource = new ClasspathResource(parent, PlasticInternalUtils
                                .toClassPath(className));

                        changeTracker.add(baseResource.toURL());

                        if (isRoot)
                        {
                            implementComponentInterface(plasticClass);
                        }

                        boolean isPage = resolver.isPage(className);

                        boolean superClassImplementsPageLifecycle = plasticClass.isInterfaceImplemented(PageLifecycleListener.class);

                        final MutableComponentModel model = new MutableComponentModelImpl(className, logger, baseResource,
                                parentModel, isPage);

                        transformerChain.transform(plasticClass, new TransformationSupportImpl(plasticClass, isRoot, model), model);

                        if (!superClassImplementsPageLifecycle && plasticClass.isInterfaceImplemented(PageLifecycleListener.class))
                        {
                            plasticClass.onConstruct(REGISTER_AS_PAGE_LIFECYCLE_LISTENER);
                        }

                        classToModel.put(className, model);
                    }
                });
    }

    private void implementComponentInterface(PlasticClass plasticClass)
    {
        plasticClass.introduceInterface(Component.class);

        final PlasticField resourcesField = plasticClass.introduceField(InternalComponentResources.class,
                "internalComponentResources").injectFromInstanceContext();

        plasticClass.introduceMethod(GET_COMPONENT_RESOURCES, new InstructionBuilderCallback()
        {
            public void doBuild(InstructionBuilder builder)
            {
                builder.loadThis().getField(resourcesField).returnResult();
            }
        });
    }

    public <T> ClassInstantiator<T> configureInstantiator(String className, ClassInstantiator<T> instantiator)
    {
        return instantiator;
    }

    private String buildSuggestedPackageName(String className)
    {
        for (String subpackage : InternalConstants.SUBPACKAGES)
        {
            String term = "." + subpackage + ".";

            int pos = className.indexOf(term);

            // Keep the leading '.' in the subpackage name and tack on "base".

            if (pos > 0)
                return className.substring(0, pos + 1) + InternalConstants.BASE_SUBPACKAGE;
        }

        // Is this even reachable? className should always be in a controlled package and so
        // some subpackage above should have matched.

        return null;
    }

    public void classWillLoad(PlasticClassEvent event)
    {
        Logger logger = loggerSource.getLogger("tapestry.transformer." + event.getPrimaryClassName());

        if (logger.isDebugEnabled())
            logger.debug(event.getDissasembledBytecode());
    }

    private class TransformationSupportImpl implements TransformationSupport
    {
        private final PlasticClass plasticClass;

        private final boolean root;

        private final MutableComponentModel model;

        public TransformationSupportImpl(PlasticClass plasticClass, boolean root, MutableComponentModel model)
        {
            this.plasticClass = plasticClass;
            this.root = root;
            this.model = model;
        }

        public Class toClass(String typeName)
        {
            try
            {
                return PlasticInternalUtils.toClass(manager.getClassLoader(), typeName);
            } catch (ClassNotFoundException ex)
            {
                throw new RuntimeException(String.format(
                        "Unable to convert type '%s' to a Class: %s", typeName,
                        InternalUtils.toMessage(ex)), ex);
            }
        }

        public boolean isRootTransformation()
        {
            return root;
        }

        public void addEventHandler(final String eventType, final int minContextValues, final String operationDescription, final ComponentEventHandler handler)
        {
            assert InternalUtils.isNonBlank(eventType);
            assert minContextValues >= 0;
            assert handler != null;

            model.addEventHandler(eventType);

            MethodAdvice advice = new MethodAdvice()
            {
                public void advise(MethodInvocation invocation)
                {
                    final ComponentEvent event = (ComponentEvent) invocation.getParameter(0);

                    boolean matches = !event.isAborted() && event.matches(eventType, "", minContextValues);

                    if (matches)
                    {
                        final Component instance = (Component) invocation.getInstance();

                        tracker.invoke(operationDescription, new Invokable<Object>()
                        {
                            public Object invoke()
                            {
                                handler.handleEvent(instance, event);

                                return null;
                            }
                        });
                    }

                    // Order of operations is key here. This logic takes precedence; base class event dispatch and event handler methods
                    // in the class come AFTER.

                    invocation.proceed();

                    if (matches)
                    {
                        invocation.setReturnValue(true);
                    }
                }
            };

            plasticClass.introduceMethod(TransformConstants.DISPATCH_COMPONENT_EVENT_DESCRIPTION).addAdvice(advice);
        }
    }
}
