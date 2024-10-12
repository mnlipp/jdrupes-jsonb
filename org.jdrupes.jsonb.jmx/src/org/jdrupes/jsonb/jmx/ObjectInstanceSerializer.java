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

package org.jdrupes.jsonb.jmx;

import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import javax.management.ObjectInstance;

///
/// Serializer for a JMX {@link ObjectInstance}.
///
public class ObjectInstanceSerializer
        implements JsonbSerializer<ObjectInstance> {

    ///
    /// Creates a new instance.
    ///
    public ObjectInstanceSerializer() {
        /// Do nothing
    }

    @Override
    public void serialize(ObjectInstance value, JsonGenerator generator,
            SerializationContext context) {
    }
}
