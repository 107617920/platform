/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.util;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;


public class JunitUtil
{
    /**
     * Copy an object, much as if it were round-tripped to/from a browser
     */
    public static Object copyObject(Object o)
    {
        try
        {
            String s = PageFlowUtil.encodeObject(o);
            Object copy = PageFlowUtil.decodeObject(s);
            return copy;
        }
        catch (IOException x)
        {
            x.printStackTrace();
            assert null == "couldn't clone object " + o.toString();
            return null;
        }
    }


    public static String getAttributeValue(Node elem, String attr)
    {
        Node node = elem.getAttributes().getNamedItem(attr);
        if (null == node)
            return null;
        return node.getNodeValue();
    }


    public static Document tidyAsDocument(String html) throws Exception
    {
        ArrayList<String> errors = new ArrayList<String>();
        String tidy = PageFlowUtil.convertHtmlToXml(html, errors);

        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
        fact.setValidating(false);
        fact.setCoalescing(true);
        fact.setIgnoringComments(true);
        fact.setNamespaceAware(false);
        DocumentBuilder builder = fact.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(tidy)));
        return doc;
    }

    /**
     * Returns the container called "_junit" to be used by test cases.
     */
    public static Container getTestContainer()
    {
        return ContainerManager.ensureContainer(ContainerManager.getSharedContainer().getParsedPath().append("_junit"));
    }

    public static void main(String[] args) throws Exception
    {
        String html = "<html><body>\n<form><br><input name=A value=1><input name=B value=2><input type=hidden name=C value=3><img src=fred.png>\n</form></body></html>";
        ArrayList<String> errors = new ArrayList<String>();
        String tidy = PageFlowUtil.convertHtmlToXml(html, errors);
        System.out.println(tidy);
        Document doc = tidyAsDocument(html);

        Element rootElem = doc.getDocumentElement();
        XPathFactory xFactory = XPathFactory.newInstance();
        XPath xpath = xFactory.newXPath();
        NodeList list = (NodeList) xpath.evaluate("/html/body/form/input", rootElem, XPathConstants.NODESET);
        int len = list.getLength();
        for (int i = 0; i < len; i++)
        {
            Node n = list.item(i);
            String name = getAttributeValue(n, "name");
            String value = getAttributeValue(n, "value");
            System.err.println(n.getNodeName() + " " + name + "=" + value);
        }
    }
}