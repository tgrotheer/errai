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

import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JParameter;
import org.mvel2.util.StringAppender;

import java.lang.annotation.Annotation;

public class DecoratorTask extends InjectionTask {
    private final IOCExtension[] IOCExtensions;

    public DecoratorTask(Injector injector, JClassType type, IOCExtension[] decs) {
        super(injector, type);
        this.IOCExtensions = decs;
    }

    public DecoratorTask(Injector injector, JField field, IOCExtension[] decs) {
        super(injector, field);
        this.IOCExtensions = decs;
    }

    public DecoratorTask(Injector injector, JMethod method, IOCExtension[] decs) {
        super(injector, method);
        this.IOCExtensions = decs;
    }

    public DecoratorTask(Injector injector, JParameter parm, IOCExtension[] decs) {
        super(injector, parm);
        this.IOCExtensions = decs;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public String doTask(InjectionContext ctx) {
        StringAppender appender = new StringAppender();
        Annotation anno = null;

        for (IOCExtension<?> dec : IOCExtensions) {
            switch (injectType) {
                case PrivateField:
                case Field:
                    anno = field.getAnnotation(dec.decoratesWith());
                    break;
                case Method:
                    anno = method.getAnnotation(dec.decoratesWith());
                    if (anno == null && field != null) {
                        anno = field.getAnnotation(dec.decoratesWith());
                    } else if (anno == null && parm != null) {
                        anno = parm.getAnnotation(dec.decoratesWith());
                    }
                    break;
                case Type:
                    anno = type.getAnnotation(dec.decoratesWith());
                    break;

            }

            appender.append(dec.generateDecorator(new InjectionPoint(anno, injectType, constructor, method, field, type, parm, injector, ctx)));

            appender.append("\n");
        }
        return appender.toString();
    }
}
