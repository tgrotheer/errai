/*
 * Copyright 2010 JBoss, a divison Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.ioc.rebind.ioc;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Qualifier;

import org.jboss.errai.bus.rebind.ScannerSingleton;
import org.jboss.errai.bus.server.service.metadata.MetaDataScanner;
import org.jboss.errai.ioc.rebind.IOCGenerator;
import org.mvel2.util.ReflectionUtil;
import org.mvel2.util.StringAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JConstructor;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JParameter;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.NotFoundException;

public class InjectUtil {
    private static final Logger log = LoggerFactory.getLogger(InjectUtil.class);

    private static final Class[] injectionAnnotations
            = {Inject.class, com.google.inject.Inject.class};

    private static final AtomicInteger counter = new AtomicInteger(0);

    public static ConstructionStrategy getConstructionStrategy(final Injector injector, final InjectionContext ctx) {
        final JClassType type = injector.getInjectedType();

        final List<JConstructor> constructorInjectionPoints = scanForConstructorInjectionPoints(type);
        final List<InjectionTask> injectionTasks = scanForTasks(injector, ctx, type);
        final List<JMethod> postConstructTasks = scanForPostConstruct(type);


        if (!constructorInjectionPoints.isEmpty()) {
            // constructor injection

            if (constructorInjectionPoints.size() > 1) {
                throw new InjectionFailure("more than one constructor in "
                        + type.getQualifiedSourceName() + " is marked as the injection point!");
            }

            final JConstructor constructor = constructorInjectionPoints.get(0);

            for (Class<? extends Annotation> a : ctx.getDecoratorAnnotationsBy(ElementType.TYPE)) {
                if (type.isAnnotationPresent(a)) {
                    DecoratorTask task = new DecoratorTask(injector, type, ctx.getDecorator(a));
                    injectionTasks.add(task);
                }
            }

            return new ConstructionStrategy() {
                public String generateConstructor() {
                    String[] vars = resolveInjectionDependencies(constructor.getParameters(), ctx, constructor);

                    StringAppender appender = new StringAppender("final ").append(type.getQualifiedSourceName())
                            .append(' ').append(injector.getVarName()).append(" = new ")
                            .append(type.getQualifiedSourceName())
                            .append('(').append(commaDelimitedList(vars)).append(");\n");

                    handleInjectionTasks(appender, ctx, injectionTasks);

                    doPostConstruct(appender, injector, postConstructTasks);

                    return IOCGenerator.debugOutput(appender.toString());
                }
            };

        } else {
            // field injection
            if (!hasDefaultConstructor(type))
                throw new InjectionFailure("there is no default constructor for type: " + type.getQualifiedSourceName());

            return new ConstructionStrategy() {
                public String generateConstructor() {
                    StringAppender appender = new StringAppender("final ").append(type.getQualifiedSourceName())
                            .append(' ').append(injector.getVarName()).append(" = new ")
                            .append(type.getQualifiedSourceName()).append("();\n");

                    handleInjectionTasks(appender, ctx, injectionTasks);

                    doPostConstruct(appender, injector, postConstructTasks);

                    return IOCGenerator.debugOutput(appender.toString());
                }
            };
        }
    }

    private static void handleInjectionTasks(StringAppender appender, InjectionContext ctx,
                                             List<InjectionTask> tasks) {
        for (InjectionTask task : tasks) {
            appender.append(task.doTask(ctx));
        }
    }

    private static void doPostConstruct(StringAppender appender, Injector injector,
                                        List<JMethod> postConstructTasks) {
        for (JMethod meth : postConstructTasks) {
            if (!meth.isPublic() || meth.getParameters().length != 0) {
                throw new InjectionFailure("PostConstruct method must be public and contain no parameters: "
                        + injector.getInjectedType().getQualifiedSourceName() + "." + meth.getName());
            }

            appender.append(injector.getVarName()).append('.').append(meth.getName()).append("();\n");
        }
    }

    private static List<InjectionTask> scanForTasks(Injector injector, InjectionContext ctx, JClassType type) {
        final List<InjectionTask> accumulator = new LinkedList<InjectionTask>();
        final Set<Class<? extends Annotation>> decorators = ctx.getDecoratorAnnotations();

        for (JField field : type.getFields()) {
            if (isInjectionPoint(field)) {
                if (!field.isPublic()) {
                    try {
                        JMethod meth = type.getMethod(ReflectionUtil.getSetter(field.getName()), new JType[]{field.getType()});
                        InjectionTask task = new InjectionTask(injector, meth);
                        task.setField(field);
                        accumulator.add(task);
                    } catch (NotFoundException e) {
                        InjectionTask task = new InjectionTask(injector, field);
                        accumulator.add(task);

                    }
                } else {
                    accumulator.add(new InjectionTask(injector, field));
                }
            }

            ElementType[] elTypes;
            for (Class<? extends Annotation> a : decorators) {
                elTypes = a.isAnnotationPresent(Target.class) ? a.getAnnotation(Target.class).value()
                        : new ElementType[]{ElementType.FIELD};

                for (ElementType elType : elTypes) {
                    switch (elType) {
                        case FIELD:
                            if (field.isAnnotationPresent(a)) {
                                accumulator.add(new DecoratorTask(injector, field, ctx.getDecorator(a)));
                            }
                            break;
                    }
                }
            }
        }

        for (JMethod meth : type.getMethods()) {
            if (isInjectionPoint(meth)) {
                accumulator.add(new InjectionTask(injector, meth));
            }

            ElementType[] elTypes;
            for (Class<? extends Annotation> a : decorators) {
                elTypes = a.isAnnotationPresent(Target.class) ? a.getAnnotation(Target.class).value()
                        : new ElementType[]{ElementType.FIELD};

                for (ElementType elType : elTypes) {
                    switch (elType) {
                        case METHOD:
                            if (meth.isAnnotationPresent(a)) {
                                accumulator.add(new DecoratorTask(injector, meth, ctx.getDecorator(a)));
                            }
                            break;
                        case PARAMETER:
                            for (JParameter parameter : meth.getParameters()) {
                                if (parameter.isAnnotationPresent(a)) {
                                    DecoratorTask task = new DecoratorTask(injector, parameter, ctx.getDecorator(a));
                                    task.setMethod(meth);
                                    accumulator.add(task);
                                }
                            }
                    }
                }
            }
        }

        return accumulator;
    }

    private static List<JConstructor> scanForConstructorInjectionPoints(JClassType type) {
        final List<JConstructor> accumulator = new LinkedList<JConstructor>();

        for (JConstructor cns : type.getConstructors()) {
            if (isInjectionPoint(cns)) {
                accumulator.add(cns);
            }
        }

        return accumulator;
    }

    private static List<JMethod> scanForPostConstruct(JClassType type) {
        final List<JMethod> accumulator = new LinkedList<JMethod>();

        for (JMethod meth : type.getMethods()) {
            if (meth.isAnnotationPresent(PostConstruct.class)) {
                accumulator.add(meth);
            }
        }

        return accumulator;
    }

    @SuppressWarnings({"unchecked"})
    private static boolean isInjectionPoint(JField field) {
        for (Class<? extends Annotation> ann : injectionAnnotations) {
            if (field.isAnnotationPresent(ann)) return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked"})
    private static boolean isInjectionPoint(JMethod meth) {
        for (Class<? extends Annotation> ann : injectionAnnotations) {
            if (meth.isAnnotationPresent(ann)) return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked"})
    private static boolean isInjectionPoint(JConstructor constructor) {
        for (Class<? extends Annotation> ann : injectionAnnotations) {
            if (constructor.isAnnotationPresent(ann)) return true;
        }
        return false;
    }

    private static boolean hasDefaultConstructor(JClassType type) {
        try {
            type.getConstructor(new JType[0]);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private static JClassType[] parametersToClassTypeArray(JParameter[] parms) {
        JClassType[] newArray = new JClassType[parms.length];
        for (int i = 0; i < parms.length; i++) {
            newArray[i] = parms[i].getType().isClassOrInterface();
        }
        return newArray;
    }

    public static String[] resolveInjectionDependencies(JParameter[] parms, InjectionContext ctx, JConstructor constructor) {
        JClassType[] parmTypes = parametersToClassTypeArray(parms);
        String[] varNames = new String[parmTypes.length];

        for (int i = 0; i < parmTypes.length; i++) {
            Injector injector = ctx.getInjector(parmTypes[i]);
            InjectionPoint injectionPoint
                    = new InjectionPoint(null, TaskType.Parameter, constructor, null, null, null, parms[i], injector, ctx);

            varNames[i] = injector.getType(ctx, injectionPoint);
        }

        return varNames;
    }

    public static String[] resolveInjectionDependencies(JParameter[] parms, InjectionContext ctx, InjectionPoint injectionPoint) {
        JClassType[] parmTypes = parametersToClassTypeArray(parms);
        String[] varNames = new String[parmTypes.length];

        for (int i = 0; i < parmTypes.length; i++) {
            varNames[i] = ctx.getInjector(parmTypes[i]).getType(ctx, injectionPoint);
        }

        return varNames;
    }

    public static String commaDelimitedList(String[] parts) {
        StringAppender appender = new StringAppender();
        for (int i = 0; i < parts.length; i++) {
            appender.append(parts[i]);
            if ((i + 1) < parts.length) appender.append(", ");
        }
        return appender.toString();
    }

    public static String getNewVarName() {
        return "inj" + counter.addAndGet(1);
    }

    public static String getPrivateFieldInjectorName(JField field) {
        return field.getEnclosingType().getQualifiedSourceName().replaceAll("\\.", "_") + "_" + field.getName();
    }

    private static Set<Class<?>> qualifiers;

    public static Set<Class<?>> getQualifiers() {
        if (qualifiers == null) {
            qualifiers = new HashSet<Class<?>>();

            MetaDataScanner scanner = ScannerSingleton.getOrCreateInstance();
            qualifiers.addAll(scanner.getTypesAnnotatedWith(Qualifier.class));
        }

        return qualifiers;
    }

    public static List<Annotation> extractQualifiers(InjectionPoint<?> injectionPoint) {
        return (injectionPoint.getMethod() != null) ?
                extractQualifiersFromMethod(injectionPoint) : extractQualifiersFromField(injectionPoint);
    }

    private static List<Annotation> extractQualifiersFromMethod(InjectionPoint<?> injectionPoint) {
        List<Annotation> qualifiers = new ArrayList<Annotation>();

        try {
            final JMethod method = injectionPoint.getMethod();
            final JParameter parm = injectionPoint.getParm();

            JType[] jMethodParms = new JType[method.getParameters().length];
            int eventParamIndex = 0;
            for (int i = 0; i < method.getParameters().length; i++) {
                if (method.getParameters()[i].getName().equals(parm.getName())) {
                    eventParamIndex = i;
                }
                jMethodParms[i] = method.getParameters()[i].getType();
            }

            JClassType jType = injectionPoint.getInjector().getInjectedType();
            JMethod observesMethod = jType.getMethod(method.getName(), jMethodParms);

            for (Class<?> qualifier : getQualifiers()) {
                if (observesMethod.getParameters()[eventParamIndex].isAnnotationPresent((Class<? extends Annotation>) qualifier)) {
                    qualifiers.add(observesMethod.getParameters()[eventParamIndex].getAnnotation((Class<? extends Annotation>) qualifier));
                }
            }
        } catch (Exception e) {
            log.error("Problem reading qualifiers for " + injectionPoint.getMethod(), e);
        }

        return qualifiers;
    }

    private static List<Annotation> extractQualifiersFromField(InjectionPoint<?> injectionPoint) {
        List<Annotation> qualifiers = new ArrayList<Annotation>();

        try {
            // find all qualifiers of the event field
            JField jEventField = injectionPoint.getField();

            for (Class<?> qualifier : getQualifiers()) {
                if (jEventField.isAnnotationPresent((Class<? extends Annotation>) qualifier)) {
                    qualifiers.add(jEventField.getAnnotation((Class<? extends Annotation>) qualifier));
                }
            }
        } catch (Exception e) {
            log.error("Problem reading qualifiers for " + injectionPoint.getField(), e);
        }
        return qualifiers;
    }

    public static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (UnsupportedOperationException e) {
            // ignore
        } catch (Throwable e) {
            // ignore
        }
        return null;
    }

    public static Field loadField(JField field) {
        Class<?> cls = loadClass(field.getEnclosingType().getQualifiedSourceName());
        if (cls == null) return null;
        try {
            return cls.getField(field.getName());
        } catch (NoSuchFieldException e) {
        }
        return null;
    }

    public static Method loadMethod(JMethod method) {
        Class<?> cls = loadClass(method.getEnclosingType().getQualifiedSourceName());
        if (cls == null) return null;

        JParameter[] jparms = method.getParameters();
        Class[] parms = new Class[jparms.length];

        for (int i = 0; i < jparms.length; i++) {
            parms[i] = loadClass(jparms[i].getType().isClassOrInterface().getQualifiedSourceName());
        }

        try {
            return cls.getMethod(method.getName(), parms);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }
}
