/**
 * (C) Copyright IBM Corporation 2023, 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */package io.openliberty.tools.common.plugins.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.openliberty.tools.common.CommonLoggerI;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import static io.openliberty.tools.common.plugins.config.ServerConfigDocument.XPATH_SERVER_VARIABLE;

public class VariableUtility {
    private static final String VARIABLE_NAME_PATTERN = "\\$\\{(.*?)\\}";
    private static final Pattern varNamePattern = Pattern.compile(VARIABLE_NAME_PATTERN);

    /**
     * Attempts to resolve all variables in the passed in nodeValue. Variable value/defaultValue can reference other variables.
     * This method is called recursively to resolve the variables. The variableChain collection keeps track of the variable references
     * in a resolution chain in order to prevent an infinite loop. The variableChain collection should be passed as null on the initial call.
     *
     * NOTE: This method also replaces all back slashes with forward slashes
     */
    public static String resolveVariables(CommonLoggerI log, String nodeValue, Collection<String> variableChain,
                                            Properties props, Properties defaultProps, Map<String, File> libDirPropFiles) {

        // For Windows, avoid escaping the backslashes in the resolvedValue by changing to forward slashes
        String resolved = nodeValue.replace("\\","/");
        Matcher varNameMatcher = varNamePattern.matcher(nodeValue);

        Collection<String> variablesToResolve = new HashSet<String> ();

        while (varNameMatcher.find()) {
            String varName = varNameMatcher.group(1);
            if (variableChain != null && variableChain.contains(varName)) {
                // Found recursive reference when resolving variables. Log message and return null.
                log.debug("Found a recursive variable reference when resolving ${" + varName + "}");
                return null;
            }
            variablesToResolve.add(varName);
        }

        for (String nextVariable : variablesToResolve) {
            String value = getPropertyValue(nextVariable, props, defaultProps, libDirPropFiles);

            if (value == null) {
                // Variable could not be resolved. Log message and return null.
                log.debug("Variable " + nextVariable + " cannot be resolved.");
                return null;
            }

            Collection<String> thisVariableChain = new HashSet<String> ();
            thisVariableChain.add(nextVariable);

            if (variableChain != null && !variableChain.isEmpty()) {
                thisVariableChain.addAll(variableChain);
            }

            String resolvedValue = resolveVariables(log, value, thisVariableChain, props, defaultProps, libDirPropFiles);

            if (resolvedValue != null) {
                String escapedVariable = Matcher.quoteReplacement(nextVariable);
                // For Windows, avoid escaping the backslashes in the resolvedValue by changing to forward slashes
                resolvedValue = resolvedValue.replace("\\","/");
                resolved = resolved.replaceAll("\\$\\{" + escapedVariable + "\\}", resolvedValue);
            } else {
                // Variable could not be resolved. Log message and return null.
                log.debug("Variable " + nextVariable + " cannot be resolved.");
                return null;
            }
        }

        log.debug("Expression "+ nodeValue +" evaluated and replaced with "+resolved);

        return resolved;
    }

    // TODO: Integer value properties can be evaluated if 'simple' arithemetic
    // TODO: A list of ports can be defined using keyword 'list', e.g. list(httpPort) -> 89,9889 versus literal '89,9889'
    public static String getPropertyValue(String propertyName, Properties prop, Properties defaultProps, Map<String, File> libertyDirPropFiles) {
        String value = null;
        if (libertyDirPropFiles.containsKey(propertyName)) {
            return stripQuotes(libertyDirPropFiles.get(propertyName).toString());
        }

        value = lookupProperty(prop, defaultProps, propertyName);
        if (value != null) {
            return value;
        }

        // try again with non-alphanumeric values replaced with '_', which is exactly \W in regex
        String propertyNameVariation = propertyName.replaceAll("\\W", "_");
        value = lookupProperty(prop, defaultProps, propertyNameVariation);
        if (value != null) {
            return value;
        }

        // try again with propertyNameVariation.toUpperCase()
        propertyNameVariation = propertyNameVariation.toUpperCase();
        value = lookupProperty(prop, defaultProps, propertyNameVariation);
        if (value != null) {
            return value;
        }

        // support for versions <19.0.0.3. Look for property without the 'env.' prefix
        if (propertyName != null && propertyName.startsWith("env.") && propertyName.length() > 4) {
            value = lookupProperty(prop, defaultProps, propertyName.substring(4));
            if (value != null) {
                return value;
            }
        }

        return value;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String lookupProperty(Properties prop, Properties defaultProps, String propertyName) {
        if (prop.containsKey(propertyName)) {
            return stripQuotes(prop.getProperty(propertyName));
        }
        if (defaultProps.containsKey(propertyName)) {
            return stripQuotes(defaultProps.getProperty(propertyName));
        }
        return null;
    }

    /**
     * <p>Parse variables for an xml document</p>
     * @param doc xml doc
     * @param defaultValues populate only default values property object
     * @param values populate only actual values property object
     * @param both both values property object will be populated
     * @return list of properties, including actual and default values
     * @throws XPathExpressionException
     */
    public static List<Properties> parseVariables(Document doc, boolean defaultValues, boolean values, boolean both) throws XPathExpressionException {
        // parse input document
        NodeList nodeList = (NodeList) XPATH_SERVER_VARIABLE.evaluate(doc, XPathConstants.NODESET);
        Properties props = new Properties();
        Properties defaultProps = new Properties();

        for (int i = 0; i < nodeList.getLength(); i++) {
            NamedNodeMap attr = nodeList.item(i).getAttributes();

            String varName = attr.getNamedItem("name").getNodeValue();

            if (!varName.isEmpty()) {
                // A variable can have either a value attribute OR a defaultValue attribute.
                String varValue = getValue(attr, "value");
                String varDefaultValue = getValue(attr, "defaultValue");

                if ((values || both) && varValue != null) {
                    props.setProperty(varName, varValue);
                }

                if ((defaultValues || both) && varDefaultValue != null) {
                    defaultProps.setProperty(varName, varDefaultValue);
                }
            }
        }
        List<Properties> result = new ArrayList<>();
        result.add(props);
        result.add(defaultProps);
        return result;
    }

    private static String getValue(NamedNodeMap attr, String nodeName) {
        String value = null;
        Node valueNode = attr.getNamedItem(nodeName);
        if (valueNode != null) {
            value = valueNode.getNodeValue();
        }
        return value;
    }

}