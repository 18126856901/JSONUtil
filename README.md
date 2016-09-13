
This is a JSON generation and parsing library for Java.  It is oriented
towards being used in web servers and includes several options for validation
and controlling the way that the JSON is generated.

#### Features
* Recursive traversal of Maps, Iterables (Collection, List, Set etc.), Enumerations, arrays and ResourceBundles
* Data structure loop detection
* [Reflection of objects is supported allowing lazy encoding of objects](https://github.com/billdavidson/JSONUtil/wiki/Using-Reflection-to-Encode-Objects-as-JSON)
* Property name validation (can use ECMAScript or JSON rules)
* Output to String or java.io.Writer
* JSON parsing to Java objects from Strings or java.io.Reader
* Options to fix certain types of bad data
* Automatic escaping of characters required to be escaped by the JSON standard.
* Several different escaping options
* Option to format dates on output and handle different formats when parsing
* ECMAScript 6 code point escapes are supported for reading and optionally for writing
* Supports Java 5, 6, 7 and 8.
* [Options to support arbitrary precision numbers](https://github.com/billdavidson/JSONUtil/wiki/Options-Which-Help-Support-Arbitrary-Precision-Arithmetic)
* [Most default configuration options can be set via JNDI](https://github.com/billdavidson/JSONUtil/wiki/Setting-Configuration-Defaults-Using-JNDI) (See the JSONConfigDefaults class)
* [Most default configuration options can be read or modified via a JMX MBean client](https://github.com/billdavidson/JSONUtil/wiki/Viewing-and-Modifying-Configuration-Defaults-Using-a-JMX-MBean-Client)
* Option to format output for debugging.

[Binaries](https://github.com/billdavidson/JSONUtil/releases)

[Getting Started Guide](https://github.com/billdavidson/JSONUtil/wiki/Getting-Started-Guide)

[Javadoc online](http://kopitubruk.org/JSONUtil/javadoc) (start with the JSONUtil class)

[JSONUtil at the Maven Central Repository](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.kopitubruk.util%22%20AND%20a%3A%22JSONUtil%22)

[Comments (Disqus)](http://kopitubruk.org/JSONUtil/#comments)

[Report Issues](https://github.com/billdavidson/JSONUtil/issues)

### What's New
* Sep 08 - [JSONUtil 1.9.3](https://github.com/billdavidson/JSONUtil/releases/tag/JSONUtil-1.9.3) released.
* Aug 24 - JSONUtil 1.9.2 released.
* Aug 17 - JSONUtil 1.9.1 released to fix bugs.
* Aug 17 - [JMX MBean Wiki page updated with images](https://github.com/billdavidson/JSONUtil/wiki/Viewing-and-Modifying-Configuration-Defaults-Using-a-JMX-MBean-Client)
* Aug 15 - JSONUtil 1.9 released to [support object reflection](https://github.com/billdavidson/JSONUtil/wiki/Using-Reflection-to-Encode-Objects-as-JSON), arbitrary precision numbers and parsing to primitive arrays.
