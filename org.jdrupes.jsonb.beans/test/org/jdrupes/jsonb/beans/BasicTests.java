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

import java.lang.management.ManagementFactory;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

class BasicTests {

//    @Test
//    void test() throws MalformedObjectNameException, InstanceNotFoundException,
//            IntrospectionException, ReflectionException {
//        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
//        var mbeanName = new ObjectName("java.lang"
//            + ":type=GarbageCollector,name=G1 Concurrent GC");
//        var object = mbs.getObjectInstance(mbeanName);
//
////        ObjectMapper mapper = new ObjectMapper();
////        mapper.registerModule(new JmxModule(mbs));
////        String json = mapper.writeValueAsString(object);
////        System.out.println(json);
//    }

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

        public PhoneNumber(String name, String number) {
            this.name = name;
            this.number = number;
        }

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

        public SpecialNumber(String name, String number) {
            super(name, number);
        }

    }

    @Test
    void test() {
        // Create custom configuration
        JsonbConfig config = new JsonbConfig()
            .withSerializers(new JavaBeanSerializer())
            .withFormatting(true);
        Jsonb jsonb = JsonbBuilder.create(config);

        var person = new Person();
        person.setName("Tom Test");
        person.setAge(42);
        person.setNumbers(
            new PhoneNumber[] { new PhoneNumber("Mobile", "123"),
                new SpecialNumber("Emergency", "456") });

        String result = jsonb.toJson(person);

        System.out.println(result);
    }

}
