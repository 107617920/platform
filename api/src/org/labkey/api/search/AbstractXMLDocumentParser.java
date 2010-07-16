package org.labkey.api.search;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class that parses XML files and passes a subset of their content on to Lucene for indexing
 * User: jeckels
 * Date: Jul 15, 2010
 */
public abstract class AbstractXMLDocumentParser extends AbstractDocumentParser
{

    /** Fires off a SAX parse that doesn't do schema validation */
    public void parseContent(InputStream stream, ContentHandler handler) throws IOException, SAXException
    {
        try
        {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            parser.getXMLReader().setFeature("http://xml.org/sax/features/validation", false);
            parser.getXMLReader().setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            parser.parse(stream, createSAXHandler(handler));
        }
        catch (ParserConfigurationException e)
        {
            throw new SAXException(e);
        }
    }

    /** Creates a SAX callback handler that passes the appropriate subset of data on to Lucene */
    protected abstract DefaultHandler createSAXHandler(ContentHandler xhtmlHandler);

    public static class SAXHandler extends DefaultHandler
    {
        private final ContentHandler _xhtmlHandler;
        private final boolean _includeElementNames;
        private final boolean _includeAttributeNames;
        private final boolean _includeAttributeValues;
        private final boolean _includeText;

        private Set<String> _stopElements = new HashSet<String>();

        /**
         * @param handler Lucene listener for content
         * @param includeElementNames whether to include the names of XML elements in the index
         * @param includeAttributeNames whether to include the names of XML attributes in the index
         * @param includeAttributeValues whether to include the values of XML attributes in the index
         * @param includeText whether to XML character data in the index 
         */
        public SAXHandler(ContentHandler handler, boolean includeElementNames, boolean includeAttributeNames,
                          boolean includeAttributeValues, boolean includeText)
        {
            _xhtmlHandler = handler;
            _includeElementNames = includeElementNames;
            _includeAttributeNames = includeAttributeNames;
            _includeAttributeValues = includeAttributeValues;
            _includeText = includeText;
        }

        /** Element name at which the parsing should stop completely */
        public void addStopElement(String elementName)
        {
            _stopElements.add(elementName);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            if (_stopElements.contains(localName))
            {
                throw new ParseFinishedException();
            }

            StringBuilder sb = new StringBuilder();
            if (_includeElementNames)
            {
                sb.append(localName);
                sb.append(" ");
            }
            for (int i = 0; i < attributes.getLength(); i++)
            {
                String attributeName = attributes.getLocalName(i);
                String attributeQName = attributes.getQName(i);
                // Don't include some of the XML overhead
                if (!attributeQName.equals("xmlns") && !attributeQName.startsWith("xmlns:") && !attributeQName.equals("xsi:schemaLocation"))
                {
                    if (_includeAttributeNames)
                    {
                        sb.append(attributeName);
                        sb.append(" ");
                    }
                    if (_includeAttributeValues)
                    {
                        sb.append(attributes.getValue(i));
                        sb.append(" ");
                    }
                }
            }
            sb.append("\n");
            _xhtmlHandler.characters(sb.toString().toCharArray(), 0, sb.length());
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException
        {
            if (_includeText)
            {
                _xhtmlHandler.characters(ch, start, length);
            }
        }
    }
}
