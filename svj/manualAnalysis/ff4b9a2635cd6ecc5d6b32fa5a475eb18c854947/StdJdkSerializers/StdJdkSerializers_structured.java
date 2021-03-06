package com.fasterxml.jackson.databind.ser.std;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.ser.BasicSerializerFactory;

/**
 * Class that providers access to serializers user for non-structured JDK types that
 * are serializer as scalars; some using basic {@link ToStringSerializer},
 * others explicit serializers.
 */
@SuppressWarnings("serial")
public class StdJdkSerializers {

    /**
     * Method called by {@link BasicSerializerFactory} to access
     * all serializers this class provides.
     */
    public static Collection<Map.Entry<Class<?>, Object>> all() {
        HashMap<Class<?>, Object> sers = new HashMap<Class<?>, Object>();
        final ToStringSerializer sls = ToStringSerializer.instance;
        sers.put(java.net.URL.class, sls);
        sers.put(java.net.URI.class, sls);
        sers.put(Currency.class, sls);
        sers.put(UUID.class, new UUIDSerializer());
        sers.put(java.util.regex.Pattern.class, sls);
        sers.put(Locale.class, sls);
        sers.put(AtomicBoolean.class, AtomicBooleanSerializer.class);
        sers.put(AtomicInteger.class, AtomicIntegerSerializer.class);
        sers.put(AtomicLong.class, AtomicLongSerializer.class);
        sers.put(File.class, FileSerializer.class);
        sers.put(Class.class, ClassSerializer.class);
        sers.put(Void.class, NullSerializer.instance);
        sers.put(Void.TYPE, NullSerializer.instance);
        try {
            sers.put(java.sql.Timestamp.class, DateSerializer.instance);
            sers.put(java.sql.Date.class, SqlDateSerializer.class);
            sers.put(java.sql.Time.class, SqlTimeSerializer.class);
        } catch (NoClassDefFoundError e) {
        }
        return sers.entrySet();
    }

    public static class AtomicBooleanSerializer extends StdScalarSerializer<AtomicBoolean> {

        public AtomicBooleanSerializer() {
            super(AtomicBoolean.class, false);
        }

        @Override
        public void serialize(AtomicBoolean value, JsonGenerator gen, SerializerProvider provider) throws IOException, JsonGenerationException {
            gen.writeBoolean(value.get());
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint) {
            return createSchemaNode("boolean", true);
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
            visitor.expectBooleanFormat(typeHint);
        }
    }

    public static class AtomicIntegerSerializer extends StdScalarSerializer<AtomicInteger> {

        public AtomicIntegerSerializer() {
            super(AtomicInteger.class, false);
        }

        @Override
        public void serialize(AtomicInteger value, JsonGenerator gen, SerializerProvider provider) throws IOException, JsonGenerationException {
            gen.writeNumber(value.get());
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint) {
            return createSchemaNode("integer", true);
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
            visitIntFormat(visitor, typeHint, JsonParser.NumberType.INT);
        }
    }

    public static class AtomicLongSerializer extends StdScalarSerializer<AtomicLong> {

        public AtomicLongSerializer() {
            super(AtomicLong.class, false);
        }

        @Override
        public void serialize(AtomicLong value, JsonGenerator gen, SerializerProvider provider) throws IOException, JsonGenerationException {
            gen.writeNumber(value.get());
        }

        @Override
        public JsonNode getSchema(SerializerProvider provider, Type typeHint) {
            return createSchemaNode("integer", true);
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType typeHint) throws JsonMappingException {
            visitIntFormat(visitor, typeHint, JsonParser.NumberType.LONG);
        }
    }
}

