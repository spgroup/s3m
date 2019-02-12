package com.fasterxml.jackson.core.sym;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.util.InternCache;

/**
 * Replacement for <code>BytesToNameCanonicalizer</code> which aims at more localized
 * memory access due to flattening of name quad data.
 * Performance improvement modest for simple JSON document data binding (maybe 3%),
 * but should help more for larger symbol tables, or for binary formats like Smile.
 *
 * @since 2.6
 */
public final class ByteQuadsCanonicalizer {

    /**
     * Initial size of the primary hash area. Each entry consumes 4 ints (16 bytes),
     * and secondary area is same as primary; so default size will use 2kB of memory_tertiaryStart
     * (plus 64x4 or 64x8 (256/512 bytes) for references to Strings, and Strings
     * themselves).
     */
    private static final int DEFAULT_T_SIZE = 64;

    //    private static final int DEFAULT_T_SIZE = 256;
    /**
     * Let's not expand symbol tables past some maximum size;
     * this should protected against OOMEs caused by large documents
     * with unique (~= random) names.
     * Size is in 
     */
    private static final int MAX_T_SIZE = 0x10000;

    // 64k entries == 2M mem hash area
    /**
     * No point in trying to construct tiny tables, just need to resize soon.
     */
    private static final int MIN_HASH_SIZE = 16;

    /**
     * Let's only share reasonably sized symbol tables. Max size set to 3/4 of 8k;
     * this corresponds to 256k main hash index. This should allow for enough distinct
     * names for almost any case, while preventing ballooning for cases where names
     * are unique (or close thereof).
     */
    static final int MAX_ENTRIES_FOR_REUSE = 6000;

    /*
    /**********************************************************
    /* Linkage, needed for merging symbol tables
    /**********************************************************
     */
    /**
     * Reference to the root symbol table, for child tables, so
     * that they can merge table information back as necessary.
     */
    private final ByteQuadsCanonicalizer _parent;

    /**
     * Member that is only used by the root table instance: root
     * passes immutable state into child instances, and children
     * may return new state if they add entries to the table.
     * Child tables do NOT use the reference.
     */
    private final AtomicReference<TableInfo> _tableInfo;

    /**
     * Seed value we use as the base to make hash codes non-static between
     * different runs, but still stable for lifetime of a single symbol table
     * instance.
     * This is done for security reasons, to avoid potential DoS attack via
     * hash collisions.
     */
    private final int _seed;

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    /**
     * Whether canonical symbol Strings are to be intern()ed before added
     * to the table or not.
     *<p>
     * NOTE: non-final to allow disabling intern()ing in case of excessive
     * collisions.
     */
    private boolean _intern;

    /**
     * Flag that indicates whether we should throw an exception if enough 
     * hash collisions are detected (true); or just worked around (false).
     * 
     * @since 2.4
     */
    private final boolean _failOnDoS;

    /*
    /**********************************************************
    /* First, main hash area info
    /**********************************************************
     */
    /**
     * Primary hash information area: consists of <code>2 * _hashSize</code>
     * entries of 16 bytes (4 ints), arranged in a cascading lookup
     * structure (details of which may be tweaked depending on expected rates
     * of collisions).
     */
    private int[] _hashArea;

    /**
     * Number of slots for primary entries within {@link #_hashArea}; which is
     * at most <code>1/8</code> of actual size of the underlying array (4-int slots,
     * primary covers only half of the area; plus, additional area for longer
     * symbols after hash area).
     */
    private int _hashSize;

    /**
     * Offset within {@link #_hashArea} where secondary entries start
     */
    private int _secondaryStart;

    /**
     * Offset within {@link #_hashArea} where tertiary entries start
     */
    private int _tertiaryStart;

    /**
     * Constant that determines size of buckets for tertiary entries:
     * <code>1 &lt;&lt; _tertiaryShift</code> is the size, and shift value
     * is also used for translating from primary offset into
     * tertiary bucket (shift right by <code>4 + _tertiaryShift</code>).
     *<p>
     * Default value is 2, for buckets of 4 slots; grows bigger with
     * bigger table sizes.
     */
    private int _tertiaryShift;

    /**
     * Total number of Strings in the symbol table; only used for child tables.
     */
    private int _count;

    /**
     * Array that contains <code>String</code> instances matching
     * entries in {@link #_hashArea}.
     * Contains nulls for unused entries. Note that this size is twice
     * that of {@link #_hashArea}
     */
    private String[] _names;

    /*
    /**********************************************************
    /* Then information on collisions etc
    /**********************************************************
     */
    /**
     * Pointer to the offset within spill-over area where there is room
     * for more spilled over entries (if any).
     * Spill over area is within fixed-size portion of {@link #_hashArea}.
     */
    private int _spilloverEnd;

    /**
     * Offset within {@link #_hashArea} that follows main slots and contains
     * quads for longer names (13 bytes or longers), and points to the
     * first available int that may be used for appending quads of the next
     * long name.
     * Note that long name area follows immediately after the fixed-size
     * main hash area ({@link #_hashArea}).
     */
    private int _longNameOffset;

    /**
     * This flag is set if, after adding a new entry, it is deemed
     * that a rehash is warranted if any more entries are to be added.
     */
    private transient boolean _needRehash;

    /*
    /**********************************************************
    /* Sharing, versioning
    /**********************************************************
     */
    // // // Which of the buffers may be shared (and are copy-on-write)?
    /**
     * Flag that indicates whether underlying data structures for
     * the main hash area are shared or not. If they are, then they
     * need to be handled in copy-on-write way, i.e. if they need
     * to be modified, a copy needs to be made first; at this point
     * it will not be shared any more, and can be modified.
     *<p>
     * This flag needs to be checked both when adding new main entries,
     * and when adding new collision list queues (i.e. creating a new
     * collision list head entry)
     */
    private boolean _hashShared;

    private ByteQuadsCanonicalizer(int sz, boolean intern, int seed, boolean failOnDoS) {
        _parent = null;
        _seed = seed;
        _intern = intern;
        _failOnDoS = failOnDoS;
        if (sz < MIN_HASH_SIZE) {
            sz = MIN_HASH_SIZE;
        } else {
            if ((sz & (sz - 1)) != 0) {
                int curr = MIN_HASH_SIZE;
                while (curr < sz) {
                    curr += curr;
                }
                sz = curr;
            }
        }
        _tableInfo = new AtomicReference<TableInfo>(TableInfo.createInitial(sz));
    }

    private ByteQuadsCanonicalizer(ByteQuadsCanonicalizer parent, boolean intern, int seed, boolean failOnDoS, TableInfo state) {
        _parent = parent;
        _seed = seed;
        _intern = intern;
        _failOnDoS = failOnDoS;
        _tableInfo = null;
        _count = state.count;
        _hashSize = state.size;
        _secondaryStart = _hashSize << 2;
        _tertiaryStart = _secondaryStart + (_secondaryStart >> 1);
        _tertiaryShift = state.tertiaryShift;
        _hashArea = state.mainHash;
        _names = state.names;
        _spilloverEnd = state.spilloverEnd;
        _longNameOffset = state.longNameOffset;
        _needRehash = false;
        _hashShared = true;
    }

    /*
    /**********************************************************
    /* Life-cycle: factory methods, merging
    /**********************************************************
     */
    /**
     * Factory method to call to create a symbol table instance with a
     * randomized seed value.
     */
    public static ByteQuadsCanonicalizer createRoot() {
        long now = System.currentTimeMillis();
        int seed = (((int) now) + ((int) (now >>> 32))) | 1;
        return createRoot(seed);
    }

    /**
     * Factory method that should only be called from unit tests, where seed
     * value should remain the same.
     */
    protected static ByteQuadsCanonicalizer createRoot(int seed) {
        return new ByteQuadsCanonicalizer(DEFAULT_T_SIZE, true, seed, true);
    }

    /**
     * Factory method used to create actual symbol table instance to
     * use for parsing.
     */
    public ByteQuadsCanonicalizer makeChild(int flags) {
        return new ByteQuadsCanonicalizer(this, JsonFactory.Feature.INTERN_FIELD_NAMES.enabledIn(flags), _seed, JsonFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW.enabledIn(flags), _tableInfo.get());
    }

    /**
     * Method called by the using code to indicate it is done
     * with this instance. This lets instance merge accumulated
     * changes into parent (if need be), safely and efficiently,
     * and without calling code having to know about parent
     * information
     */
    public void release() {
        if (_parent != null && maybeDirty()) {
            _parent.mergeChild(new TableInfo(this));
            _hashShared = true;
        }
    }

    private void mergeChild(TableInfo childState) {
        final int childCount = childState.count;
        TableInfo currState = _tableInfo.get();
        if (childCount == currState.count) {
            return;
        }
        if (childCount > MAX_ENTRIES_FOR_REUSE) {
            childState = TableInfo.createInitial(DEFAULT_T_SIZE);
        }
        _tableInfo.compareAndSet(currState, childState);
    }

    /*
    /**********************************************************
    /* API, accessors
    /**********************************************************
     */
    public int size() {
        if (_tableInfo != null) {
            return _tableInfo.get().count;
        }
        return _count;
    }

    /**
     * Returns number of primary slots table has currently
     */
    public int bucketCount() {
        return _hashSize;
    }

    /**
     * Method called to check to quickly see if a child symbol table
     * may have gotten additional entries. Used for checking to see
     * if a child table should be merged into shared table.
     */
    public boolean maybeDirty() {
        return !_hashShared;
    }

    public int hashSeed() {
        return _seed;
    }

    /**
     * Method mostly needed by unit tests; calculates number of
     * entries that are in the primary slot set. These are
     * "perfect" entries, accessible with a single lookup
     */
    public int primaryCount() {
        int count = 0;
        for (int offset = 3, end = _secondaryStart; offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    /**
     * Method mostly needed by unit tests; calculates number of entries
     * in secondary buckets
     */
    public int secondaryCount() {
        int count = 0;
        int offset = _secondaryStart + 3;
        for (int end = _tertiaryStart; offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    /**
     * Method mostly needed by unit tests; calculates number of entries
     * in tertiary buckets
     */
    public int tertiaryCount() {
        int count = 0;
        int offset = _tertiaryStart + 3;
        for (int end = offset + _hashSize; offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    /**
     * Method mostly needed by unit tests; calculates number of entries
     * in shared spillover area
     */
    public int spilloverCount() {
        return (_spilloverEnd - _spilloverStart()) >> 2;
    }

    public int totalCount() {
        int count = 0;
        for (int offset = 3, end = (_hashSize << 3); offset < end; offset += 4) {
            if (_hashArea[offset] != 0) {
                ++count;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        int pri = primaryCount();
        int sec = secondaryCount();
        int tert = tertiaryCount();
        int spill = spilloverCount();
        int total = totalCount();
        return String.format("[%s: size=%d, hashSize=%d, %d/%d/%d/%d pri/sec/ter/spill (=%s), total:%d]", getClass().getName(), _count, _hashSize, pri, sec, tert, spill, (pri + sec + tert + spill), total);
    }

    /*
    /**********************************************************
    /* Public API, accessing symbols
    /**********************************************************
     */
    public String findName(int q1) {
        int offset = _calcOffset(calcHash(q1));
        final int[] hashArea = _hashArea;
        int len = hashArea[offset + 3];
        if (len == 1) {
            if (hashArea[offset] == q1) {
                return _names[offset >> 2];
            }
        } else {
            if (len == 0) {
                return null;
            }
        }
        int offset2 = _secondaryStart + ((offset >> 3) << 2);
        len = hashArea[offset2 + 3];
        if (len == 1) {
            if (hashArea[offset2] == q1) {
                return _names[offset2 >> 2];
            }
        } else {
            if (len == 0) {
                return null;
            }
        }
        return _findSecondary(offset, q1);
    }

    public String findName(int q1, int q2) {
        int offset = _calcOffset(calcHash(q1, q2));
        final int[] hashArea = _hashArea;
        int len = hashArea[offset + 3];
        if (len == 2) {
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset + 1])) {
                return _names[offset >> 2];
            }
        } else {
            if (len == 0) {
                return null;
            }
        }
        int offset2 = _secondaryStart + ((offset >> 3) << 2);
        len = hashArea[offset2 + 3];
        if (len == 2) {
            if ((q1 == hashArea[offset2]) && (q2 == hashArea[offset2 + 1])) {
                return _names[offset2 >> 2];
            }
        } else {
            if (len == 0) {
                return null;
            }
        }
        return _findSecondary(offset, q1, q2);
    }

    public String findName(int q1, int q2, int q3) {
        int offset = _calcOffset(calcHash(q1, q2, q3));
        final int[] hashArea = _hashArea;
        int len = hashArea[offset + 3];
        if (len == 3) {
            if ((q1 == hashArea[offset]) && (hashArea[offset + 1] == q2) && (hashArea[offset + 2] == q3)) {
                return _names[offset >> 2];
            }
        } else {
            if (len == 0) {
                return null;
            }
        }
        int offset2 = _secondaryStart + ((offset >> 3) << 2);
        len = hashArea[offset2 + 3];
        if (len == 3) {
            if ((q1 == hashArea[offset2]) && (hashArea[offset2 + 1] == q2) && (hashArea[offset2 + 2] == q3)) {
                return _names[offset2 >> 2];
            }
        } else {
            if (len == 0) {
                return null;
            }
        }
        return _findSecondary(offset, q1, q2, q3);
    }

    public String findName(int[] q, int qlen) {
        if (qlen < 4) {
            if (qlen == 3) {
                return findName(q[0], q[1], q[2]);
            }
            if (qlen == 2) {
                return findName(q[0], q[1]);
            }
            return findName(q[0]);
        }
        final int hash = calcHash(q, qlen);
        int offset = _calcOffset(hash);
        final int[] hashArea = _hashArea;
        final int len = hashArea[offset + 3];
        if ((hash == hashArea[offset]) && (len == qlen)) {
            if (_verifyLongName(q, qlen, hashArea[offset + 1])) {
                return _names[offset >> 2];
            }
        }
        if (len == 0) {
            return null;
        }
        int offset2 = _secondaryStart + ((offset >> 3) << 2);
        final int len2 = hashArea[offset2 + 3];
        if ((hash == hashArea[offset2]) && (len2 == qlen)) {
            if (_verifyLongName(q, qlen, hashArea[offset2 + 1])) {
                return _names[offset2 >> 2];
            }
        }
        return _findSecondary(offset, hash, q, qlen);
    }

    private final int _calcOffset(int hash) {
        int ix = hash & (_hashSize - 1);
        return (ix << 2);
    }

    /*
    /**********************************************************
    /* Access from spill-over areas
    /**********************************************************
     */
    private String _findSecondary(int origOffset, int q1) {
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;
        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset + 3];
            if ((q1 == hashArea[offset]) && (1 == len)) {
                return _names[offset >> 2];
            }
            if (len == 0) {
                return null;
            }
        }
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((q1 == hashArea[offset]) && (1 == hashArea[offset + 3])) {
                return _names[offset >> 2];
            }
        }
        return null;
    }

    private String _findSecondary(int origOffset, int q1, int q2) {
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;
        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset + 3];
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset + 1]) && (2 == len)) {
                return _names[offset >> 2];
            }
            if (len == 0) {
                return null;
            }
        }
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset + 1]) && (2 == hashArea[offset + 3])) {
                return _names[offset >> 2];
            }
        }
        return null;
    }

    private String _findSecondary(int origOffset, int q1, int q2, int q3) {
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;
        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset + 3];
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset + 1]) && (q3 == hashArea[offset + 2]) && (3 == len)) {
                return _names[offset >> 2];
            }
            if (len == 0) {
                return null;
            }
        }
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((q1 == hashArea[offset]) && (q2 == hashArea[offset + 1]) && (q3 == hashArea[offset + 2]) && (3 == hashArea[offset + 3])) {
                return _names[offset >> 2];
            }
        }
        return null;
    }

    private String _findSecondary(int origOffset, int hash, int[] q, int qlen) {
        int offset = _tertiaryStart + ((origOffset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int[] hashArea = _hashArea;
        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset + bucketSize; offset < end; offset += 4) {
            int len = hashArea[offset + 3];
            if ((hash == hashArea[offset]) && (qlen == len)) {
                if (_verifyLongName(q, qlen, hashArea[offset + 1])) {
                    return _names[offset >> 2];
                }
            }
            if (len == 0) {
                return null;
            }
        }
        for (offset = _spilloverStart(); offset < _spilloverEnd; offset += 4) {
            if ((hash == hashArea[offset]) && (qlen == hashArea[offset + 3])) {
                if (_verifyLongName(q, qlen, hashArea[offset + 1])) {
                    return _names[offset >> 2];
                }
            }
        }
        return null;
    }

    private boolean _verifyLongName(int[] q, int qlen, int spillOffset) {
        final int[] hashArea = _hashArea;
        int ix = 0;
        switch(qlen) {
            default:
                return _verifyLongName2(q, qlen, spillOffset);
            case 8:
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
            case 7:
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
            case 6:
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
            case 5:
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
            case 4:
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
                if (q[ix++] != hashArea[spillOffset++]) {
                    return false;
                }
        }
        return true;
    }

    private boolean _verifyLongName2(int[] q, int qlen, int spillOffset) {
        int ix = 0;
        do {
            if (q[ix++] != _hashArea[spillOffset++]) {
                return false;
            }
        } while (ix < qlen);
        return true;
    }

    /*
    /**********************************************************
    /* API, mutators
    /**********************************************************
     */
    public String addName(String name, int q1) {
        _verifySharing();
        if (_intern) {
            name = InternCache.instance.intern(name);
        }
        int offset = _findOffsetForAdd(calcHash(q1));
        _hashArea[offset] = q1;
        _hashArea[offset + 3] = 1;
        _names[offset >> 2] = name;
        ++_count;
        _verifyNeedForRehash();
        return name;
    }

    public String addName(String name, int q1, int q2) {
        _verifySharing();
        if (_intern) {
            name = InternCache.instance.intern(name);
        }
        int hash = (q2 == 0) ? calcHash(q1) : calcHash(q1, q2);
        int offset = _findOffsetForAdd(hash);
        _hashArea[offset] = q1;
        _hashArea[offset + 1] = q2;
        _hashArea[offset + 3] = 2;
        _names[offset >> 2] = name;
        ++_count;
        _verifyNeedForRehash();
        return name;
    }

    public String addName(String name, int q1, int q2, int q3) {
        _verifySharing();
        if (_intern) {
            name = InternCache.instance.intern(name);
        }
        int offset = _findOffsetForAdd(calcHash(q1, q2, q3));
        _hashArea[offset] = q1;
        _hashArea[offset + 1] = q2;
        _hashArea[offset + 2] = q3;
        _hashArea[offset + 3] = 3;
        _names[offset >> 2] = name;
        ++_count;
        _verifyNeedForRehash();
        return name;
    }

    public String addName(String name, int[] q, int qlen) {
        _verifySharing();
        if (_intern) {
            name = InternCache.instance.intern(name);
        }
        int offset;
        switch(qlen) {
            case 1:
                {
                    offset = _findOffsetForAdd(calcHash(q[0]));
                    _hashArea[offset] = q[0];
                    _hashArea[offset + 3] = 1;
                }
                break;
            case 2:
                {
                    offset = _findOffsetForAdd(calcHash(q[0], q[1]));
                    _hashArea[offset] = q[0];
                    _hashArea[offset + 1] = q[1];
                    _hashArea[offset + 3] = 2;
                }
                break;
            case 3:
                {
                    offset = _findOffsetForAdd(calcHash(q[0], q[1], q[2]));
                    _hashArea[offset] = q[0];
                    _hashArea[offset + 1] = q[1];
                    _hashArea[offset + 2] = q[2];
                    _hashArea[offset + 3] = 3;
                }
                break;
            default:
                final int hash = calcHash(q, qlen);
                offset = _findOffsetForAdd(hash);
                _hashArea[offset] = hash;
                int longStart = _appendLongName(q, qlen);
                _hashArea[offset + 1] = longStart;
                _hashArea[offset + 3] = qlen;
        }
        _names[offset >> 2] = name;
        ++_count;
        _verifyNeedForRehash();
        return name;
    }

    private void _verifyNeedForRehash() {
        if (_count > (_hashSize >> 1)) {
            int spillCount = (_spilloverEnd - _spilloverStart()) >> 2;
            if ((spillCount > (1 + _count >> 7)) || (_count > (_hashSize * 0.80))) {
                _needRehash = true;
            }
        }
    }

    private void _verifySharing() {
        if (_hashShared) {
            _hashArea = Arrays.copyOf(_hashArea, _hashArea.length);
            _names = Arrays.copyOf(_names, _names.length);
            _hashShared = false;
            _verifyNeedForRehash();
        }
        if (_needRehash) {
            rehash();
        }
    }

    /**
     * Method called to find the location within hash table to add a new symbol in.
     */
    private int _findOffsetForAdd(int hash) {
        int offset = _calcOffset(hash);
        final int[] hashArea = _hashArea;
        if (hashArea[offset + 3] == 0) {
            return offset;
        }
        int offset2 = _secondaryStart + ((offset >> 3) << 2);
        if (hashArea[offset2 + 3] == 0) {
            return offset2;
        }
        offset2 = _tertiaryStart + ((offset >> (_tertiaryShift + 2)) << _tertiaryShift);
        final int bucketSize = (1 << _tertiaryShift);
        for (int end = offset2 + bucketSize; offset2 < end; offset2 += 4) {
            if (hashArea[offset2 + 3] == 0) {
                return offset2;
            }
        }
        offset = _spilloverEnd;
        _spilloverEnd += 4;
        final int end = (_hashSize << 3);
        if (_spilloverEnd >= end) {
            if (_failOnDoS) {
                _reportTooManyCollisions();
            }
            _needRehash = true;
        }
        return offset;
    }

    private int _appendLongName(int[] quads, int qlen) {
        int start = _longNameOffset;
        if ((start + qlen) > _hashArea.length) {
            int toAdd = (start + qlen) - _hashArea.length;
            int minAdd = Math.min(4096, _hashSize);
            int newSize = _hashArea.length + Math.max(toAdd, minAdd);
            _hashArea = Arrays.copyOf(_hashArea, newSize);
        }
        System.arraycopy(quads, 0, _hashArea, start, qlen);
        _longNameOffset += qlen;
        return start;
    }

    /*
    /**********************************************************
    /* Hash calculation
    /**********************************************************
     */
    /* Note on hash calculation: we try to make it more difficult to
     * generate collisions automatically; part of this is to avoid
     * simple "multiply-add" algorithm (like JDK String.hashCode()),
     * and add bit of shifting. And other part is to make this
     * non-linear, at least for shorter symbols.
     */
    // JDK uses 31; other fine choices are 33 and 65599, let's use 33
    // as it seems to give fewest collisions for us
    // (see [http://www.cse.yorku.ca/~oz/hash.html] for details)
    private static final int MULT = 33;

    private static final int MULT2 = 65599;

    private static final int MULT3 = 31;

    public int calcHash(int q1) {
        int hash = q1 ^ _seed;
        hash += (hash >>> 16);
        hash ^= (hash << 3);
        hash += (hash >>> 12);
        return hash;
    }

    public int calcHash(int q1, int q2) {
        int hash = q1;
        hash += (hash >>> 15);
        hash ^= (hash >>> 9);
        hash += (q2 * MULT);
        hash ^= _seed;
        hash += (hash >>> 16);
        hash ^= (hash >>> 4);
        hash += (hash << 3);
        return hash;
    }

    public int calcHash(int q1, int q2, int q3) {
        int hash = q1 ^ _seed;
        hash += (hash >>> 9);
        hash *= MULT3;
        hash += q2;
        hash *= MULT;
        hash += (hash >>> 15);
        hash ^= q3;
        hash += (hash >>> 4);
        hash += (hash >>> 15);
        hash ^= (hash << 9);
        return hash;
    }

    public int calcHash(int[] q, int qlen) {
        if (qlen < 4) {
            throw new IllegalArgumentException();
        }
        int hash = q[0] ^ _seed;
        hash += (hash >>> 9);
        hash += q[1];
        hash += (hash >>> 15);
        hash *= MULT;
        hash ^= q[2];
        hash += (hash >>> 4);
        for (int i = 3; i < qlen; ++i) {
            int next = q[i];
            next = next ^ (next >> 21);
            hash += next;
        }
        hash *= MULT2;
        hash += (hash >>> 19);
        hash ^= (hash << 5);
        return hash;
    }

    /*
    /**********************************************************
    /* Rehashing
    /**********************************************************
     */
    private void rehash() {
        _needRehash = false;
        _hashShared = false;
        final int[] oldHashArea = _hashArea;
        final String[] oldNames = _names;
        final int oldSize = _hashSize;
        final int oldCount = _count;
        final int newSize = oldSize + oldSize;
        final int oldEnd = _spilloverEnd;
        if (newSize > MAX_T_SIZE) {
            nukeSymbols(true);
            return;
        }
        _hashArea = new int[oldHashArea.length + (oldSize << 3)];
        _hashSize = newSize;
        _secondaryStart = (newSize << 2);
        _tertiaryStart = _secondaryStart + (_secondaryStart >> 1);
        _tertiaryShift = _calcTertiaryShift(newSize);
        _names = new String[oldNames.length << 1];
        nukeSymbols(false);
        int copyCount = 0;
        int[] q = new int[16];
        for (int offset = 0, end = oldEnd; offset < end; offset += 4) {
            int len = oldHashArea[offset + 3];
            if (len == 0) {
                continue;
            }
            ++copyCount;
            String name = oldNames[offset >> 2];
            switch(len) {
                case 1:
                    q[0] = oldHashArea[offset];
                    addName(name, q, 1);
                    break;
                case 2:
                    q[0] = oldHashArea[offset];
                    q[1] = oldHashArea[offset + 1];
                    addName(name, q, 2);
                    break;
                case 3:
                    q[0] = oldHashArea[offset];
                    q[1] = oldHashArea[offset + 1];
                    q[2] = oldHashArea[offset + 2];
                    addName(name, q, 3);
                    break;
                default:
                    if (len > q.length) {
                        q = new int[len];
                    }
                    int qoff = oldHashArea[offset + 1];
                    System.arraycopy(oldHashArea, qoff, q, 0, len);
                    addName(name, q, len);
                    break;
            }
        }
        if (copyCount != oldCount) {
            throw new IllegalStateException("Failed rehash(): old count=" + oldCount + ", copyCount=" + copyCount);
        }
    }

    /**
     * Helper method called to empty all shared symbols, but to leave
     * arrays allocated
     */
    private void nukeSymbols(boolean fill) {
        _count = 0;
        _spilloverEnd = _spilloverStart();
        _longNameOffset = _hashSize << 3;
        if (fill) {
            Arrays.fill(_hashArea, 0);
            Arrays.fill(_names, null);
        }
    }

    /*
    /**********************************************************
    /* Helper methods
    /**********************************************************
     */
    /**
     * Helper method that calculates start of the spillover area
     */
    private final int _spilloverStart() {
        int offset = _hashSize;
        return (offset << 3) - offset;
    }

    protected void _reportTooManyCollisions() {
        if (_hashSize <= 1024) {
            return;
        }
        throw new IllegalStateException("Spill-over slots in symbol table with " + _count + " entries, hash area of " + _hashSize + " slots is now full (all " + (_hashSize >> 3) + " slots -- suspect a DoS attack based on hash collisions." + " You can disable the check via `JsonFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW`");
    }

    static int _calcTertiaryShift(int primarySlots) {
        int tertSlots = (primarySlots) >> 2;
        if (tertSlots < 64) {
            return 4;
        }
        if (tertSlots <= 256) {
            return 5;
        }
        if (tertSlots <= 1024) {
            return 6;
        }
        return 7;
    }

    /**
     * Immutable value class used for sharing information as efficiently
     * as possible, by only require synchronization of reference manipulation
     * but not access to contents.
     * 
     * @since 2.1
     */
    private static final class TableInfo {

        public final int size;

        public final int count;

        public final int tertiaryShift;

        public final int[] mainHash;

        public final String[] names;

        public final int spilloverEnd;

        public final int longNameOffset;

        public TableInfo(int size, int count, int tertiaryShift, int[] mainHash, String[] names, int spilloverEnd, int longNameOffset) {
            this.size = size;
            this.count = count;
            this.tertiaryShift = tertiaryShift;
            this.mainHash = mainHash;
            this.names = names;
            this.spilloverEnd = spilloverEnd;
            this.longNameOffset = longNameOffset;
        }

        public TableInfo(ByteQuadsCanonicalizer src) {
            size = src._hashSize;
            count = src._count;
            tertiaryShift = src._tertiaryShift;
            mainHash = src._hashArea;
            names = src._names;
            spilloverEnd = src._spilloverEnd;
            longNameOffset = src._longNameOffset;
        }

        public static TableInfo createInitial(int sz) {
            int hashAreaSize = sz << 3;
            int tertShift = _calcTertiaryShift(sz);
            return new TableInfo(sz, 0, tertShift, new int[hashAreaSize], new String[sz << 1], hashAreaSize - sz, hashAreaSize);
        }
    }
}

