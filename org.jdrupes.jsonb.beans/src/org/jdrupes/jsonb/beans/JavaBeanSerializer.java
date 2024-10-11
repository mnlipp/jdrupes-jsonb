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

import jakarta.json.JsonValue;
import jakarta.json.bind.annotation.JsonbAnnotation;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

///
/// A serializer that treats all objects as JavaBeans. The JSON
/// description of an object can have an additional key/value pair with
/// key "@class" and a class name. This class information is generated
/// only if it is needed, i.e. if it cannot be derived from the containing
/// object.
/// 
/// Given the following classes:
/// 
/// ```java
/// public static class Person {
/// 
///     private String name;
///     private int age;
///     private PhoneNumber[] numbers;
/// 
///     public String getName() {
///         return name;
///     }
///     
///     public void setName(String name) {
///         this.name = name;
///     }
///     
///     public int getAge() {
///         return age;
///     }
///     
///     public void setAge(int age) {
///         this.age = age;
///     }
///     
///     public PhoneNumber[] getNumbers() {
///         return numbers;
///     }
///     
///     public void setNumbers(PhoneNumber[] numbers) {
///         this.numbers = numbers;
///     }
/// }
/// 
/// public static class PhoneNumber {
///     private String name;
///     private String number;
/// 
///     public PhoneNumber() {
///     }
///     
///     public String getName() {
///         return name;
///     }
///     
///     public void setName(String name) {
///         this.name = name;
///     }
///     
///     public String getNumber() {
///         return number;
///     }
///     
///     public void setNumber(String number) {
///         this.number = number;
///     }
/// }
/// 
/// public static class SpecialNumber extends PhoneNumber {
/// }
/// ```
/// 
/// A serialization result may look like this:
/// 
/// ```json
/// {
///     "age": 42,
///     "name": "Simon Sample",
///     "numbers": [
///         {
///             "name": "Home",
///             "number": "06751 51 56 57"
///         },
///         {
///             "@class": "test.json.SpecialNumber",
///             "name": "Work",
///             "number": "030 77 35 44"
///         }
///     ]
/// } 
/// ```
///
/// As there is no marker interface for JavaBeans, this serializer
/// considers all objects to be JavaBeans by default and obtains all
/// information for serialization from the generated (or explicitly
/// provided) {@link BeanInfo}. It is, however, possible to exclude
/// classes from bing handled by this serializer by either annotating them
/// with {@link JsonbAnnotation} or 
///
public class JavaBeanSerializer extends JavaBeanConverter
        implements JsonbSerializer<Object> {

    private static record Expected(Object[] values, Class<?> type) {
    }

    private ThreadLocal<Expected> expected = new ThreadLocal<>();
    private boolean omitClass;
    private final Map<Class<?>, String> aliases = new HashMap<>();
    private final Set<Class<?>> ignored = new HashSet<>();

    /// Create a new instance.
    public JavaBeanSerializer() {
        // Can be instantiated
    }

    ///
    /// Adds an alias for a class.
    ///
    /// @param clazz the clazz
    /// @param alias the alias
    /// @return the java bean serializer
    ///
    public JavaBeanSerializer addAlias(Class<?> clazz, String alias) {
        aliases.put(clazz, alias);
        return this;
    }

    ///
    /// Sets the expected class for the object passed as parameter
    /// to {@link jakarta.json.bind.Jsonb#toJson}.
    ///
    /// @param type the type
    /// @return the java bean serializer
    ///
    public JavaBeanSerializer setExpected(Class<?> type) {
        expected.set(new Expected(null, type));
        return this;
    }

    ///
    /// Don't handle the given type(s) as JavaBeans. Pass them to the
    /// default serialization mechanism instead.
    ///
    /// @param type the type(s) to ignore
    /// @return the java bean serializer
    ///
    public JavaBeanSerializer addIgnored(Class<?>... type) {
        ignored.addAll(Arrays.asList(type));
        return this;
    }

    @Override
    public void serialize(Object value, JsonGenerator generator,
            SerializationContext context) {
        if (value == null
            || value instanceof Boolean
            || value instanceof Byte
            || value instanceof Number
            || value.getClass().isArray()
            || value instanceof Collection<?>
            || value instanceof Map<?, ?>
            || value instanceof JsonValue
            || value.getClass().getAnnotation(JsonbAnnotation.class) != null
            || ignored.contains(value.getClass())) {
            context.serialize(value, generator);
            return;
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
        Class<?> expectedType = Optional.ofNullable(expected.get())
            .filter(e -> e.values() == null
                || Arrays.stream(e.values()).anyMatch(v -> v == bean))
            .map(e -> e.type()).orElse(null);
        if (expectedType != null && !bean.getClass().equals(expectedType)
            && !omitClass) {
            context.serialize("@class", aliases.computeIfAbsent(
                bean.getClass(), k -> k.getName()), generator);
        }
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
                Expected old = expected.get();
                try {
                    if (propDesc.getPropertyType().isArray()) {
                        expected.set(new Expected((Object[]) propValue,
                            propDesc.getPropertyType().componentType()));
                    } else {
                        expected.set(new Expected(new Object[] { propValue },
                            propDesc.getPropertyType()));
                    }
                    context.serialize(propDesc.getName(), propValue, generator);
                } finally {
                    expected.set(old);
                }
                continue;
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                // Bad luck
            }
        }
        generator.writeEnd();
    }

}
