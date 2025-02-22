package nl.nn.adapterframework.xml;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.URL;
import java.util.jar.JarFile;

import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.junit.Test;
import org.xml.sax.SAXException;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.configuration.classloaders.JarFileClassLoader;

public class ClassLoaderXmlEntityResolverTest {

	private String publicId="fakePublicId";
	
	@Test
	public void localClassPathFileOnRootOfClasspath() throws SAXException, IOException {
		ClassLoader localClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoaderXmlEntityResolver resolver = new ClassLoaderXmlEntityResolver(localClassLoader);
		XMLResourceIdentifier resourceIdentifier = new ResourceIdentifier(); 
		resourceIdentifier.setPublicId(publicId);
		
		resourceIdentifier.setExpandedSystemId("AppConstants.properties"); // this file is known to be in the root of the classpath
		
		XMLInputSource inputSource = resolver.resolveEntity(resourceIdentifier);
		assertNotNull(inputSource);
	}

	@Test
	public void localClassPathFileOnRootOfClasspathAbsolute() throws SAXException, IOException {
		ClassLoader localClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoaderXmlEntityResolver resolver = new ClassLoaderXmlEntityResolver(localClassLoader);
		XMLResourceIdentifier resourceIdentifier = new ResourceIdentifier(); 
		resourceIdentifier.setPublicId(publicId);

		resourceIdentifier.setExpandedSystemId("/AppConstants.properties"); // this file is known to be in the root of the classpath

		XMLInputSource inputSource = resolver.resolveEntity(resourceIdentifier);
		assertNotNull(inputSource);
	}

	@Test
	public void localClassPathAbsolute() throws SAXException, IOException {
		ClassLoader localClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoaderXmlEntityResolver resolver = new ClassLoaderXmlEntityResolver(localClassLoader);
		XMLResourceIdentifier resourceIdentifier = new ResourceIdentifier(); 
		resourceIdentifier.setPublicId(publicId);

		resourceIdentifier.setExpandedSystemId("/Xslt/importDocument/lookup.xml");
		
		XMLInputSource inputSource = resolver.resolveEntity(resourceIdentifier);
		assertNotNull(inputSource);
	}

	
	@Test
	public void bytesClassPath() throws SAXException, IOException, ConfigurationException {
		ClassLoader localClassLoader = Thread.currentThread().getContextClassLoader();

		URL file = this.getClass().getResource("/classLoader-test.zip");
		assertNotNull("jar url not found", file);
		JarFile jarFile = new JarFile(file.getFile());
		assertNotNull("jar file not found",jarFile);

		JarFileClassLoader cl = new JarFileClassLoader(localClassLoader);
		cl.setJar(file.getFile());
		cl.configure(null, "");

		ClassLoaderXmlEntityResolver resolver = new ClassLoaderXmlEntityResolver(cl);
		XMLResourceIdentifier resourceIdentifier = new ResourceIdentifier(); 
		resourceIdentifier.setPublicId(publicId);

		resourceIdentifier.setExpandedSystemId("Xslt/names.xsl");

		XMLInputSource inputSource = resolver.resolveEntity(resourceIdentifier);
		assertNotNull(inputSource);
	}

	@Test
	public void bytesClassPathAbsolute() throws SAXException, IOException, ConfigurationException  {
		ClassLoader localClassLoader = Thread.currentThread().getContextClassLoader();

		URL file = this.getClass().getResource("/classLoader-test.zip");
		assertNotNull("jar url not found", file);
		JarFile jarFile = new JarFile(file.getFile());
		assertNotNull("jar file not found",jarFile);

		JarFileClassLoader cl = new JarFileClassLoader(localClassLoader);
		cl.setJar(file.getFile());
		cl.configure(null, "");

		ClassLoaderXmlEntityResolver resolver = new ClassLoaderXmlEntityResolver(cl);
		XMLResourceIdentifier resourceIdentifier = new ResourceIdentifier(); 
		resourceIdentifier.setPublicId(publicId);

		resourceIdentifier.setExpandedSystemId("/Xslt/names.xsl");

		XMLInputSource inputSource = resolver.resolveEntity(resourceIdentifier);
		assertNotNull(inputSource);
	}

	private class ResourceIdentifier implements XMLResourceIdentifier {

		private String publicId;
		private String expandedSystemId;
		private String literalSystemId;
		private String baseSystemId;
		private String namespace;
		
		@Override
		public void setPublicId(String publicId) {
			this.publicId=publicId;
		}
		@Override
		public String getPublicId() {
			return publicId;
		}

		@Override
		public void setExpandedSystemId(String systemId) {
			this.expandedSystemId=systemId;
		}
		@Override
		public String getExpandedSystemId() {
			return expandedSystemId;
		}

		@Override
		public void setLiteralSystemId(String systemId) {
			this.literalSystemId=systemId;
		}
		@Override
		public String getLiteralSystemId() {
			return literalSystemId;
		}

		@Override
		public void setBaseSystemId(String systemId) {
			this.baseSystemId=systemId;
		}
		@Override
		public String getBaseSystemId() {
			return baseSystemId;
		}

		@Override
		public void setNamespace(String namespace) {
			this.namespace=namespace;
		}
		@Override
		public String getNamespace() {
			return namespace;
		}
		
	}

}
