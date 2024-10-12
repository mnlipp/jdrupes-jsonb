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

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import java.beans.BeanInfo;
import java.beans.ConstructorProperties;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

///
/// Decoder for converting JSON to a Java object graph. The decoding
/// is based on the expected type passed to the decode method.
/// 
/// The conversion rules are as follows:
///  * If the expected type is a primitive, an array, a {@link Collection},
///    a {@link Map} or a {@link JsonValue} (i.e. cannot be a JavaBean)
///    {@link DeserializationContext#deserialize} is used for the conversion.
///  * If the expected type is neither of the above, it is assumed
///    to be a JavaBean and the JSON input must be a JSON object.
///    The key/value pairs of the JSON input are interpreted as properties
///    of the JavaBean and set if the values have been parsed successfully.
///    The type of the properties are used as expected types when
///    parsing the values.
///    
///    Constructors with {@link ConstructorProperties}
///    are used if all required values are available. Else, if no setter is
///    available for a key/value pair, an attempt
///    is made to gain access to a private field with the name of the
///    key and assign the value to that field. Note that this  will fail
///    when using Java 9 modules unless you explicitly grant the decoder 
///    access to private fields. So defining a constructor with
///    a {@link ConstructorProperties} annotation and all immutable
///    properties as parameters is strongly recommended.
///      
/// A JSON object can have a "@class" key. It must be the first key
/// provided by the parser, i.e. the property order strategy must be
/// lexicographic. Its value is used to instantiate the Java object
/// in which the information of the JSON object is stored. If
/// provided, the class specified by this key/value pair overrides 
/// the class passed as expected class. It is checked, however, that the
/// specified class is assignable to the expected class.
///  
/// The value specified is first matched against the aliases that
/// have been registered with the decoder 
/// (see {@link #addAlias(Class, String)}). If no match is found,
/// the converter set with {@link #setClassConverter(Function)}
/// is used to convert the name to a class. The function defaults
/// to {@link Class#forName(String)}. If the converter does not
/// return a result, {@link DeserializationContext#deserialize} is
/// used for the conversion.
///
public class JavaBeanDeserializer extends JavaBeanConverter
        implements JsonbDeserializer<Object> {

    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<String, Class<?>> aliases = new HashMap<>();
    private boolean skipUnknown;
    private Function<String, Optional<Class<?>>> classConverter = name -> {
        try {
            return Optional
                .ofNullable(getClass().getClassLoader().loadClass(name));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    };

    ///
    /// Creates a new instance.
    ///
    public JavaBeanDeserializer() {
        // Nothing to do.
    }

    ///
    /// Adds an alias for a class.
    ///
    /// @param alias the alias
    /// @param clazz the clazz
    /// @return the java bean serializer
    ///
    public JavaBeanDeserializer addAlias(Class<?> clazz, String alias) {
        aliases.put(alias, clazz);
        return this;
    }

    ///
    /// Sets the converter that maps a specified "class" to an actual Java
    /// {@link Class}. If it does not return a class, a {@link HashMap} is
    /// used to store the data of the JSON object.
    ///
    /// @param converter the converter to use
    /// @return the conversion result
    ///
    @SuppressWarnings("PMD.LinguisticNaming")
    public JavaBeanDeserializer setClassConverter(
            Function<String, Optional<Class<?>>> converter) {
        this.classConverter = converter;
        return this;
    }

    ///
    /// Cause this decoder to silently skip information from the JSON source
    /// that cannot be mapped to a property of the bean being created.
    /// This is useful if e.g. a REST invocation returns data that you
    /// are not interested in and therefore don't want to model in your
    /// JavaBean.
    ///
    /// @return the decoder for chaining
    ///
    public JavaBeanDeserializer skipUnknown() {
        skipUnknown = true;
        return this;
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext ctx,
            Type rtType) {
        if (!(rtType instanceof Class<?>)) {
            return ctx.deserialize(rtType, parser);
        }
        Class<?> expected = (Class<?>) rtType;
        if (expected.isEnum() || expected.isArray()
            || JsonValue.class.isAssignableFrom(expected)
            || Map.class.isAssignableFrom(expected)
            || Collection.class.isAssignableFrom(expected)
            || Boolean.class.isAssignableFrom(expected)
            || Byte.class.isAssignableFrom(expected)
            || Number.class.isAssignableFrom(expected)) {
            return ctx.deserialize(rtType, parser);
        }
        if (parser.next() != Event.START_OBJECT) {
            throw new JsonbException(
                parser.getLocation() + ": Expected START_OBJECT");
        }
        Class<?> actualCls = expected;
        Event prefetched = parser.next();
        if (prefetched.equals(Event.KEY_NAME)) {
            String key = parser.getString();
            if ("@class".equals(key)) {
                prefetched = null; // Now it's consumed
                parser.next();
                var provided = ((JsonString) parser.getValue()).getString();
                if (aliases.containsKey(provided)) {
                    actualCls = aliases.get(provided);
                } else {
                    actualCls = classConverter.apply(provided)
                        .orElse(expected);
                }
            }
        }
        return objectToBean(parser, ctx, actualCls, prefetched);
    }

    private <T> T objectToBean(JsonParser parser, DeserializationContext ctx,
            Class<T> beanCls, Event prefetched) {
        BeanInfo beanInfo = findBeanInfo(beanCls);
        if (beanInfo == null) {
            throw new JsonbException(
                parser.getLocation() + ": Cannot introspect " + beanCls);
        }
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, PropertyDescriptor> beanProps = new HashMap<>();
        for (PropertyDescriptor p : beanInfo.getPropertyDescriptors()) {
            beanProps.put(p.getName(), p);
        }

        // Get properties as map first.
        Map<String, Object> propsMap
            = parseProperties(parser, ctx, beanCls, beanProps, prefetched);

        // Prepare result, using constructor with parameters if available.
        T result = createBean(parser, beanCls, propsMap);

        // Set (remaining) properties.
        for (Map.Entry<String, ?> e : propsMap.entrySet()) {
            PropertyDescriptor property = beanProps.get(e.getKey());
            if (property == null) {
                if (skipUnknown) {
                    continue;
                }
                throw new JsonbException(parser.getLocation()
                    + ": No bean property for key " + e.getKey());
            }
            setProperty(parser, result, property, e.getValue());
        }
        return result;
    }

    private <T> T createBean(JsonParser parser, Class<T> beanCls,
            Map<String, Object> propsMap) {
        try {
            SortedMap<ConstructorProperties, Constructor<T>> cons
                = new TreeMap<>(Comparator.comparingInt(
                    (ConstructorProperties cp) -> cp.value().length)
                    .reversed());
            for (Constructor<?> c : beanCls.getConstructors()) {
                ConstructorProperties[] allCps = c.getAnnotationsByType(
                    ConstructorProperties.class);
                if (allCps.length > 0) {
                    @SuppressWarnings("unchecked")
                    Constructor<T> beanConstructor = (Constructor<T>) c;
                    cons.put(allCps[0], beanConstructor);
                }
            }
            for (Map.Entry<ConstructorProperties, Constructor<T>> e : cons
                .entrySet()) {
                String[] conProps = e.getKey().value();
                if (propsMap.keySet().containsAll(Arrays.asList(conProps))) {
                    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                    Object[] args = new Object[conProps.length];
                    for (int i = 0; i < conProps.length; i++) {
                        args[i] = propsMap.remove(conProps[i]);
                    }
                    return e.getValue().newInstance(args);
                }
            }

            return beanCls.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            throw new JsonbException(parser.getLocation()
                + ": Cannot create " + beanCls.getName(), e);
        }
    }

    private Map<String, Object> parseProperties(JsonParser parser,
            DeserializationContext ctx, Class<?> beanCls,
            Map<String, PropertyDescriptor> beanProps, Event prefetched) {
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, Object> map = new HashMap<>();
        whileLoop: while (true) {
            @SuppressWarnings("PMD.ConfusingTernary")
            Event event
                = (prefetched != null) ? prefetched : parser.next();
            prefetched = null; // Consumed.
            switch (event) {
            case END_OBJECT:
                break whileLoop;

            case KEY_NAME:
                String key = parser.getString();
                PropertyDescriptor property = beanProps.get(key);
                Object value;
                if (property == null) {
                    value = ctx.deserialize(Object.class, parser);
                } else {
                    value = ctx.deserialize(property.getPropertyType(), parser);
                }
                if (value instanceof String text) {
                    PropertyEditor propertyEditor = findPropertyEditor(beanCls);
                    if (propertyEditor != null) {
                        propertyEditor.setAsText(text);
                        value = propertyEditor.getValue();
                    }

                }
                map.put(key, value);
                break;

            default:
                throw new JsonbException(parser.getLocation()
                    + ": Unexpected Json event " + event);
            }
        }
        return map;
    }

    @SuppressWarnings({ "PMD.AvoidAccessibilityAlteration" })
    private <T> void setProperty(JsonParser parser, T obj,
            PropertyDescriptor property, Object value) {
        try {
            Method writeMethod = property.getWriteMethod();
            if (writeMethod != null) {
                writeMethod.invoke(obj, value);
                return;
            }
            Field propField = findField(obj.getClass(), property.getName());
            if (!propField.canAccess(obj)) {
                propField.setAccessible(true);
            }
            propField.set(obj, value);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchFieldException e) {
            throw new JsonbException(parser.getLocation()
                + ": Cannot write property " + property.getName(), e);
        }
    }

    private Field findField(Class<?> cls, String fieldName)
            throws NoSuchFieldException {
        if (cls.equals(Object.class)) {
            throw new NoSuchFieldException();
        }
        try {
            return cls.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            return findField(cls.getSuperclass(), fieldName);
        }
    }

}
