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

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.beans.ConstructorProperties;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class DeserializationTests {

    public static class Person {

        private String name;
        private int age;
        private PhoneNumber[] numbers;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public PhoneNumber[] getNumbers() {
            return numbers;
        }

        public void setNumbers(PhoneNumber[] numbers) {
            this.numbers = numbers;
        }
    }

    public static class PhoneNumber {
        private String name;
        private String number;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }
    }

    public static class SpecialNumber extends PhoneNumber {
    }

    String jsonSample1
        = """
                {
                  "age":42,
                  "name":"Tom Test",
                  "numbers":[
                    {
                      "name":"Mobile",
                      "number":"123"
                    },
                    {
                      "@class":"SpecialNumber",
                      "name":"Emergency",
                      "number":"911"
                    }
                  ]
                }""";

    @Test
    void readAsBean() {
        // Create custom configuration
        JsonbConfig config = new JsonbConfig()
            .withDeserializers(
                new JavaBeanDeserializer()
                    .addAlias(SpecialNumber.class, "SpecialNumber"));
        Jsonb jsonb = JsonbBuilder.create(config);

        Person obj = jsonb.fromJson(jsonSample1, Person.class);
        assertEquals(PhoneNumber.class, obj.getNumbers()[0].getClass());
        assertEquals(SpecialNumber.class, obj.getNumbers()[1].getClass());
    }

    @Test
    void readAsMap() {
        // Create custom configuration
        JsonbConfig config = new JsonbConfig()
            .withDeserializers(
                new JavaBeanDeserializer()
                    .addAlias(SpecialNumber.class, "SpecialNumber"));
        Jsonb jsonb = JsonbBuilder.create(config);

        Map<?, ?> result = jsonb.fromJson(jsonSample1, Map.class);
        assertNotEquals(null, result);
        assertEquals(42, result.get("age"));
        assertEquals(((List<?>) result.get("numbers")).size(), 2);
        assertTrue(((List<?>) result.get("numbers")).get(0) instanceof Map);
        assertTrue(((List<?>) result.get("numbers")).get(1) instanceof Map);
    }

    public static class RoBean {

        private int value = 0;

        public RoBean() {
        }

        public RoBean(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @Test
    public void testRoBean() {
        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig()
            .withDeserializers(new JavaBeanDeserializer()));
        String json = "{ \"value\": 42 }";
        RoBean result = jsonb.fromJson(json, RoBean.class);
        assertEquals(42, result.getValue());
    }

    public static class ImmutablePoint {

        private int posX = 0;
        private int posY = 0;
        private String name = null;
        public boolean xyConstCalled = false;
        public boolean allConstCalled = false;

        // Not to be picked
        @ConstructorProperties({ "name", "x", "y" })
        public ImmutablePoint(String name, int posX, int posY) {
            this(posX, posY);
            this.name = name;
            allConstCalled = true;
        }

        @ConstructorProperties({ "x", "y" })
        public ImmutablePoint(int posX, int posY) {
            this.posX = posX;
            this.posY = posY;
            xyConstCalled = true;
        }

        public String getName() {
            return name;
        }

        public int getX() {
            return posX;
        }

        public int getY() {
            return posY;
        }

    }

    @Test
    public void testImmutablePoint() {
        Jsonb jsonb = JsonbBuilder.create(new JsonbConfig()
            .withDeserializers(new JavaBeanDeserializer()));
        String json = "{ \"x\": 1, \"y\": 2 }";
        ImmutablePoint result = jsonb.fromJson(json, ImmutablePoint.class);
        assertTrue(result.xyConstCalled);
        assertFalse(result.allConstCalled);
        assertEquals(1, result.getX());
        assertEquals(2, result.getY());
        assertNull(result.getName());

        json = "{ \"name\": \"point\", \"x\": 1, \"y\": 2 }";
        result = jsonb.fromJson(json, ImmutablePoint.class);
        assertEquals(1, result.getX());
        assertEquals(2, result.getY());
        assertEquals("point", result.getName());
        assertTrue(result.allConstCalled);
    }

}
