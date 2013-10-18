
package ch.loway.oss.ari4java.codegen;

import ch.loway.oss.ari4java.codegen.genJava.JavaInterface;
import ch.loway.oss.ari4java.codegen.models.Action;
import ch.loway.oss.ari4java.codegen.models.Apis;
import ch.loway.oss.ari4java.codegen.models.Operation;
import ch.loway.oss.ari4java.codegen.models.Model;
import ch.loway.oss.ari4java.codegen.models.ModelField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 *
 * $Id$
 * @author lenz
 */
public class DefMapper {

    List<Model> mymodels = new ArrayList<Model>();
    List<String> knownModels = new ArrayList<String>();

    List<Apis> myAPIs = new ArrayList<Apis>();

    Map<String, JavaInterface> interfaces = new HashMap<String, JavaInterface>();

    public void load( File f, String apiVersion ) throws IOException {

        ObjectMapper om = new ObjectMapper();
        System.out.println( "Loading as: " + f.getAbsolutePath() );

        JsonNode rootNode = om.readTree(f);
        loadModels(rootNode.get("models"), f, apiVersion );
        loadApis( rootNode.get("apis"), f, apiVersion );

        // Now generate common APIs
        for ( Model m: mymodels ) {
            JavaInterface j = interfaces.get(m.getInterfaceName());
            if ( j == null ) {
                j = new JavaInterface();
                j.pkgName = "ch.loway.oss.ari4java.generated";
                j.className = m.getInterfaceName();
                interfaces.put(m.getInterfaceName(), j);
            }

            m.registerInterfaces(j);
        }

        for ( Apis api: myAPIs ) {
            JavaInterface j = interfaces.get(api.getInterfaceName());
            if ( j == null ) {
                j = new JavaInterface();
                j.pkgName = "ch.loway.oss.ari4java.generated";
                j.className = api.getInterfaceName();
                interfaces.put(api.getInterfaceName(), j);
            }

            api.registerInterfaces(j);
        }



        //
        for ( String ifName: interfaces.keySet() ) {
            JavaInterface ji = interfaces.get(ifName);
            saveToDisk(ji);
        }

        



    }

    private void loadModels(JsonNode models, File f, String apiVersion) throws IOException {
        
        for (JsonNode modelName : models) {
            // Creazione di un modello
            Model currentModel = new Model();
            String thisModel = txt(modelName.get("id"));
            String thisModelDesc = txt(modelName.get("description"));

            currentModel.setPackageInfo(thisModel, apiVersion);
            currentModel.description = thisModelDesc;
            currentModel.comesFromFile = f.getName();
            currentModel.extendsModel = extendsObject(modelName);
            knownModels.add(thisModel);

            JsonNode properties = modelName.get("properties");
            Iterator<String> propNames = properties.fieldNames();
            while (propNames.hasNext()) {
                String field = propNames.next();
                JsonNode property = properties.get(field);
                String javaType = remapType(txt(property.get("type")));
                String comment = txt(property.get("description"));
                ModelField mf = new ModelField();
                mf.field = field;
                mf.type = javaType;
                mf.comment = comment;
                currentModel.fileds.add(mf);
            }
            mymodels.add(currentModel);
            System.out.println(currentModel.toString());

            saveToDisk( currentModel);
        }
    }


    private void loadApis(JsonNode apis, File f, String apiVersion ) throws IOException {

        Apis api = new Apis();

        api.setPackageInfo( f.getName().replace(".json", ""), apiVersion);


        for (JsonNode apiEntry : apis) {

            Action action = new Action();
            action.path = txt(apiEntry.get("path"));
            action.description = txt(apiEntry.get("description"));
            action.javaFile = f.getName();

            for (JsonNode operation : apiEntry.get("operations")) {
                Operation op = new Operation();
                action.operations.add(op);
                op.method = txt(operation.get("httpMethod"));
                op.nickname = txt(operation.get("nickname"));
                op.responseClass = remapType(txt(operation.get("responseClass")));

                JsonNode parameters = operation.get("parameters");
                if (parameters != null) {
                    for (JsonNode parameter : parameters) {
                        Operation.Param p = new Operation.Param();
                        p.javaType = remapType(txt(parameter.get("dataType")));
                        p.name = txt(parameter.get("name"));
                        p.required = txt(parameter.get("required")).equalsIgnoreCase("true");
                        p.inUri = txt(parameter.get("paramType")).equalsIgnoreCase( "path");

                        op.parms.add(p);

                    }
                }

                JsonNode errorResponses = operation.get("errorResponses");
                if (errorResponses != null) {
                    for (JsonNode errorResponse : errorResponses) {
                        Operation.ErrorResp err = new Operation.ErrorResp();
                        err.code = errorResponse.get("code").asInt();
                        err.reason = errorResponse.get("reason").asText();
                        op.errorCodes.add(err);
                    }
                }


            }

            System.out.println( action.toString() );
            api.actions.add(action);
        }

        myAPIs.add(api);
        // salva su disco
        saveToDisk(api);

    }

    public void saveToDisk( String baseJavaClasses, String pkgName, String className, String classText  ) throws IOException {

        String fName = baseJavaClasses
                     + pkgName.replace(".", "/" )
                     + "/"
                     + className + ".java";

        FileWriter outFile = new FileWriter( fName );
        PrintWriter out = new PrintWriter(outFile);
        out.println( classText );
        out.close();

    }

    public void saveToDisk( Model model ) throws IOException {
        saveToDisk( "classes/", model.getModelPackage(), model.getImplName(), model.toString() );
    }

    public void saveToDisk( Apis api ) throws IOException {
        saveToDisk( "classes/", api.getActionsPackage(), api.getImplName(), api.toString() );
    }

    public void saveToDisk( JavaInterface ji ) throws IOException {
        saveToDisk( "classes/", "ch.loway.oss.ari4java.generated", ji.className, ji.toString() );
    }


    private String txt( JsonNode n ) {
        if ( n == null ) {
            return "";
        } else {
            return n.asText();
        }
    }

    public String extendsObject( JsonNode model ) {
        if ( model.get( "extends" ) != null ) {
            return remapType( model.get("extends").asText() );
        }
        return "";

    }

    public String remapType( String jsonType ) {

        String listAry = "List[";

        if ( jsonType.startsWith( listAry ) ) {
            return "List<" + remapType( jsonType.substring(listAry.length(), jsonType.length()-1 )) + ">";
        }
        if ( jsonType.equalsIgnoreCase("string") ) {
            return "String";
        } else
        if ( jsonType.equalsIgnoreCase("long") ) {
            return "long";
        } else
        if ( jsonType.equalsIgnoreCase("int") ) {
            return "int";
        } else
        if ( jsonType.equalsIgnoreCase("Date") ) {
            return "Date";
        } else
        if ( jsonType.equalsIgnoreCase("object") ) {
            return "String";
        } else
        if ( jsonType.equalsIgnoreCase("boolean") ) {
            return "boolean";
        } else
        {
            return jsonType;
        }

//        if ( knownModels.contains(jsonType) ) {
//            return jsonType;
//        }
//        else
//        {
//            throw new IllegalArgumentException("Unknown type: " + jsonType );
//        }
    }







}

// $Log$
//
