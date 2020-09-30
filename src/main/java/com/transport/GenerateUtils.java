package com.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.converter.IOConverter;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

public class GenerateUtils {
    private String smzFolder;
    private String packageName;
    private String mqQueue;

    public GenerateUtils() {

    }

    public GenerateUtils(String smzFolder, String packageName, String mqQueue) {
        this.smzFolder = smzFolder;
        this.packageName = packageName;
        this.mqQueue = mqQueue;
    }

    public String setPayload(Object instanceObject) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(instanceObject);
    }

    public JsonNode setPayload(String payload) throws JsonProcessingException {
        return new ObjectMapper().readTree(payload);
    }

    public Object unmarshallerDocument(Document document, Class classesToBeBound) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(classesToBeBound);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return unmarshaller.unmarshal(document);
    }

    public Document createDocument(String documentString) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document businessDocument = documentBuilder.parse(IOConverter.toInputStream(documentString, null));
        businessDocument.getDocumentElement().normalize();
        return businessDocument;
    }

    public Object findInstance(String typeClass) throws InstantiationException, IllegalAccessException, ClassNotFoundException, MalformedURLException {
        URLClassLoader classLoader = new URLClassLoader(new URL[]{(new File(smzFolder).toURI().toURL())});
        return classLoader.loadClass(packageName + "."+ typeClass).newInstance();
    }

    public String getStringJson(JsonNode node) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    public Request mapperObject(Rows row) throws Exception {
        Document document = createDocument(row.getXmlStr());
        Object obj = unmarshallerDocument(document, findInstance(row.getMethodsName()).getClass());
        String str = setPayload(obj);
        return fillRequest(setPayload(str), row.getMethodsName());
    }

    public Request fillRequest(JsonNode payload, String methodsName) {
        Request request = new Request();
        request.setExternalId(UUID.randomUUID().toString());
        request.setPayload(payload);
        request.setRequest(methodsName);
        request.setResponseQueue(mqQueue);
        return request;
    }
}
