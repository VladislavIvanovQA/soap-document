package com.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.XSInstance;
import jlibs.xml.xsd.XSParser;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.xerces.xs.XSModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Mojo(name = "GenerateDoc", defaultPhase = LifecyclePhase.COMPILE)
public class GenerateDocMojo extends AbstractMojo {
    @Parameter(required = true)
    private String xsdPath;
    @Parameter(required = true)
    private String smzFolder;
    @Parameter(required = true)
    private String resourceFolder;
    @Parameter(required = true)
    private String packageName;
    @Parameter(required = true)
    private String mqQueue;

    public void generate() throws IOException {
        System.out.println("Plugin work");
        System.out.println("xsdPath: " + xsdPath);
        System.out.println("indexHtml: " + resourceFolder);
        System.out.println("smzFolder: " + smzFolder);
        System.out.println("packageName: " + smzFolder);
        System.out.println("mqQueue: " + mqQueue);
        List<String> items = new ArrayList<>();
        Map<Integer, String> rows = new HashMap<>();
        Map<Request, Request> temps = new HashMap<>();
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(new File(xsdPath));
            NodeList listMethods = doc.getElementsByTagName("xs:element");
            if (listMethods.getLength() == 0) {
                System.err.println("XSD empty!");
                return;
            } else {
                System.out.println("Methods: " + listMethods.getLength());
            }
            for (int i = 0; i < listMethods.getLength(); i++) {
                Element first = (Element) listMethods.item(i);
                if (first.hasAttributes()) {
                    if (first.getAttribute("type").isEmpty()) {
                        if (first.getAttributes().getLength() == 1) {
                            items.add(first.getAttribute("name"));
                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException ignored) {

        }
        if (items.size() == 0) {
            System.err.println("Methods not found!");
        } else {
            List<String> collect = items.stream().filter(methods -> {
                return methods.contains("Request") || methods.contains("Response");
            }).collect(Collectors.toList());

            collect.remove("RequestResult");
            int counter = 0;
            for (int i = 0; i < collect.size(); i++) {
                String str1 = collect.get(i);
                if (str1.contains("Request")) {
                    String[] requests = str1.split("Request");
                    String request = requests[0];
                    String findResponse = request + "Response";
                    if (requests.length >= 2) {
                        findResponse += requests[1];
                    }
                    if (collect.remove(findResponse)) {
                        rows.put(counter, str1 + " " + findResponse);
                        counter++;
                    } else {
                        System.err.println(str1 + " " + findResponse);
                    }
                }
            }
            System.out.println("Count pair: " + counter);
            GenerateUtils mapper = new GenerateUtils(smzFolder, packageName, mqQueue);
            rows.forEach((index, str) -> {
                String[] strings = str.split(" ");
                try {
                    Request request = mapper.mapperObject(getRows(strings[0]));
                    Request response = mapper.mapperObject(getRows(strings[1]));
                    temps.put(request, response);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            Temp temp = new Temp();
            temp.methodsList = temps;
            List<Result> entrySet = temp.getList();

            Map<String, Object> context = new HashMap<>();
            context.put("entrySet", entrySet);
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile("todo.mustache");

            StringWriter writer = new StringWriter();
            mustache.execute(writer, context).flush();
            FileWriter fWriter = null;
            BufferedWriter bufferedWriter = null;
            try {
                fWriter = new FileWriter(resourceFolder + File.separator + "index.html");
                bufferedWriter = new BufferedWriter(fWriter);
                bufferedWriter.write(writer.toString());
                bufferedWriter.newLine(); //this is not actually needed for html files - can make your code more readable though
                bufferedWriter.close(); //make sure you close the writer object
            } catch (Exception e) {
                //catch any exceptions here
            }
            System.out.println("Plugin finish");
        }
    }

    private Rows getRows(String string) {
        Rows row = new Rows();
        row.setMethodsName(string);
//        row.setXmlStr(jaxbObjectToXML(string).replace("urn", "http"));
        row.setXmlStr(jaxbObjectToXML(string));
        return row;
    }

    private String jaxbObjectToXML(String methodsName) {
        XSModel parse = new XSParser().parse(xsdPath);
        XSInstance xsInstance = new XSInstance();
        xsInstance.minimumElementsGenerated = 2;
        xsInstance.maximumElementsGenerated = 4;
        xsInstance.generateOptionalElements = Boolean.TRUE;
        xsInstance.showContentModel = false;
        xsInstance.minimumListItemsGenerated = 2;
        xsInstance.maximumListItemsGenerated = 4;

        String url = parse.getNamespaces().get(0).toString();

        try {
            QName root = new QName(url, methodsName);
            File file = new File("test.xml");
            XMLDocument xmlDocument = new XMLDocument(new StreamResult(file), false, 4, null);
            xsInstance.generate(parse, root, xmlDocument);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String everything = "";
        try (FileInputStream inputStream = new FileInputStream("test.xml")) {
            everything = IOUtils.toString(inputStream);
            // do something with everything string
        } catch (Exception e) {
            e.printStackTrace();
        }

        return everything;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            generate();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute plugin", e);
        }
    }

    private static class Temp {
        private Map<Request, Request> methodsList = new HashMap<>();
        private List<Result> results = new ArrayList<>();

        public void addItem(Request request, Request response) {
            this.methodsList.put(request, response);
        }

        public List<Result> getList() {
            GenerateUtils utils = new GenerateUtils();
            methodsList.forEach((key, value) -> {
                try {
                    String request = utils.setPayload(key);
                    String response = utils.setPayload(value);
                    Result result = new Result();
                    result.setMethodName(key.getRequest());
                    result.setRequest(utils.getStringJson(utils.setPayload(request)));
                    result.setResponse(utils.getStringJson(utils.setPayload(response)));
                    results.add(result);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            });
            return results;
        }
    }
}
