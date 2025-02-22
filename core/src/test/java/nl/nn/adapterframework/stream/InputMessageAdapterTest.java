/*
   Copyright 2019 Integration Partners

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
package nl.nn.adapterframework.stream;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import nl.nn.adapterframework.util.StreamUtil;
import nl.nn.adapterframework.util.XmlUtils;

public class InputMessageAdapterTest {

	private boolean TEST_CDATA=true;
	private String CDATA_START=TEST_CDATA?"<![CDATA[":"";
	private String CDATA_END=TEST_CDATA?"]]>":"";

	protected String testString="<root><sub>abc&amp;&lt;&gt;</sub><sub>"+CDATA_START+"<a>a&amp;b</a>"+CDATA_END+"</sub></root>";
	
	
	protected void testAsStream(InputMessageAdapter adapter) throws IOException {
		InputStream result = adapter.asInputStream();
		String actual = StreamUtil.streamToString(result, null, "UTF-8");
		assertEquals(testString, actual);
	}
	
	protected void testAsReader(InputMessageAdapter adapter) throws IOException {
		Reader result = adapter.asReader();
		String actual = StreamUtil.readerToString(result, null);
		assertEquals(testString, actual);
	}

	protected void testAsInputSource(InputMessageAdapter adapter) throws IOException, SAXException {
		InputSource result = adapter.asInputSource();
		XmlWriter sink =  new XmlWriter();
		XmlUtils.parseXml(sink, result);
		
		String actual = sink.toString();
		assertEquals(testString, actual);
	}
	
	
	@Test
	public void testStreamAsStream() throws Exception {
		ByteArrayInputStream source = new ByteArrayInputStream(testString.getBytes());
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsStream(adapter);
	}
	
	@Test
	public void testStreamAsReader() throws Exception {
		ByteArrayInputStream source = new ByteArrayInputStream(testString.getBytes());
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsReader(adapter);
	}
	
	@Test
	public void testStreamAsInputSource() throws Exception {
		ByteArrayInputStream source = new ByteArrayInputStream(testString.getBytes());
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsInputSource(adapter);
	}
	
	
	@Test
	public void testReaderAsStream() throws Exception {
		StringReader source = new StringReader(testString);
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsStream(adapter);
	}

	@Test
	public void testReaderAsReader() throws Exception {
		StringReader source = new StringReader(testString);
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsReader(adapter);
	}

	@Test
	public void testReaderAsInputSource() throws Exception {
		StringReader source = new StringReader(testString);
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsInputSource(adapter);
	}


	@Test
	public void testStringAsStream() throws Exception {
		String source = testString;
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsStream(adapter);
	}

	@Test
	public void testStringAsReader() throws Exception {
		String source = testString;
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsReader(adapter);
	}

	@Test
	public void testStringAsInputSource() throws Exception {
		String source = testString;
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsInputSource(adapter);
	}


	@Test
	public void testByteArrayAsStream() throws Exception {
		byte[] source = testString.getBytes();
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsStream(adapter);
	}

	@Test
	public void testByteArrayAsReader() throws Exception {
		byte[] source = testString.getBytes();
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsReader(adapter);
	}

	@Test
	public void testByteArrayAsInputSource() throws Exception {
		byte[] source = testString.getBytes();
		InputMessageAdapter adapter = new InputMessageAdapter(source);
		testAsInputSource(adapter);
	}

}
