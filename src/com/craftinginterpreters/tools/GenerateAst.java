package com.craftinginterpreters.tools;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if(args.length != 1){
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];
        defineAst(outputDir,"Expr",Arrays.asList(
            "Binary : Expr left, Token operator, Expr right",
            "Grouping    : Expr expression",
            "Literal     : Object value",
            "Unary       : Token operator, Expr right"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String path = outputDir + File.separator + baseName + ".java";
        PrintWriter pWriter = new PrintWriter(path,"UTF-8");

        pWriter.println("package com.craftinginterpreters.lox;");
        pWriter.println();
        pWriter.println("import java.util.List;");
        pWriter.println();
        pWriter.println("abstract class "+baseName+" {");

        defineVisitor(pWriter,baseName,types);

        for(String type:types){
            String[] typeArray = type.split(":");
            String className = typeArray[0].trim();
            String fields = typeArray[1].trim();
            defineType(pWriter,baseName,className,fields);
        }
        pWriter.println();
        pWriter.println("   abstract <R> R accept(Visitor<R> visitor);");
        pWriter.println("}");
        pWriter.close();
    }

    private static void defineVisitor(PrintWriter pWriter, String baseName, List<String> types) {
        pWriter.println("   interface Visitor<R> {");
        for(String type:types){
            String typeName = type.split(":")[0].trim();
            pWriter.println("   R visit"+typeName+baseName+"("+typeName+" "+baseName.toLowerCase()+");");
        }
        pWriter.println("  }");
    }

    private static void defineType(PrintWriter pWriter, String baseName, String className, String fieldList) {
        pWriter.println("  static class "+className+" extends "+baseName+" {");
        //constructor
        pWriter.println("    " +className+"("+fieldList+") {");
        
        //store parameter in fields
        String[] fields = fieldList.split(", ");
        for(String field:fields){
            String name = field.split(" ")[1];
            pWriter.println("     this."+name+" = "+name+";");
        }
        pWriter.println("     }");

        pWriter.println();
        pWriter.println("    @Override");
        pWriter.println("    <R> R accept(Visitor<R> visitor) {");
        pWriter.println("      return visitor.visit"+className+baseName+"(this);");
        pWriter.println("    }");

        //Fields
        pWriter.println();
        for(String field:fields){
            pWriter.println("    final "+field+";");
        }

        pWriter.println("  }");
    }
    
}
