package org.kopitubruk.util.json;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Some reflection utility constants to be used with
 * {@link JSONConfig#setReflectionPrivacy(int)} and
 * {@link JSONConfigDefaults#setReflectionPrivacy(int)}
 *
 * @author Bill Davidson
 * @since 1.9
 */
public class ReflectUtil
{
    /**
     * This needs to be saved at class load time so that the correct class
     * loader is used if someone tries to load a class via a JMX client.
     */
    private static ClassLoader classLoader;

    /**
     * Reflection will attempt to serialize all fields including private.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PRIVATE = 0;

    /**
     * Reflection will attempt to serialize package private, protected and
     * public fields or fields that have package private, protected or public
     * get methods that conform to JavaBean naming conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PACKAGE = 1;

    /**
     * Reflection will attempt to serialize protected and public fields or
     * fields that have protected or public get methods that conform to JavaBean
     * naming conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PROTECTED = 2;

    /**
     * Reflection will attempt to serialize only fields that are public or have
     * public get methods that conform to JavaBean naming conventions.
     *
     * @see JSONConfig#setReflectionPrivacy(int)
     * @see JSONConfigDefaults#setReflectionPrivacy(int)
     */
    public static final int PUBLIC = 3;

    /**
     * Getter name pattern.
     */
    private static final Pattern GETTER = Pattern.compile("^(get|is)\\p{Lu}.*$");

    /**
     * A set of permissible levels for reflection privacy.
     */
    private static final Set<Integer> PERMITTED_LEVELS =
            new HashSet<>(Arrays.asList(PRIVATE, PACKAGE, PROTECTED, PUBLIC));

    /**
     * Primitive number types and the number class which includes all number
     * wrappers and BigDecimal and BigInteger.
     */
    private static final Set<Class<?>> NUMBERS =
            new HashSet<>(Arrays.asList(Double.TYPE, Float.TYPE, Long.TYPE, Integer.TYPE, Short.TYPE, Byte.TYPE, Number.class));

    /**
     * Boolean types.
     */
    private static final Set<Class<?>> BOOLEANS =
            new HashSet<>(Arrays.asList(Boolean.class, Boolean.TYPE));

    /**
     * String types.
     */
    private static final Set<Class<?>> STRINGS =
            new HashSet<>(Arrays.asList(CharSequence.class, Character.class, Character.TYPE));
    /**
     * Types that become arrays in JSON.
     */
    private static final Set<Class<?>> ARRAY_TYPES =
            new HashSet<>(Arrays.asList(Iterable.class,Enumeration.class));

    /**
     * Types that become maps/objects in JSON.
     */
    private static final Set<Class<?>> MAP_TYPES =
            new HashSet<>(Arrays.asList(Map.class,ResourceBundle.class));

    /**
     * Cache for fields.
     */
    private static volatile Map<Class<?>,Map<String,Field>> FIELDS;

    /**
     * Cache for getter methods.
     */
    private static volatile Map<Class<?>,Map<String,Method>> GETTER_METHODS;

    /**
     * The field-method compatibility cache.
     */
    private static volatile Map<Class<?>,Map<Field,Method>> FIELD_METHOD_COMPAT;

    /**
     * The field-method incompatibility cache.
     */
    private static volatile Map<Class<?>,Map<Field,Method>> FIELD_METHOD_INCOMPAT;

    /**
     * Minimum getter privacy level
     */
    private static volatile Map<Class<?>,Integer> MIN_GETTER_PRIVACY;

    static {
        // needed for loading classes via JMX MBean client.
        classLoader = ReflectUtil.class.getClassLoader();
        clearReflectionCache();
    }

    /**
     * Clear the reflection cache.
     */
    static synchronized void clearReflectionCache()
    {
        FIELDS = null;
        GETTER_METHODS = null;
        FIELD_METHOD_COMPAT = null;
        FIELD_METHOD_INCOMPAT = null;
        MIN_GETTER_PRIVACY = null;
    }

    /**
     * Get the field cache.
     *
     * @return The field cache.
     */
    private static synchronized Map<Class<?>,Map<String,Field>> getFieldCache()
    {
        if ( FIELDS == null ){
            FIELDS = new Hashtable<>(0);
        }
        return FIELDS;
    }

    /**
     * Get the method cache.
     *
     * @return The method cache.
     */
    private static synchronized Map<Class<?>,Map<String,Method>> getMethodCache()
    {
        if ( GETTER_METHODS == null ){
            GETTER_METHODS = new Hashtable<>(0);
        }
        return GETTER_METHODS;
    }

    /**
     * Get the method cache.
     *
     * @return The method cache.
     */
    private static synchronized Map<Class<?>,Integer> getMinGetterCache()
    {
        if ( MIN_GETTER_PRIVACY == null ){
            MIN_GETTER_PRIVACY = new Hashtable<>(0);
        }
        return MIN_GETTER_PRIVACY;
    }

    /**
     * Get the compat cache.
     *
     * @return The compat cache.
     */
    private static synchronized Map<Class<?>,Map<Field,Method>> getFieldMethodCompat()
    {
        if ( FIELD_METHOD_COMPAT == null ){
            FIELD_METHOD_COMPAT = new HashMap<>(0);
        }
        return FIELD_METHOD_COMPAT;
    }

    /**
     * Get the incompat cache.
     *
     * @return The incompat cache.
     */
    private static synchronized Map<Class<?>,Map<Field,Method>> getFieldMethodIncompat()
    {
        if ( FIELD_METHOD_INCOMPAT == null ){
            FIELD_METHOD_INCOMPAT = new HashMap<>(0);
        }
        return FIELD_METHOD_INCOMPAT;
    }

    /**
     * Get the class of the given object or the object if it's a class object.
     *
     * @param obj The object
     * @return The object's class.
     * @since 1.9
     */
    static Class<?> getClass( Object obj )
    {
        if ( obj == null ){
            throw new JSONReflectionException();
        }
        Class<?> result = null;
        if ( obj instanceof Class ){
            result = (Class<?>)obj;
        }else if ( obj instanceof JSONReflectedClass ){
            result = ((JSONReflectedClass)obj).getObjClass();
        }else{
            result = obj.getClass();
        }
        return result;
    }

    /**
     * Get the {@link JSONReflectedClass} version of this object class.
     *
     * @param obj The object.
     * @return the {@link JSONReflectedClass} version of this object class.
     */
    static JSONReflectedClass ensureReflectedClass( Object obj )
    {
        if ( obj instanceof JSONReflectedClass ){
             return (JSONReflectedClass)obj;
        }else if ( obj != null ){
            return new JSONReflectedClass(getClass(obj));
        }else{
            return null;
        }
    }

    /**
     * Get the class object for the given class name.
     *
     * @param className The name of the class.
     * @return The class object for that class.
     * @throws ClassNotFoundException If there's an error loading the class.
     * @since 1.9
     */
    static Class<?> getClassByName( String className ) throws ClassNotFoundException
    {
        return classLoader.loadClass(className);
    }

    /**
     * Check that the given privacy level is valid.
     *
     * @param privacyLevel The privacy level to check.
     * @param cfg The config for the exception.
     * @return The value if valid.
     * @throws JSONReflectionException if the privacyLevel is invalid.
     */
    static int confirmPrivacyLevel( int privacyLevel, JSONConfig cfg ) throws JSONReflectionException
    {
        if ( PERMITTED_LEVELS.contains(privacyLevel) ){
            return privacyLevel;
        }else{
            throw new JSONReflectionException(privacyLevel, cfg);
        }
    }

    /**
     * Use reflection to build a map of the properties of the given object
     *
     * @param propertyValue The object to be appended via reflection.
     * @param cfg A configuration object to use.
     * @return A map representing the object's fields.
     */
    static Map<Object,Object> getReflectedObject( Object propertyValue, JSONConfig cfg )
    {
        boolean isCacheData = cfg.isCacheReflectionData();
        JSONReflectedClass refClass = cfg.ensureReflectedClass(propertyValue);
        String[] fieldNames = refClass.getFieldNamesRaw();
        Class<?> clazz = refClass.getObjClass();
        boolean isFieldsSpecified = fieldNames != null;
        boolean isNotFieldsSpecified = ! isFieldsSpecified;
        int privacyLevel, modifiers = 0;
        String name = "getReflectedObject()";

        try {
            Map<String,Field> fields = getFields(clazz, isCacheData);
            if ( isFieldsSpecified ){
                privacyLevel = PRIVATE;
            }else{
                privacyLevel = cfg.getReflectionPrivacy();
                fieldNames = fields.keySet().toArray(new String[fields.size()]);
            }
            boolean isPrivate = privacyLevel == PRIVATE;
            Map<String,Method> getterMethods = getGetterMethods(clazz, privacyLevel, isCacheData);
            Map<Object,Object> obj = new LinkedHashMap<>(fieldNames.length);

            for ( String fieldName : fieldNames ){
                name = refClass.getAlias(fieldName);
                Field field = fields.get(fieldName);
                if ( isNotFieldsSpecified ){
                    modifiers = field.getModifiers();
                    if ( Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) ){
                        continue;       // ignore static and transient fields.
                    }
                }
                Method getter = getGetter(clazz, getterMethods, field, fieldName, isPrivate, isCacheData);
                if ( getter != null ){
                    ensureAccessible(getter);
                    obj.put(name, getter.invoke(propertyValue));
                }else if ( field != null && (isPrivate || getLevel(modifiers) >= privacyLevel) ){
                    ensureAccessible(field);
                    obj.put(name, field.get(propertyValue));
                }else if ( isFieldsSpecified ){
                    throw new JSONReflectionException(propertyValue, fieldName, cfg);
                }
            }
            return obj;
        }catch ( JSONReflectionException e ){
            throw e;
        }catch ( Exception e ){
            throw new JSONReflectionException(propertyValue, name, e, cfg);
        }
    }

    /**
     * Get the privacy level for reflection.
     *
     * @param modifiers The reflection modifiers.
     * @return The privacy level.
     */
    private static int getLevel( int modifiers )
    {
        if ( Modifier.isPrivate(modifiers) ){
            return PRIVATE;
        }else if ( Modifier.isProtected(modifiers) ){
            return PROTECTED;
        }else if ( Modifier.isPublic(modifiers) ){
            return PUBLIC;
        }else{
            return PACKAGE;
        }
    }

    /**
     * Get all of the fields for a given class.
     *
     * @param clazz The class.
     * @param cacheFields if true, then cache data.
     * @return The fields.
     */
    private static Map<String,Field> getFields( Class<?> clazz, boolean cacheFields )
    {
        Map<String,Field> fields;
        Map<Class<?>,Map<String,Field>> theCache = null;

        if ( cacheFields ){
            theCache = getFieldCache();
            fields = theCache.get(clazz);
            if ( fields != null ){
                return fields;
            }
        }

        fields = new LinkedHashMap<>();
        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Field field : tmpClass.getDeclaredFields() ){
                String name = field.getName();
                if ( ! fields.containsKey(name) ){
                    fields.put(name, field);
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        if ( cacheFields ){
            fields = new LinkedHashMap<>(fields);
            theCache.put(clazz, fields);
        }

        return fields;
    }

    /**
     * Get all of the instance fields for a given class that match the given
     * type.
     *
     * @param clazz The class.
     * @param type The type to match.
     * @return The fields.
     */
    static Map<String,Field> getFields( Class<?> clazz, Class<?> type )
    {
        // build a map of the object's properties.
        Map<String,Field> fields = new HashMap<>();

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Field field : tmpClass.getDeclaredFields() ){
                int modifiers = field.getModifiers();
                if ( Modifier.isTransient(modifiers) ){
                    continue;       // ignore transient fields.
                }
                if ( type.equals(field.getType()) ){
                    String name = field.getName();
                    if ( ! fields.containsKey(name) ){
                        fields.put(name, field);
                    }
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return fields;
    }

    /**
     * Get the name of the setter for the given field of the given
     * class.
     *
     * @param clazz The class.
     * @param field The field.
     * @return The setter or null if there isn't one.
     */
    static Method getSetter( Class<?> clazz, Field field )
    {
        String fieldName = field.getName();
        String setterName = makeBeanMethodName(fieldName,"set");

        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                if ( setterName.equals(method.getName()) && method.getParameterCount() == 1 ){
                    return method;
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        return null;
    }

    /**
     * Get all of the parameterless getter methods for a given class that
     * are visible with the given privacy level.
     *
     * @param clazz The class.
     * @param privacyLevel The minimum class privacy level
     * @param cacheMethods If true then cache method data.
     * @return The methods.
     */
    private static Map<String,Method> getGetterMethods( Class<?> clazz, int privacyLevel, boolean cacheMethods )
    {
        Map<String,Method> getterMethods = null;
        Map<String,Method> methodCache = null;
        Map<Class<?>,Map<String,Method>> theCache = null;
        boolean isPrivate = privacyLevel == PRIVATE;

        if ( cacheMethods ){
            theCache = getMethodCache();
            methodCache = theCache.get(clazz);
            if ( methodCache == null ){
                if ( ! isPrivate ){
                    methodCache = new HashMap<>(0);
                }
            }else{
                if ( isPrivate ){
                    return methodCache;
                }else{
                    Map<Class<?>,Integer> minGetterCache = getMinGetterCache();
                    Integer minGetter = minGetterCache.get(clazz);
                    boolean getterPrivacy = minGetter != null;
                    boolean noGetterPrivacy = ! getterPrivacy;
                    if ( getterPrivacy && privacyLevel <= minGetter ){
                        return methodCache;
                    }
                    int g = 0;
                    int m = methodCache.size();
                    int minPrivacy = PUBLIC;
                    // filter by privacy level.
                    getterMethods = new HashMap<>(0);
                    for ( Method method : methodCache.values() ){
                        int getterLevel = getLevel(method.getModifiers());
                        if ( getterLevel >= privacyLevel ){
                            getterMethods.put(method.getName(), method);
                            ++g;
                        }
                        if ( noGetterPrivacy && getterLevel < minPrivacy ){
                            minPrivacy = getterLevel;
                        }
                    }
                    if ( noGetterPrivacy ){
                        minGetterCache.put(clazz, minPrivacy);
                    }
                    return g == m ? methodCache : getterMethods;
                }
            }
        }

        int g = 0;
        int m = 0;
        int minPrivacy = PUBLIC;
        getterMethods = new HashMap<>(0);
        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Method method : tmpClass.getDeclaredMethods() ){
                if ( method.getParameterCount() != 0 ){
                    continue;
                }
                Class<?> retType = method.getReturnType();
                if ( Void.TYPE.equals(retType) ){
                    continue;
                }
                String name = method.getName();
                if ( getterMethods.containsKey(name) || ! GETTER.matcher(name).matches() ){
                    continue;
                }
                if ( name.startsWith("is") && ! BOOLEANS.contains(retType) ){
                    continue;   // "is" prefix only valid getter for booleans.
                }
                if ( isPrivate ){
                    getterMethods.put(name, method);
                }else{
                    int getterLevel = getLevel(method.getModifiers());
                    if ( getterLevel >= privacyLevel ){
                        getterMethods.put(name, method);
                        ++g;
                    }
                    if ( cacheMethods ){
                        methodCache.put(name, method);
                        ++m;
                        if ( getterLevel < minPrivacy ){
                            minPrivacy = getterLevel;
                        }
                    }
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }

        if ( cacheMethods ){
            if ( g == m ){
                methodCache = getterMethods = new HashMap<>(getterMethods);
            }else{
                methodCache = new HashMap<>(methodCache);
            }
            theCache.put(clazz, methodCache);
            if ( m > 0 ){
                getMinGetterCache().put(clazz, minPrivacy);
            }
        }

        return getterMethods;
    }

    /**
     * Get the getter for the given field.
     *
     * @param clazz The class for this field and getter.
     * @param getterMethods The available getter methods at the current privacy level.
     * @param field The field
     * @param name The field name.
     * @param isPrivate if the privacy level is private.
     * @param cacheMethods if true cache the method objects by field.
     * @return The getter or null if one cannot be found.
     */
    private static Method getGetter( Class<?> clazz, Map<String,Method> getterMethods, Field field, String name, boolean isPrivate, boolean cacheMethods )
    {
        if ( cacheMethods && field != null ){
            // check the cache for previous validations.
            Method getter;
            Map<Class<?>,Map<Field,Method>> compatCache = getFieldMethodCompat();
            synchronized ( compatCache ){
                Map<Field,Method> compat = compatCache.get(clazz);
                getter = compat != null ? compat.get(field) : null;
            }
            if ( getter != null ){
                // check privacy level and return null if level not met.
                return isPrivate ? getter : getterMethods.get(getter.getName());
            }
            Map<Class<?>,Map<Field,Method>> incompatCache = getFieldMethodIncompat();
            synchronized ( incompatCache ){
                Map<Field,Method> incompat = incompatCache.get(clazz);
                getter = incompat != null ? incompat.get(field) : null;
            }
            if ( getter != null ){
                return null;
            }
        }

        Method getter = getterMethods.get(makeBeanMethodName(name,"get"));
        if ( getter == null ){
            getter = getterMethods.get(makeBeanMethodName(name,"is"));
        }
        if ( field == null || getter == null ){
            return getter;
        }

        boolean isCompatible = isCompatible(field, getter);

        if ( cacheMethods ){
            if ( isCompatible ){
                Map<Class<?>,Map<Field,Method>> compatCache = getFieldMethodCompat();
                synchronized ( compatCache ){
                    Map<Field,Method> compat = compatCache.get(clazz);
                    if ( compat == null ){
                        compat = new HashMap<>(0);
                        compatCache.put(clazz, compat);
                    }
                    compat.put(field, getter);
                }
            }else{
                Map<Class<?>,Map<Field,Method>> incompatCache = getFieldMethodIncompat();
                synchronized ( incompatCache ){
                    Map<Field,Method> incompat = incompatCache.get(clazz);
                    if ( incompat == null ){
                        incompat = new HashMap<>(0);
                        incompatCache.put(clazz, incompat);
                    }
                    incompat.put(field, getter);
                }
            }
        }

        return isCompatible ? getter : null;
    }

    /**
     * Make a bean method name.
     *
     * @param fieldName the name of the field.
     * @param prefix The prefix for the bean method name.
     * @return The bean method name.
     */
    private static String makeBeanMethodName( String fieldName, String prefix )
    {
        int len = fieldName.length();
        StringBuilder buf = new StringBuilder(len+prefix.length());
        buf.append(prefix);
        int codePoint = fieldName.codePointAt(0);
        int charCount = Character.charCount(codePoint);
        if ( Character.isLowerCase(codePoint) ){
            codePoint = Character.toUpperCase(codePoint);
        }
        buf.appendCodePoint(codePoint);
        if ( len > charCount ){
            buf.append(fieldName.substring(charCount));
        }
        return buf.toString();
    }

    /**
     * Return true if the type returned by the method is compatible in JSON
     * with the type of the field.
     *
     * @param field The field.
     * @param method The method to check the return type of.
     * @return true if they are compatible in JSON.
     */
    private static boolean isCompatible( Field field, Method method )
    {
        Class<?> fieldType = field.getType();
        Set<Class<?>> methodTypes = getTypes(method.getReturnType());

        if ( isType(methodTypes, fieldType) ){
            return true;
        }else{
            Set<Class<?>> fieldTypes = getTypes(fieldType);

            if ( isNumber(fieldTypes) && isNumber(methodTypes) ){
                return true;
            }else if ( isString(fieldTypes) && isString(methodTypes) ){
                return true;
            }else if ( isBoolean(fieldTypes) && isBoolean(methodTypes) ){
                return true;
            }else if ( isJSONArray(fieldTypes) && isJSONArray(methodTypes) ){
                return true;
            }else if ( isJSONMap(fieldTypes) && isJSONMap(methodTypes) ){
                return true;
            }
        }

        return false;
    }

    /**
     * Return true if the given type is a {@link Number} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link Number} type.
     */
    private static boolean isNumber( Set<Class<?>> objTypes )
    {
        return isType(objTypes, NUMBERS);
    }

    /**
     * Return true if the given type is a {@link Boolean} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link Boolean} type.
     */
    private static boolean isBoolean( Set<Class<?>> objTypes )
    {
        return isType(objTypes, BOOLEANS);
    }

    /**
     * Return true if the given type is a {@link CharSequence} type.
     *
     * @param type the type to check.
     * @return true if the given type is a {@link CharSequence} type.
     */
    private static boolean isString( Set<Class<?>> objTypes )
    {
        return isType(objTypes, STRINGS);
    }

    /**
     * Return true if the given type is a JSON array type.
     *
     * @param type the type to check.
     * @return true if the given type is a JSON array type.
     */
    private static boolean isJSONArray( Set<Class<?>> objTypes )
    {
        for ( Class<?> clazz : objTypes ){
            if ( clazz.isArray() ){
                return true;
            }else{
                break;
            }
        }
        return isType(objTypes, ARRAY_TYPES);
    }

    /**
     * Return true if the given type is a JSON map type.
     *
     * @param type the type to check.
     * @return true if the given type is a JSON map type.
     */
    private static boolean isJSONMap( Set<Class<?>> objTypes )
    {
        return isType(objTypes, MAP_TYPES);
    }

    /**
     * Return true if the objTypes or any of its super types or interfaces is
     * the same as the given type.
     *
     * @param objType The type to check.
     * @param type The type to check against.
     * @return true if there's a match.
     */
    private static boolean isType( Set<Class<?>> objTypes, Class<?> type )
    {
        return isType(objTypes, new HashSet<>(Arrays.asList(type)));
    }

    /**
     * Return true if the objTypes or any of its super types or interfaces is
     * the same as the given types.
     *
     * @param objType The type to check.
     * @param type The type to check against.
     * @return true if there's a match.
     */
    private static boolean isType( Set<Class<?>> objTypes, Set<Class<?>> types )
    {
        if ( objTypes.size() >= types.size() ){
            for ( Class<?> type : types ){
                if ( objTypes.contains(type) ){
                    return true;
                }
            }
        }else{
            for ( Class<?> type : objTypes ){
                if ( types.contains(type) ){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get a collection of types represented by the given type including
     * all super types and interfaces.
     *
     * @param objType the original type.
     * @return the type and all its super types and interfaces.
     */
    private static Set<Class<?>> getTypes( Class<?> objType )
    {
        Set<Class<?>> types = new LinkedHashSet<>();
        Class<?> tmpClass = objType;
        while ( tmpClass != null ){
            if ( ! "java.lang.Object".equals(tmpClass.getCanonicalName()) ){
                types.add(tmpClass);
            }
            tmpClass = tmpClass.getSuperclass();
        }
        getInterfaces(objType, types);
        return types;
    }

    /**
     * Get a complete list of interfaces for a given class including
     * all super interfaces.
     *
     * @param clazz The class.
     * @param The set of interfaces.
     */
    private static void getInterfaces( Class<?> clazz, Set<Class<?>> interfaces )
    {
        Class<?> tmpClass = clazz;
        while ( tmpClass != null ){
            for ( Class<?> itfc : clazz.getInterfaces() ){
                if ( interfaces.add(itfc) ){
                    getInterfaces(itfc, interfaces);
                }
            }
            tmpClass = tmpClass.getSuperclass();
        }
    }

    /**
     * Make sure that the given object is accessible.
     *
     * @param obj the object
     */
    static void ensureAccessible( AccessibleObject obj )
    {
        if ( ! obj.isAccessible() ){
            obj.setAccessible(true);
        }
    }

    /**
     * This class should never be instantiated.
     */
    private ReflectUtil()
    {
    }
}
