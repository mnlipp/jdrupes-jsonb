/*
 * JDrupes Json-B plugins
 * Copyright (C) 2024 Michael N. Lipp
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jdrupes.jsonb.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

/// This is *markdown*
public class JavaBeanSerializer implements JsonbSerializer<Object> {

    @SuppressWarnings({ "PMD.UseConcurrentHashMap",
        "PMD.FieldNamingConventions", "PMD.VariableNamingConventions" })
    private static final Map<Class<?>, PropertyEditor> propertyEditorCache
        = Collections.synchronizedMap(new WeakHashMap<>());
    @SuppressWarnings({ "PMD.UseConcurrentHashMap",
        "PMD.FieldNamingConventions", "PMD.VariableNamingConventions" })
    private static final Map<Class<?>, BeanInfo> beanInfoCache
        = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Find the property editor for the given class.
     *
     * @param cls the class
     * @return the property editor
     */
    private static PropertyEditor findPropertyEditor(Class<?> cls) {
        PropertyEditor propertyEditor = propertyEditorCache.get(cls);
        if (propertyEditor == null && !propertyEditorCache.containsKey(cls)) {
            // Never looked for before.
            propertyEditor = PropertyEditorManager.findEditor(cls);
            propertyEditorCache.put(cls, propertyEditor);
        }
        return propertyEditor;
    }

    /**
     * Find the bean info for the given class.
     *
     * @param cls the class
     * @return the bean info
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    protected static BeanInfo findBeanInfo(Class<?> cls) {
        BeanInfo beanInfo = beanInfoCache.get(cls);
        if (beanInfo == null && !beanInfoCache.containsKey(cls)) {
            try {
                beanInfo = Introspector.getBeanInfo(cls, Object.class);
            } catch (IntrospectionException e) {
                // Bad luck
            }
            beanInfoCache.put(cls, beanInfo);
        }
        return beanInfo;
    }

    @Override
    public void serialize(Object value, JsonGenerator generator,
            SerializationContext context) {
        if (value == null
            || value instanceof Boolean
            || value instanceof Byte
            || value instanceof Number) {
            context.serialize(value, generator);
        }
        PropertyEditor propertyEditor = findPropertyEditor(value.getClass());
        if (propertyEditor != null) {
            propertyEditor.setValue(value);
            generator.write(propertyEditor.getAsText());
            return;
        }
        BeanInfo beanInfo = findBeanInfo(value.getClass());
        if (beanInfo != null && beanInfo.getPropertyDescriptors().length > 0) {
            writeJavaBean(value, beanInfo, generator, context);
            return;
        }
    }

    @SuppressWarnings("PMD.EmptyCatchBlock")
    private void writeJavaBean(Object bean, BeanInfo beanInfo,
            JsonGenerator generator, SerializationContext context) {
        generator.writeStartObject();
//        if (!obj.getClass().equals(expectedType) && !omitClass) {
//            gen.writeStringField("class", aliases.computeIfAbsent(
//                obj.getClass(), k -> k.getName()));
//        }
        for (PropertyDescriptor propDesc : beanInfo
            .getPropertyDescriptors()) {
            if (propDesc.getValue("transient") != null) {
                continue;
            }
            Method method = propDesc.getReadMethod();
            if (method == null) {
                continue;
            }
            try {
                Object propValue = method.invoke(bean);
                context.serialize(propDesc.getName(), propValue, generator);
//                generator.wr.writeFieldName(propDesc.getName());
//                doWriteObject(value, propDesc.getPropertyType());
                continue;
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // Bad luck
            }
        }
        generator.writeEnd();
    }

}
