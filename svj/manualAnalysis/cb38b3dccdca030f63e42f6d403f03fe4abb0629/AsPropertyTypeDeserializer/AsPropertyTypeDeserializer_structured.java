package com.fasterxml.jackson.databind.jsontype.impl;

import java.io.IOException;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.JsonParserSequence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.util.TokenBuffer;

/**
 * Type deserializer used with {@link As#PROPERTY}
 * inclusion mechanism.
 * Uses regular form (additional key/value entry before actual data)
 * when typed object is expressed as JSON Object; otherwise behaves similar to how
 * {@link As#WRAPPER_ARRAY} works.
 * Latter is used if JSON representation is polymorphic
 */
public class AsPropertyTypeDeserializer extends AsArrayTypeDeserializer {

    private static final long serialVersionUID = 1L;

    protected final As _inclusion;

    public AsPropertyTypeDeserializer(JavaType bt, TypeIdResolver idRes, String typePropertyName, boolean typeIdVisible, JavaType defaultImpl) {
        this(bt, idRes, typePropertyName, typeIdVisible, defaultImpl, As.PROPERTY);
    }

    public AsPropertyTypeDeserializer(JavaType bt, TypeIdResolver idRes, String typePropertyName, boolean typeIdVisible, JavaType defaultImpl, As inclusion) {
        super(bt, idRes, typePropertyName, typeIdVisible, defaultImpl);
        _inclusion = inclusion;
    }

    public AsPropertyTypeDeserializer(AsPropertyTypeDeserializer src, BeanProperty property) {
        super(src, property);
        _inclusion = src._inclusion;
    }

    @Override
    public TypeDeserializer forProperty(BeanProperty prop) {
        return (prop == _property) ? this : new AsPropertyTypeDeserializer(this, prop);
    }

    @Override
    public As getTypeInclusion() {
        return _inclusion;
    }

    /**
     * This is the trickiest thing to handle, since property we are looking
     * for may be anywhere...
     */
    @Override
    @SuppressWarnings(value = { "resource" })
    public Object deserializeTypedFromObject(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.canReadTypeId()) {
            Object typeId = p.getTypeId();
            if (typeId != null) {
                return _deserializeWithNativeTypeId(p, ctxt, typeId);
            }
        }
        JsonToken t = p.getCurrentToken();
        if (t == JsonToken.START_OBJECT) {
            t = p.nextToken();
        } else {
            if (t != JsonToken.FIELD_NAME) {
                return _deserializeTypedUsingDefaultImpl(p, ctxt, null);
            }
        }
        TokenBuffer tb = null;
        for (; t == JsonToken.FIELD_NAME; t = p.nextToken()) {
            String name = p.getCurrentName();
            p.nextToken();
            if (name.equals(_typePropertyName)) {
                return _deserializeTypedForId(p, ctxt, tb);
            }
            if (tb == null) {
                tb = new TokenBuffer(p, ctxt);
            }
            tb.writeFieldName(name);
            tb.copyCurrentStructure(p);
        }
        return _deserializeTypedUsingDefaultImpl(p, ctxt, tb);
    }

    @SuppressWarnings(value = { "resource" })
    protected Object _deserializeTypedForId(JsonParser p, DeserializationContext ctxt, TokenBuffer tb) throws IOException {
        String typeId = p.getText();
        JsonDeserializer<Object> deser = _findDeserializer(ctxt, typeId);
        if (_typeIdVisible) {
            if (tb == null) {
                tb = new TokenBuffer(p, ctxt);
            }
            tb.writeFieldName(p.getCurrentName());
            tb.writeString(typeId);
        }
        if (tb != null) {
            p.clearCurrentToken();
            p = JsonParserSequence.createFlattened(false, tb.asParser(p), p);
        }
        p.nextToken();
        return deser.deserialize(p, ctxt);
    }

    // off-lined to keep main method lean and mean...
    @SuppressWarnings(value = { "resource" })
    protected Object _deserializeTypedUsingDefaultImpl(JsonParser p, DeserializationContext ctxt, TokenBuffer tb) throws IOException {
        JsonDeserializer<Object> deser = _findDefaultImplDeserializer(ctxt);
        if (deser != null) {
            if (tb != null) {
                tb.writeEndObject();
                p = tb.asParser(p);
                p.nextToken();
            }
            return deser.deserialize(p, ctxt);
        }
        Object result = TypeDeserializer.deserializeIfNatural(p, ctxt, _baseType);
        if (result != null) {
            return result;
        }
        if (p.isExpectedStartArrayToken()) {
            return super.deserializeTypedFromAny(p, ctxt);
        }
        if (p.hasToken(JsonToken.VALUE_STRING)) {
            if (ctxt.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)) {
                String str = p.getText().trim();
                if (str.isEmpty()) {
                    return null;
                }
            }
        }
        ctxt.reportWrongTokenException(baseType(), JsonToken.FIELD_NAME, "missing property \'" + _typePropertyName + "\' that is to contain type id  (for class " + baseTypeName() + ")");
        return null;
    }

    /* Also need to re-route "unknown" version. Need to think
     * this through bit more in future, but for now this does address issue and has
     * no negative side effects (at least within existing unit test suite).
     */
    @Override
    public Object deserializeTypedFromAny(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.getCurrentToken() == JsonToken.START_ARRAY) {
            return super.deserializeTypedFromArray(p, ctxt);
        }
        return deserializeTypedFromObject(p, ctxt);
    }
}

