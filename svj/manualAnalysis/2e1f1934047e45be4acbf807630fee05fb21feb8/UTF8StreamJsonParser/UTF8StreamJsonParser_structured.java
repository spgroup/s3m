package com.fasterxml.jackson.core.json;

import java.io.*;
import java.util.Arrays;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.core.util.*;
import static com.fasterxml.jackson.core.JsonTokenId.*;

/**
 * This is a concrete implementation of {@link JsonParser}, which is
 * based on a {@link java.io.InputStream} as the input source.
 */
public class UTF8StreamJsonParser extends ParserBase {

    static final byte BYTE_LF = (byte) '\n';

    // This is the main input-code lookup table, fetched eagerly
    private static final int[] _icUTF8 = CharTypes.getInputCodeUtf8();

    // Latin1 encoding is not supported, but we do use 8-bit subset for
    // pre-processing task, to simplify first pass, keep it fast.
    protected static final int[] _icLatin1 = CharTypes.getInputCodeLatin1();

    protected static final int FEAT_MASK_TRAILING_COMMA = Feature.ALLOW_TRAILING_COMMA.getMask();

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    /**
     * Codec used for data binding when (if) requested; typically full
     * <code>ObjectMapper</code>, but that abstract is not part of core
     * package.
     */
    protected ObjectCodec _objectCodec;

    /**
     * Symbol table that contains field names encountered so far
     */
    protected final ByteQuadsCanonicalizer _symbols;

    /*
    /**********************************************************
    /* Parsing state
    /**********************************************************
     */
    /**
     * Temporary buffer used for name parsing.
     */
    protected int[] _quadBuffer = new int[16];

    /**
     * Flag that indicates that the current token has not yet
     * been fully processed, and needs to be finished for
     * some access (or skipped to obtain the next token)
     */
    protected boolean _tokenIncomplete;

    /**
     * Temporary storage for partially parsed name bytes.
     */
    private int _quad1;

    /**
     * Value of {@link #_inputPtr} at the time when the first character of
     * name token was read. Used for calculating token location when requested;
     * combined with {@link #_currInputProcessed}, may be updated appropriately
     * as needed.
     *
     * @since 2.7
     */
    protected int _nameStartOffset;

    /**
     * @since 2.7
     */
    protected int _nameStartRow;

    /**
     * @since 2.7
     */
    protected int _nameStartCol;

    /*
    /**********************************************************
    /* Input buffering (from former 'StreamBasedParserBase')
    /**********************************************************
     */
    protected InputStream _inputStream;

    /*
    /**********************************************************
    /* Current input data
    /**********************************************************
     */
    /**
     * Current buffer from which data is read; generally data is read into
     * buffer from input source, but in some cases pre-loaded buffer
     * is handed to the parser.
     */
    protected byte[] _inputBuffer;

    /**
     * Flag that indicates whether the input buffer is recycable (and
     * needs to be returned to recycler once we are done) or not.
     *<p>
     * If it is not, it also means that parser can NOT modify underlying
     * buffer.
     */
    protected boolean _bufferRecyclable;

    public UTF8StreamJsonParser(IOContext ctxt, int features, InputStream in, ObjectCodec codec, ByteQuadsCanonicalizer sym, byte[] inputBuffer, int start, int end, boolean bufferRecyclable) {
        super(ctxt, features);
        _inputStream = in;
        _objectCodec = codec;
        _symbols = sym;
        _inputBuffer = inputBuffer;
        _inputPtr = start;
        _inputEnd = end;
        _currInputRowStart = start;
        _currInputProcessed = -start;
        _bufferRecyclable = bufferRecyclable;
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    /*
    /**********************************************************
    /* Overrides for life-cycle
    /**********************************************************
     */
    @Override
    public int releaseBuffered(OutputStream out) throws IOException {
        int count = _inputEnd - _inputPtr;
        if (count < 1) {
            return 0;
        }
        int origPtr = _inputPtr;
        out.write(_inputBuffer, origPtr, count);
        return count;
    }

    @Override
    public Object getInputSource() {
        return _inputStream;
    }

    /*
    /**********************************************************
    /* Overrides, low-level reading
    /**********************************************************
     */
    protected final boolean _loadMore() throws IOException {
        final int bufSize = _inputEnd;
        _currInputProcessed += _inputEnd;
        _currInputRowStart -= _inputEnd;
        _nameStartOffset -= bufSize;
        if (_inputStream != null) {
            int space = _inputBuffer.length;
            if (space == 0) {
                return false;
            }
            int count = _inputStream.read(_inputBuffer, 0, space);
            if (count > 0) {
                _inputPtr = 0;
                _inputEnd = count;
                return true;
            }
            _closeInput();
            if (count == 0) {
                throw new IOException("InputStream.read() returned 0 characters when trying to read " + _inputBuffer.length + " bytes");
            }
        }
        return false;
    }

    /**
     * Helper method that will try to load at least specified number bytes in
     * input buffer, possible moving existing data around if necessary
     */
    protected final boolean _loadToHaveAtLeast(int minAvailable) throws IOException {
        if (_inputStream == null) {
            return false;
        }
        int amount = _inputEnd - _inputPtr;
        if (amount > 0 && _inputPtr > 0) {
            final int ptr = _inputPtr;
            _currInputProcessed += ptr;
            _currInputRowStart -= ptr;
            _nameStartOffset -= ptr;
            System.arraycopy(_inputBuffer, ptr, _inputBuffer, 0, amount);
            _inputEnd = amount;
        } else {
            _inputEnd = 0;
        }
        _inputPtr = 0;
        while (_inputEnd < minAvailable) {
            int count = _inputStream.read(_inputBuffer, _inputEnd, _inputBuffer.length - _inputEnd);
            if (count < 1) {
                _closeInput();
                if (count == 0) {
                    throw new IOException("InputStream.read() returned 0 characters when trying to read " + amount + " bytes");
                }
                return false;
            }
            _inputEnd += count;
        }
        return true;
    }

    @Override
    protected void _closeInput() throws IOException {
        if (_inputStream != null) {
            if (_ioContext.isResourceManaged() || isEnabled(Feature.AUTO_CLOSE_SOURCE)) {
                _inputStream.close();
            }
            _inputStream = null;
        }
    }

    /**
     * Method called to release internal buffers owned by the base
     * reader. This may be called along with {@link #_closeInput} (for
     * example, when explicitly closing this reader instance), or
     * separately (if need be).
     */
    @Override
    protected void _releaseBuffers() throws IOException {
        super._releaseBuffers();
        _symbols.release();
        if (_bufferRecyclable) {
            byte[] buf = _inputBuffer;
            if (buf != null) {
                _inputBuffer = ByteArrayBuilder.NO_BYTES;
                _ioContext.releaseReadIOBuffer(buf);
            }
        }
    }

    /*
    /**********************************************************
    /* Public API, data access
    /**********************************************************
     */
    @Override
    public String getText() throws IOException {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                return _finishAndReturnString();
            }
            return _textBuffer.contentsAsString();
        }
        return _getText2(_currToken);
    }

    @Override
    public int getText(Writer writer) throws IOException {
        JsonToken t = _currToken;
        if (t == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                _finishString();
            }
            return _textBuffer.contentsToWriter(writer);
        }
        if (t == JsonToken.FIELD_NAME) {
            String n = _parsingContext.getCurrentName();
            writer.write(n);
            return n.length();
        }
        if (t != null) {
            if (t.isNumeric()) {
                return _textBuffer.contentsToWriter(writer);
            }
            char[] ch = t.asCharArray();
            writer.write(ch);
            return ch.length;
        }
        return 0;
    }

    // // // Let's override default impls for improved performance
    // @since 2.1
    @Override
    public String getValueAsString() throws IOException {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                return _finishAndReturnString();
            }
            return _textBuffer.contentsAsString();
        }
        if (_currToken == JsonToken.FIELD_NAME) {
            return getCurrentName();
        }
        return super.getValueAsString(null);
    }

    // @since 2.1
    @Override
    public String getValueAsString(String defValue) throws IOException {
        if (_currToken == JsonToken.VALUE_STRING) {
            if (_tokenIncomplete) {
                _tokenIncomplete = false;
                return _finishAndReturnString();
            }
            return _textBuffer.contentsAsString();
        }
        if (_currToken == JsonToken.FIELD_NAME) {
            return getCurrentName();
        }
        return super.getValueAsString(defValue);
    }

    // since 2.6
    @Override
    public int getValueAsInt() throws IOException {
        JsonToken t = _currToken;
        if ((t == JsonToken.VALUE_NUMBER_INT) || (t == JsonToken.VALUE_NUMBER_FLOAT)) {
            if ((_numTypesValid & NR_INT) == 0) {
                if (_numTypesValid == NR_UNKNOWN) {
                    return _parseIntValue();
                }
                if ((_numTypesValid & NR_INT) == 0) {
                    convertNumberToInt();
                }
            }
            return _numberInt;
        }
        return super.getValueAsInt(0);
    }

    // since 2.6
    @Override
    public int getValueAsInt(int defValue) throws IOException {
        JsonToken t = _currToken;
        if ((t == JsonToken.VALUE_NUMBER_INT) || (t == JsonToken.VALUE_NUMBER_FLOAT)) {
            if ((_numTypesValid & NR_INT) == 0) {
                if (_numTypesValid == NR_UNKNOWN) {
                    return _parseIntValue();
                }
                if ((_numTypesValid & NR_INT) == 0) {
                    convertNumberToInt();
                }
            }
            return _numberInt;
        }
        return super.getValueAsInt(defValue);
    }

    protected final String _getText2(JsonToken t) {
        if (t == null) {
            return null;
        }
        switch(t.id()) {
            case ID_FIELD_NAME:
                return _parsingContext.getCurrentName();
            case ID_STRING:
            case ID_NUMBER_INT:
            case ID_NUMBER_FLOAT:
                return _textBuffer.contentsAsString();
            default:
                return t.asString();
        }
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        if (_currToken != null) {
            switch(_currToken.id()) {
                case ID_FIELD_NAME:
                    if (!_nameCopied) {
                        String name = _parsingContext.getCurrentName();
                        int nameLen = name.length();
                        if (_nameCopyBuffer == null) {
                            _nameCopyBuffer = _ioContext.allocNameCopyBuffer(nameLen);
                        } else {
                            if (_nameCopyBuffer.length < nameLen) {
                                _nameCopyBuffer = new char[nameLen];
                            }
                        }
                        name.getChars(0, nameLen, _nameCopyBuffer, 0);
                        _nameCopied = true;
                    }
                    return _nameCopyBuffer;
                case ID_STRING:
                    if (_tokenIncomplete) {
                        _tokenIncomplete = false;
                        _finishString();
                    }
                case ID_NUMBER_INT:
                case ID_NUMBER_FLOAT:
                    return _textBuffer.getTextBuffer();
                default:
                    return _currToken.asCharArray();
            }
        }
        return null;
    }

    @Override
    public int getTextLength() throws IOException {
        if (_currToken != null) {
            switch(_currToken.id()) {
                case ID_FIELD_NAME:
                    return _parsingContext.getCurrentName().length();
                case ID_STRING:
                    if (_tokenIncomplete) {
                        _tokenIncomplete = false;
                        _finishString();
                    }
                case ID_NUMBER_INT:
                case ID_NUMBER_FLOAT:
                    return _textBuffer.size();
                default:
                    return _currToken.asCharArray().length;
            }
        }
        return 0;
    }

    @Override
    public int getTextOffset() throws IOException {
        if (_currToken != null) {
            switch(_currToken.id()) {
                case ID_FIELD_NAME:
                    return 0;
                case ID_STRING:
                    if (_tokenIncomplete) {
                        _tokenIncomplete = false;
                        _finishString();
                    }
                case ID_NUMBER_INT:
                case ID_NUMBER_FLOAT:
                    return _textBuffer.getTextOffset();
                default:
            }
        }
        return 0;
    }

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException {
        if (_currToken != JsonToken.VALUE_STRING && (_currToken != JsonToken.VALUE_EMBEDDED_OBJECT || _binaryValue == null)) {
            _reportError("Current token (" + _currToken + ") not VALUE_STRING or VALUE_EMBEDDED_OBJECT, can not access as binary");
        }
        if (_tokenIncomplete) {
            try {
                _binaryValue = _decodeBase64(b64variant);
            } catch (IllegalArgumentException iae) {
                throw _constructError("Failed to decode VALUE_STRING as base64 (" + b64variant + "): " + iae.getMessage());
            }
            _tokenIncomplete = false;
        } else {
            if (_binaryValue == null) {
                @SuppressWarnings(value = { "resource" }) ByteArrayBuilder builder = _getByteArrayBuilder();
                _decodeBase64(getText(), builder, b64variant);
                _binaryValue = builder.toByteArray();
            }
        }
        return _binaryValue;
    }

    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out) throws IOException {
        if (!_tokenIncomplete || _currToken != JsonToken.VALUE_STRING) {
            byte[] b = getBinaryValue(b64variant);
            out.write(b);
            return b.length;
        }
        byte[] buf = _ioContext.allocBase64Buffer();
        try {
            return _readBinary(b64variant, out, buf);
        } finally {
            _ioContext.releaseBase64Buffer(buf);
        }
    }

    protected int _readBinary(Base64Variant b64variant, OutputStream out, byte[] buffer) throws IOException {
        int outputPtr = 0;
        final int outputEnd = buffer.length - 3;
        int outputCount = 0;
        while (true) {
            int ch;
            do {
                if (_inputPtr >= _inputEnd) {
                    _loadMoreGuaranteed();
                }
                ch = (int) _inputBuffer[_inputPtr++] & 0xFF;
            } while (ch <= INT_SPACE);
            int bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (ch == INT_QUOTE) {
                    break;
                }
                bits = _decodeBase64Escape(b64variant, ch, 0);
                if (bits < 0) {
                    continue;
                }
            }
            if (outputPtr > outputEnd) {
                outputCount += outputPtr;
                out.write(buffer, 0, outputPtr);
                outputPtr = 0;
            }
            int decodedData = bits;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                bits = _decodeBase64Escape(b64variant, ch, 1);
            }
            decodedData = (decodedData << 6) | bits;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == '\"' && !b64variant.usesPadding()) {
                        decodedData >>= 4;
                        buffer[outputPtr++] = (byte) decodedData;
                        break;
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 2);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    if (_inputPtr >= _inputEnd) {
                        _loadMoreGuaranteed();
                    }
                    ch = _inputBuffer[_inputPtr++] & 0xFF;
                    if (!b64variant.usesPaddingChar(ch)) {
                        throw reportInvalidBase64Char(b64variant, ch, 3, "expected padding character \'" + b64variant.getPaddingChar() + "\'");
                    }
                    decodedData >>= 4;
                    buffer[outputPtr++] = (byte) decodedData;
                    continue;
                }
            }
            decodedData = (decodedData << 6) | bits;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == '\"' && !b64variant.usesPadding()) {
                        decodedData >>= 2;
                        buffer[outputPtr++] = (byte) (decodedData >> 8);
                        buffer[outputPtr++] = (byte) decodedData;
                        break;
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 3);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    decodedData >>= 2;
                    buffer[outputPtr++] = (byte) (decodedData >> 8);
                    buffer[outputPtr++] = (byte) decodedData;
                    continue;
                }
            }
            decodedData = (decodedData << 6) | bits;
            buffer[outputPtr++] = (byte) (decodedData >> 16);
            buffer[outputPtr++] = (byte) (decodedData >> 8);
            buffer[outputPtr++] = (byte) decodedData;
        }
        _tokenIncomplete = false;
        if (outputPtr > 0) {
            outputCount += outputPtr;
            out.write(buffer, 0, outputPtr);
        }
        return outputCount;
    }

    /*
    /**********************************************************
    /* Public API, traversal, basic
    /**********************************************************
     */
    /**
     * @return Next token from the stream, if any found, or null
     *   to indicate end-of-input
     */
    @Override
    public JsonToken nextToken() throws IOException {
        if (_currToken == JsonToken.FIELD_NAME) {
            return _nextAfterName();
        }
        _numTypesValid = NR_UNKNOWN;
        if (_tokenIncomplete) {
            _skipString();
        }
        int i = _skipWSOrEnd();
        if (i < 0) {
            close();
            return (_currToken = null);
        }
        _binaryValue = null;
        if (i == INT_RBRACKET) {
            _closeArrayScope();
            return (_currToken = JsonToken.END_ARRAY);
        }
        if (i == INT_RCURLY) {
            _closeObjectScope();
            return (_currToken = JsonToken.END_OBJECT);
        }
        if (_parsingContext.expectComma()) {
            if (i != INT_COMMA) {
                _reportUnexpectedChar(i, "was expecting comma to separate " + _parsingContext.typeDesc() + " entries");
            }
            i = _skipWS();
            if ((_features & FEAT_MASK_TRAILING_COMMA) != 0) {
                if ((i == INT_RBRACKET) || (i == INT_RCURLY)) {
                    return _closeScope(i);
                }
            }
        }
        if (!_parsingContext.inObject()) {
            _updateLocation();
            return _nextTokenNotInObject(i);
        }
        _updateNameLocation();
        String n = _parseName(i);
        _parsingContext.setCurrentName(n);
        _currToken = JsonToken.FIELD_NAME;
        i = _skipColon();
        _updateLocation();
        if (i == INT_QUOTE) {
            _tokenIncomplete = true;
            _nextToken = JsonToken.VALUE_STRING;
            return _currToken;
        }
        JsonToken t;
        switch(i) {
            case '-':
                t = _parseNegNumber();
                break;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                t = _parsePosNumber(i);
                break;
            case 'f':
                _matchToken("false", 1);
                t = JsonToken.VALUE_FALSE;
                break;
            case 'n':
                _matchToken("null", 1);
                t = JsonToken.VALUE_NULL;
                break;
            case 't':
                _matchToken("true", 1);
                t = JsonToken.VALUE_TRUE;
                break;
            case '[':
                t = JsonToken.START_ARRAY;
                break;
            case '{':
                t = JsonToken.START_OBJECT;
                break;
            default:
                t = _handleUnexpectedValue(i);
        }
        _nextToken = t;
        return _currToken;
    }

    private final JsonToken _nextTokenNotInObject(int i) throws IOException {
        if (i == INT_QUOTE) {
            _tokenIncomplete = true;
            return (_currToken = JsonToken.VALUE_STRING);
        }
        switch(i) {
            case '[':
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
                return (_currToken = JsonToken.START_ARRAY);
            case '{':
                _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
                return (_currToken = JsonToken.START_OBJECT);
            case 't':
                _matchToken("true", 1);
                return (_currToken = JsonToken.VALUE_TRUE);
            case 'f':
                _matchToken("false", 1);
                return (_currToken = JsonToken.VALUE_FALSE);
            case 'n':
                _matchToken("null", 1);
                return (_currToken = JsonToken.VALUE_NULL);
            case '-':
                return (_currToken = _parseNegNumber());
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return (_currToken = _parsePosNumber(i));
        }
        return (_currToken = _handleUnexpectedValue(i));
    }

    private final JsonToken _nextAfterName() {
        _nameCopied = false;
        JsonToken t = _nextToken;
        _nextToken = null;
        if (t == JsonToken.START_ARRAY) {
            _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
        } else {
            if (t == JsonToken.START_OBJECT) {
                _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
            }
        }
        return (_currToken = t);
    }

    @Override
    public void finishToken() throws IOException {
        if (_tokenIncomplete) {
            _tokenIncomplete = false;
            _finishString();
        }
    }

    /*
    /**********************************************************
    /* Public API, traversal, nextXxxValue/nextFieldName
    /**********************************************************
     */
    @Override
    public boolean nextFieldName(SerializableString str) throws IOException {
        _numTypesValid = NR_UNKNOWN;
        if (_currToken == JsonToken.FIELD_NAME) {
            _nextAfterName();
            return false;
        }
        if (_tokenIncomplete) {
            _skipString();
        }
        int i = _skipWSOrEnd();
        if (i < 0) {
            close();
            _currToken = null;
            return false;
        }
        _binaryValue = null;
        if (i == INT_RBRACKET) {
            _closeArrayScope();
            _currToken = JsonToken.END_ARRAY;
            return false;
        }
        if (i == INT_RCURLY) {
            _closeObjectScope();
            _currToken = JsonToken.END_OBJECT;
            return false;
        }
        if (_parsingContext.expectComma()) {
            if (i != INT_COMMA) {
                _reportUnexpectedChar(i, "was expecting comma to separate " + _parsingContext.typeDesc() + " entries");
            }
            i = _skipWS();
            if ((_features & FEAT_MASK_TRAILING_COMMA) != 0) {
                if ((i == INT_RBRACKET) || (i == INT_RCURLY)) {
                    _closeScope(i);
                    return false;
                }
            }
        }
        if (!_parsingContext.inObject()) {
            _updateLocation();
            _nextTokenNotInObject(i);
            return false;
        }
        _updateNameLocation();
        if (i == INT_QUOTE) {
            byte[] nameBytes = str.asQuotedUTF8();
            final int len = nameBytes.length;
            if ((_inputPtr + len + 4) < _inputEnd) {
                final int end = _inputPtr + len;
                if (_inputBuffer[end] == INT_QUOTE) {
                    int offset = 0;
                    int ptr = _inputPtr;
                    while (true) {
                        if (ptr == end) {
                            _parsingContext.setCurrentName(str.getValue());
                            i = _skipColonFast(ptr + 1);
                            _isNextTokenNameYes(i);
                            return true;
                        }
                        if (nameBytes[offset] != _inputBuffer[ptr]) {
                            break;
                        }
                        ++offset;
                        ++ptr;
                    }
                }
            }
        }
        return _isNextTokenNameMaybe(i, str);
    }

    @Override
    public String nextFieldName() throws IOException {
        _numTypesValid = NR_UNKNOWN;
        if (_currToken == JsonToken.FIELD_NAME) {
            _nextAfterName();
            return null;
        }
        if (_tokenIncomplete) {
            _skipString();
        }
        int i = _skipWSOrEnd();
        if (i < 0) {
            close();
            _currToken = null;
            return null;
        }
        _binaryValue = null;
        if (i == INT_RBRACKET) {
            _closeArrayScope();
            _currToken = JsonToken.END_ARRAY;
            return null;
        }
        if (i == INT_RCURLY) {
            _closeObjectScope();
            _currToken = JsonToken.END_OBJECT;
            return null;
        }
        if (_parsingContext.expectComma()) {
            if (i != INT_COMMA) {
                _reportUnexpectedChar(i, "was expecting comma to separate " + _parsingContext.typeDesc() + " entries");
            }
            i = _skipWS();
            if ((_features & FEAT_MASK_TRAILING_COMMA) != 0) {
                if ((i == INT_RBRACKET) || (i == INT_RCURLY)) {
                    _closeScope(i);
                    return null;
                }
            }
        }
        if (!_parsingContext.inObject()) {
            _updateLocation();
            _nextTokenNotInObject(i);
            return null;
        }
        _updateNameLocation();
        final String nameStr = _parseName(i);
        _parsingContext.setCurrentName(nameStr);
        _currToken = JsonToken.FIELD_NAME;
        i = _skipColon();
        _updateLocation();
        if (i == INT_QUOTE) {
            _tokenIncomplete = true;
            _nextToken = JsonToken.VALUE_STRING;
            return nameStr;
        }
        JsonToken t;
        switch(i) {
            case '-':
                t = _parseNegNumber();
                break;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                t = _parsePosNumber(i);
                break;
            case 'f':
                _matchToken("false", 1);
                t = JsonToken.VALUE_FALSE;
                break;
            case 'n':
                _matchToken("null", 1);
                t = JsonToken.VALUE_NULL;
                break;
            case 't':
                _matchToken("true", 1);
                t = JsonToken.VALUE_TRUE;
                break;
            case '[':
                t = JsonToken.START_ARRAY;
                break;
            case '{':
                t = JsonToken.START_OBJECT;
                break;
            default:
                t = _handleUnexpectedValue(i);
        }
        _nextToken = t;
        return nameStr;
    }

    // Variant called when we know there's at least 4 more bytes available
    private final int _skipColonFast(int ptr) throws IOException {
        int i = _inputBuffer[ptr++];
        if (i == INT_COLON) {
            i = _inputBuffer[ptr++];
            if (i > INT_SPACE) {
                if (i != INT_SLASH && i != INT_HASH) {
                    _inputPtr = ptr;
                    return i;
                }
            } else {
                if (i == INT_SPACE || i == INT_TAB) {
                    i = (int) _inputBuffer[ptr++];
                    if (i > INT_SPACE) {
                        if (i != INT_SLASH && i != INT_HASH) {
                            _inputPtr = ptr;
                            return i;
                        }
                    }
                }
            }
            _inputPtr = ptr - 1;
            return _skipColon2(true);
        }
        if (i == INT_SPACE || i == INT_TAB) {
            i = _inputBuffer[ptr++];
        }
        if (i == INT_COLON) {
            i = _inputBuffer[ptr++];
            if (i > INT_SPACE) {
                if (i != INT_SLASH && i != INT_HASH) {
                    _inputPtr = ptr;
                    return i;
                }
            } else {
                if (i == INT_SPACE || i == INT_TAB) {
                    i = (int) _inputBuffer[ptr++];
                    if (i > INT_SPACE) {
                        if (i != INT_SLASH && i != INT_HASH) {
                            _inputPtr = ptr;
                            return i;
                        }
                    }
                }
            }
            _inputPtr = ptr - 1;
            return _skipColon2(true);
        }
        _inputPtr = ptr - 1;
        return _skipColon2(false);
    }

    private final void _isNextTokenNameYes(int i) throws IOException {
        _currToken = JsonToken.FIELD_NAME;
        _updateLocation();
        switch(i) {
            case '\"':
                _tokenIncomplete = true;
                _nextToken = JsonToken.VALUE_STRING;
                return;
            case '[':
                _nextToken = JsonToken.START_ARRAY;
                return;
            case '{':
                _nextToken = JsonToken.START_OBJECT;
                return;
            case 't':
                _matchToken("true", 1);
                _nextToken = JsonToken.VALUE_TRUE;
                return;
            case 'f':
                _matchToken("false", 1);
                _nextToken = JsonToken.VALUE_FALSE;
                return;
            case 'n':
                _matchToken("null", 1);
                _nextToken = JsonToken.VALUE_NULL;
                return;
            case '-':
                _nextToken = _parseNegNumber();
                return;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                _nextToken = _parsePosNumber(i);
                return;
        }
        _nextToken = _handleUnexpectedValue(i);
    }

    private final boolean _isNextTokenNameMaybe(int i, SerializableString str) throws IOException {
        String n = _parseName(i);
        _parsingContext.setCurrentName(n);
        final boolean match = n.equals(str.getValue());
        _currToken = JsonToken.FIELD_NAME;
        i = _skipColon();
        _updateLocation();
        if (i == INT_QUOTE) {
            _tokenIncomplete = true;
            _nextToken = JsonToken.VALUE_STRING;
            return match;
        }
        JsonToken t;
        switch(i) {
            case '[':
                t = JsonToken.START_ARRAY;
                break;
            case '{':
                t = JsonToken.START_OBJECT;
                break;
            case 't':
                _matchToken("true", 1);
                t = JsonToken.VALUE_TRUE;
                break;
            case 'f':
                _matchToken("false", 1);
                t = JsonToken.VALUE_FALSE;
                break;
            case 'n':
                _matchToken("null", 1);
                t = JsonToken.VALUE_NULL;
                break;
            case '-':
                t = _parseNegNumber();
                break;
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                t = _parsePosNumber(i);
                break;
            default:
                t = _handleUnexpectedValue(i);
        }
        _nextToken = t;
        return match;
    }

    @Override
    public String nextTextValue() throws IOException {
        if (_currToken == JsonToken.FIELD_NAME) {
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_STRING) {
                if (_tokenIncomplete) {
                    _tokenIncomplete = false;
                    return _finishAndReturnString();
                }
                return _textBuffer.contentsAsString();
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else {
                if (t == JsonToken.START_OBJECT) {
                    _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
                }
            }
            return null;
        }
        return (nextToken() == JsonToken.VALUE_STRING) ? getText() : null;
    }

    @Override
    public int nextIntValue(int defaultValue) throws IOException {
        if (_currToken == JsonToken.FIELD_NAME) {
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_NUMBER_INT) {
                return getIntValue();
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else {
                if (t == JsonToken.START_OBJECT) {
                    _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
                }
            }
            return defaultValue;
        }
        return (nextToken() == JsonToken.VALUE_NUMBER_INT) ? getIntValue() : defaultValue;
    }

    @Override
    public long nextLongValue(long defaultValue) throws IOException {
        if (_currToken == JsonToken.FIELD_NAME) {
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_NUMBER_INT) {
                return getLongValue();
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else {
                if (t == JsonToken.START_OBJECT) {
                    _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
                }
            }
            return defaultValue;
        }
        return (nextToken() == JsonToken.VALUE_NUMBER_INT) ? getLongValue() : defaultValue;
    }

    @Override
    public Boolean nextBooleanValue() throws IOException {
        if (_currToken == JsonToken.FIELD_NAME) {
            _nameCopied = false;
            JsonToken t = _nextToken;
            _nextToken = null;
            _currToken = t;
            if (t == JsonToken.VALUE_TRUE) {
                return Boolean.TRUE;
            }
            if (t == JsonToken.VALUE_FALSE) {
                return Boolean.FALSE;
            }
            if (t == JsonToken.START_ARRAY) {
                _parsingContext = _parsingContext.createChildArrayContext(_tokenInputRow, _tokenInputCol);
            } else {
                if (t == JsonToken.START_OBJECT) {
                    _parsingContext = _parsingContext.createChildObjectContext(_tokenInputRow, _tokenInputCol);
                }
            }
            return null;
        }
        JsonToken t = nextToken();
        if (t == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        if (t == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        return null;
    }

    /*
    /**********************************************************
    /* Internal methods, number parsing
    /**********************************************************
     */
    /**
     * Initial parsing method for number values. It needs to be able
     * to parse enough input to be able to determine whether the
     * value is to be considered a simple integer value, or a more
     * generic decimal value: latter of which needs to be expressed
     * as a floating point number. The basic rule is that if the number
     * has no fractional or exponential part, it is an integer; otherwise
     * a floating point number.
     *<p>
     * Because much of input has to be processed in any case, no partial
     * parsing is done: all input text will be stored for further
     * processing. However, actual numeric value conversion will be
     * deferred, since it is usually the most complicated and costliest
     * part of processing.
     */
    protected JsonToken _parsePosNumber(int c) throws IOException {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        if (c == INT_0) {
            c = _verifyNoLeadingZeroes();
        }
        outBuf[0] = (char) c;
        int intLen = 1;
        int outPtr = 1;
        int end = _inputPtr + outBuf.length - 1;
        if (end > _inputEnd) {
            end = _inputEnd;
        }
        while (true) {
            if (_inputPtr >= end) {
                return _parseNumber2(outBuf, outPtr, false, intLen);
            }
            c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            if (c < INT_0 || c > INT_9) {
                break;
            }
            ++intLen;
            outBuf[outPtr++] = (char) c;
        }
        if (c == '.' || c == 'e' || c == 'E') {
            return _parseFloat(outBuf, outPtr, c, false, intLen);
        }
        --_inputPtr;
        _textBuffer.setCurrentLength(outPtr);
        if (_parsingContext.inRoot()) {
            _verifyRootSpace(c);
        }
        return resetInt(false, intLen);
    }

    protected JsonToken _parseNegNumber() throws IOException {
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        int outPtr = 0;
        outBuf[outPtr++] = '-';
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int c = (int) _inputBuffer[_inputPtr++] & 0xFF;
        if (c < INT_0 || c > INT_9) {
            return _handleInvalidNumberStart(c, true);
        }
        if (c == INT_0) {
            c = _verifyNoLeadingZeroes();
        }
        outBuf[outPtr++] = (char) c;
        int intLen = 1;
        int end = _inputPtr + outBuf.length - outPtr;
        if (end > _inputEnd) {
            end = _inputEnd;
        }
        while (true) {
            if (_inputPtr >= end) {
                return _parseNumber2(outBuf, outPtr, true, intLen);
            }
            c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            if (c < INT_0 || c > INT_9) {
                break;
            }
            ++intLen;
            outBuf[outPtr++] = (char) c;
        }
        if (c == '.' || c == 'e' || c == 'E') {
            return _parseFloat(outBuf, outPtr, c, true, intLen);
        }
        --_inputPtr;
        _textBuffer.setCurrentLength(outPtr);
        if (_parsingContext.inRoot()) {
            _verifyRootSpace(c);
        }
        return resetInt(true, intLen);
    }

    /**
     * Method called to handle parsing when input is split across buffer boundary
     * (or output is longer than segment used to store it)
     */
    private final JsonToken _parseNumber2(char[] outBuf, int outPtr, boolean negative, int intPartLength) throws IOException {
        while (true) {
            if (_inputPtr >= _inputEnd && !_loadMore()) {
                _textBuffer.setCurrentLength(outPtr);
                return resetInt(negative, intPartLength);
            }
            int c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            if (c > INT_9 || c < INT_0) {
                if (c == INT_PERIOD || c == INT_e || c == INT_E) {
                    return _parseFloat(outBuf, outPtr, c, negative, intPartLength);
                }
                break;
            }
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
            ++intPartLength;
        }
        --_inputPtr;
        _textBuffer.setCurrentLength(outPtr);
        if (_parsingContext.inRoot()) {
            _verifyRootSpace(_inputBuffer[_inputPtr++] & 0xFF);
        }
        return resetInt(negative, intPartLength);
    }

    /**
     * Method called when we have seen one zero, and want to ensure
     * it is not followed by another
     */
    private final int _verifyNoLeadingZeroes() throws IOException {
        if (_inputPtr >= _inputEnd && !_loadMore()) {
            return INT_0;
        }
        int ch = _inputBuffer[_inputPtr] & 0xFF;
        if (ch < INT_0 || ch > INT_9) {
            return INT_0;
        }
        if (!isEnabled(Feature.ALLOW_NUMERIC_LEADING_ZEROS)) {
            reportInvalidNumber("Leading zeroes not allowed");
        }
        ++_inputPtr;
        if (ch == INT_0) {
            while (_inputPtr < _inputEnd || _loadMore()) {
                ch = _inputBuffer[_inputPtr] & 0xFF;
                if (ch < INT_0 || ch > INT_9) {
                    return INT_0;
                }
                ++_inputPtr;
                if (ch != INT_0) {
                    break;
                }
            }
        }
        return ch;
    }

    private final JsonToken _parseFloat(char[] outBuf, int outPtr, int c, boolean negative, int integerPartLength) throws IOException {
        int fractLen = 0;
        boolean eof = false;
        if (c == INT_PERIOD) {
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
            fract_loop: while (true) {
                if (_inputPtr >= _inputEnd && !_loadMore()) {
                    eof = true;
                    break fract_loop;
                }
                c = (int) _inputBuffer[_inputPtr++] & 0xFF;
                if (c < INT_0 || c > INT_9) {
                    break fract_loop;
                }
                ++fractLen;
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                outBuf[outPtr++] = (char) c;
            }
            if (fractLen == 0) {
                reportUnexpectedNumberChar(c, "Decimal point not followed by a digit");
            }
        }
        int expLen = 0;
        if (c == INT_e || c == INT_E) {
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            if (c == '-' || c == '+') {
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                outBuf[outPtr++] = (char) c;
                if (_inputPtr >= _inputEnd) {
                    _loadMoreGuaranteed();
                }
                c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            }
            exp_loop: while (c <= INT_9 && c >= INT_0) {
                ++expLen;
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                outBuf[outPtr++] = (char) c;
                if (_inputPtr >= _inputEnd && !_loadMore()) {
                    eof = true;
                    break exp_loop;
                }
                c = (int) _inputBuffer[_inputPtr++] & 0xFF;
            }
            if (expLen == 0) {
                reportUnexpectedNumberChar(c, "Exponent indicator not followed by a digit");
            }
        }
        if (!eof) {
            --_inputPtr;
            if (_parsingContext.inRoot()) {
                _verifyRootSpace(c);
            }
        }
        _textBuffer.setCurrentLength(outPtr);
        return resetFloat(negative, integerPartLength, fractLen, expLen);
    }

    /**
     * Method called to ensure that a root-value is followed by a space
     * token.
     *<p>
     * NOTE: caller MUST ensure there is at least one character available;
     * and that input pointer is AT given char (not past)
     */
    private final void _verifyRootSpace(int ch) throws IOException {
        ++_inputPtr;
        switch(ch) {
            case ' ':
            case '\t':
                return;
            case '\r':
                _skipCR();
                return;
            case '\n':
                ++_currInputRow;
                _currInputRowStart = _inputPtr;
                return;
        }
        _reportMissingRootWS(ch);
    }

    /*
    /**********************************************************
    /* Internal methods, secondary parsing
    /**********************************************************
     */
    protected final String _parseName(int i) throws IOException {
        if (i != INT_QUOTE) {
            return _handleOddName(i);
        }
        if ((_inputPtr + 13) > _inputEnd) {
            return slowParseName();
        }
        final byte[] input = _inputBuffer;
        final int[] codes = _icLatin1;
        int q = input[_inputPtr++] & 0xFF;
        if (codes[q] == 0) {
            i = input[_inputPtr++] & 0xFF;
            if (codes[i] == 0) {
                q = (q << 8) | i;
                i = input[_inputPtr++] & 0xFF;
                if (codes[i] == 0) {
                    q = (q << 8) | i;
                    i = input[_inputPtr++] & 0xFF;
                    if (codes[i] == 0) {
                        q = (q << 8) | i;
                        i = input[_inputPtr++] & 0xFF;
                        if (codes[i] == 0) {
                            _quad1 = q;
                            return parseMediumName(i);
                        }
                        if (i == INT_QUOTE) {
                            return findName(q, 4);
                        }
                        return parseName(q, i, 4);
                    }
                    if (i == INT_QUOTE) {
                        return findName(q, 3);
                    }
                    return parseName(q, i, 3);
                }
                if (i == INT_QUOTE) {
                    return findName(q, 2);
                }
                return parseName(q, i, 2);
            }
            if (i == INT_QUOTE) {
                return findName(q, 1);
            }
            return parseName(q, i, 1);
        }
        if (q == INT_QUOTE) {
            return "";
        }
        return parseName(0, q, 0);
    }

    protected final String parseMediumName(int q2) throws IOException {
        final byte[] input = _inputBuffer;
        final int[] codes = _icLatin1;
        int i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, 1);
            }
            return parseName(_quad1, q2, i, 1);
        }
        q2 = (q2 << 8) | i;
        i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, 2);
            }
            return parseName(_quad1, q2, i, 2);
        }
        q2 = (q2 << 8) | i;
        i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, 3);
            }
            return parseName(_quad1, q2, i, 3);
        }
        q2 = (q2 << 8) | i;
        i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, 4);
            }
            return parseName(_quad1, q2, i, 4);
        }
        return parseMediumName2(i, q2);
    }

    /**
     * @since 2.6
     */
    protected final String parseMediumName2(int q3, final int q2) throws IOException {
        final byte[] input = _inputBuffer;
        final int[] codes = _icLatin1;
        int i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, q3, 1);
            }
            return parseName(_quad1, q2, q3, i, 1);
        }
        q3 = (q3 << 8) | i;
        i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, q3, 2);
            }
            return parseName(_quad1, q2, q3, i, 2);
        }
        q3 = (q3 << 8) | i;
        i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, q3, 3);
            }
            return parseName(_quad1, q2, q3, i, 3);
        }
        q3 = (q3 << 8) | i;
        i = input[_inputPtr++] & 0xFF;
        if (codes[i] != 0) {
            if (i == INT_QUOTE) {
                return findName(_quad1, q2, q3, 4);
            }
            return parseName(_quad1, q2, q3, i, 4);
        }
        return parseLongName(i, q2, q3);
    }

    protected final String parseLongName(int q, final int q2, int q3) throws IOException {
        _quadBuffer[0] = _quad1;
        _quadBuffer[1] = q2;
        _quadBuffer[2] = q3;
        final byte[] input = _inputBuffer;
        final int[] codes = _icLatin1;
        int qlen = 3;
        while ((_inputPtr + 4) <= _inputEnd) {
            int i = input[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 1);
                }
                return parseEscapedName(_quadBuffer, qlen, q, i, 1);
            }
            q = (q << 8) | i;
            i = input[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 2);
                }
                return parseEscapedName(_quadBuffer, qlen, q, i, 2);
            }
            q = (q << 8) | i;
            i = input[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 3);
                }
                return parseEscapedName(_quadBuffer, qlen, q, i, 3);
            }
            q = (q << 8) | i;
            i = input[_inputPtr++] & 0xFF;
            if (codes[i] != 0) {
                if (i == INT_QUOTE) {
                    return findName(_quadBuffer, qlen, q, 4);
                }
                return parseEscapedName(_quadBuffer, qlen, q, i, 4);
            }
            if (qlen >= _quadBuffer.length) {
                _quadBuffer = growArrayBy(_quadBuffer, qlen);
            }
            _quadBuffer[qlen++] = q;
            q = i;
        }
        return parseEscapedName(_quadBuffer, qlen, 0, q, 0);
    }

    /**
     * Method called when not even first 8 bytes are guaranteed
     * to come consecutively. Happens rarely, so this is offlined;
     * plus we'll also do full checks for escaping etc.
     */
    protected String slowParseName() throws IOException {
        if (_inputPtr >= _inputEnd) {
            if (!_loadMore()) {
                _reportInvalidEOF(": was expecting closing \'\"\' for name", JsonToken.FIELD_NAME);
            }
        }
        int i = _inputBuffer[_inputPtr++] & 0xFF;
        if (i == INT_QUOTE) {
            return "";
        }
        return parseEscapedName(_quadBuffer, 0, 0, i, 0);
    }

    private final String parseName(int q1, int ch, int lastQuadBytes) throws IOException {
        return parseEscapedName(_quadBuffer, 0, q1, ch, lastQuadBytes);
    }

    private final String parseName(int q1, int q2, int ch, int lastQuadBytes) throws IOException {
        _quadBuffer[0] = q1;
        return parseEscapedName(_quadBuffer, 1, q2, ch, lastQuadBytes);
    }

    private final String parseName(int q1, int q2, int q3, int ch, int lastQuadBytes) throws IOException {
        _quadBuffer[0] = q1;
        _quadBuffer[1] = q2;
        return parseEscapedName(_quadBuffer, 2, q3, ch, lastQuadBytes);
    }

    /**
     * Slower parsing method which is generally branched to when
     * an escape sequence is detected (or alternatively for long
     * names, one crossing input buffer boundary).
     * Needs to be able to handle more exceptional cases, gets slower,
     * and hence is offlined to a separate method.
     */
    protected final String parseEscapedName(int[] quads, int qlen, int currQuad, int ch, int currQuadBytes) throws IOException {
        final int[] codes = _icLatin1;
        while (true) {
            if (codes[ch] != 0) {
                if (ch == INT_QUOTE) {
                    break;
                }
                if (ch != INT_BACKSLASH) {
                    _throwUnquotedSpace(ch, "name");
                } else {
                    ch = _decodeEscaped();
                }
                if (ch > 127) {
                    if (currQuadBytes >= 4) {
                        if (qlen >= quads.length) {
                            _quadBuffer = quads = growArrayBy(quads, quads.length);
                        }
                        quads[qlen++] = currQuad;
                        currQuad = 0;
                        currQuadBytes = 0;
                    }
                    if (ch < 0x800) {
                        currQuad = (currQuad << 8) | (0xc0 | (ch >> 6));
                        ++currQuadBytes;
                    } else {
                        currQuad = (currQuad << 8) | (0xe0 | (ch >> 12));
                        ++currQuadBytes;
                        if (currQuadBytes >= 4) {
                            if (qlen >= quads.length) {
                                _quadBuffer = quads = growArrayBy(quads, quads.length);
                            }
                            quads[qlen++] = currQuad;
                            currQuad = 0;
                            currQuadBytes = 0;
                        }
                        currQuad = (currQuad << 8) | (0x80 | ((ch >> 6) & 0x3f));
                        ++currQuadBytes;
                    }
                    ch = 0x80 | (ch & 0x3f);
                }
            }
            if (currQuadBytes < 4) {
                ++currQuadBytes;
                currQuad = (currQuad << 8) | ch;
            } else {
                if (qlen >= quads.length) {
                    _quadBuffer = quads = growArrayBy(quads, quads.length);
                }
                quads[qlen++] = currQuad;
                currQuad = ch;
                currQuadBytes = 1;
            }
            if (_inputPtr >= _inputEnd) {
                if (!_loadMore()) {
                    _reportInvalidEOF(" in field name", JsonToken.FIELD_NAME);
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
        }
        if (currQuadBytes > 0) {
            if (qlen >= quads.length) {
                _quadBuffer = quads = growArrayBy(quads, quads.length);
            }
            quads[qlen++] = pad(currQuad, currQuadBytes);
        }
        String name = _symbols.findName(quads, qlen);
        if (name == null) {
            name = addName(quads, qlen, currQuadBytes);
        }
        return name;
    }

    /**
     * Method called when we see non-white space character other
     * than double quote, when expecting a field name.
     * In standard mode will just throw an exception; but
     * in non-standard modes may be able to parse name.
     */
    protected String _handleOddName(int ch) throws IOException {
        if (ch == '\'' && isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
            return _parseAposName();
        }
        if (!isEnabled(Feature.ALLOW_UNQUOTED_FIELD_NAMES)) {
            char c = (char) _decodeCharForError(ch);
            _reportUnexpectedChar(c, "was expecting double-quote to start field name");
        }
        final int[] codes = CharTypes.getInputCodeUtf8JsNames();
        if (codes[ch] != 0) {
            _reportUnexpectedChar(ch, "was expecting either valid name character (for unquoted name) or double-quote (for quoted) to start field name");
        }
        int[] quads = _quadBuffer;
        int qlen = 0;
        int currQuad = 0;
        int currQuadBytes = 0;
        while (true) {
            if (currQuadBytes < 4) {
                ++currQuadBytes;
                currQuad = (currQuad << 8) | ch;
            } else {
                if (qlen >= quads.length) {
                    _quadBuffer = quads = growArrayBy(quads, quads.length);
                }
                quads[qlen++] = currQuad;
                currQuad = ch;
                currQuadBytes = 1;
            }
            if (_inputPtr >= _inputEnd) {
                if (!_loadMore()) {
                    _reportInvalidEOF(" in field name", JsonToken.FIELD_NAME);
                }
            }
            ch = _inputBuffer[_inputPtr] & 0xFF;
            if (codes[ch] != 0) {
                break;
            }
            ++_inputPtr;
        }
        if (currQuadBytes > 0) {
            if (qlen >= quads.length) {
                _quadBuffer = quads = growArrayBy(quads, quads.length);
            }
            quads[qlen++] = currQuad;
        }
        String name = _symbols.findName(quads, qlen);
        if (name == null) {
            name = addName(quads, qlen, currQuadBytes);
        }
        return name;
    }

    /* Parsing to support [JACKSON-173]. Plenty of duplicated code;
     * main reason being to try to avoid slowing down fast path
     * for valid JSON -- more alternatives, more code, generally
     * bit slower execution.
     */
    protected String _parseAposName() throws IOException {
        if (_inputPtr >= _inputEnd) {
            if (!_loadMore()) {
                _reportInvalidEOF(": was expecting closing \'\'\' for field name", JsonToken.FIELD_NAME);
            }
        }
        int ch = _inputBuffer[_inputPtr++] & 0xFF;
        if (ch == '\'') {
            return "";
        }
        int[] quads = _quadBuffer;
        int qlen = 0;
        int currQuad = 0;
        int currQuadBytes = 0;
        final int[] codes = _icLatin1;
        while (true) {
            if (ch == '\'') {
                break;
            }
            if (ch != '\"' && codes[ch] != 0) {
                if (ch != '\\') {
                    _throwUnquotedSpace(ch, "name");
                } else {
                    ch = _decodeEscaped();
                }
                if (ch > 127) {
                    if (currQuadBytes >= 4) {
                        if (qlen >= quads.length) {
                            _quadBuffer = quads = growArrayBy(quads, quads.length);
                        }
                        quads[qlen++] = currQuad;
                        currQuad = 0;
                        currQuadBytes = 0;
                    }
                    if (ch < 0x800) {
                        currQuad = (currQuad << 8) | (0xc0 | (ch >> 6));
                        ++currQuadBytes;
                    } else {
                        currQuad = (currQuad << 8) | (0xe0 | (ch >> 12));
                        ++currQuadBytes;
                        if (currQuadBytes >= 4) {
                            if (qlen >= quads.length) {
                                _quadBuffer = quads = growArrayBy(quads, quads.length);
                            }
                            quads[qlen++] = currQuad;
                            currQuad = 0;
                            currQuadBytes = 0;
                        }
                        currQuad = (currQuad << 8) | (0x80 | ((ch >> 6) & 0x3f));
                        ++currQuadBytes;
                    }
                    ch = 0x80 | (ch & 0x3f);
                }
            }
            if (currQuadBytes < 4) {
                ++currQuadBytes;
                currQuad = (currQuad << 8) | ch;
            } else {
                if (qlen >= quads.length) {
                    _quadBuffer = quads = growArrayBy(quads, quads.length);
                }
                quads[qlen++] = currQuad;
                currQuad = ch;
                currQuadBytes = 1;
            }
            if (_inputPtr >= _inputEnd) {
                if (!_loadMore()) {
                    _reportInvalidEOF(" in field name", JsonToken.FIELD_NAME);
                }
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
        }
        if (currQuadBytes > 0) {
            if (qlen >= quads.length) {
                _quadBuffer = quads = growArrayBy(quads, quads.length);
            }
            quads[qlen++] = pad(currQuad, currQuadBytes);
        }
        String name = _symbols.findName(quads, qlen);
        if (name == null) {
            name = addName(quads, qlen, currQuadBytes);
        }
        return name;
    }

    private final String findName(int q1, int lastQuadBytes) throws JsonParseException {
        q1 = pad(q1, lastQuadBytes);
        String name = _symbols.findName(q1);
        if (name != null) {
            return name;
        }
        _quadBuffer[0] = q1;
        return addName(_quadBuffer, 1, lastQuadBytes);
    }

    private final String findName(int q1, int q2, int lastQuadBytes) throws JsonParseException {
        q2 = pad(q2, lastQuadBytes);
        String name = _symbols.findName(q1, q2);
        if (name != null) {
            return name;
        }
        _quadBuffer[0] = q1;
        _quadBuffer[1] = q2;
        return addName(_quadBuffer, 2, lastQuadBytes);
    }

    private final String findName(int q1, int q2, int q3, int lastQuadBytes) throws JsonParseException {
        q3 = pad(q3, lastQuadBytes);
        String name = _symbols.findName(q1, q2, q3);
        if (name != null) {
            return name;
        }
        int[] quads = _quadBuffer;
        quads[0] = q1;
        quads[1] = q2;
        quads[2] = pad(q3, lastQuadBytes);
        return addName(quads, 3, lastQuadBytes);
    }

    private final String findName(int[] quads, int qlen, int lastQuad, int lastQuadBytes) throws JsonParseException {
        if (qlen >= quads.length) {
            _quadBuffer = quads = growArrayBy(quads, quads.length);
        }
        quads[qlen++] = pad(lastQuad, lastQuadBytes);
        String name = _symbols.findName(quads, qlen);
        if (name == null) {
            return addName(quads, qlen, lastQuadBytes);
        }
        return name;
    }

    private final String addName(int[] quads, int qlen, int lastQuadBytes) throws JsonParseException {
        int byteLen = (qlen << 2) - 4 + lastQuadBytes;
        int lastQuad;
        if (lastQuadBytes < 4) {
            lastQuad = quads[qlen - 1];
            quads[qlen - 1] = (lastQuad << ((4 - lastQuadBytes) << 3));
        } else {
            lastQuad = 0;
        }
        char[] cbuf = _textBuffer.emptyAndGetCurrentSegment();
        int cix = 0;
        for (int ix = 0; ix < byteLen; ) {
            int ch = quads[ix >> 2];
            int byteIx = (ix & 3);
            ch = (ch >> ((3 - byteIx) << 3)) & 0xFF;
            ++ix;
            if (ch > 127) {
                int needed;
                if ((ch & 0xE0) == 0xC0) {
                    ch &= 0x1F;
                    needed = 1;
                } else {
                    if ((ch & 0xF0) == 0xE0) {
                        ch &= 0x0F;
                        needed = 2;
                    } else {
                        if ((ch & 0xF8) == 0xF0) {
                            ch &= 0x07;
                            needed = 3;
                        } else {
                            _reportInvalidInitial(ch);
                            needed = ch = 1;
                        }
                    }
                }
                if ((ix + needed) > byteLen) {
                    _reportInvalidEOF(" in field name", JsonToken.FIELD_NAME);
                }
                int ch2 = quads[ix >> 2];
                byteIx = (ix & 3);
                ch2 = (ch2 >> ((3 - byteIx) << 3));
                ++ix;
                if ((ch2 & 0xC0) != 0x080) {
                    _reportInvalidOther(ch2);
                }
                ch = (ch << 6) | (ch2 & 0x3F);
                if (needed > 1) {
                    ch2 = quads[ix >> 2];
                    byteIx = (ix & 3);
                    ch2 = (ch2 >> ((3 - byteIx) << 3));
                    ++ix;
                    if ((ch2 & 0xC0) != 0x080) {
                        _reportInvalidOther(ch2);
                    }
                    ch = (ch << 6) | (ch2 & 0x3F);
                    if (needed > 2) {
                        ch2 = quads[ix >> 2];
                        byteIx = (ix & 3);
                        ch2 = (ch2 >> ((3 - byteIx) << 3));
                        ++ix;
                        if ((ch2 & 0xC0) != 0x080) {
                            _reportInvalidOther(ch2 & 0xFF);
                        }
                        ch = (ch << 6) | (ch2 & 0x3F);
                    }
                }
                if (needed > 2) {
                    ch -= 0x10000;
                    if (cix >= cbuf.length) {
                        cbuf = _textBuffer.expandCurrentSegment();
                    }
                    cbuf[cix++] = (char) (0xD800 + (ch >> 10));
                    ch = 0xDC00 | (ch & 0x03FF);
                }
            }
            if (cix >= cbuf.length) {
                cbuf = _textBuffer.expandCurrentSegment();
            }
            cbuf[cix++] = (char) ch;
        }
        String baseName = new String(cbuf, 0, cix);
        if (lastQuadBytes < 4) {
            quads[qlen - 1] = lastQuad;
        }
        return _symbols.addName(baseName, quads, qlen);
    }

    protected void _loadMoreGuaranteed() throws IOException {
        if (!_loadMore()) {
            _reportInvalidEOF();
        }
    }

    @Override
    protected void _finishString() throws IOException {
        int ptr = _inputPtr;
        if (ptr >= _inputEnd) {
            _loadMoreGuaranteed();
            ptr = _inputPtr;
        }
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = _icUTF8;
        final int max = Math.min(_inputEnd, (ptr + outBuf.length));
        final byte[] inputBuffer = _inputBuffer;
        while (ptr < max) {
            int c = (int) inputBuffer[ptr] & 0xFF;
            if (codes[c] != 0) {
                if (c == INT_QUOTE) {
                    _inputPtr = ptr + 1;
                    _textBuffer.setCurrentLength(outPtr);
                    return;
                }
                break;
            }
            ++ptr;
            outBuf[outPtr++] = (char) c;
        }
        _inputPtr = ptr;
        _finishString2(outBuf, outPtr);
    }

    protected String _finishAndReturnString() throws IOException {
        int ptr = _inputPtr;
        if (ptr >= _inputEnd) {
            _loadMoreGuaranteed();
            ptr = _inputPtr;
        }
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = _icUTF8;
        final int max = Math.min(_inputEnd, (ptr + outBuf.length));
        final byte[] inputBuffer = _inputBuffer;
        while (ptr < max) {
            int c = (int) inputBuffer[ptr] & 0xFF;
            if (codes[c] != 0) {
                if (c == INT_QUOTE) {
                    _inputPtr = ptr + 1;
                    return _textBuffer.setCurrentAndReturn(outPtr);
                }
                break;
            }
            ++ptr;
            outBuf[outPtr++] = (char) c;
        }
        _inputPtr = ptr;
        _finishString2(outBuf, outPtr);
        return _textBuffer.contentsAsString();
    }

    private final void _finishString2(char[] outBuf, int outPtr) throws IOException {
        int c;
        final int[] codes = _icUTF8;
        final byte[] inputBuffer = _inputBuffer;
        main_loop: while (true) {
            ascii_loop: while (true) {
                int ptr = _inputPtr;
                if (ptr >= _inputEnd) {
                    _loadMoreGuaranteed();
                    ptr = _inputPtr;
                }
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                final int max = Math.min(_inputEnd, (ptr + (outBuf.length - outPtr)));
                while (ptr < max) {
                    c = (int) inputBuffer[ptr++] & 0xFF;
                    if (codes[c] != 0) {
                        _inputPtr = ptr;
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (char) c;
                }
                _inputPtr = ptr;
            }
            if (c == INT_QUOTE) {
                break main_loop;
            }
            switch(codes[c]) {
                case 1:
                    c = _decodeEscaped();
                    break;
                case 2:
                    c = _decodeUtf8_2(c);
                    break;
                case 3:
                    if ((_inputEnd - _inputPtr) >= 2) {
                        c = _decodeUtf8_3fast(c);
                    } else {
                        c = _decodeUtf8_3(c);
                    }
                    break;
                case 4:
                    c = _decodeUtf8_4(c);
                    outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                    if (outPtr >= outBuf.length) {
                        outBuf = _textBuffer.finishCurrentSegment();
                        outPtr = 0;
                    }
                    c = 0xDC00 | (c & 0x3FF);
                    break;
                default:
                    if (c < INT_SPACE) {
                        _throwUnquotedSpace(c, "string value");
                    } else {
                        _reportInvalidChar(c);
                    }
            }
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
    }

    protected void _skipString() throws IOException {
        _tokenIncomplete = false;
        final int[] codes = _icUTF8;
        final byte[] inputBuffer = _inputBuffer;
        main_loop: while (true) {
            int c;
            ascii_loop: while (true) {
                int ptr = _inputPtr;
                int max = _inputEnd;
                if (ptr >= max) {
                    _loadMoreGuaranteed();
                    ptr = _inputPtr;
                    max = _inputEnd;
                }
                while (ptr < max) {
                    c = (int) inputBuffer[ptr++] & 0xFF;
                    if (codes[c] != 0) {
                        _inputPtr = ptr;
                        break ascii_loop;
                    }
                }
                _inputPtr = ptr;
            }
            if (c == INT_QUOTE) {
                break main_loop;
            }
            switch(codes[c]) {
                case 1:
                    _decodeEscaped();
                    break;
                case 2:
                    _skipUtf8_2();
                    break;
                case 3:
                    _skipUtf8_3();
                    break;
                case 4:
                    _skipUtf8_4(c);
                    break;
                default:
                    if (c < INT_SPACE) {
                        _throwUnquotedSpace(c, "string value");
                    } else {
                        _reportInvalidChar(c);
                    }
            }
        }
    }

    protected JsonToken _handleUnexpectedValue(int c) throws IOException {
        switch(c) {
            case ']':
                if (!_parsingContext.inArray()) {
                    break;
                }
            case ',':
                if (isEnabled(Feature.ALLOW_MISSING_VALUES)) {
                    _inputPtr--;
                    return JsonToken.VALUE_NULL;
                }
            case '}':
                _reportUnexpectedChar(c, "expected a value");
            case '\'':
                if (isEnabled(Feature.ALLOW_SINGLE_QUOTES)) {
                    return _handleApos();
                }
                break;
            case 'N':
                _matchToken("NaN", 1);
                if (isEnabled(Feature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    return resetAsNaN("NaN", Double.NaN);
                }
                _reportError("Non-standard token \'NaN\': enable JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS to allow");
                break;
            case 'I':
                _matchToken("Infinity", 1);
                if (isEnabled(Feature.ALLOW_NON_NUMERIC_NUMBERS)) {
                    return resetAsNaN("Infinity", Double.POSITIVE_INFINITY);
                }
                _reportError("Non-standard token \'Infinity\': enable JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS to allow");
                break;
            case '+':
                if (_inputPtr >= _inputEnd) {
                    if (!_loadMore()) {
                        _reportInvalidEOFInValue(JsonToken.VALUE_NUMBER_INT);
                    }
                }
                return _handleInvalidNumberStart(_inputBuffer[_inputPtr++] & 0xFF, false);
        }
        if (Character.isJavaIdentifierStart(c)) {
            _reportInvalidToken("" + ((char) c), "(\'true\', \'false\' or \'null\')");
        }
        _reportUnexpectedChar(c, "expected a valid value (number, String, array, object, \'true\', \'false\' or \'null\')");
        return null;
    }

    protected JsonToken _handleApos() throws IOException {
        int c = 0;
        int outPtr = 0;
        char[] outBuf = _textBuffer.emptyAndGetCurrentSegment();
        final int[] codes = _icUTF8;
        final byte[] inputBuffer = _inputBuffer;
        main_loop: while (true) {
            ascii_loop: while (true) {
                if (_inputPtr >= _inputEnd) {
                    _loadMoreGuaranteed();
                }
                if (outPtr >= outBuf.length) {
                    outBuf = _textBuffer.finishCurrentSegment();
                    outPtr = 0;
                }
                int max = _inputEnd;
                {
                    int max2 = _inputPtr + (outBuf.length - outPtr);
                    if (max2 < max) {
                        max = max2;
                    }
                }
                while (_inputPtr < max) {
                    c = (int) inputBuffer[_inputPtr++] & 0xFF;
                    if (c == '\'' || codes[c] != 0) {
                        break ascii_loop;
                    }
                    outBuf[outPtr++] = (char) c;
                }
            }
            if (c == '\'') {
                break main_loop;
            }
            switch(codes[c]) {
                case 1:
                    c = _decodeEscaped();
                    break;
                case 2:
                    c = _decodeUtf8_2(c);
                    break;
                case 3:
                    if ((_inputEnd - _inputPtr) >= 2) {
                        c = _decodeUtf8_3fast(c);
                    } else {
                        c = _decodeUtf8_3(c);
                    }
                    break;
                case 4:
                    c = _decodeUtf8_4(c);
                    outBuf[outPtr++] = (char) (0xD800 | (c >> 10));
                    if (outPtr >= outBuf.length) {
                        outBuf = _textBuffer.finishCurrentSegment();
                        outPtr = 0;
                    }
                    c = 0xDC00 | (c & 0x3FF);
                    break;
                default:
                    if (c < INT_SPACE) {
                        _throwUnquotedSpace(c, "string value");
                    }
                    _reportInvalidChar(c);
            }
            if (outPtr >= outBuf.length) {
                outBuf = _textBuffer.finishCurrentSegment();
                outPtr = 0;
            }
            outBuf[outPtr++] = (char) c;
        }
        _textBuffer.setCurrentLength(outPtr);
        return JsonToken.VALUE_STRING;
    }

    /**
     * Method called if expected numeric value (due to leading sign) does not
     * look like a number
     */
    protected JsonToken _handleInvalidNumberStart(int ch, boolean neg) throws IOException {
        while (ch == 'I') {
            if (_inputPtr >= _inputEnd) {
                if (!_loadMore()) {
                    _reportInvalidEOFInValue(JsonToken.VALUE_NUMBER_FLOAT);
                }
            }
            ch = _inputBuffer[_inputPtr++];
            String match;
            if (ch == 'N') {
                match = neg ? "-INF" : "+INF";
            } else {
                if (ch == 'n') {
                    match = neg ? "-Infinity" : "+Infinity";
                } else {
                    break;
                }
            }
            _matchToken(match, 3);
            if (isEnabled(Feature.ALLOW_NON_NUMERIC_NUMBERS)) {
                return resetAsNaN(match, neg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            }
            _reportError("Non-standard token \'" + match + "\': enable JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS to allow");
        }
        reportUnexpectedNumberChar(ch, "expected digit (0-9) to follow minus sign, for valid numeric value");
        return null;
    }

    protected final void _matchToken(String matchStr, int i) throws IOException {
        final int len = matchStr.length();
        if ((_inputPtr + len) >= _inputEnd) {
            _matchToken2(matchStr, i);
            return;
        }
        do {
            if (_inputBuffer[_inputPtr] != matchStr.charAt(i)) {
                _reportInvalidToken(matchStr.substring(0, i));
            }
            ++_inputPtr;
        } while (++i < len);
        int ch = _inputBuffer[_inputPtr] & 0xFF;
        if (ch >= '0' && ch != ']' && ch != '}') {
            _checkMatchEnd(matchStr, i, ch);
        }
    }

    private final void _matchToken2(String matchStr, int i) throws IOException {
        final int len = matchStr.length();
        do {
            if (((_inputPtr >= _inputEnd) && !_loadMore()) || (_inputBuffer[_inputPtr] != matchStr.charAt(i))) {
                _reportInvalidToken(matchStr.substring(0, i));
            }
            ++_inputPtr;
        } while (++i < len);
        if (_inputPtr >= _inputEnd && !_loadMore()) {
            return;
        }
        int ch = _inputBuffer[_inputPtr] & 0xFF;
        if (ch >= '0' && ch != ']' && ch != '}') {
            _checkMatchEnd(matchStr, i, ch);
        }
    }

    private final void _checkMatchEnd(String matchStr, int i, int ch) throws IOException {
        char c = (char) _decodeCharForError(ch);
        if (Character.isJavaIdentifierPart(c)) {
            _reportInvalidToken(matchStr.substring(0, i));
        }
    }

    /*
    /**********************************************************
    /* Internal methods, ws skipping, escape/unescape
    /**********************************************************
     */
    private final int _skipWS() throws IOException {
        while (_inputPtr < _inputEnd) {
            int i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i == INT_SLASH || i == INT_HASH) {
                    --_inputPtr;
                    return _skipWS2();
                }
                return i;
            }
            if (i != INT_SPACE) {
                if (i == INT_LF) {
                    ++_currInputRow;
                    _currInputRowStart = _inputPtr;
                } else {
                    if (i == INT_CR) {
                        _skipCR();
                    } else {
                        if (i != INT_TAB) {
                            _throwInvalidSpace(i);
                        }
                    }
                }
            }
        }
        return _skipWS2();
    }

    private final int _skipWS2() throws IOException {
        while (_inputPtr < _inputEnd || _loadMore()) {
            int i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i == INT_SLASH) {
                    _skipComment();
                    continue;
                }
                if (i == INT_HASH) {
                    if (_skipYAMLComment()) {
                        continue;
                    }
                }
                return i;
            }
            if (i != INT_SPACE) {
                if (i == INT_LF) {
                    ++_currInputRow;
                    _currInputRowStart = _inputPtr;
                } else {
                    if (i == INT_CR) {
                        _skipCR();
                    } else {
                        if (i != INT_TAB) {
                            _throwInvalidSpace(i);
                        }
                    }
                }
            }
        }
        throw _constructError("Unexpected end-of-input within/between " + _parsingContext.typeDesc() + " entries");
    }

    private final int _skipWSOrEnd() throws IOException {
        if (_inputPtr >= _inputEnd) {
            if (!_loadMore()) {
                return _eofAsNextChar();
            }
        }
        int i = _inputBuffer[_inputPtr++] & 0xFF;
        if (i > INT_SPACE) {
            if (i == INT_SLASH || i == INT_HASH) {
                --_inputPtr;
                return _skipWSOrEnd2();
            }
            return i;
        }
        if (i != INT_SPACE) {
            if (i == INT_LF) {
                ++_currInputRow;
                _currInputRowStart = _inputPtr;
            } else {
                if (i == INT_CR) {
                    _skipCR();
                } else {
                    if (i != INT_TAB) {
                        _throwInvalidSpace(i);
                    }
                }
            }
        }
        while (_inputPtr < _inputEnd) {
            i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i == INT_SLASH || i == INT_HASH) {
                    --_inputPtr;
                    return _skipWSOrEnd2();
                }
                return i;
            }
            if (i != INT_SPACE) {
                if (i == INT_LF) {
                    ++_currInputRow;
                    _currInputRowStart = _inputPtr;
                } else {
                    if (i == INT_CR) {
                        _skipCR();
                    } else {
                        if (i != INT_TAB) {
                            _throwInvalidSpace(i);
                        }
                    }
                }
            }
        }
        return _skipWSOrEnd2();
    }

    private final int _skipWSOrEnd2() throws IOException {
        while ((_inputPtr < _inputEnd) || _loadMore()) {
            int i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i == INT_SLASH) {
                    _skipComment();
                    continue;
                }
                if (i == INT_HASH) {
                    if (_skipYAMLComment()) {
                        continue;
                    }
                }
                return i;
            } else {
                if (i != INT_SPACE) {
                    if (i == INT_LF) {
                        ++_currInputRow;
                        _currInputRowStart = _inputPtr;
                    } else {
                        if (i == INT_CR) {
                            _skipCR();
                        } else {
                            if (i != INT_TAB) {
                                _throwInvalidSpace(i);
                            }
                        }
                    }
                }
            }
        }
        return _eofAsNextChar();
    }

    private final int _skipColon() throws IOException {
        if ((_inputPtr + 4) >= _inputEnd) {
            return _skipColon2(false);
        }
        int i = _inputBuffer[_inputPtr];
        if (i == INT_COLON) {
            i = _inputBuffer[++_inputPtr];
            if (i > INT_SPACE) {
                if (i == INT_SLASH || i == INT_HASH) {
                    return _skipColon2(true);
                }
                ++_inputPtr;
                return i;
            }
            if (i == INT_SPACE || i == INT_TAB) {
                i = (int) _inputBuffer[++_inputPtr];
                if (i > INT_SPACE) {
                    if (i == INT_SLASH || i == INT_HASH) {
                        return _skipColon2(true);
                    }
                    ++_inputPtr;
                    return i;
                }
            }
            return _skipColon2(true);
        }
        if (i == INT_SPACE || i == INT_TAB) {
            i = _inputBuffer[++_inputPtr];
        }
        if (i == INT_COLON) {
            i = _inputBuffer[++_inputPtr];
            if (i > INT_SPACE) {
                if (i == INT_SLASH || i == INT_HASH) {
                    return _skipColon2(true);
                }
                ++_inputPtr;
                return i;
            }
            if (i == INT_SPACE || i == INT_TAB) {
                i = (int) _inputBuffer[++_inputPtr];
                if (i > INT_SPACE) {
                    if (i == INT_SLASH || i == INT_HASH) {
                        return _skipColon2(true);
                    }
                    ++_inputPtr;
                    return i;
                }
            }
            return _skipColon2(true);
        }
        return _skipColon2(false);
    }

    private final int _skipColon2(boolean gotColon) throws IOException {
        while (_inputPtr < _inputEnd || _loadMore()) {
            int i = _inputBuffer[_inputPtr++] & 0xFF;
            if (i > INT_SPACE) {
                if (i == INT_SLASH) {
                    _skipComment();
                    continue;
                }
                if (i == INT_HASH) {
                    if (_skipYAMLComment()) {
                        continue;
                    }
                }
                if (gotColon) {
                    return i;
                }
                if (i != INT_COLON) {
                    _reportUnexpectedChar(i, "was expecting a colon to separate field name and value");
                }
                gotColon = true;
            } else {
                if (i != INT_SPACE) {
                    if (i == INT_LF) {
                        ++_currInputRow;
                        _currInputRowStart = _inputPtr;
                    } else {
                        if (i == INT_CR) {
                            _skipCR();
                        } else {
                            if (i != INT_TAB) {
                                _throwInvalidSpace(i);
                            }
                        }
                    }
                }
            }
        }
        _reportInvalidEOF(" within/between " + _parsingContext.typeDesc() + " entries", null);
        return -1;
    }

    private final void _skipComment() throws IOException {
        if (!isEnabled(Feature.ALLOW_COMMENTS)) {
            _reportUnexpectedChar('/', "maybe a (non-standard) comment? (not recognized as one since Feature \'ALLOW_COMMENTS\' not enabled for parser)");
        }
        if (_inputPtr >= _inputEnd && !_loadMore()) {
            _reportInvalidEOF(" in a comment", null);
        }
        int c = _inputBuffer[_inputPtr++] & 0xFF;
        if (c == '/') {
            _skipLine();
        } else {
            if (c == '*') {
                _skipCComment();
            } else {
                _reportUnexpectedChar(c, "was expecting either \'*\' or \'/\' for a comment");
            }
        }
    }

    private final void _skipCComment() throws IOException {
        final int[] codes = CharTypes.getInputCodeComment();
        main_loop: while ((_inputPtr < _inputEnd) || _loadMore()) {
            int i = (int) _inputBuffer[_inputPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                switch(code) {
                    case '*':
                        if (_inputPtr >= _inputEnd && !_loadMore()) {
                            break main_loop;
                        }
                        if (_inputBuffer[_inputPtr] == INT_SLASH) {
                            ++_inputPtr;
                            return;
                        }
                        break;
                    case INT_LF:
                        ++_currInputRow;
                        _currInputRowStart = _inputPtr;
                        break;
                    case INT_CR:
                        _skipCR();
                        break;
                    case 2:
                        _skipUtf8_2();
                        break;
                    case 3:
                        _skipUtf8_3();
                        break;
                    case 4:
                        _skipUtf8_4(i);
                        break;
                    default:
                        _reportInvalidChar(i);
                }
            }
        }
        _reportInvalidEOF(" in a comment", null);
    }

    private final boolean _skipYAMLComment() throws IOException {
        if (!isEnabled(Feature.ALLOW_YAML_COMMENTS)) {
            return false;
        }
        _skipLine();
        return true;
    }

    /**
     * Method for skipping contents of an input line; usually for CPP
     * and YAML style comments.
     */
    private final void _skipLine() throws IOException {
        final int[] codes = CharTypes.getInputCodeComment();
        while ((_inputPtr < _inputEnd) || _loadMore()) {
            int i = (int) _inputBuffer[_inputPtr++] & 0xFF;
            int code = codes[i];
            if (code != 0) {
                switch(code) {
                    case INT_LF:
                        ++_currInputRow;
                        _currInputRowStart = _inputPtr;
                        return;
                    case INT_CR:
                        _skipCR();
                        return;
                    case '*':
                        break;
                    case 2:
                        _skipUtf8_2();
                        break;
                    case 3:
                        _skipUtf8_3();
                        break;
                    case 4:
                        _skipUtf8_4(i);
                        break;
                    default:
                        if (code < 0) {
                            _reportInvalidChar(i);
                        }
                }
            }
        }
    }

    @Override
    protected char _decodeEscaped() throws IOException {
        if (_inputPtr >= _inputEnd) {
            if (!_loadMore()) {
                _reportInvalidEOF(" in character escape sequence", JsonToken.VALUE_STRING);
            }
        }
        int c = (int) _inputBuffer[_inputPtr++];
        switch(c) {
            case 'b':
                return '\b';
            case 't':
                return '\t';
            case 'n':
                return '\n';
            case 'f':
                return '\f';
            case 'r':
                return '\r';
            case '\"':
            case '/':
            case '\\':
                return (char) c;
            case 'u':
                break;
            default:
                return _handleUnrecognizedCharacterEscape((char) _decodeCharForError(c));
        }
        int value = 0;
        for (int i = 0; i < 4; ++i) {
            if (_inputPtr >= _inputEnd) {
                if (!_loadMore()) {
                    _reportInvalidEOF(" in character escape sequence", JsonToken.VALUE_STRING);
                }
            }
            int ch = (int) _inputBuffer[_inputPtr++];
            int digit = CharTypes.charToHex(ch);
            if (digit < 0) {
                _reportUnexpectedChar(ch, "expected a hex-digit for character escape sequence");
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    protected int _decodeCharForError(int firstByte) throws IOException {
        int c = firstByte & 0xFF;
        if (c > 0x7F) {
            int needed;
            if ((c & 0xE0) == 0xC0) {
                c &= 0x1F;
                needed = 1;
            } else {
                if ((c & 0xF0) == 0xE0) {
                    c &= 0x0F;
                    needed = 2;
                } else {
                    if ((c & 0xF8) == 0xF0) {
                        c &= 0x07;
                        needed = 3;
                    } else {
                        _reportInvalidInitial(c & 0xFF);
                        needed = 1;
                    }
                }
            }
            int d = nextByte();
            if ((d & 0xC0) != 0x080) {
                _reportInvalidOther(d & 0xFF);
            }
            c = (c << 6) | (d & 0x3F);
            if (needed > 1) {
                d = nextByte();
                if ((d & 0xC0) != 0x080) {
                    _reportInvalidOther(d & 0xFF);
                }
                c = (c << 6) | (d & 0x3F);
                if (needed > 2) {
                    d = nextByte();
                    if ((d & 0xC0) != 0x080) {
                        _reportInvalidOther(d & 0xFF);
                    }
                    c = (c << 6) | (d & 0x3F);
                }
            }
        }
        return c;
    }

    private final int _decodeUtf8_2(int c) throws IOException {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c & 0x1F) << 6) | (d & 0x3F);
    }

    private final int _decodeUtf8_3(int c1) throws IOException {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        c1 &= 0x0F;
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    private final int _decodeUtf8_3fast(int c1) throws IOException {
        c1 &= 0x0F;
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        int c = (c1 << 6) | (d & 0x3F);
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        return c;
    }

    private final int _decodeUtf8_4(int c) throws IOException {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = ((c & 0x07) << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        c = (c << 6) | (d & 0x3F);
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        return ((c << 6) | (d & 0x3F)) - 0x10000;
    }

    private final void _skipUtf8_2() throws IOException {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int c = (int) _inputBuffer[_inputPtr++];
        if ((c & 0xC0) != 0x080) {
            _reportInvalidOther(c & 0xFF, _inputPtr);
        }
    }

    private final void _skipUtf8_3() throws IOException {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int c = (int) _inputBuffer[_inputPtr++];
        if ((c & 0xC0) != 0x080) {
            _reportInvalidOther(c & 0xFF, _inputPtr);
        }
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        c = (int) _inputBuffer[_inputPtr++];
        if ((c & 0xC0) != 0x080) {
            _reportInvalidOther(c & 0xFF, _inputPtr);
        }
    }

    private final void _skipUtf8_4(int c) throws IOException {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        int d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        d = (int) _inputBuffer[_inputPtr++];
        if ((d & 0xC0) != 0x080) {
            _reportInvalidOther(d & 0xFF, _inputPtr);
        }
    }

    /*
    /**********************************************************
    /* Internal methods, input loading
    /**********************************************************
     */
    /**
     * We actually need to check the character value here
     * (to see if we have \n following \r).
     */
    protected final void _skipCR() throws IOException {
        if (_inputPtr < _inputEnd || _loadMore()) {
            if (_inputBuffer[_inputPtr] == BYTE_LF) {
                ++_inputPtr;
            }
        }
        ++_currInputRow;
        _currInputRowStart = _inputPtr;
    }

    private int nextByte() throws IOException {
        if (_inputPtr >= _inputEnd) {
            _loadMoreGuaranteed();
        }
        return _inputBuffer[_inputPtr++] & 0xFF;
    }

    /*
    /**********************************************************
    /* Internal methods, error reporting
    /**********************************************************
     */
    protected void _reportInvalidToken(String matchedPart) throws IOException {
        _reportInvalidToken(matchedPart, "\'null\', \'true\', \'false\' or NaN");
    }

    protected void _reportInvalidToken(String matchedPart, String msg) throws IOException {
        StringBuilder sb = new StringBuilder(matchedPart);
        final int maxTokenLength = 256;
        while (sb.length() < maxTokenLength) {
            if (_inputPtr >= _inputEnd && !_loadMore()) {
                break;
            }
            int i = (int) _inputBuffer[_inputPtr++];
            char c = (char) _decodeCharForError(i);
            if (!Character.isJavaIdentifierPart(c)) {
                break;
            }
            sb.append(c);
        }
        if (sb.length() == maxTokenLength) {
            sb.append("...");
        }
        _reportError("Unrecognized token \'" + sb.toString() + "\': was expecting " + msg);
    }

    protected void _reportInvalidChar(int c) throws JsonParseException {
        if (c < INT_SPACE) {
            _throwInvalidSpace(c);
        }
        _reportInvalidInitial(c);
    }

    protected void _reportInvalidInitial(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 start byte 0x" + Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask) throws JsonParseException {
        _reportError("Invalid UTF-8 middle byte 0x" + Integer.toHexString(mask));
    }

    protected void _reportInvalidOther(int mask, int ptr) throws JsonParseException {
        _inputPtr = ptr;
        _reportInvalidOther(mask);
    }

    public static int[] growArrayBy(int[] arr, int more) {
        if (arr == null) {
            return new int[more];
        }
        return Arrays.copyOf(arr, arr.length + more);
    }

    /*
    /**********************************************************
    /* Internal methods, binary access
    /**********************************************************
     */
    /**
     * Efficient handling for incremental parsing of base64-encoded
     * textual content.
     */
    @SuppressWarnings(value = { "resource" })
    protected final byte[] _decodeBase64(Base64Variant b64variant) throws IOException {
        ByteArrayBuilder builder = _getByteArrayBuilder();
        while (true) {
            int ch;
            do {
                if (_inputPtr >= _inputEnd) {
                    _loadMoreGuaranteed();
                }
                ch = (int) _inputBuffer[_inputPtr++] & 0xFF;
            } while (ch <= INT_SPACE);
            int bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (ch == INT_QUOTE) {
                    return builder.toByteArray();
                }
                bits = _decodeBase64Escape(b64variant, ch, 0);
                if (bits < 0) {
                    continue;
                }
            }
            int decodedData = bits;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                bits = _decodeBase64Escape(b64variant, ch, 1);
            }
            decodedData = (decodedData << 6) | bits;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == '\"' && !b64variant.usesPadding()) {
                        decodedData >>= 4;
                        builder.append(decodedData);
                        return builder.toByteArray();
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 2);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    if (_inputPtr >= _inputEnd) {
                        _loadMoreGuaranteed();
                    }
                    ch = _inputBuffer[_inputPtr++] & 0xFF;
                    if (!b64variant.usesPaddingChar(ch)) {
                        throw reportInvalidBase64Char(b64variant, ch, 3, "expected padding character \'" + b64variant.getPaddingChar() + "\'");
                    }
                    decodedData >>= 4;
                    builder.append(decodedData);
                    continue;
                }
            }
            decodedData = (decodedData << 6) | bits;
            if (_inputPtr >= _inputEnd) {
                _loadMoreGuaranteed();
            }
            ch = _inputBuffer[_inputPtr++] & 0xFF;
            bits = b64variant.decodeBase64Char(ch);
            if (bits < 0) {
                if (bits != Base64Variant.BASE64_VALUE_PADDING) {
                    if (ch == '\"' && !b64variant.usesPadding()) {
                        decodedData >>= 2;
                        builder.appendTwoBytes(decodedData);
                        return builder.toByteArray();
                    }
                    bits = _decodeBase64Escape(b64variant, ch, 3);
                }
                if (bits == Base64Variant.BASE64_VALUE_PADDING) {
                    decodedData >>= 2;
                    builder.appendTwoBytes(decodedData);
                    continue;
                }
            }
            decodedData = (decodedData << 6) | bits;
            builder.appendThreeBytes(decodedData);
        }
    }

    /*
    /**********************************************************
    /* Improved location updating (refactored in 2.7)
    /**********************************************************
     */
    // As per [core#108], must ensure we call the right method
    @Override
    public JsonLocation getTokenLocation() {
        final Object src = _ioContext.getSourceReference();
        if (_currToken == JsonToken.FIELD_NAME) {
            long total = _currInputProcessed + (_nameStartOffset - 1);
            return new JsonLocation(src, total, -1L, _nameStartRow, _nameStartCol);
        }
        return new JsonLocation(src, _tokenInputTotal - 1, -1L, _tokenInputRow, _tokenInputCol);
    }

    // As per [core#108], must ensure we call the right method
    @Override
    public JsonLocation getCurrentLocation() {
        int col = _inputPtr - _currInputRowStart + 1;
        return new JsonLocation(_ioContext.getSourceReference(), _currInputProcessed + _inputPtr, -1L, _currInputRow, col);
    }

    // @since 2.7
    private final void _updateLocation() {
        _tokenInputRow = _currInputRow;
        final int ptr = _inputPtr;
        _tokenInputTotal = _currInputProcessed + ptr;
        _tokenInputCol = ptr - _currInputRowStart;
    }

    // @since 2.7
    private final void _updateNameLocation() {
        _nameStartRow = _currInputRow;
        final int ptr = _inputPtr;
        _nameStartOffset = ptr;
        _nameStartCol = ptr - _currInputRowStart;
    }

    /*
    /**********************************************************
    /* Internal methods, other
    /**********************************************************
     */
    private final JsonToken _closeScope(int i) throws JsonParseException {
        if (i == INT_RCURLY) {
            _closeObjectScope();
            return (_currToken = JsonToken.END_OBJECT);
        }
        _closeArrayScope();
        return (_currToken = JsonToken.END_ARRAY);
    }

    private final void _closeArrayScope() throws JsonParseException {
        _updateLocation();
        if (!_parsingContext.inArray()) {
            _reportMismatchedEndMarker(']', '}');
        }
        _parsingContext = _parsingContext.clearAndGetParent();
    }

    private final void _closeObjectScope() throws JsonParseException {
        _updateLocation();
        if (!_parsingContext.inObject()) {
            _reportMismatchedEndMarker('}', ']');
        }
        _parsingContext = _parsingContext.clearAndGetParent();
    }

    /**
     * Helper method needed to fix [Issue#148], masking of 0x00 character
     */
    private static final int pad(int q, int bytes) {
        return (bytes == 4) ? q : (q | (-1 << (bytes << 3)));
    }
}

