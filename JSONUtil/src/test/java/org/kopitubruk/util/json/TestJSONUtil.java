/*
 * Copyright 2015 Bill Davidson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kopitubruk.util.json;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.Vector;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

//import org.junit.Rule;
//import java.text.SimpleDateFormat;
//import org.junit.rules.TestRule;
//import org.junit.rules.TestWatcher;
//import org.junit.runner.Description;

/**
 * Tests for JSONUtil.  Java 5 does not support a scripting engine so
 * the output cannot be validated against a Javascript engine.
 *
 * @author Bill Davidson
 */
public class TestJSONUtil
{
    private static Log s_log = LogFactory.getLog(TestJSONUtil.class);

    //private static SimpleDateFormat s_sdf;

    /**
     * Print out the name of the currently running test.
     *
    @Rule
    public TestRule watcher = new TestWatcher()
    {
        protected void starting( Description description )
        {
            System.out.println(s_sdf.format(new Date()) + ' ' + description.getMethodName());
        }
    }; */

    /**
     * Create a dummy JNDI initial context in order to avoid having
     * JNDI code throw an exception during the JUnit tests.
     */
    @BeforeClass
    public static void setUpClass()
    {
        try{
            Context ctx = JNDIUtil.createEnvContext(JSONUtil.class.getPackage().getName().replaceAll("\\.", "/"));

            ctx.bind("appName", "TestJSONUtil");
            ctx.bind("maxReflectIndex", 0);
            ctx.bind("reflectClass0", "org.kopitubruk.util.json.ReflectTestClass,a,e");
        }catch ( NamingException e ){
            s_log.fatal("Couldn't create context", e);
            System.exit(-1);
        }

        /*
         * Some of these tests depend upon error messages which need to be in
         * English, so it's forced during the tests.
         */
        JSONConfigDefaults.setLocale(Locale.US);
    }

    /**
     * Cleanup resources.
     */
    @AfterClass
    public static void cleanUpResources()
    {
        JSONConfigDefaults.clearMBean();
    }

    /**
     * Test all characters allowed for property names. Every start character
     * gets tested in the start position and most part characters get tested
     * twice but all at least once.
     * <p>
     * This test is slow because it's doing over 100,000 validations through
     * the Javascript interpreter, which is slow.  If you comment out that
     * validation, then this test takes about 1 second on my laptop, but you
     * only test that none of the valid code points cause an exception and
     * not that they won't cause a syntax error.  With the validation on it
     * typically takes about 68 seconds for this to run on my laptop, which
     * is annoying but it provides a better test.
     * <p>
     * It should be noted that some valid characters need the identifier to
     * be in quotes in order for the validation to work properly so the
     * validation tests that those identifiers that need that do get quoted
     * even though I turned identifier quoting off for the tests.
     */
    @Test
    public void testValidPropertyNames()
    {
        JSONConfig cfg = new JSONConfig();

        ArrayList<Integer> validStart = new ArrayList<Integer>();
        ArrayList<Integer> validPart = new ArrayList<Integer>();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( JSONUtil.isValidIdentifierStart(i, cfg) ){
                validStart.add(i);
            }else if ( JSONUtil.isValidIdentifierPart(i, cfg) ){
                validPart.add(i);
            }
        }
        validStart.trimToSize();
        s_log.debug(validStart.size() + " valid start code points");
        validPart.addAll(validStart);
        Collections.sort(validPart);
        s_log.debug(validPart.size() + " valid part code points");

        final int MAX_LENGTH = 3;
        int[] propertyName = new int[MAX_LENGTH];
        int startIndex = 0;
        int partIndex = 0;
        int nameIndex = 0;

        Map<String,Object> jsonObj = new HashMap<String,Object>(2);

        int startSize = validStart.size();
        int partSize = validPart.size();
        propertyName[nameIndex++] = validStart.get(startIndex++);

        while ( startIndex < startSize ){
            propertyName[nameIndex++] = validPart.get(partIndex++);
            if ( nameIndex == MAX_LENGTH ){
                jsonObj.clear();
                jsonObj.put(new String(propertyName,0,nameIndex), 0);
                // String json =
                        JSONUtil.toJSON(jsonObj, cfg);
                // validateJSON(json);    // this makes this test take a long time to run.
                nameIndex = 0;
                if ( startIndex < startSize ){
                    // start new string.
                    propertyName[nameIndex++] = validStart.get(startIndex++);
                }
            }
            if ( partIndex == partSize ){
                partIndex = 0;
            }
        }
    }

    /**
     * Test all characters allowed for property names by JSON.parse() but not by
     * Javascript eval(). The JSON standard is much looser with characters
     * allowed in property names than ECMAScript. It allows pretty much any
     * defined code point greater than or equal to 32. There are no start
     * character rules either as there are with ECMAScript identifiers.
     *
     */
    @Test
    public void testJSONPropertyNames()
    {
        JSONConfig jcfg = new JSONConfig();
        jcfg.setFullJSONIdentifierCodePoints(false);
        JSONConfig cfg = new JSONConfig();
        cfg.setFullJSONIdentifierCodePoints(true);

        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        int[] normalIdent = new int[1];
        int[] escName = new int[2];
        escName[0] = '\\';
        int jsonOnlyCount = 0;

        for ( int i = ' '; i <= Character.MAX_CODE_POINT; i++ ){
            if ( JSONUtil.isValidIdentifierStart(i, cfg) && ! JSONUtil.isValidIdentifierPart(i, jcfg) ){
                normalIdent[0] = i;
                jsonObj.clear();
                jsonObj.put(new String(normalIdent,0,1), 0);
                //String json =
                        JSONUtil.toJSON(jsonObj, cfg);
                // these would fail eval().
                //parseJSON(json);
                ++jsonOnlyCount;
            }
        }

        s_log.debug(jsonOnlyCount+" code points are valid identifier start characters for JSON.parse() but not for eval()");
    }

    /**
     * Test all characters not allowed for property names by JSON.parse().
     */
    @Test
    public void testBadJSONPropertyNames()
    {
        JSONConfig cfg = new JSONConfig();
        cfg.setFullJSONIdentifierCodePoints(true);

        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        int[] codePoints = new int[256];
        int j = 0;

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if (  i < ' ' || ! Character.isDefined(i) ){
                codePoints[j++] = i;
                if ( j == codePoints.length ){
                    testBadIdentifier(codePoints, 0, j, jsonObj, cfg);
                    j = 0;
                }
            }
        }
        if ( j > 0 ){
            testBadIdentifier(codePoints, 0, j, jsonObj, cfg);
        }
    }

    /**
     * Test bad code points for the start of characters in names.
     */
    @Test
    public void testBadStartPropertyNames()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        int[] codePoints = new int[1];
        JSONConfig cfg = new JSONConfig();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( ! JSONUtil.isValidIdentifierStart(i, cfg) ){
                codePoints[0] = i;
                testBadIdentifier(codePoints, 0, 1, jsonObj, cfg);
            }
        }
    }

    /**
     * Test bad code points for non-start characters in names.
     */
    @Test
    public void testBadPartPropertyNames()
    {
        int[] codePoints = new int[256];
        int j = 1;
        codePoints[0] = '_';
        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        JSONConfig cfg = new JSONConfig();

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( isNormalCodePoint(i) && ! JSONUtil.isValidIdentifierStart(i, cfg) && ! JSONUtil.isValidIdentifierPart(i, cfg) ){
                // high surrogates break the test unless they are followed immediately by low surrogates.
                // just skip them.  anyone who sends bad surrogate pairs deserves what they get.
                codePoints[j++] = i;
                if ( j == codePoints.length ){
                    testBadIdentifier(codePoints, 1, j, jsonObj, cfg);
                    j = 1;
                }
            }
        }
        if ( j > 1 ){
            testBadIdentifier(codePoints, 1, j, jsonObj, cfg);
        }
    }

    /**
     * Test a bad identifier.  This is a utility method used by other test methods.
     *
     * @param codePoints A set of code points with bad code points.
     * @param start The index of the first code point to check for.
     * @param end The index just after the last code point to check for.
     * @param jsonObj A jsonObj to use for the test.
     */
    private void testBadIdentifier( int[] codePoints, int start, int end, Map<String,Object> jsonObj, JSONConfig cfg )
    {
        // clear in order to avoid memory abuse.
        // didn't create the object here because it would have to be recreated millions of times.
        jsonObj.clear();
        try{
            jsonObj.put(new String(codePoints,0,end), 0);
            JSONUtil.toJSON(jsonObj, cfg);
            fail(String.format("Expected a BadPropertyNameException to be thrown for U+%04X", codePoints[start]));
        }catch ( BadPropertyNameException e ){
            String message = e.getMessage();
            for ( int i = start; i < end; i++ ){
                assertThat(message, containsString(String.format("Code point U+%04X", codePoints[i])));
            }
        }
    }

    /**
     * Test valid Unicode strings.
     */
    @Test
    public void testValidStrings()
    {
        int[] codePoints = new int[32768];
        Map<String,Object> jsonObj = new HashMap<String,Object>(2);
        JSONConfig cfg = new JSONConfig();
        int j = 0;

        for ( int i = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( isNormalCodePoint(i) ){
                // 247650 code points, the last time I checked.
                codePoints[j++] = i;
                if ( j == codePoints.length/2 ){
                    jsonObj.put("x", new String(codePoints,0,j));
                    JSONUtil.toJSON(jsonObj, cfg);
                    // validateJSON(JSONUtil.toJSON(jsonObj, cfg));
                    j = 0;
                }
            }
        }
        if ( j > 0 ){
            jsonObj.put("x", new String(codePoints,0,j));
            // validateJSON(JSONUtil.toJSON(jsonObj, cfg));
        }
    }

    /**
     * Test that Unicode escape sequences in identifiers work.
     */
    @Test
    public void testUnicodeEscapeInIdentifier()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String[] ids = { "a\\u1234", "\\u1234x" };
        for ( String id : ids ){
            jsonObj.clear();
            jsonObj.put(id, 0);

            String json = JSONUtil.toJSON(jsonObj);
            // validateJSON(json);
            assertThat(json, is("{\""+id+"\":0}"));
        }
    }

    /**
     * Test that ECMAScript 6 code point escapes.
     */
    @Test
    public void testECMA6UnicodeEscapeInString()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        JSONConfig cfg = new JSONConfig();
        cfg.setUseECMA6(true);
        cfg.setEscapeNonAscii(true);
        StringBuilder buf = new StringBuilder();
        int codePoint = 0x1F4A9;
        buf.append("x");
        buf.appendCodePoint(codePoint);
        jsonObj.put("x", buf);
        String json = JSONUtil.toJSON(jsonObj, cfg);
        // Nashorn doesn't understand ECMAScript 6 code point escapes.
        //validateJSON(json);
        assertThat(json, is("{\"x\":\"x\\u{1F4A9}\"}"));
    }

    /**
     * Test that Unicode escape sequences in identifiers work.
     */
    @Test
    public void testEscapePassThrough()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        JSONConfig cfg = new JSONConfig();
        cfg.setUnEscapeWherePossible(false);
        cfg.setPassThroughEscapes(true);
        cfg.setUseECMA6(true);
        // escapes that get passed through.
        String[] strs = { "\\u1234", "a\\u{41}", "\\\"", "\\/", "\\b", "\\f", "\\n", "\\r", "\\t", "\\\\" };
        for ( String str : strs ){
            jsonObj.clear();
            jsonObj.put("x", str);

            String json = JSONUtil.toJSON(jsonObj, cfg);
            if ( str.indexOf('{') < 0 ){
                // Nashorn doesn't understand ECMAScript 6 code point escapes.
                //validateJSON(json);
            }
            assertThat(json, is("{\"x\":\""+str+"\"}"));
        }
    }

    /**
     * Test that unescape works.
     */
    @Test
    public void testUnEscape()
    {
        Map<String,Object> jsonObj = new LinkedHashMap<String, Object>();
        String[] strs = {"a\\u0041", "d\\u{41}", "e\\v", "f\\'"};
        JSONConfig cfg = new JSONConfig();
        cfg.setUnEscapeWherePossible(true);
        for ( String str : strs ){
            jsonObj.clear();
            jsonObj.put("x", str);

            String json = JSONUtil.toJSON(jsonObj, cfg);
            char firstChar = str.charAt(0);
            if ( firstChar != 'd' ){
                // Nashorn doesn't understand EMCAScript 6 code point escapes.
                // validateJSON(json);
            }
            // \v unescaped will be re-escaped as a Unicode code unit.
            String result;
            switch ( firstChar ){
                case 'e':
                    result = firstChar + "\\u000B";
                    break;
                case 'f':
                    result = firstChar + "'";
                    break;
                default:
                    result = firstChar + "A";
                    break;
            }
            assertThat(json, is("{\"x\":\""+result+"\"}"));
        }

        // test that these get fixed regardless.
        cfg.setUnEscapeWherePossible(false);
        cfg.setPassThroughEscapes(true);

        // test octal/hex unescape.
        for ( int i = 0; i < 256; i++ ){
            jsonObj.clear();
            jsonObj.put("x", String.format("a\\%o", i));
            jsonObj.put("y", String.format("a\\x%02X", i));
            String result = CodePointData.getEscape((char)i);
            if ( result == null ){
                result = i < 0x20 ? String.format("\\u%04X", i) : String.format("%c", (char)i);
            }
            String json = JSONUtil.toJSON(jsonObj, cfg);
            //validateJSON(json);
            assertThat(json, is("{\"x\":\"a"+result+"\",\"y\":\"a"+result+"\"}"));
        }
    }

    /**
     * Test the parser.
     *
     * @throws ParseException for parsing problems.
     */
    @Test
    public void testParser() throws ParseException
    {
        Object obj = JSONParser.parseJSON("{\"foo\":\"b\\\\\\\"ar\",\"a\":5,\"b\":2.37e24,c:Infinity,\"d\":NaN,\"e\":[1,2,3,{\"a\":4}]}");
        String json = JSONUtil.toJSON(obj);
        assertEquals("{\"foo\":\"b\\\\\\\"ar\",\"a\":5,\"b\":2.37E24,\"c\":\"Infinity\",\"d\":\"NaN\",\"e\":[1,2,3,{\"a\":4}]}", json);

        obj = JSONParser.parseJSON("'foo'");
        assertEquals("foo", obj);

        obj = JSONParser.parseJSON("2.37e24");
        assertEquals(2.37e24, obj);

        obj = JSONParser.parseJSON("Infinity");
        assertTrue(Double.isInfinite((Double)obj));

        obj = JSONParser.parseJSON("NaN");
        assertTrue(Double.isNaN((Double)obj));

        obj = JSONParser.parseJSON("false");
        assertEquals(Boolean.FALSE, obj);

        obj = JSONParser.parseJSON("null");
        assertEquals(null, obj);

        JSONConfig cfg = new JSONConfig();
        cfg.setUsePrimitiveArrays(true);

        obj = JSONParser.parseJSON("[1.1,2.2,-3.134598765,4.0]", cfg);
        double[] doubles = (double[])obj;
        assertEquals(new Double(1.1), new Double(doubles[0]));
        assertEquals(new Double(2.2), new Double(doubles[1]));
        assertEquals(new Double(-3.134598765), new Double(doubles[2]));
        assertEquals(new Double(4.0), new Double(doubles[3]));

        obj = JSONParser.parseJSON("[1.1,2.2,-3.134,4.0]", cfg);
        float[] floats = (float[])obj;
        assertEquals(new Float(1.1), new Float(floats[0]));
        assertEquals(new Float(2.2), new Float(floats[1]));
        assertEquals(new Float(-3.134), new Float(floats[2]));
        assertEquals(new Float(4.0), new Float(floats[3]));

        obj = JSONParser.parseJSON("[1,2,-3,4]", cfg);
        byte[] bytes = (byte[])obj;
        assertEquals(new Byte((byte)1), new Byte(bytes[0]));
        assertEquals(new Byte((byte)2), new Byte(bytes[1]));
        assertEquals(new Byte((byte)-3), new Byte(bytes[2]));
        assertEquals(new Byte((byte)4), new Byte(bytes[3]));

        // parse various forms of date strings.
        cfg.setEncodeDatesAsStrings(true);
        DateFormat fmt = cfg.getDateGenFormat();

        Date dt = (Date)JSONParser.parseJSON("new Date(\"2015-09-16T14:08:34.034Z\")", cfg);
        assertEquals("2015-09-16T14:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34.034Z\"", cfg);
        assertEquals("2015-09-16T14:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34.034+01\"", cfg);
        assertEquals("2015-09-16T13:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34.034+01:30\"", cfg);
        assertEquals("2015-09-16T12:38:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34\"", cfg);
        assertEquals("2015-09-16T14:08:34.034Z", fmt.format(dt));

        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34+01:30\"", cfg);
        assertEquals("2015-09-16T12:38:34.034Z", fmt.format(dt));

        // custom formats.
        DateFormat nfmt = cfg.setDateGenFormat("EEE, d MMM yyyy HH:mm:ss Z");
        nfmt.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
        cfg.addDateParseFormat("yyyy.MM.dd G 'at' HH:mm:ss z");
        dt = (Date)JSONParser.parseJSON("\"2001.07.04 AD at 12:08:56 EDT\"", cfg);
        assertEquals("Wed, 4 Jul 2001 12:08:56 -0400", nfmt.format(dt));

        // test that the old one still works.
        dt = (Date)JSONParser.parseJSON("\"2015-09-16T14:08:34+01:30\"", cfg);
        assertEquals("2015-09-16T12:38:34.034Z", fmt.format(dt));

        try{
            JSONParser.parseJSON("{\"foo\":\"b\\\\\\\"ar\",\"a\":5,\"b\":2.37e24,\"c\":&*^,\"d\":NaN,\"e\":[1,2,3,{\"a\":4}]}");
            fail("Expected JSONParserException for bad data");
        }catch ( JSONParserException e ){
        }
    }

    /**
     * Test using reserved words in identifiers.
     */
    @Test
    public void testReservedWordsInIdentifiers()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        JSONConfig cfg = new JSONConfig();
        cfg.setAllowReservedWordsInIdentifiers(true);
        Set<String> reservedWords = JSONUtil.getJavascriptReservedWords();
        for ( String reservedWord : reservedWords ){
            jsonObj.clear();
            jsonObj.put(reservedWord, 0);

            // test with allow reserved words.
            String json = JSONUtil.toJSON(jsonObj, cfg);
            //validateJSON(json);
            assertThat(json, is("{\""+reservedWord+"\":0}"));

            // test with reserved words disallowed.
            try{
                JSONUtil.toJSON(jsonObj);
                fail("Expected BadPropertyNameException for reserved word "+reservedWord);
            }catch ( BadPropertyNameException e ){
                String message = e.getMessage();
                assertThat(message, is(reservedWord+" is a reserved word."));
            }
        }
    }

    /**
     * Test EscapeBadIdentifierCodePoints
     */
    @Test
    public void testEscapeBadIdentifierCodePoints()
    {

        Map<Object,Object> jsonObj = new LinkedHashMap<Object,Object>();
        StringBuilder buf = new StringBuilder("c");
        StringBuilder cmpBuf = new StringBuilder();
        JSONConfig cfg = new JSONConfig();
        cfg.setEscapeBadIdentifierCodePoints(true);
        jsonObj.put("x\u0005", 0);

        String json = JSONUtil.toJSON(jsonObj, cfg);
        //validateJSON(json);
        assertThat(json, is("{\"x\\u0005\":0}"));

        // test octal/hex unescape.
        for ( int i = 0; i < 256; i++ ){
            buf.setLength(0);
            buf.append("c").append((char)i);
            jsonObj.clear();
            jsonObj.put(String.format("a\\%o", i), 0);
            jsonObj.put(String.format("b\\x%02X", i), 0);
            jsonObj.put(buf, 0);                            // raw.
            json = JSONUtil.toJSON(jsonObj, cfg);
            //validateJSON(json);

            String r = JSONUtil.isValidIdentifierPart(i, cfg) ? String.format("%c", (char)i) : String.format("\\u%04X", i);

            assertThat(json, is("{\"a"+r+"\":0,\"b"+r+"\":0,\"c"+r+"\":0}"));
        }

        int maxLen = 256;
        buf.setLength(0);
        buf.append("c");
        cmpBuf.setLength(0);
        cmpBuf.append("{\"c");
        for ( int i = 256, ct = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( isNormalCodePoint(i) ){
                buf.appendCodePoint(i);
                addCmp(i, cmpBuf, cfg);
                ++ct;
            }
            if ( ct % maxLen == 0 || i == Character.MAX_CODE_POINT ){
                jsonObj.clear();
                jsonObj.put(buf, 0);                            // raw.
                json = JSONUtil.toJSON(jsonObj, cfg);
                //validateJSON(json);

                cmpBuf.append("\":0}");
                assertThat(json, is(cmpBuf.toString()));

                buf.setLength(0);
                buf.append("c");
                cmpBuf.setLength(0);
                cmpBuf.append("{\"c");
            }
        }

        cfg.setFullJSONIdentifierCodePoints(true);

        // test octal/hex unescape.
        for ( int i = 0; i < 256; i++ ){
            buf.setLength(0);
            buf.append("c").append((char)i);
            jsonObj.clear();
            jsonObj.put(String.format("a\\%o", i), 0);
            jsonObj.put(String.format("b\\x%02X", i), 0);
            jsonObj.put(buf, 0);                            // raw.
            json = JSONUtil.toJSON(jsonObj, cfg);
            //validateJSON(json);

            String r = CodePointData.getEscape((char)i);
            if ( r == null ){
                r = JSONUtil.isValidIdentifierPart(i, cfg) ? String.format("%c", (char)i) : String.format("\\u%04X", i);
            }

            assertThat(json, is("{\"a"+r+"\":0,\"b"+r+"\":0,\"c"+r+"\":0}"));
        }

        buf.setLength(0);
        buf.append("c");
        for ( int i = 256, ct = 0; i <= Character.MAX_CODE_POINT; i++ ){
            if ( isNormalCodePoint(i) ){
                buf.appendCodePoint(i);
                addCmp(i, cmpBuf, cfg);
                ++ct;
            }
            if ( ct % maxLen == 0 || i == Character.MAX_CODE_POINT ){
                jsonObj.clear();
                jsonObj.put(buf, 0);
                json = JSONUtil.toJSON(jsonObj, cfg);
                //parseJSON(json);

                cmpBuf.append("\":0}");
                assertThat(json, is(cmpBuf.toString()));

                buf.setLength(0);
                buf.append("c");
                cmpBuf.setLength(0);
                cmpBuf.append("{\"c");
            }
        }
    }

    /**
     * Append the expected comparison data for the given code point
     * to the compare buffer.
     *
     * @param i the code point.
     * @param cmpBuf the compare buffer.
     * @param cfg the config object.
     */
    private void addCmp( int i, StringBuilder cmpBuf, JSONConfig cfg )
    {
        if ( JSONUtil.isValidIdentifierPart(i, cfg) ){
            cmpBuf.appendCodePoint(i);
        }else if ( i <= 0xFFFF ){
            cmpBuf.append(String.format("\\u%04X", i));
        }else{
            StringBuilder m = new StringBuilder();
            m.setLength(0);
            m.appendCodePoint(i);
            cmpBuf.append(String.format("\\u%04X\\u%04X", (int)m.charAt(0), (int)m.charAt(1)));
        }
    }

    /**
     * Test setting default locale.
     */
    @Test
    public void testDefaultLocale()
    {
        Locale loc = new Locale("es","JP");
        Locale oldDefLoc = JSONConfigDefaults.getLocale();
        JSONConfigDefaults.setLocale(loc);
        Locale defLoc = JSONConfigDefaults.getLocale();
        JSONConfigDefaults.setLocale(oldDefLoc);
        assertEquals("Default locale not set", defLoc, loc);
        assertNotEquals(oldDefLoc, loc);
    }

    /**
     * Test dates.
     */
    @Test
    public void testDate()
    {
        JSONConfig cfg = new JSONConfig();

        // non-standard JSON - only works with eval() and my parser.
        cfg.setEncodeDatesAsObjects(true);
        Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(2015, 8, 16, 14, 8, 34);
        cal.set(Calendar.MILLISECOND, 34);
        jsonObj.put("t", cal.getTime());
        String json = JSONUtil.toJSON(jsonObj, cfg);
        assertThat(json, is("{\"t\":new Date(\"2015-09-16T14:08:34.034Z\")}"));

        cfg.setEncodeDatesAsStrings(true);
        json = JSONUtil.toJSON(jsonObj, cfg);
        //validateJSON(json);
        assertThat(json, is("{\"t\":\"2015-09-16T14:08:34.034Z\"}"));
    }

    /**
     * Test booleans.
     */
    @Test
    public void testBoolean() // throws ScriptException
    {
        Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
        jsonObj.put("t", true);
        jsonObj.put("f", false);
        String json = JSONUtil.toJSON(jsonObj);
        //validateJSON(json);
        assertThat(json, is("{\"t\":true,\"f\":false}"));
    }

    /**
     * Test a byte value.
     */
    @Test
    public void testByte()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        byte b = 27;
        jsonObj.put("x", b);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":27}"));
    }

    /**
     * Test a char value.
     */
    @Test
    public void testChar()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        char ch = '@';
        jsonObj.put("x", ch);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":\"@\"}"));
    }

    /**
     * Test a short value.
     */
    @Test
    public void testShort()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        short s = 275;
        jsonObj.put("x", s);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":275}"));
    }

    /**
     * Test a int value.
     */
    @Test
    public void testInt()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        int i = 100000;
        jsonObj.put("x", i);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":100000}"));
    }

    /**
     * Test a byte value.
     */
    @Test
    public void testLong()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        long l = 68719476735L;
        jsonObj.put("x", l);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":68719476735}"));
    }

    /**
     * Test a float value.
     */
    @Test
    public void testFloat()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        float f = 3.14f;
        jsonObj.put("x", f);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":3.14}"));
    }

    /**
     * Test a double value.
     */
    @Test
    public void testDouble()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        double d = 6.28;
        jsonObj.put("x", d);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":6.28}"));
    }

    /**
     * Test a BigInteger value.
     */
    @Test
    public void testBigInteger()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        BigInteger bi = new BigInteger("1234567890");
        jsonObj.put("x", bi);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":1234567890}"));
    }

    /**
     * Test a BigDecimal value.
     */
    @Test
    public void testBigDecimal()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        BigDecimal bd = new BigDecimal("12345.67890");
        jsonObj.put("x", bd);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":12345.67890}"));
    }

    /**
     * Test a custom number format.
     */
    @Test
    public void testNumberFormat()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        float f = 1.23456f;
        jsonObj.put("x", f);
        JSONConfig cfg = new JSONConfig();
        NumberFormat fmt = NumberFormat.getInstance();
        fmt.setMaximumFractionDigits(3);
        cfg.addNumberFormat(f, fmt);
        String json = JSONUtil.toJSON(jsonObj, cfg);
        // validateJSON(json);
        assertThat(json, is("{\"x\":1.235}"));
    }

    /**
     * Test a string value.
     */
    @Test
    public void testString()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String s = "bar";
        jsonObj.put("x", s);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":\"bar\"}"));
    }

    /**
     * Test a string with a quote value.
     */
    @Test
    public void testQuoteString()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        String s = "ba\"r";
        jsonObj.put("x", s);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":\"ba\\\"r\"}"));
    }

    /**
     * Test a string with a quote value.
     */
    @Test
    public void testNonBmp()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        StringBuilder buf = new StringBuilder(2);
        buf.appendCodePoint(0x10080);
        jsonObj.put("x", buf);
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":\"\uD800\uDC80\"}"));
    }

    /**
     * Test a Iterable value.
     */
    @Test
    public void testIterable()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();
        jsonObj.put("x", Arrays.asList(1,2,3));
        String json = JSONUtil.toJSON(jsonObj);
        //validateJSON(json);
        assertThat(json, is("{\"x\":[1,2,3]}"));
    }

    /**
     * Test an Enumeration.
     */
    @Test
    public void testEnumeration()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>();

        Vector<Integer> list = new Vector<Integer>(Arrays.asList(1,2,3));
        jsonObj.put("x", list.elements());
        String json = JSONUtil.toJSON(jsonObj);
        // validateJSON(json);
        assertThat(json, is("{\"x\":[1,2,3]}"));
    }

    /**
     * Test a Map value.
     */
    @Test
    public void testLoop()
    {
        Map<String,Object> jsonObj = new HashMap<String,Object>(4);
        jsonObj.put("a",1);
        jsonObj.put("b",2);
        jsonObj.put("c",3);
        jsonObj.put("x", jsonObj);

        JSONConfig cfg = new JSONConfig();
        try{
            JSONUtil.toJSON(jsonObj, cfg);
            fail("Expected a DataStructureLoopException to be thrown");
        }catch ( DataStructureLoopException e ){
            assertThat(e.getMessage(), containsString("java.util.HashMap includes itself which would cause infinite recursion."));
        }
    }

    /**
     * Test a resource bundle.
     */
    @Test
    public void testResourceBundle()
    {
        String bundleName = getClass().getCanonicalName();
        ResourceBundle bundle = ResourceBundle.getBundle(bundleName);
        JSONConfig cfg = new JSONConfig();

        cfg.setEncodeNumericStringsAsNumbers(false);
        //String json =
                JSONUtil.toJSON(bundle, cfg);
        // validateJSON(json);
        //assertThat(json, is("{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\",\"d\":\"4\",\"e\":\"5\",\"f\":\"6\",\"g\":\"7\",\"h\":\"8\",\"i\":\"9\",\"j\":\"10\",\"k\":\"11\",\"l\":\"12\",\"m\":\"13\",\"n\":\"14\",\"o\":\"15\",\"p\":\"16\",\"q\":\"17\",\"r\":\"18\",\"s\":\"19\",\"t\":\"20\",\"u\":\"21\",\"v\":\"22\",\"w\":\"23\",\"x\":\"24\",\"y\":\"25\",\"z\":\"26\"}"));

        cfg.setEncodeNumericStringsAsNumbers(true);
        //json =
                JSONUtil.toJSON(bundle, cfg);
        // validateJSON(json);
        //assertThat(json, is("{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5,\"f\":6,\"g\":7,\"h\":8,\"i\":9,\"j\":10,\"k\":11,\"l\":12,\"m\":13,\"n\":14,\"o\":15,\"p\":16,\"q\":17,\"r\":18,\"s\":19,\"t\":20,\"u\":21,\"v\":22,\"w\":23,\"x\":24,\"y\":25,\"z\":26}"));
    }

    /**
     * Test a complex value.
     */
    @Test
    public void testComplex()
    {
        Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
        jsonObj.put("a",1);
        jsonObj.put("b","x");
        String[] ia = {"1","2","3"};
        List<String> il = Arrays.asList(ia);
        jsonObj.put("c",ia);
        jsonObj.put("d",il);
        Object[] objs = new Object[3];
        objs[0] = null;
        objs[1] = new JSONAble()
                      {
                          public void toJSON( JSONConfig jsonConfig, Writer json ) throws BadPropertyNameException, DataStructureLoopException, IOException
                          {
                              JSONConfig cfg = jsonConfig == null ? new JSONConfig() : jsonConfig;
                              Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
                              jsonObj.put("a", 0);
                              jsonObj.put("b", 2);
                              int[] ar = {1, 2, 3};
                              jsonObj.put("x", ar);
                              jsonObj.put("t", null);

                              JSONUtil.toJSON(jsonObj, cfg, json);
                          }

						  public String toJSON()
						  {
							  return null;
						  }

						  public String toJSON( JSONConfig jsonConfig )
						  {
							  return null;
						  }

						  public void toJSON( Writer json ) throws IOException
						  {
						  }
                      };
        objs[2] = il;
        jsonObj.put("e", objs);

        JSONConfig cfg = new JSONConfig();
        String json = JSONUtil.toJSON(jsonObj, cfg);
        // validateJSON(json);
        assertThat(json, is("{\"a\":1,\"b\":\"x\",\"c\":[\"1\",\"2\",\"3\"],\"d\":[\"1\",\"2\",\"3\"],\"e\":[null,{\"a\":0,\"b\":2,\"x\":[1,2,3],\"t\":null},[\"1\",\"2\",\"3\"]]}"));
    }

    /**
     * Test indenting.
     */
    @Test
    public void testIndent()
    {
        Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
        jsonObj.put("a",1);
        jsonObj.put("b","x");
        String[] ia = {"1","2","3"};
        List<String> il = Arrays.asList(ia);
        jsonObj.put("c",ia);
        jsonObj.put("d",il);
        Object[] objs = new Object[3];
        objs[0] = null;
        objs[1] = new JSONAble()
                  {
                      public void toJSON( JSONConfig jsonConfig, Writer json ) throws BadPropertyNameException, DataStructureLoopException, IOException
                      {
                          JSONConfig cfg = jsonConfig == null ? new JSONConfig() : jsonConfig;
                          Map<String,Object> jsonObj = new LinkedHashMap<String,Object>();
                          Map<String,Object> more = new LinkedHashMap<String,Object>();
                          jsonObj.put("a", 0);
                          jsonObj.put("b", 2);
                          int[] ar = {1, 2, 3};
                          jsonObj.put("x", ar);
                          more.put("z", 4);
                          more.put("y", 2);
                          more.put("t", null);
                          jsonObj.put("w", more);

                          JSONUtil.toJSON(jsonObj, cfg, json);
                      }

                      public String toJSON()
                      {
                          return null;
                      }

                      public String toJSON( JSONConfig jsonConfig )
                      {
                          return null;
                      }

                      public void toJSON( Writer json ) throws IOException
                      {
                      }
                  };
        objs[2] = il;
        jsonObj.put("e", objs);

        JSONConfig cfg = new JSONConfig();
        cfg.setIndentPadding(new IndentPadding("\t", String.format("%n")));
        String json = JSONUtil.toJSON(jsonObj, cfg);
        //validateJSON(json);
        @SuppressWarnings("unchecked")
        Map<String,Object> obj = (Map<String,Object>)JSONParser.parseJSON(json);
        assertThat((String)obj.get("b"), is("x"));
        @SuppressWarnings("unchecked")
        ArrayList<Object> innerObj = (ArrayList<Object>)obj.get("e");
        @SuppressWarnings("unchecked")
        Map<Object,Object> jsonAble = (Map<Object,Object>)innerObj.get(1);
        assertThat(((Number)jsonAble.get("b")).intValue(), is(2));
        //System.out.println(json);
    }

    /**
     * Return true if the character is defined and not a surrogate.
     *
     * @param codePoint The code point to check.
     * @return true if the character is defined and not a surrogate.
     */
    private boolean isNormalCodePoint( int codePoint )
    {
        if ( Character.isDefined(codePoint) ){
            if ( codePoint <= 0xFFFF && JSONUtil.isSurrogate((char)codePoint) ){
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Test the oddball case where a map has keys which are not equal
     * but produce the same toString() output.
     */
    @Test
    public void testDuplicateKeys()
    {
        Map<Object,Object> jsonObj = new HashMap<Object, Object>();
        jsonObj.put(new DupStr(1), 0);
        jsonObj.put(new DupStr(2), 1);
        JSONConfig cfg = new JSONConfig();
        try{
            JSONUtil.toJSON(jsonObj, cfg);
            fail("Expected a DuplicatePropertyNameException to be thrown");
        }catch ( DuplicatePropertyNameException e ){
            assertThat(e.getMessage(), is("Property x occurs twice in the same object."));
        }
    }

    /**
     * Used by testDuplicateKeys().
     */
    private class DupStr
    {
        private int x;

        public DupStr( int x )
        {
            this.x = x;
        }

        @Override
        public String toString()
        {
            return "x";
        }

        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + x;
            return result;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj ){
                return true;
            }
            if ( obj == null ){
                return false;
            }
            if ( getClass() != obj.getClass() ){
                return false;
            }
            DupStr other = (DupStr)obj;
            if ( !getOuterType().equals(other.getOuterType()) ){
                return false;
            }
            if ( x != other.x ){
                return false;
            }
            return true;
        }

        private TestJSONUtil getOuterType()
        {
            return TestJSONUtil.this;
        }
    }

    /**
     * Test reflection.
     */
    @Test
    public void testReflect()
    {
        Map<Object,Object> jsonObj = new HashMap<Object,Object>();
        jsonObj.put("f", new ReflectTestClass());
        JSONConfig cfg = new JSONConfig();

        // JNDI set up to only show fields a and e.
        String json = JSONUtil.toJSON(jsonObj, cfg);
        assertThat(json, is("{\"f\":{\"a\":1,\"e\":25.0}}"));

        cfg.clearReflectClasses();
        cfg.addReflectClass(ReflectTestClass.class);

        cfg.setReflectionPrivacy(ReflectUtil.PRIVATE);
        json = JSONUtil.toJSON(jsonObj, cfg);
        assertThat(json, is("{\"f\":{\"a\":1,\"b\":\"something\",\"c\":[],\"d\":null}}"));

        cfg.setReflectionPrivacy(ReflectUtil.PACKAGE);
        json = JSONUtil.toJSON(jsonObj, cfg);
        assertThat(json, is("{\"f\":{\"a\":1,\"b\":\"something\",\"c\":[]}}"));

        cfg.setReflectionPrivacy(ReflectUtil.PROTECTED);
        json = JSONUtil.toJSON(jsonObj, cfg);
        assertThat(json, is("{\"f\":{\"a\":1,\"b\":\"something\"}}"));

        cfg.setReflectionPrivacy(ReflectUtil.PUBLIC);
        json = JSONUtil.toJSON(jsonObj, cfg);
        assertThat(json, is("{\"f\":{\"a\":1}}"));

        cfg = new JSONConfig(); // reload defaults.
        json = JSONUtil.toJSON(jsonObj, cfg);
        assertThat(json, is("{\"f\":{\"a\":1,\"e\":25.0}}"));


        JSONConfigDefaults.getInstance().clearReflectClasses();
        ReflectUtil.clearReflectionCache();
        cfg.setReflectionPrivacy(ReflectUtil.PRIVATE);
        cfg.addReflectClass(ReflectTestClass.class);
        cfg.setCacheReflectionData(false);
        long start = System.currentTimeMillis();
        for ( int i = 0; i < 100000; i++ ){
            json = JSONUtil.toJSON(jsonObj, cfg);
        }
        long end = System.currentTimeMillis();
        s_log.debug("uncached: "+(end-start));

        cfg.setCacheReflectionData(true);
        start = System.currentTimeMillis();
        for ( int i = 0; i < 100000; i++ ){
            json = JSONUtil.toJSON(jsonObj, cfg);
        }
        end = System.currentTimeMillis();
        s_log.debug("cached: "+(end-start));

    }
}
