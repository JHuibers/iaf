/*
   Copyright 2017-2018 Nationale-Nederlanden

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.xml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.log4j.Logger;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;

import nl.nn.adapterframework.configuration.classloaders.BytesClassLoader;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.LogUtil;

/**
 * Xerces native EntityResolver. Appears to be only used in XercesXmlValidator currently.
 * @author Jaco de Groot
 * @see ClassLoaderURIResolver
 */
public class ClassLoaderXmlEntityResolver implements XMLEntityResolver {
	private Logger log = LogUtil.getLogger(this);
	private ClassLoader classLoader;

	public ClassLoaderXmlEntityResolver(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public XMLInputSource resolveEntity(XMLResourceIdentifier resourceIdentifier) throws XNIException, IOException {
		if (log.isDebugEnabled()) log.debug("resolveEntity publicId ["+resourceIdentifier.getPublicId()+"] expandedSystemId ["+resourceIdentifier.getExpandedSystemId()+"] literalSystemId ["+resourceIdentifier.getLiteralSystemId()+"] namespace ["+resourceIdentifier.getNamespace()+"]");
		String systemId = resourceIdentifier.getExpandedSystemId();
		if (resourceIdentifier.getBaseSystemId() == null
				&& resourceIdentifier.getExpandedSystemId() == null
				&& resourceIdentifier.getLiteralSystemId() == null
				&& resourceIdentifier.getNamespace() == null
				&& resourceIdentifier.getPublicId() == null) {
			// This seems to happen sometimes. For example with import of
			// sub01a.xsd and sub05.xsd without namespace in
			// /XmlValidator/import_include/root.xsd of Ibis4TestIAF. The
			// default resolve entity implementation seems to ignore it, hence
			// return null.
			return null;
		}
		if (systemId == null) {
			// Ignore import with namespace but without schemaLocation
			return null;
		}
		if (systemId.length() == 0 || systemId.equals(BytesClassLoader.PROTOCOL + ":")) {
			String message = "Cannot resolve entity with empty systemId";
			log.warn(message); // TODO remove this warning, when sure IOException is properly logged
			throw new IOException(message);
		}
		// Apparently the resource was already resolved to a URL as the
		// systemId is in URL syntax (but a string object instead of a URL).
		// We need to convert it back to a URL which the JVM is able to do for
		// standard url's but for our custom class loader we need to do it
		// manually.
		URL url = null;
		try {
			url = ClassUtils.getResourceURL(classLoader, systemId);
			if (url==null) {
				String message = "cannot find resource for entity [" + systemId + "]";
				log.warn(message); // TODO remove this warning, when sure IOException is properly logged
				throw new IOException(message);
			}
		} catch (Exception e) {
			String message = "Exception resolving entity [" + systemId + "]";
			log.warn(message,e); // TODO remove this warning, when sure IOException is properly logged
			throw new IOException(message,e);
		}
		InputStream inputStream = url.openStream();
		return new XMLInputSource(null, resourceIdentifier.getExpandedSystemId(), null, inputStream, null);
	}

}
