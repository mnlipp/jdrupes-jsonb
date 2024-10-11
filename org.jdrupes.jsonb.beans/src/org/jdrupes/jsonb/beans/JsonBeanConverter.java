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
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/// Common base class for the JavaBean serializer and deserializer.
public class JsonBeanConverter {

    @SuppressWarnings({ "PMD.UseConcurrentHashMap",
        "PMD.FieldNamingConventions", "PMD.VariableNamingConventions" })
    private static final Map<Class<?>, PropertyEditor> propertyEditorCache
        = Collections.synchronizedMap(new WeakHashMap<>());
    @SuppressWarnings({ "PMD.UseConcurrentHashMap",
        "PMD.FieldNamingConventions", "PMD.VariableNamingConventions" })
    private static final Map<Class<?>, BeanInfo> beanInfoCache
        = Collections.synchronizedMap(new WeakHashMap<>());

    /// Find the property editor for the given class.
    ///
    /// @param cls the class
    /// @return the property editor
    protected static PropertyEditor findPropertyEditor(Class<?> cls) {
        PropertyEditor propertyEditor = propertyEditorCache.get(cls);
        if (propertyEditor == null && !propertyEditorCache.containsKey(cls)) {
            // Never looked for before.
            propertyEditor = PropertyEditorManager.findEditor(cls);
            propertyEditorCache.put(cls, propertyEditor);
        }
        return propertyEditor;
    }

    /// Find the bean info for the given class.
    ///
    /// @param cls the class
    /// @return the bean info
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

    /// Create a new instance.
    public JsonBeanConverter() {
        // Can be instantiated.
    }
}
