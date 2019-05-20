package com.fasterxml.jackson.dataformat.csv;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.GeneratorBase;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.csv.impl.CsvEncoder;

public class CsvGenerator extends GeneratorBase {

    /**
     * Enumeration that defines all togglable features for CSV writers
     * (if any: currently none)
     */
    public enum Feature implements FormatFeature {

        /**
         * Feature that determines how much work is done before determining that
         * a column value requires quoting: when set as <code>true</code>, full
         * check is made to only use quoting when it is strictly necessary;
         * but when <code>false</code>, a faster but more conservative check
         * is made, and possibly quoting is used for values that might not need it.
         * Trade-offs is basically between optimal/minimal quoting (true), and
         * faster handling (false).
         * Faster check involves only checking first N characters of value, as well
         * as possible looser checks.
         *<p>
         * Note, however, that regardless setting, all values that need to be quoted
         * will be: it is just that when set to <code>false</code>, other values may
         * also be quoted (to avoid having to do more expensive checks).
         *<p>
         * Default value is <code>false</code> for "loose" (approximate, conservative)
         * checking.
         * 
         * @since 2.4
         */
        STRICT_CHECK_FOR_QUOTING(false), /**
         * Feature that determines whether columns without matching value may be omitted,
         * when they are the last values of the row.
         * If <code>true</code>, values and separators between values may be omitted, to slightly reduce
         * length of the row; if <code>false</code>, separators need to stay in place and values
         * are indicated by empty Strings.
         * 
         * @since 2.4
         */
        OMIT_MISSING_TAIL_COLUMNS(false), /**
         * Feature that determines whether values written as Strings (from <code>java.lang.String</code>
         * valued POJO properties) should be forced to be quoted, regardless of whether they
         * actually need this.
         * Note that this feature has precedence over {@link #STRICT_CHECK_FOR_QUOTING}, when
         * both would be applicable.
         *
         * @since 2.5
         */
        ALWAYS_QUOTE_STRINGS(false);

        protected final boolean _defaultState;

        protected final int _mask;

        /**
         * Method that calculates bit set (flags) of all features that
         * are enabled by default.
         */
        public static int collectDefaults() {
            int flags = 0;
            for (Feature f : values()) {
                if (f.enabledByDefault()) {
                    flags |= f.getMask();
                }
            }
            return flags;
        }

        private Feature(boolean defaultState) {
            _defaultState = defaultState;
            _mask = (1 << ordinal());
        }

        @Override
        public boolean enabledIn(int flags) {
            return (flags & _mask) != 0;
        }

        @Override
        public boolean enabledByDefault() {
            return _defaultState;
        }

        @Override
        public int getMask() {
            return _mask;
        }
    }

    protected static final long MIN_INT_AS_LONG = Integer.MIN_VALUE;

    protected static final long MAX_INT_AS_LONG = Integer.MAX_VALUE;

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    private static final CsvSchema EMPTY_SCHEMA;

    static {
        EMPTY_SCHEMA = CsvSchema.emptySchema();
    }

    static {
        EMPTY_SCHEMA = CsvSchema.emptySchema();
    }

    protected final IOContext _ioContext;

    /**
     * Bit flag composed of bits that indicate which
     * {@link CsvGenerator.Feature}s
     * are enabled.
     */
    protected int _formatFeatures;

    /**
     * Definition of columns being written, if available.
     */
    protected CsvSchema _schema = EMPTY_SCHEMA;

    // note: can not be final since we may need to re-create it for new schema
    protected CsvEncoder _writer;

    /*
    /**********************************************************
    /* Output state
    /**********************************************************
     */
    /**
     * Flag that indicates that we need to write header line, if
     * one is needed. Used because schema may be specified after
     * instance is constructed.
     */
    protected boolean _handleFirstLine = true;

    /**
     * Index of column that we will be getting next, based on
     * field name call that was made.
     */
    protected int _nextColumnByName = -1;

    /**
     * Flag set when property to write is unknown, and the matching value
     * is to be skipped quietly.
     *
     * @since 2.5
     */
    protected boolean _skipValue;

    /**
     * Separator to use during writing of (simple) array value, to be encoded as a
     * single column value, if any.
     *
     * @since 2.5
     */
    protected String _arraySeparator = CsvSchema.NO_ARRAY_ELEMENT_SEPARATOR;

    /**
     * Accumulated contents of an array cell, if any
     */
    protected StringBuilder _arrayContents;

    /**
     * Additional counter that indicates number of value entries in the
     * array. Needed because `null` entries do not add content, but need
     * to be separated by array cell separator
     *
     * @since 2.7
     */
    protected int _arrayElements;

    /**
     * When skipping output (for "unknown" output), outermost write context
     * where skipping should occur
     *
     * @since 2.7
     */
    protected JsonWriteContext _skipWithin;

    public CsvGenerator(IOContext ctxt, int jsonFeatures, int csvFeatures, ObjectCodec codec, Writer out, CsvSchema schema) {
        super(jsonFeatures, codec);
        _ioContext = ctxt;
        _formatFeatures = csvFeatures;
        _schema = schema;
        _writer = new CsvEncoder(ctxt, csvFeatures, out, schema);
    }

    public CsvGenerator(IOContext ctxt, int jsonFeatures, int csvFeatures, ObjectCodec codec, CsvEncoder csvWriter) {
        super(jsonFeatures, codec);
        _ioContext = ctxt;
        _formatFeatures = csvFeatures;
        _writer = csvWriter;
    }

    /*                                                                                       
    /**********************************************************                              
    /* Versioned                                                                             
    /**********************************************************                              
     */
    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************
    /* Overridden methods, configuration
    /**********************************************************
     */
    /**
     * No way (or need) to indent anything, so let's block any attempts.
     * (should we throw an exception instead?)
     */
    @Override
    public CsvGenerator useDefaultPrettyPrinter() {
        return this;
    }

    /**
     * No way (or need) to indent anything, so let's block any attempts.
     * (should we throw an exception instead?)
     */
    @Override
    public CsvGenerator setPrettyPrinter(PrettyPrinter pp) {
        return this;
    }

    @Override
    public Object getOutputTarget() {
        return _writer.getOutputTarget();
    }

    /**
     * NOTE: while this method will return some information on amount of data buffered, it
     * may be an incomplete view as some buffering happens at a higher level, as not-yet-serialized
     * values.
     */
    @Override
    public int getOutputBuffered() {
        return _writer.getOutputBuffered();
    }

    @Override
    public void setSchema(FormatSchema schema) {
        if (schema instanceof CsvSchema) {
            if (_schema != schema) {
                _schema = (CsvSchema) schema;
                _writer = _writer.withSchema(_schema);
            }
        } else {
            super.setSchema(schema);
        }
    }

    @Override
    public int getFormatFeatures() {
        return _formatFeatures;
    }

    @Override
    public JsonGenerator overrideFormatFeatures(int values, int mask) {
        int oldF = _formatFeatures;
        int newF = (_formatFeatures & ~mask) | (values & mask);
        if (oldF != newF) {
            _formatFeatures = newF;
            _writer.overrideFormatFeatures(newF);
        }
        return this;
    }

    /*
    /**********************************************************
    /* Public API, capability introspection methods
    /**********************************************************
     */
    @Override
    public boolean canUseSchema(FormatSchema schema) {
        return (schema instanceof CsvSchema);
    }

    @Override
    public boolean canOmitFields() {
        return false;
    }

    @Override
    public boolean canWriteFormattedNumbers() {
        return true;
    }

    /*
    /**********************************************************************
    /* Overridden methods; writing field names
    /**********************************************************************
     */
    /* And then methods overridden to make final, streamline some
     * aspects...
     */
    @Override
    public final void writeFieldName(String name) throws IOException {
        if (_writeContext.writeFieldName(name) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeFieldName(name);
    }

    @Override
    public final void writeFieldName(SerializableString name) throws IOException {
        if (_writeContext.writeFieldName(name.getValue()) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeFieldName(name.getValue());
    }

    @Override
    public final void writeStringField(String fieldName, String value) throws IOException {
        if (_writeContext.writeFieldName(fieldName) == JsonWriteContext.STATUS_EXPECT_VALUE) {
            _reportError("Can not write a field name, expecting a value");
        }
        _writeFieldName(fieldName);
        writeString(value);
    }

    private final void _writeFieldName(String name) throws IOException {
        if (_schema == null) {
            _reportMappingError("Unrecognized column \'" + name + "\', can not resolve without CsvSchema");
        }
        if (_skipWithin != null) {
            _skipValue = true;
            _nextColumnByName = -1;
            return;
        }
        CsvSchema.Column col = _schema.column(name, _nextColumnByName + 1);
        if (col == null) {
            if (isEnabled(JsonGenerator.Feature.IGNORE_UNKNOWN)) {
                _skipValue = true;
                _nextColumnByName = -1;
                return;
            }
            _reportMappingError("Unrecognized column \'" + name + "\': known columns: " + _schema.getColumnDesc());
        }
        _skipValue = false;
        _nextColumnByName = col.getIndex();
    }

    /*
    /**********************************************************
    /* Extended API, configuration
    /**********************************************************
     */
    public final boolean isEnabled(Feature f) {
        return (_formatFeatures & f.getMask()) != 0;
    }

    public CsvGenerator configure(Feature f, boolean state) {
        if (state) {
            return enable(f);
        }
        return disable(f);
    }

    public CsvGenerator enable(Feature f) {
        _formatFeatures |= f.getMask();
        _writer.overrideFormatFeatures(_formatFeatures);
        return this;
    }

    public CsvGenerator disable(Feature f) {
        _formatFeatures &= ~f.getMask();
        _writer.overrideFormatFeatures(_formatFeatures);
        return this;
    }

    /*
    /**********************************************************
    /* Public API: low-level I/O
    /**********************************************************
     */
    @Override
    public final void flush() throws IOException {
        _writer.flush(isEnabled(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM));
    }

    @Override
    public void close() throws IOException {
        super.close();
        finishRow();
        if (_handleFirstLine) {
            _handleFirstLine();
        }
        _writer.close(_ioContext.isResourceManaged() || isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET));
    }

    /*
    /**********************************************************
    /* Public API: structural output
    /**********************************************************
     */
    @Override
    public final void writeStartArray() throws IOException {
        _verifyValueWrite("start an array");
        if (_writeContext.inObject()) {
            if ((_skipWithin == null) && _skipValue && isEnabled(JsonGenerator.Feature.IGNORE_UNKNOWN)) {
                _skipWithin = _writeContext;
            } else {
                if (!_skipValue) {
                    String sep;
                    if (_nextColumnByName >= 0) {
                        CsvSchema.Column col = _schema.column(_nextColumnByName);
                        sep = col.isArray() ? col.getArrayElementSeparator() : CsvSchema.NO_ARRAY_ELEMENT_SEPARATOR;
                    } else {
                        sep = CsvSchema.NO_ARRAY_ELEMENT_SEPARATOR;
                    }
                    if (sep.isEmpty()) {
                        if (!_schema.hasArrayElementSeparator()) {
                            _reportError("CSV generator does not support Array values for properties without setting \'arrayElementSeparator\' in schema");
                        }
                        sep = _schema.getArrayElementSeparator();
                    }
                    _arraySeparator = sep;
                    if (_arrayContents == null) {
                        _arrayContents = new StringBuilder();
                    } else {
                        _arrayContents.setLength(0);
                    }
                    _arrayElements = 0;
                }
            }
        } else {
            if (!_arraySeparator.isEmpty()) {
                _reportError("CSV generator does not support nested Array values");
            }
        }
        _writeContext = _writeContext.createChildArrayContext();
    }

    @Override
    public final void writeEndArray() throws IOException {
        if (!_writeContext.inArray()) {
            _reportError("Current context not Array but " + _writeContext.typeDesc());
        }
        _writeContext = _writeContext.getParent();
        if (_skipWithin != null) {
            if (_writeContext == _skipWithin) {
                _skipWithin = null;
            }
            return;
        }
        if (!_arraySeparator.isEmpty()) {
            _arraySeparator = CsvSchema.NO_ARRAY_ELEMENT_SEPARATOR;
            _writer.write(_columnIndex(), _arrayContents.toString());
        }
        if (!_writeContext.inObject()) {
            finishRow();
        }
    }

    @Override
    public final void writeStartObject() throws IOException {
        _verifyValueWrite("start an object");
        if (_writeContext.inObject()) {
            if (_skipWithin == null) {
                if (_skipValue && isEnabled(JsonGenerator.Feature.IGNORE_UNKNOWN)) {
                    _skipWithin = _writeContext;
                } else {
                    _reportMappingError("CSV generator does not support Object values for properties");
                }
            }
        }
        _writeContext = _writeContext.createChildObjectContext();
    }

    @Override
    public final void writeEndObject() throws IOException {
        if (!_writeContext.inObject()) {
            _reportError("Current context not Object but " + _writeContext.typeDesc());
        }
        _writeContext = _writeContext.getParent();
        if (_skipWithin != null) {
            if (_writeContext == _skipWithin) {
                _skipWithin = null;
            }
            return;
        }
        finishRow();
    }

    /*
    /**********************************************************
    /* Output method implementations, textual
    /**********************************************************
     */
    @Override
    public void writeString(String text) throws IOException {
        if (text == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write String value");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(text);
            } else {
                _writer.write(_columnIndex(), text);
            }
        }
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws IOException {
        _verifyValueWrite("write String value");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(new String(text, offset, len));
            } else {
                _writer.write(_columnIndex(), text, offset, len);
            }
        }
    }

    @Override
    public final void writeString(SerializableString sstr) throws IOException {
        _verifyValueWrite("write String value");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(sstr.getValue());
            } else {
                _writer.write(_columnIndex(), sstr.getValue());
            }
        }
    }

    @Override
    public void writeRawUTF8String(byte[] text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeUTF8String(byte[] text, int offset, int len) throws IOException {
        writeString(new String(text, offset, len, "UTF-8"));
    }

    /*
    /**********************************************************
    /* Output method implementations, unprocessed ("raw")
    /**********************************************************
     */
    @Override
    public void writeRaw(String text) throws IOException {
        _writer.writeRaw(text);
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException {
        _writer.writeRaw(text, offset, len);
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException {
        _writer.writeRaw(text, offset, len);
    }

    @Override
    public void writeRaw(char c) throws IOException {
        _writer.writeRaw(c);
    }

    @Override
    public void writeRawValue(String text) throws IOException {
        _verifyValueWrite("write Raw value");
        if (!_skipValue) {
            _writer.writeNonEscaped(_columnIndex(), text);
        }
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws IOException {
        _verifyValueWrite("write Raw value");
        if (!_skipValue) {
            _writer.writeNonEscaped(_columnIndex(), text.substring(offset, offset + len));
        }
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws IOException {
        _verifyValueWrite("write Raw value");
        if (!_skipValue) {
            _writer.writeNonEscaped(_columnIndex(), new String(text, offset, len));
        }
    }

    /*
    /**********************************************************
    /* Output method implementations, base64-encoded binary
    /**********************************************************
     */
    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws IOException, JsonGenerationException {
        if (data == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write Binary value");
        if (!_skipValue) {
            if (offset > 0 || (offset + len) != data.length) {
                data = Arrays.copyOfRange(data, offset, offset + len);
            }
            String encoded = b64variant.encode(data);
            if (!_arraySeparator.isEmpty()) {
                _addToArray(encoded);
            } else {
                _writer.write(_columnIndex(), encoded);
            }
        }
    }

    /*
    /**********************************************************
    /* Output method implementations, primitive
    /**********************************************************
     */
    @Override
    public void writeBoolean(boolean state) throws IOException {
        _verifyValueWrite("write boolean value");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(state ? "true" : "false");
            } else {
                _writer.write(_columnIndex(), state);
            }
        }
    }

    @Override
    public void writeNull() throws IOException {
        _verifyValueWrite("write null value");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(_schema.getNullValueOrEmpty());
            } else {
                if (_writeContext.inObject()) {
                    _writer.writeNull(_columnIndex());
                } else {
                    if (_writeContext.inArray()) {
                        if (!_writeContext.getParent().inRoot()) {
                            _writer.writeNull(_columnIndex());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void writeNumber(int v) throws IOException {
        _verifyValueWrite("write number");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(String.valueOf(v));
            } else {
                _writer.write(_columnIndex(), v);
            }
        }
    }

    @Override
    public void writeNumber(long v) throws IOException {
        if (v <= MAX_INT_AS_LONG && v >= MIN_INT_AS_LONG) {
            writeNumber((int) v);
            return;
        }
        _verifyValueWrite("write number");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(String.valueOf(v));
            } else {
                _writer.write(_columnIndex(), v);
            }
        }
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException {
        if (v == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(String.valueOf(v));
            } else {
                _writer.write(_columnIndex(), v.toString());
            }
        }
    }

    @Override
    public void writeNumber(double v) throws IOException {
        _verifyValueWrite("write number");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(String.valueOf(v));
            } else {
                _writer.write(_columnIndex(), v);
            }
        }
    }

    @Override
    public void writeNumber(float v) throws IOException {
        _verifyValueWrite("write number");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(String.valueOf(v));
            } else {
                _writer.write(_columnIndex(), v);
            }
        }
    }

    @Override
    public void writeNumber(BigDecimal v) throws IOException {
        if (v == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        if (!_skipValue) {
            String str = isEnabled(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN) ? v.toPlainString() : v.toString();
            if (!_arraySeparator.isEmpty()) {
                _addToArray(String.valueOf(v));
            } else {
                _writer.write(_columnIndex(), str);
            }
        }
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException {
        if (encodedValue == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write number");
        if (!_skipValue) {
            if (!_arraySeparator.isEmpty()) {
                _addToArray(encodedValue);
            } else {
                _writer.write(_columnIndex(), encodedValue);
            }
        }
    }

    /*
    /**********************************************************
    /* Overrides for field methods
    /**********************************************************
     */
    @Override
    public void writeOmittedField(String fieldName) throws IOException {
        CsvSchema.Column col = _schema.column(fieldName);
        if (col == null) {
        } else {
            if (_writeContext.writeFieldName(fieldName) == JsonWriteContext.STATUS_EXPECT_VALUE) {
                _reportError("Can not skip a field, expecting a value");
            }
            _nextColumnByName = col.getIndex();
            _verifyValueWrite("skip positional value due to filtering");
            _writer.write(_columnIndex(), "");
        }
    }

    /*
    /**********************************************************
    /* Implementations for methods from base class
    /**********************************************************
     */
    @Override
    protected final void _verifyValueWrite(String typeMsg) throws IOException {
        int status = _writeContext.writeValue();
        if (status == JsonWriteContext.STATUS_EXPECT_NAME) {
            _reportError("Can not " + typeMsg + ", expecting field name");
        }
        if (_handleFirstLine) {
            _handleFirstLine();
        }
    }

    @Override
    protected void _releaseBuffers() {
        _writer._releaseBuffers();
    }

    /*
    /**********************************************************
    /* Internal methods, error reporting
    /**********************************************************
     */
    /**
     * Method called when there is a problem related to mapping data
     * (compared to a low-level generation); if so, should be surfaced
     * as 
     *
     * @since 2.7
     */
    protected void _reportMappingError(String msg) throws JsonProcessingException {
        throw JsonMappingException.from(this, msg);
    }

    /*
    /**********************************************************
    /* Internal methods, other
    /**********************************************************
     */
    protected final int _columnIndex() {
        int ix = _nextColumnByName;
        if (ix < 0) {
            ix = _writer.nextColumnIndex();
        }
        return ix;
    }

    /**
     * Method called when the current row is complete; typically
     * will flush possibly buffered column values, append linefeed
     * and reset state appropriately.
     */
    protected void finishRow() throws IOException {
        _writer.endRow();
        _nextColumnByName = -1;
    }

    protected void _handleFirstLine() throws IOException {
        _handleFirstLine = false;
        if (_schema.usesHeader()) {
            int count = _schema.size();
            if (count == 0) {
                _reportMappingError("Schema specified that header line is to be written; but contains no column names");
            }
            for (CsvSchema.Column column : _schema) {
                _writer.writeColumnName(column.getName());
            }
            _writer.endRow();
        }
    }

    protected void _addToArray(String value) {
        if (_arrayElements > 0) {
            _arrayContents.append(_arraySeparator);
        }
        ++_arrayElements;
        _arrayContents.append(value);
    }

    protected void _addToArray(char[] value) {
        if (_arrayElements > 0) {
            _arrayContents.append(_arraySeparator);
        }
        ++_arrayElements;
        _arrayContents.append(value);
    }
}

