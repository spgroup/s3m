package com.fasterxml.jackson.databind.type;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.util.ArrayBuilders;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.databind.util.LRUMap;

/**
 * Class used for creating concrete {@link JavaType} instances,
 * given various inputs.
 *<p>
 * Instances of this class are accessible using {@link com.fasterxml.jackson.databind.ObjectMapper}
 * as well as many objects it constructs (like
* {@link com.fasterxml.jackson.databind.DeserializationConfig} and
 * {@link com.fasterxml.jackson.databind.SerializationConfig})),
 * but usually those objects also 
 * expose convenience methods (<code>constructType</code>).
 * So, you can do for example:
 *<pre>
 *   JavaType stringType = mapper.constructType(String.class);
 *</pre>
 * However, more advanced methods are only exposed by factory so that you
 * may need to use:
 *<pre>
 *   JavaType stringCollection = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
 *</pre>
 */
@SuppressWarnings({ "rawtypes" })
public final class TypeFactory implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private static final JavaType[] NO_TYPES = new JavaType[0];

    /**
     * Globally shared singleton. Not accessed directly; non-core
     * code should use per-ObjectMapper instance (via configuration objects).
     * Core Jackson code uses {@link #defaultInstance} for accessing it.
     */
    protected static final TypeFactory instance = new TypeFactory();

    protected static final TypeBindings EMPTY_BINDINGS = TypeBindings.emptyBindings();

    /*
    /**********************************************************
    /* Constants for "well-known" classes
    /**********************************************************
     */
    // // // Let's assume that a small set of core primitive/basic types
    // // // will not be modified, and can be freely shared to streamline
    // // // parts of processing
    private static final Class<?> CLS_STRING = String.class;

    private static final Class<?> CLS_OBJECT = Object.class;

    private static final Class<?> CLS_COMPARABLE = Comparable.class;

    private static final Class<?> CLS_CLASS = Class.class;

    private static final Class<?> CLS_ENUM = Enum.class;

    private static final Class<?> CLS_BOOL = Boolean.TYPE;

    private static final Class<?> CLS_INT = Integer.TYPE;

    private static final Class<?> CLS_LONG = Long.TYPE;

    /*
    /**********************************************************
    /* Cached pre-constructed JavaType instances
    /**********************************************************
     */
    // note: these are primitive, hence no super types
    protected static final SimpleType CORE_TYPE_BOOL = new SimpleType(CLS_BOOL);

    protected static final SimpleType CORE_TYPE_INT = new SimpleType(CLS_INT);

    protected static final SimpleType CORE_TYPE_LONG = new SimpleType(CLS_LONG);

    // and as to String... well, for now, ignore its super types
    protected static final SimpleType CORE_TYPE_STRING = new SimpleType(CLS_STRING);

    // @since 2.7
    protected static final SimpleType CORE_TYPE_OBJECT = new SimpleType(CLS_OBJECT);

    /**
     * Cache {@link Comparable} because it is both parameteric (relatively costly to
     * resolve) and mostly useless (no special handling), better handle directly
     *
     * @since 2.7
     */
    protected static final SimpleType CORE_TYPE_COMPARABLE = new SimpleType(CLS_COMPARABLE);

    /**
     * Cache {@link Enum} because it is parametric AND self-referential (costly to
     * resolve) and useless in itself (no special handling).
     *
     * @since 2.7
     */
    protected static final SimpleType CORE_TYPE_ENUM = new SimpleType(CLS_ENUM);

    /**
     * Cache {@link Class} because it is nominally parametric, but has no really
     * useful information.
     *
     * @since 2.7
     */
    protected static final SimpleType CORE_TYPE_CLASS = new SimpleType(CLS_CLASS);

    /**
     * Since type resolution can be expensive (specifically when resolving
     * actual generic types), we will use small cache to avoid repetitive
     * resolution of core types
     */
    protected final LRUMap<Object, JavaType> _typeCache = new LRUMap<Object, JavaType>(16, 100);

    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */
    /**
     * Registered {@link TypeModifier}s: objects that can change details
     * of {@link JavaType} instances factory constructs.
     */
    protected final TypeModifier[] _modifiers;

    protected final TypeParser _parser;

    /**
     * ClassLoader used by this factory [databind#624].
     */
    protected final ClassLoader _classLoader;

    private TypeFactory() {
        _parser = new TypeParser(this);
        _modifiers = null;
        _classLoader = null;
    }

    protected TypeFactory(TypeParser p, TypeModifier[] mods) {
        this(p, mods, null);
    }

    protected TypeFactory(TypeParser p, TypeModifier[] mods, ClassLoader classLoader) {
        _parser = p.withFactory(this);
        _modifiers = mods;
        _classLoader = classLoader;
    }

    public TypeFactory withModifier(TypeModifier mod) {
        if (mod == null) {
            return new TypeFactory(_parser, _modifiers, _classLoader);
        }
        if (_modifiers == null) {
            return new TypeFactory(_parser, new TypeModifier[] { mod }, _classLoader);
        }
        return new TypeFactory(_parser, ArrayBuilders.insertInListNoDup(_modifiers, mod), _classLoader);
    }

    public TypeFactory withClassLoader(ClassLoader classLoader) {
        return new TypeFactory(_parser, _modifiers, classLoader);
    }

    /**
     * Method used to access the globally shared instance, which has
     * no custom configuration. Used by <code>ObjectMapper</code> to
     * get the default factory when constructed.
     */
    public static TypeFactory defaultInstance() {
        return instance;
    }

    /**
     * Method that will clear up any cached type definitions that may
     * be cached by this {@link TypeFactory} instance.
     * This method should not be commonly used, that is, only use it
     * if you know there is a problem with retention of type definitions;
     * the most likely (and currently only known) problem is retention
     * of {@link Class} instances via {@link JavaType} reference.
     * 
     * @since 2.4.1
     */
    public void clearCache() {
        _typeCache.clear();
    }

    public ClassLoader getClassLoader() {
        return _classLoader;
    }

    /*
    /**********************************************************
    /* Static methods for non-instance-specific functionality
    /**********************************************************
     */
    /**
     * Method for constructing a marker type that indicates missing generic
     * type information, which is handled same as simple type for
     * <code>java.lang.Object</code>.
     */
    public static JavaType unknownType() {
        return defaultInstance()._unknownType();
    }

    /**
     * Static helper method that can be called to figure out type-erased
     * call for given JDK type. It can be called statically since type resolution
     * process can never change actual type-erased class; thereby static
     * default instance is used for determination.
     */
    public static Class<?> rawClass(Type t) {
        if (t instanceof Class<?>) {
            return (Class<?>) t;
        }
        return defaultInstance().constructType(t).getRawClass();
    }

    /*
    /**********************************************************
    /* Low-level helper methods
    /**********************************************************
     */
    /**
     * Low-level lookup method moved from {@link com.fasterxml.jackson.databind.util.ClassUtil},
     * to allow for overriding of lookup functionality in environments like OSGi.
     *
     * @since 2.6
     */
    public Class<?> findClass(String className) throws ClassNotFoundException {
        if (className.indexOf('.') < 0) {
            Class<?> prim = _findPrimitive(className);
            if (prim != null) {
                return prim;
            }
        }
        Throwable prob = null;
        ClassLoader loader = this.getClassLoader();
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader != null) {
            try {
                return classForName(className, true, loader);
            } catch (Exception e) {
                prob = ClassUtil.getRootCause(e);
            }
        }
        try {
            return classForName(className);
        } catch (Exception e) {
            if (prob == null) {
                prob = ClassUtil.getRootCause(e);
            }
        }
        if (prob instanceof RuntimeException) {
            throw (RuntimeException) prob;
        }
        throw new ClassNotFoundException(prob.getMessage(), prob);
    }

    protected Class<?> classForName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, true, loader);
    }

    protected Class<?> classForName(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    protected Class<?> _findPrimitive(String className) {
        if ("int".equals(className)) {
            return Integer.TYPE;
        }
        if ("long".equals(className)) {
            return Long.TYPE;
        }
        if ("float".equals(className)) {
            return Float.TYPE;
        }
        if ("double".equals(className)) {
            return Double.TYPE;
        }
        if ("boolean".equals(className)) {
            return Boolean.TYPE;
        }
        if ("byte".equals(className)) {
            return Byte.TYPE;
        }
        if ("char".equals(className)) {
            return Character.TYPE;
        }
        if ("short".equals(className)) {
            return Short.TYPE;
        }
        if ("void".equals(className)) {
            return Void.TYPE;
        }
        return null;
    }

    /*
    /**********************************************************
    /* Type conversion, parameterization resolution methods
    /**********************************************************
     */
    /**
     * Factory method for creating a subtype of given base type, as defined
     * by specified subclass; but retaining generic type information if any.
     * Can be used, for example, to get equivalent of "HashMap&lt;String,Integer&gt;"
     * from "Map&lt;String,Integer&gt;" by giving <code>HashMap.class</code>
     * as subclass.
     */
    public JavaType constructSpecializedType(JavaType baseType, Class<?> subclass) {
        final Class<?> rawBase = baseType.getRawClass();
        if (rawBase == subclass) {
            return baseType;
        }
        JavaType newType;
        do {
            if (rawBase == Object.class) {
                newType = _fromClass(null, subclass, TypeBindings.emptyBindings());
                break;
            }
            if (!rawBase.isAssignableFrom(subclass)) {
                throw new IllegalArgumentException(String.format("Class %s not subtype of %s", subclass.getName(), baseType));
            }
            if (baseType.getBindings().isEmpty()) {
                newType = _fromClass(null, subclass, TypeBindings.emptyBindings());
                break;
            }
            if (baseType.isContainerType()) {
                if (baseType.isMapLikeType()) {
                    if ((subclass == HashMap.class) || (subclass == LinkedHashMap.class) || (subclass == EnumMap.class) || (subclass == TreeMap.class)) {
                        newType = _fromClass(null, subclass, TypeBindings.create(subclass, baseType.getKeyType(), baseType.getContentType()));
                        break;
                    }
                } else {
                    if (baseType.isCollectionLikeType()) {
                        if ((subclass == ArrayList.class) || (subclass == LinkedList.class) || (subclass == HashSet.class) || (subclass == TreeSet.class)) {
                            newType = _fromClass(null, subclass, TypeBindings.create(subclass, baseType.getContentType()));
                            break;
                        }
                        if (rawBase == EnumSet.class) {
                            return baseType;
                        }
                    }
                }
            }
            int typeParamCount = subclass.getTypeParameters().length;
            if (typeParamCount == 0) {
                newType = _fromClass(null, subclass, TypeBindings.emptyBindings());
                break;
            }
            TypeBindings tb = _bindingsForSubtype(baseType, typeParamCount, subclass);
            if (baseType.isInterface()) {
                newType = baseType.refine(subclass, tb, null, new JavaType[] { baseType });
            } else {
                newType = baseType.refine(subclass, tb, baseType, NO_TYPES);
            }
            if (newType == null) {
                newType = _fromClass(null, subclass, tb);
            }
        } while (false);
        return newType;
    }

    private TypeBindings _bindingsForSubtype(JavaType baseType, int typeParamCount, Class<?> subclass) {
        int baseCount = baseType.containedTypeCount();
        if (baseCount == typeParamCount) {
            if (typeParamCount == 1) {
                return TypeBindings.create(subclass, baseType.containedType(0));
            }
            if (typeParamCount == 2) {
                return TypeBindings.create(subclass, baseType.containedType(0), baseType.containedType(1));
            }
            List<JavaType> types = new ArrayList<JavaType>(baseCount);
            for (int i = 0; i < baseCount; ++i) {
                types.add(baseType.containedType(i));
            }
            return TypeBindings.create(subclass, types);
        }
        return TypeBindings.emptyBindings();
    }

    /**
     * Method similar to {@link #constructSpecializedType}, but that creates a
     * less-specific type of given type. Usually this is as simple as simply
     * finding super-type with type erasure of <code>superClass</code>, but
     * there may be need for some additional work-arounds.
     *
     * @param superClass
     *
     * @since 2.7
     */
    public JavaType constructGeneralizedType(JavaType baseType, Class<?> superClass) {
        final Class<?> rawBase = baseType.getRawClass();
        if (rawBase == superClass) {
            return baseType;
        }
        JavaType superType = baseType.findSuperType(superClass);
        if (superType == null) {
            if (!superClass.isAssignableFrom(rawBase)) {
                throw new IllegalArgumentException(String.format("Class %s not a super-type of %s", superClass.getName(), baseType));
            }
            throw new IllegalArgumentException(String.format("Internal error: class %s not included as super-type for %s", superClass.getName(), baseType));
        }
        return superType;
    }

    /**
     * Factory method for constructing a {@link JavaType} out of its canonical
     * representation (see {@link JavaType#toCanonical()}).
     * 
     * @param canonical Canonical string representation of a type
     * 
     * @throws IllegalArgumentException If canonical representation is malformed,
     *   or class that type represents (including its generic parameters) is
     *   not found
     */
    public JavaType constructFromCanonical(String canonical) throws IllegalArgumentException {
        return _parser.parse(canonical);
    }

    /**
     * Method that is to figure out actual type parameters that given
     * class binds to generic types defined by given (generic)
     * interface or class.
     * This could mean, for example, trying to figure out
     * key and value types for Map implementations.
     * 
     * @param type Sub-type (leaf type) that implements <code>expType</code>
     */
    public JavaType[] findTypeParameters(JavaType type, Class<?> expType) {
        JavaType match = type.findSuperType(expType);
        if (match == null) {
            return NO_TYPES;
        }
        return match.getBindings().typeParameterArray();
    }

    /**
     * @deprecated Since 2.7 resolve raw type first, then find type parameters
     */
    @Deprecated
    public JavaType[] findTypeParameters(Class<?> clz, Class<?> expType, TypeBindings bindings) {
        return findTypeParameters(constructType(clz, bindings), expType);
    }

    /**
     * @deprecated Since 2.7 resolve raw type first, then find type parameters
     */
    @Deprecated
    public JavaType[] findTypeParameters(Class<?> clz, Class<?> expType) {
        return findTypeParameters(constructType(clz), expType);
    }

    /**
     * Method that can be called to figure out more specific of two
     * types (if they are related; that is, one implements or extends the
     * other); or if not related, return the primary type.
     * 
     * @param type1 Primary type to consider
     * @param type2 Secondary type to consider
     * 
     * @since 2.2
     */
    public JavaType moreSpecificType(JavaType type1, JavaType type2) {
        if (type1 == null) {
            return type2;
        }
        if (type2 == null) {
            return type1;
        }
        Class<?> raw1 = type1.getRawClass();
        Class<?> raw2 = type2.getRawClass();
        if (raw1 == raw2) {
            return type1;
        }
        if (raw1.isAssignableFrom(raw2)) {
            return type2;
        }
        return type1;
    }

    /*
    /**********************************************************
    /* Public factory methods
    /**********************************************************
     */
    public JavaType constructType(Type type) {
        return _fromAny(null, type, EMPTY_BINDINGS);
    }

    public JavaType constructType(Type type, TypeBindings bindings) {
        return _fromAny(null, type, bindings);
    }

    public JavaType constructType(TypeReference<?> typeRef) {
        return _fromAny(null, typeRef.getType(), EMPTY_BINDINGS);
    }

    /**
     * @deprecated Since 2.7 (accidentally removed in 2.7.0; added back in 2.7.1)
     */
    @Deprecated
    public JavaType constructType(Type type, Class<?> contextClass) {
        TypeBindings bindings = (contextClass == null) ? TypeBindings.emptyBindings() : constructType(contextClass).getBindings();
        return _fromAny(null, type, bindings);
    }

    /**
     * @deprecated Since 2.7 (accidentally removed in 2.7.0; added back in 2.7.1)
     */
    @Deprecated
    public JavaType constructType(Type type, JavaType contextType) {
        TypeBindings bindings = (contextType == null) ? TypeBindings.emptyBindings() : contextType.getBindings();
        return _fromAny(null, type, bindings);
    }

    /*
    /**********************************************************
    /* Direct factory methods
    /**********************************************************
     */
    /**
     * Method for constructing an {@link ArrayType}.
     *<p>
     * NOTE: type modifiers are NOT called on array type itself; but are called
     * for element type (and other contained types)
     */
    public ArrayType constructArrayType(Class<?> elementType) {
        return ArrayType.construct(_fromAny(null, elementType, null), null);
    }

    /**
     * Method for constructing an {@link ArrayType}.
     *<p>
     * NOTE: type modifiers are NOT called on array type itself; but are called
     * for contained types.
     */
    public ArrayType constructArrayType(JavaType elementType) {
        return ArrayType.construct(elementType, null);
    }

    /**
     * Method for constructing a {@link CollectionType}.
     *<p>
     * NOTE: type modifiers are NOT called on Collection type itself; but are called
     * for contained types.
     */
    public CollectionType constructCollectionType(Class<? extends Collection> collectionClass, Class<?> elementClass) {
        return constructCollectionType(collectionClass, _fromClass(null, elementClass, EMPTY_BINDINGS));
    }

    /**
     * Method for constructing a {@link CollectionType}.
     *<p>
     * NOTE: type modifiers are NOT called on Collection type itself; but are called
     * for contained types.
     */
    public CollectionType constructCollectionType(Class<? extends Collection> collectionClass, JavaType elementType) {
        return (CollectionType) _fromClass(null, collectionClass, TypeBindings.create(collectionClass, elementType));
    }

    /**
     * Method for constructing a {@link CollectionLikeType}.
     *<p>
     * NOTE: type modifiers are NOT called on constructed type itself; but are called
     * for contained types.
     */
    public CollectionLikeType constructCollectionLikeType(Class<?> collectionClass, Class<?> elementClass) {
        return constructCollectionLikeType(collectionClass, _fromClass(null, elementClass, EMPTY_BINDINGS));
    }

    /**
     * Method for constructing a {@link CollectionLikeType}.
     *<p>
     * NOTE: type modifiers are NOT called on constructed type itself; but are called
     * for contained types.
     */
    public CollectionLikeType constructCollectionLikeType(Class<?> collectionClass, JavaType elementType) {
        JavaType type = _fromClass(null, collectionClass, TypeBindings.createIfNeeded(collectionClass, elementType));
        if (type instanceof CollectionLikeType) {
            return (CollectionLikeType) type;
        }
        return CollectionLikeType.upgradeFrom(type, elementType);
    }

    /**
     * Method for constructing a {@link MapType} instance
     *<p>
     * NOTE: type modifiers are NOT called on constructed type itself; but are called
     * for contained types.
     */
    public MapType constructMapType(Class<? extends Map> mapClass, Class<?> keyClass, Class<?> valueClass) {
        JavaType kt, vt;
        if (mapClass == Properties.class) {
            kt = vt = CORE_TYPE_STRING;
        } else {
            kt = _fromClass(null, keyClass, EMPTY_BINDINGS);
            vt = _fromClass(null, valueClass, EMPTY_BINDINGS);
        }
        return constructMapType(mapClass, kt, vt);
    }

    /**
     * Method for constructing a {@link MapType} instance
     *<p>
     * NOTE: type modifiers are NOT called on constructed type itself; but are called
     * for contained types.
     */
    public MapType constructMapType(Class<? extends Map> mapClass, JavaType keyType, JavaType valueType) {
        return (MapType) _fromClass(null, mapClass, TypeBindings.create(mapClass, new JavaType[] { keyType, valueType }));
    }

    /**
     * Method for constructing a {@link MapLikeType} instance
     *<p>
     * NOTE: type modifiers are NOT called on constructed type itself; but are called
     * for contained types.
     */
    public MapLikeType constructMapLikeType(Class<?> mapClass, Class<?> keyClass, Class<?> valueClass) {
        return constructMapLikeType(mapClass, _fromClass(null, keyClass, EMPTY_BINDINGS), _fromClass(null, valueClass, EMPTY_BINDINGS));
    }

    /**
     * Method for constructing a {@link MapLikeType} instance
     *<p>
     * NOTE: type modifiers are NOT called on constructed type itself; but are called
     * for contained types.
     */
    public MapLikeType constructMapLikeType(Class<?> mapClass, JavaType keyType, JavaType valueType) {
        JavaType type = _fromClass(null, mapClass, TypeBindings.createIfNeeded(mapClass, new JavaType[] { keyType, valueType }));
        if (type instanceof MapLikeType) {
            return (MapLikeType) type;
        }
        return MapLikeType.upgradeFrom(type, keyType, valueType);
    }

    /**
     * Method for constructing a type instance with specified parameterization.
     *<p>
     * NOTE: was briefly deprecated for 2.6.
     */
    public JavaType constructSimpleType(Class<?> rawType, JavaType[] parameterTypes) {
        return _fromClass(null, rawType, TypeBindings.create(rawType, parameterTypes));
    }

    /**
     * Method for constructing a type instance with specified parameterization.
     *
     * @since 2.6
     *
     * @deprecated Since 2.7
     */
    @Deprecated
    public JavaType constructSimpleType(Class<?> rawType, Class<?> parameterTarget, JavaType[] parameterTypes) {
        return constructSimpleType(rawType, parameterTypes);
    }

    /**
     * @since 2.6
     */
    public JavaType constructReferenceType(Class<?> rawType, JavaType referredType) {
        return ReferenceType.construct(rawType, null, null, null, referredType);
    }

    /**
     * Method that use by core Databind functionality, and that should NOT be called
     * by application code outside databind package.
     *<p> 
     * Unchecked here not only means that no checks are made as to whether given class
     * might be non-simple type (like {@link CollectionType}) but also that most of supertype
     * information is not gathered. This means that unless called on primitive types or
     * {@link java.lang.String}, results are probably not what you want to use.
     *
     * @deprecated Since 2.8, to indicate users should never call this method.
     */
    @Deprecated
    public JavaType uncheckedSimpleType(Class<?> cls) {
        return _constructSimple(cls, EMPTY_BINDINGS, null, null);
    }

    /**
     * Factory method for constructing {@link JavaType} that
     * represents a parameterized type. For example, to represent
     * type <code>List&lt;Set&lt;Integer>></code>, you could
     * call
     *<pre>
     *  JavaType inner = TypeFactory.constructParametrizedType(Set.class, Set.class, Integer.class);
     *  return TypeFactory.constructParametrizedType(ArrayList.class, List.class, inner);
     *</pre>
     *<p>
     * The reason for first two arguments to be separate is that parameterization may
     * apply to a super-type. For example, if generic type was instead to be
     * constructed for <code>ArrayList&lt;Integer></code>, the usual call would be:
     *<pre>
     *  TypeFactory.constructParametrizedType(ArrayList.class, List.class, Integer.class);
     *</pre>
     * since parameterization is applied to {@link java.util.List}.
     * In most cases distinction does not matter, but there are types where it does;
     * one such example is parameterization of types that implement {@link java.util.Iterator}.
     *<p>
     * NOTE: type modifiers are NOT called on constructed type.
     * 
     * @param parametrized Actual full type
     * @param parameterClasses Type parameters to apply
     *
     * @since 2.5 NOTE: was briefly deprecated for 2.6
     */
    public JavaType constructParametricType(Class<?> parametrized, Class<?>... parameterClasses) {
        int len = parameterClasses.length;
        JavaType[] pt = new JavaType[len];
        for (int i = 0; i < len; ++i) {
            pt[i] = _fromClass(null, parameterClasses[i], null);
        }
        return constructParametricType(parametrized, pt);
    }

    /**
     * Factory method for constructing {@link JavaType} that
     * represents a parameterized type. For example, to represent
     * type <code>List&lt;Set&lt;Integer>></code>, you could
     * call
     *<pre>
     *  JavaType inner = TypeFactory.constructParametrizedType(Set.class, Set.class, Integer.class);
     *  return TypeFactory.constructParametrizedType(ArrayList.class, List.class, inner);
     *</pre>
     *<p>
     * The reason for first two arguments to be separate is that parameterization may
     * apply to a super-type. For example, if generic type was instead to be
     * constructed for <code>ArrayList&lt;Integer></code>, the usual call would be:
     *<pre>
     *  TypeFactory.constructParametrizedType(ArrayList.class, List.class, Integer.class);
     *</pre>
     * since parameterization is applied to {@link java.util.List}.
     * In most cases distinction does not matter, but there are types where it does;
     * one such example is parameterization of types that implement {@link java.util.Iterator}.
     *<p>
     * NOTE: type modifiers are NOT called on constructed type.
     * 
     * @param rawType Actual type-erased type
     * @param parameterTypes Type parameters to apply
     * 
     * @since 2.5 NOTE: was briefly deprecated for 2.6
     */
    public JavaType constructParametricType(Class<?> rawType, JavaType... parameterTypes) {
        return _fromClass(null, rawType, TypeBindings.create(rawType, parameterTypes));
    }

    /**
     * @since 2.5 -- but will probably deprecated in 2.7 or 2.8 (not needed with 2.7)
     */
    public JavaType constructParametrizedType(Class<?> parametrized, Class<?> parametersFor, JavaType... parameterTypes) {
        return constructParametricType(parametrized, parameterTypes);
    }

    /**
     * @since 2.5 -- but will probably deprecated in 2.7 or 2.8 (not needed with 2.7)
     */
    public JavaType constructParametrizedType(Class<?> parametrized, Class<?> parametersFor, Class<?>... parameterClasses) {
        return constructParametricType(parametrized, parameterClasses);
    }

    /*
    /**********************************************************
    /* Direct factory methods for "raw" variants, used when
    /* parameterization is unknown
    /**********************************************************
     */
    /**
     * Method that can be used to construct "raw" Collection type; meaning that its
     * parameterization is unknown.
     * This is similar to using <code>Object.class</code> parameterization,
     * and is equivalent to calling:
     *<pre>
     *  typeFactory.constructCollectionType(collectionClass, typeFactory.unknownType());
     *</pre>
     *<p>
     * This method should only be used if parameterization is completely unavailable.
     */
    public CollectionType constructRawCollectionType(Class<? extends Collection> collectionClass) {
        return constructCollectionType(collectionClass, unknownType());
    }

    /**
     * Method that can be used to construct "raw" Collection-like type; meaning that its
     * parameterization is unknown.
     * This is similar to using <code>Object.class</code> parameterization,
     * and is equivalent to calling:
     *<pre>
     *  typeFactory.constructCollectionLikeType(collectionClass, typeFactory.unknownType());
     *</pre>
     *<p>
     * This method should only be used if parameterization is completely unavailable.
     */
    public CollectionLikeType constructRawCollectionLikeType(Class<?> collectionClass) {
        return constructCollectionLikeType(collectionClass, unknownType());
    }

    /**
     * Method that can be used to construct "raw" Map type; meaning that its
     * parameterization is unknown.
     * This is similar to using <code>Object.class</code> parameterization,
     * and is equivalent to calling:
     *<pre>
     *  typeFactory.constructMapType(collectionClass, typeFactory.unknownType(), typeFactory.unknownType());
     *</pre>
     *<p>
     * This method should only be used if parameterization is completely unavailable.
     */
    public MapType constructRawMapType(Class<? extends Map> mapClass) {
        return constructMapType(mapClass, unknownType(), unknownType());
    }

    /**
     * Method that can be used to construct "raw" Map-like type; meaning that its
     * parameterization is unknown.
     * This is similar to using <code>Object.class</code> parameterization,
     * and is equivalent to calling:
     *<pre>
     *  typeFactory.constructMapLikeType(collectionClass, typeFactory.unknownType(), typeFactory.unknownType());
     *</pre>
     *<p>
     * This method should only be used if parameterization is completely unavailable.
     */
    public MapLikeType constructRawMapLikeType(Class<?> mapClass) {
        return constructMapLikeType(mapClass, unknownType(), unknownType());
    }

    /*
    /**********************************************************
    /* Low-level factory methods
    /**********************************************************
     */
    private JavaType _mapType(Class<?> rawClass, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
        JavaType kt, vt;
        if (rawClass == Properties.class) {
            kt = vt = CORE_TYPE_STRING;
        } else {
            List<JavaType> typeParams = bindings.getTypeParameters();
            switch(typeParams.size()) {
                case 0:
                    kt = vt = _unknownType();
                    break;
                case 2:
                    kt = typeParams.get(0);
                    vt = typeParams.get(1);
                    break;
                default:
                    throw new IllegalArgumentException("Strange Map type " + rawClass.getName() + ": can not determine type parameters");
            }
        }
        return MapType.construct(rawClass, bindings, superClass, superInterfaces, kt, vt);
    }

    private JavaType _collectionType(Class<?> rawClass, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
        List<JavaType> typeParams = bindings.getTypeParameters();
        JavaType ct;
        if (typeParams.isEmpty()) {
            ct = _unknownType();
        } else {
            if (typeParams.size() == 1) {
                ct = typeParams.get(0);
            } else {
                throw new IllegalArgumentException("Strange Collection type " + rawClass.getName() + ": can not determine type parameters");
            }
        }
        return CollectionType.construct(rawClass, bindings, superClass, superInterfaces, ct);
    }

    private JavaType _referenceType(Class<?> rawClass, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
        List<JavaType> typeParams = bindings.getTypeParameters();
        JavaType ct;
        if (typeParams.isEmpty()) {
            ct = _unknownType();
        } else {
            if (typeParams.size() == 1) {
                ct = typeParams.get(0);
            } else {
                throw new IllegalArgumentException("Strange Reference type " + rawClass.getName() + ": can not determine type parameters");
            }
        }
        return ReferenceType.construct(rawClass, bindings, superClass, superInterfaces, ct);
    }

    /**
     * Factory method to call when no special {@link JavaType} is needed,
     * no generic parameters are passed. Default implementation may check
     * pre-constructed values for "well-known" types, but if none found
     * will simply call {@link #_newSimpleType}
     *
     * @since 2.7
     */
    protected JavaType _constructSimple(Class<?> raw, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
        if (bindings.isEmpty()) {
            JavaType result = _findWellKnownSimple(raw);
            if (result != null) {
                return result;
            }
        }
        return _newSimpleType(raw, bindings, superClass, superInterfaces);
    }

    /**
     * Factory method that is to create a new {@link SimpleType} with no
     * checks whatsoever. Default implementation calls the single argument
     * constructor of {@link SimpleType}.
     *
     * @since 2.7
     */
    protected JavaType _newSimpleType(Class<?> raw, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
        return new SimpleType(raw, bindings, superClass, superInterfaces);
    }

    protected JavaType _unknownType() {
        return CORE_TYPE_OBJECT;
    }

    /**
     * Helper method called to see if requested, non-generic-parameterized
     * type is one of common, "well-known" types, instances of which are
     * pre-constructed and do not need dynamic caching.
     *
     * @since 2.7
     */
    protected JavaType _findWellKnownSimple(Class<?> clz) {
        if (clz.isPrimitive()) {
            if (clz == CLS_BOOL) {
                return CORE_TYPE_BOOL;
            }
            if (clz == CLS_INT) {
                return CORE_TYPE_INT;
            }
            if (clz == CLS_LONG) {
                return CORE_TYPE_LONG;
            }
        } else {
            if (clz == CLS_STRING) {
                return CORE_TYPE_STRING;
            }
            if (clz == CLS_OBJECT) {
                return CORE_TYPE_OBJECT;
            }
        }
        return null;
    }

    /*
    /**********************************************************
    /* Actual type resolution, traversal
    /**********************************************************
     */
    /**
     * Factory method that can be used if type information is passed
     * as Java typing returned from <code>getGenericXxx</code> methods
     * (usually for a return or argument type).
     */
    protected JavaType _fromAny(ClassStack context, Type type, TypeBindings bindings) {
        JavaType resultType;
        if (type instanceof Class<?>) {
            resultType = _fromClass(context, (Class<?>) type, EMPTY_BINDINGS);
        } else {
            if (type instanceof ParameterizedType) {
                resultType = _fromParamType(context, (ParameterizedType) type, bindings);
            } else {
                if (type instanceof JavaType) {
                    return (JavaType) type;
                } else {
                    if (type instanceof GenericArrayType) {
                        resultType = _fromArrayType(context, (GenericArrayType) type, bindings);
                    } else {
                        if (type instanceof TypeVariable<?>) {
                            resultType = _fromVariable(context, (TypeVariable<?>) type, bindings);
                        } else {
                            if (type instanceof WildcardType) {
                                resultType = _fromWildcard(context, (WildcardType) type, bindings);
                            } else {
                                throw new IllegalArgumentException("Unrecognized Type: " + ((type == null) ? "[null]" : type.toString()));
                            }
                        }
                    }
                }
            }
        }
        if (_modifiers != null) {
            TypeBindings b = resultType.getBindings();
            if (b == null) {
                b = EMPTY_BINDINGS;
            }
            for (TypeModifier mod : _modifiers) {
                JavaType t = mod.modifyType(resultType, type, b, this);
                if (t == null) {
                    throw new IllegalStateException(String.format("TypeModifier %s (of type %s) return null for type %s", mod, mod.getClass().getName(), resultType));
                }
                resultType = t;
            }
        }
        return resultType;
    }

    /**
     * @param bindings Mapping of formal parameter declarations (for generic
     *   types) into actual types
     */
    protected JavaType _fromClass(ClassStack context, Class<?> rawType, TypeBindings bindings) {
        JavaType result = _findWellKnownSimple(rawType);
        if (result != null) {
            return result;
        }
        final Object key;
        if ((bindings == null) || bindings.isEmpty()) {
            key = rawType;
            if (result != null) {
                return result;
            }
        } else {
            key = null;
        }
        if (context == null) {
            context = new ClassStack(rawType);
        } else {
            ClassStack prev = context.find(rawType);
            if (prev != null) {
                ResolvedRecursiveType selfRef = new ResolvedRecursiveType(rawType, EMPTY_BINDINGS);
                prev.addSelfReference(selfRef);
                return selfRef;
            }
            context = context.child(rawType);
        }
        if (rawType.isArray()) {
            result = ArrayType.construct(_fromAny(context, rawType.getComponentType(), bindings), bindings);
        } else {
            JavaType superClass;
            JavaType[] superInterfaces;
            if (rawType.isInterface()) {
                superClass = null;
                superInterfaces = _resolveSuperInterfaces(context, rawType, bindings);
            } else {
                superClass = _resolveSuperClass(context, rawType, bindings);
                superInterfaces = _resolveSuperInterfaces(context, rawType, bindings);
            }
            if (rawType == Properties.class) {
                result = MapType.construct(rawType, bindings, superClass, superInterfaces, CORE_TYPE_STRING, CORE_TYPE_STRING);
            } else {
                if (superClass != null) {
                    result = superClass.refine(rawType, bindings, superClass, superInterfaces);
                }
            }
            if (result == null) {
                result = _fromWellKnownClass(context, rawType, bindings, superClass, superInterfaces);
                if (result == null) {
                    result = _fromWellKnownInterface(context, rawType, bindings, superClass, superInterfaces);
                    if (result == null) {
                        result = _newSimpleType(rawType, bindings, superClass, superInterfaces);
                    }
                }
            }
        }
        context.resolveSelfReferences(result);
        if (key != null) {
            _typeCache.putIfAbsent(key, result);
        }
        return result;
    }

    protected JavaType _resolveSuperClass(ClassStack context, Class<?> rawType, TypeBindings parentBindings) {
        Type parent = ClassUtil.getGenericSuperclass(rawType);
        if (parent == null) {
            return null;
        }
        return _fromAny(context, parent, parentBindings);
    }

    protected JavaType[] _resolveSuperInterfaces(ClassStack context, Class<?> rawType, TypeBindings parentBindings) {
        Type[] types = ClassUtil.getGenericInterfaces(rawType);
        if (types == null || types.length == 0) {
            return NO_TYPES;
        }
        int len = types.length;
        JavaType[] resolved = new JavaType[len];
        for (int i = 0; i < len; ++i) {
            Type type = types[i];
            resolved[i] = _fromAny(context, type, parentBindings);
        }
        return resolved;
    }

    /**
     * Helper class used to check whether exact class for which type is being constructed
     * is one of well-known base interfaces or classes that indicates alternate
     * {@link JavaType} implementation.
     */
    protected JavaType _fromWellKnownClass(ClassStack context, Class<?> rawType, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
        if (rawType == Map.class) {
            return _mapType(rawType, bindings, superClass, superInterfaces);
        }
        if (rawType == Collection.class) {
            return _collectionType(rawType, bindings, superClass, superInterfaces);
        }
        if (rawType == AtomicReference.class) {
            return _referenceType(rawType, bindings, superClass, superInterfaces);
        }
        return null;
    }

    protected JavaType _fromWellKnownInterface(ClassStack context, Class<?> rawType, TypeBindings bindings, JavaType superClass, JavaType[] superInterfaces) {
        final int intCount = superInterfaces.length;
        for (int i = 0; i < intCount; ++i) {
            JavaType result = superInterfaces[i].refine(rawType, bindings, superClass, superInterfaces);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * This method deals with parameterized types, that is,
     * first class generic classes.
     */
    protected JavaType _fromParamType(ClassStack context, ParameterizedType ptype, TypeBindings parentBindings) {
        Class<?> rawType = (Class<?>) ptype.getRawType();
        if (rawType == CLS_ENUM) {
            return CORE_TYPE_ENUM;
        }
        if (rawType == CLS_COMPARABLE) {
            return CORE_TYPE_COMPARABLE;
        }
        if (rawType == CLS_CLASS) {
            return CORE_TYPE_CLASS;
        }
        Type[] args = ptype.getActualTypeArguments();
        int paramCount = (args == null) ? 0 : args.length;
        JavaType[] pt;
        TypeBindings newBindings;
        if (paramCount == 0) {
            newBindings = EMPTY_BINDINGS;
        } else {
            pt = new JavaType[paramCount];
            for (int i = 0; i < paramCount; ++i) {
                pt[i] = _fromAny(context, args[i], parentBindings);
            }
            newBindings = TypeBindings.create(rawType, pt);
        }
        return _fromClass(context, rawType, newBindings);
    }

    protected JavaType _fromArrayType(ClassStack context, GenericArrayType type, TypeBindings bindings) {
        JavaType elementType = _fromAny(context, type.getGenericComponentType(), bindings);
        return ArrayType.construct(elementType, bindings);
    }

    protected JavaType _fromVariable(ClassStack context, TypeVariable<?> var, TypeBindings bindings) {
        final String name = var.getName();
        JavaType type = bindings.findBoundType(name);
        if (type != null) {
            return type;
        }
        if (bindings.hasUnbound(name)) {
            return CORE_TYPE_OBJECT;
        }
        bindings = bindings.withUnboundVariable(name);
        Type[] bounds = var.getBounds();
        return _fromAny(context, bounds[0], bindings);
    }

    protected JavaType _fromWildcard(ClassStack context, WildcardType type, TypeBindings bindings) {
        return _fromAny(context, type.getUpperBounds()[0], bindings);
    }
}

